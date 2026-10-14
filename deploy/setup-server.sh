#!/usr/bin/env bash
# Vidoc Note 服务器一键初始化。把整个 bundle 目录传到服务器后执行：
#   bash setup-server.sh
# 幂等：已完成的步骤会跳过，可以反复重跑。
# 配置全部读同目录的 site.env，本脚本不含任何密钥。
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$HERE"

REQUIRED=(site.env vidoc.env vidoc-server.jar vidoc.service nginx.conf api-proxy.conf security-headers.conf schema.sql)
for f in "${REQUIRED[@]}"; do
    [ -f "$f" ] || { echo "缺少文件：$HERE/$f"; exit 1; }
done

# shellcheck disable=SC1091
. ./site.env
: "${ACME_EMAIL:?site.env 缺少 ACME_EMAIL}"
: "${WEB_DOMAIN:?site.env 缺少 WEB_DOMAIN}"
: "${API_DOMAIN:?site.env 缺少 API_DOMAIN}"
: "${DB_NAME:?site.env 缺少 DB_NAME}"
: "${DB_USER:?site.env 缺少 DB_USER}"
: "${DB_PASSWORD:?site.env 缺少 DB_PASSWORD}"

[ "$(id -u)" = 0 ] || { echo "请用 root 运行：sudo bash setup-server.sh"; exit 1; }

step() { printf '\n===== %s =====\n' "$1"; }

step "0/8 DNS 前置检查"
MYIP="$(curl -fsS -4 --max-time 10 https://api.ipify.org || true)"
RESOLVED="$(getent ahostsv4 "$API_DOMAIN" | awk 'NR==1{print $1}' || true)"
echo "$API_DOMAIN -> ${RESOLVED:-（无解析）}"
echo "本机公网 IP -> ${MYIP:-（取不到）}"
[ -n "$RESOLVED" ] || { echo "❌ DNS 还没生效：先加 A 记录 $API_DOMAIN -> $MYIP，生效后重跑本脚本"; exit 1; }
[ "$RESOLVED" = "$MYIP" ] || echo "⚠️  解析地址与本机不一致，证书签发可能失败（继续尝试）"

step "1/8 安装依赖（Java 17 / nginx / certbot / MySQL，2~5 分钟，期间有进度输出）"
# NEEDRESTART_SUSPEND：Ubuntu 24.04 默认的 needrestart 会在装完后弹交互菜单，
#   在网页终端里可能渲染不出来、看起来像卡死 —— 直接关掉它
# Dpkg::Options：配置文件冲突不询问，一律保留现有版本（非交互下最安全）
export DEBIAN_FRONTEND=noninteractive
export NEEDRESTART_SUSPEND=1
DPKG_OPTS=(-o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold)
apt-get update
apt-get install -y "${DPKG_OPTS[@]}" \
    openjdk-17-jre-headless nginx certbot mysql-server curl

step "2/8 yt-dlp（官方二进制 + 每日自更新）"
# apt 里的版本太旧会被 YouTube 拒；yt-dlp 每几周一变，-U 是唯一必需的运维项
if [ ! -x /usr/local/bin/yt-dlp ]; then
    curl -fsSL -o /usr/local/bin/yt-dlp https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux
    chmod +x /usr/local/bin/yt-dlp
fi
/usr/local/bin/yt-dlp --version
crontab -l 2>/dev/null | grep -q 'yt-dlp -U' \
    || { crontab -l 2>/dev/null; echo '0 4 * * * /usr/local/bin/yt-dlp -U'; } | crontab -

step "3/8 防火墙（只放 SSH / 80 / 443）"
# ⚠️ 后端 8080 绑在 0.0.0.0 且没有 server.address 收口，不挡就是全网可直连、绕过 TLS 与限流
SSH_PORT="${SSH_CONNECTION##* }"
case "$SSH_PORT" in ''|*[!0-9]*) SSH_PORT=22 ;; esac
ufw allow "$SSH_PORT/tcp" >/dev/null
ufw allow 80/tcp >/dev/null
ufw allow 443/tcp >/dev/null
ufw --force enable >/dev/null
ufw status | head -8

step "4/8 运行用户与目录"
id -u vidoc >/dev/null 2>&1 \
    || useradd --system --create-home --home-dir /opt/vidoc --shell /usr/sbin/nologin vidoc
mkdir -p /opt/vidoc
chown -R vidoc:vidoc /opt/vidoc

step "5/8 数据库（schema.sql 建库 + 专用账号）"
# schema.sql 里硬编码了库名 vidoc，所以 DB_NAME 保持 vidoc
systemctl enable --now mysql
# apt 装完不代表 mysqld 已经能接受连接（2C 机器上首次初始化数据字典要几十秒），
# 不等就会拿到 "Can't connect to local MySQL server through socket"，然后脚本在第 5 步中断
for i in $(seq 1 30); do
    mysqladmin ping --silent 2>/dev/null && break
    echo "等 mysqld 就绪…（$i/30）"
    sleep 2
done
mysqladmin ping --silent 2>/dev/null \
    || { echo "❌ mysqld 没起来：systemctl status mysql --no-pager -l"; exit 1; }

echo "载入 schema.sql…"
# schema.sql 里的 CREATE TABLE 不带 IF NOT EXISTS：库里已经有表时直接重跑会以 1050 中断整个脚本。
# 所以先数表：空库严格载入（有错就报），已有表则 --force 增量补齐、忽略「表已存在」。
TABLE_COUNT="$(mysql -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME'" 2>/dev/null || echo 0)"
if [ "${TABLE_COUNT:-0}" = "0" ]; then
    mysql < schema.sql
else
    echo "库里已有 $TABLE_COUNT 张表，增量载入（已存在的表会报 1050，已忽略）"
    mysql --force < schema.sql
fi
mysql -e "CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASSWORD';"
mysql -e "ALTER USER '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASSWORD';"
mysql -e "GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO '$DB_USER'@'localhost'; FLUSH PRIVILEGES;"
mysql -e "SELECT COUNT(*) AS tables_created FROM information_schema.tables WHERE table_schema='$DB_NAME';"
echo "用应用账号试连一次（密码错会在这里就炸，而不是等到起后端）"
mysql -u "$DB_USER" -p"$DB_PASSWORD" -e "SELECT 1 AS ok FROM DUAL;" "$DB_NAME"

step "6/8 后端（systemd）"
install -o vidoc -g vidoc -m 644 vidoc-server.jar /opt/vidoc/vidoc-server.jar
install -o root -g root -m 600 vidoc.env /opt/vidoc/vidoc.env
install -o root -g root -m 644 vidoc.service /etc/systemd/system/vidoc.service
systemctl daemon-reload
systemctl enable vidoc >/dev/null
systemctl restart vidoc
sleep 10
echo "服务状态：$(systemctl is-active vidoc)"
curl -s -o /dev/null -w "本机 http://127.0.0.1:8080/api/auth/me -> %{http_code}（401 = 正常，鉴权生效）\n" \
    http://127.0.0.1:8080/api/auth/me || true
echo "--- 最近日志 ---"
journalctl -u vidoc -n 12 --no-pager || true

step "7/8 nginx + 证书"
mkdir -p /var/www/vidoc/.well-known/acme-challenge
rm -f /etc/nginx/sites-enabled/default
install -o root -g root -m 644 security-headers.conf /etc/nginx/snippets/vidoc-security-headers.conf
install -o root -g root -m 644 api-proxy.conf /etc/nginx/snippets/vidoc-api-proxy.conf

# 443 的 server 块引用了证书文件，证书不存在时 nginx -t 必失败 → 先只放 80 的临时配置签证书
if [ ! -d "/etc/letsencrypt/live/$WEB_DOMAIN" ]; then
    cat > /etc/nginx/sites-available/vidoc <<EOF
server {
    listen 80;
    server_name $API_DOMAIN;
    location ^~ /.well-known/acme-challenge/ { root /var/www/vidoc; }
    location / { return 404; }
}
EOF
    ln -sf /etc/nginx/sites-available/vidoc /etc/nginx/sites-enabled/vidoc
    nginx -t
    systemctl reload nginx
    certbot certonly --webroot -w /var/www/vidoc --cert-name "$WEB_DOMAIN" -d "$API_DOMAIN" \
        --agree-tos -m "$ACME_EMAIL" --no-eff-email -n
else
    echo "证书已存在，跳过签发（续期由 certbot.timer 自动处理）"
fi

install -o root -g root -m 644 nginx.conf /etc/nginx/sites-available/vidoc
ln -sf /etc/nginx/sites-available/vidoc /etc/nginx/sites-enabled/vidoc
nginx -t || { echo "❌ nginx 配置有语法错误（nginx 仍跑着旧配置，没断服务）。把上面的输出发给我。"; exit 1; }
systemctl reload nginx

step "8/8 验收"
curl -s -o /dev/null -w "https://$API_DOMAIN/api/auth/me -> %{http_code}（401 = 正常）\n" \
    "https://$API_DOMAIN/api/auth/me" || true
echo "CORS 头："
curl -s -i -H "Origin: https://$WEB_DOMAIN" "https://$API_DOMAIN/api/auth/me" \
    | grep -i '^access-control-allow-origin' || echo "⚠️  没看到 CORS 头，检查 VIDOC_CORS_ALLOWED_ORIGINS"

cat <<EOF

================ 服务器侧完成 ================
后端  https://$API_DOMAIN     （systemd 服务名 vidoc，日志：journalctl -u vidoc -f）
静态  /var/www/vidoc          （域名迁回本机时才生效，现在前端在 EdgeOne Pages）

剩下两件只能在浏览器里做的：
  1. 前端重新构建并发布到 EdgeOne Pages（本地跑 deploy\\go.ps1 -Web）
  2. 确认登录能过之后，把 /opt/vidoc/vidoc.env 的 VIDOC_TURNSTILE_ENABLED 改成 true 并
     systemctl restart vidoc（重启会让所有已登录用户掉线，挑低峰）
EOF
