# Vidoc

视频内容总结平台：粘贴 B 站等平台视频链接，自动提取字幕并生成结构化总结笔记，支持积分计费、历史记录与在线阅读。

## 项目结构

```
├── nuxt-web/          # 前端（Nuxt 4 + @nuxt/ui v4 + Tailwind CSS v4）
├── springboot-server/ # 后端（Spring Boot + JPA + MySQL + Sa-Token）
├── deploy/            # 部署配置（Nginx、systemd、环境脚本）
└── DESIGN.md          # 前端设计规范（唯一设计基准）
```

## 功能特性

- **视频总结**：通过 yt-dlp 获取视频信息与字幕，调用 LLM 生成结构化总结
- **积分体系**：注册赠送积分、消费扣减、流水记录、套餐购买
- **用户系统**：注册登录（Sa-Token 鉴权）、Cloudflare Turnstile 人机验证
- **历史记录**：总结记录管理与 Markdown 在线阅读
- **文档中心**：Nuxt Content 驱动的产品文档站

## 技术栈

| 端 | 技术 |
|---|---|
| 前端 | Nuxt 4、@nuxt/ui v4、Tailwind CSS v4、Tiptap |
| 后端 | Spring Boot、JPA、MySQL、Sa-Token |
| 依赖服务 | yt-dlp、OpenAI 兼容 LLM API、支付宝支付 |

## 快速开始

### 前端

```bash
cd nuxt-web
npm install
npm run dev
```

### 后端

```bash
cd springboot-server
./mvnw spring-boot:run
```

后端默认端口 `8080`，需本地 MySQL 及可用的 `yt-dlp` 命令。

## 配置

后端本地配置统一写在 `springboot-server/src/main/resources/application-local.yml`（已 gitignore），包括数据库、LLM、yt-dlp、摘要并发、支付宝与 Turnstile 等配置。

基础默认值见 `springboot-server/src/main/resources/application.yml`；部署环境仍可通过系统环境变量覆盖对应配置。

## 部署

`deploy/` 目录提供 Nginx 反向代理、systemd 服务及服务器初始化脚本，供生产环境参考使用。

## License

仅供学习交流使用。
