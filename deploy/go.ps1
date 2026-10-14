# Vidoc 部署：本地一条命令完成「打包产物 → 上传 → 服务器初始化 → 验收」。
#
#   .\deploy\go.ps1 -Server 1.2.3.4            # 部署后端
#   .\deploy\go.ps1 -Server 1.2.3.4 -Web       # 再顺手构建前端产物
#
# 前置：springboot-server\.env.deploy 已填好（含线上密钥）、deploy\site.env 已填好、
#       服务器可 SSH 登录（推荐先把本机公钥加到服务器，否则每次要输密码）。
# 本脚本不打印任何密钥。
param(
    [Parameter(Mandatory = $true)][string]$Server,
    [string]$User = 'root',
    [switch]$Web,
    [string]$TurnstileSiteKey = '0x4AAAAAAFEMMofOf3Sud_8x',
    [switch]$SkipSetup
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$SSH = Join-Path $env:SystemRoot 'System32\OpenSSH\ssh.exe'
$SCP = Join-Path $env:SystemRoot 'System32\OpenSSH\scp.exe'
foreach ($exe in @($SSH, $SCP)) {
    if (-not (Test-Path $exe)) { throw "找不到 $exe" }
}

function Step($t) { Write-Host "`n===== $t =====" -ForegroundColor Cyan }

# ssh / scp 会往 stderr 打无害提示（例如 systemctl 的 SysV 同步警告），而 PowerShell 会把子进程的
# stderr 变成 ErrorRecord —— 配合上面的 $ErrorActionPreference='Stop' 会让整个脚本当场中断，
# 连带掐死服务器上正在跑的流程（踩过：服务器停在第 5 步，日志里只有一句 sync 警告）。
# 原生调用一律走这个包装，成败只认退出码。
function Invoke-Native([string]$exe, [string[]]$argv) {
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & $exe @argv } finally { $ErrorActionPreference = $prev }
}

Step '1/5 检查本地文件'
if (-not $SkipSetup) {
    $jar = Join-Path $repo 'springboot-server\target\springboot-server-0.0.1-SNAPSHOT.jar'
    if (-not (Test-Path $jar)) { throw "没有打包产物：$jar`n先跑：cd springboot-server; .\mvnw.cmd -B package" }
    $envDeploy = Join-Path $repo 'springboot-server\.env.deploy'
    if (-not (Test-Path $envDeploy)) { throw "缺 $envDeploy`n先跑：Copy-Item springboot-server\.env springboot-server\.env.deploy 并把 DB_PASSWORD 改成线上密码" }
    $siteEnv = Join-Path $PSScriptRoot 'site.env'
    if (-not (Test-Path $siteEnv)) { throw "缺 $siteEnv" }
    Get-Content $siteEnv | Where-Object { $_ -match '^\s*DB_PASSWORD=\s*$' } | ForEach-Object { throw 'site.env 里的 DB_PASSWORD 是空的' }
    Write-Host "产物 $(('{0:N1}' -f ((Get-Item $jar).Length / 1MB))) MB，环境文件就绪"
}

Step '2/5 组织上传目录'
$stage = Join-Path $env:TEMP 'vidoc'
if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
New-Item -ItemType Directory -Path $stage | Out-Null
if (-not $SkipSetup) {
    Copy-Item $jar (Join-Path $stage 'vidoc-server.jar')
    Copy-Item $envDeploy (Join-Path $stage 'vidoc.env')
    Copy-Item (Join-Path $repo 'springboot-server\db\schema.sql') $stage
    foreach ($f in 'vidoc.service', 'nginx.conf', 'api-proxy.conf', 'security-headers.conf', 'site.env', 'setup-server.sh') {
        Copy-Item (Join-Path $PSScriptRoot $f) $stage
    }
    Get-ChildItem $stage | Select-Object Name, Length | Format-Table -AutoSize | Out-String | Write-Host
}

$target = "$User@$Server"
$sshOpts = @('-o', 'StrictHostKeyChecking=accept-new', '-o', 'ConnectTimeout=15', '-o', 'ServerAliveInterval=30')

if (-not $SkipSetup) {
    Step "3/5 上传到 ${target}:/root/vidoc"
    Invoke-Native $SSH ($sshOpts + @($target, "rm -rf /root/vidoc 2>&1"))
    if ($LASTEXITCODE -ne 0) { throw "SSH 连不上 $target（先确认 IP / 账号 / 密码）" }
    Invoke-Native $SCP ($sshOpts + @('-q', '-r', $stage, "${target}:/root/"))
    if ($LASTEXITCODE -ne 0) { throw 'SCP 上传失败' }

    Step '4/5 服务器初始化（apt 安装 + MySQL + systemd + nginx + 证书，约 3~8 分钟）'
    # 远端 2>&1：把服务器上的 stderr 合并进 stdout，别让它经本地 stderr 触发上面那个坑
    Invoke-Native $SSH ($sshOpts + @($target, "bash /root/vidoc/setup-server.sh 2>&1"))
    $setupExit = $LASTEXITCODE
} else {
    Step '3~4/5 已跳过上传与初始化'
    $setupExit = 0
}

Step '5/5 验收'
$api = 'https://api.vidocnote.com/api/auth/me'
try {
    Invoke-WebRequest $api -UseBasicParsing -TimeoutSec 25 | Out-Null
    Write-Host "⚠️  $api 返回 200，匿名访问不该成功" -ForegroundColor Yellow
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    if ($code -eq 401) {
        Write-Host "✅ $api -> 401（后端在跑、鉴权生效）" -ForegroundColor Green
    } else {
        Write-Host "⚠️  $api -> $code（期望 401；DNS 可能还没生效或 nginx/证书没起来）" -ForegroundColor Yellow
    }
}

if ($Web) {
    Step '额外：构建前端产物'
    Push-Location (Join-Path $repo 'nuxt-web')
    try {
        $env:NUXT_PUBLIC_SITE_URL = 'https://vidocnote.com'
        $env:NUXT_PUBLIC_API_BASE = 'https://api.vidocnote.com'
        $env:NUXT_PUBLIC_TURNSTILE_SITE_KEY = $TurnstileSiteKey
        # 必须走 npm.cmd：PATH 里的 npm 在本机解析成 npm.ps1（PowerShell shim），参数会被它吃掉
        Invoke-Native 'npm.cmd' @('run', 'generate')
        if ($LASTEXITCODE -ne 0) { throw 'nuxt generate 失败' }
    } finally {
        Pop-Location
    }
    $out = Join-Path $repo 'nuxt-web\.output\public'
    $leak = Select-String -Path (Join-Path $out '*.html'), (Join-Path $out '*.txt'), (Join-Path $out '*.xml') -Pattern 'localhost' -ErrorAction SilentlyContinue
    if ($leak) {
        Write-Host "⚠️  产物里还有 $($leak.Count) 处 localhost，别发版" -ForegroundColor Yellow
        $leak | Select-Object -First 5 | ForEach-Object { Write-Host "    $($_.Filename):$($_.LineNumber)" }
    } else {
        Write-Host '✅ 产物里没有 localhost' -ForegroundColor Green
    }
    Write-Host "产物目录：$out"
    Write-Host '→ 上传到 EdgeOne Pages（或触发它的自动构建），这一步只能在浏览器/控制台完成'
}

if ($setupExit -ne 0) { throw "服务器初始化脚本退出码 $setupExit —— 把上面最后一段输出发我" }
Write-Host '完成。' -ForegroundColor Green
