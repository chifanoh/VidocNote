# AGENTS.md

Vidoc 是一个视频内容总结平台：前端 Nuxt 4，后端 Spring Boot 3，通过 yt-dlp 获取视频字幕并调用 LLM 生成结构化总结笔记，含积分体系、历史记录、支付宝支付与 Cloudflare Turnstile。

## 项目布局

- `nuxt-web/`：前端（Nuxt 4 + @nuxt/ui v4 + Tailwind CSS v4 + Tiptap），业务代码在 `nuxt-web/app/`（`components`、`composables`、`layouts`、`middleware`、`pages`、`plugins`、`types`、`utils`），文档站在 `nuxt-web/content/`
- `springboot-server/`：后端（Spring Boot 3.5 + Java 17 + JPA + MySQL + Sa-Token），代码在 `src/main/java/com/vidoc/` 下按 `controller` / `service` / `repository` / `entity` / `dto` / `config` / `security` / `llm` / `payment` / `subtitle` / `video` / `ytdlp` / `exception` 分层
- `deploy/`：Nginx、systemd、安全头、部署脚本与说明
- `.agents/skills/`、`.claude/`：OpenSpec 工作流技能，属于流程工具，不是业务代码

## 常用命令

后端（在 `springboot-server/` 下执行）：

```bash
./mvnw test                 # 运行测试
./mvnw spring-boot:run      # 启动后端（默认 8080）
```

前端（在 `nuxt-web/` 下执行）：

```bash
npm run dev                 # 开发服务器
npm run build               # 构建
npm run generate            # 静态生成并执行 SPA fallback 脚本
```

初始化：后端本地配置写 `springboot-server/src/main/resources/application-local.yml`（已 gitignore）。包含真实密钥的文件绝不能提交。

## 架构与约束

- 后端分层：Controller 只做 HTTP 入参/出参，业务逻辑放 Service，数据访问走 Repository；保持现有包结构，不要跨层调用。
- 鉴权使用 Sa-Token 默认模式（服务端会话，无 JWT 插件）；密码哈希用独立 BCrypt 库，须兼容库里已有 `$2a$` 哈希。
- 对外标识使用 UUIDv7（`uuid-creator`），不要改用自增 ID 或随机 UUID 作为对外 ID。
- LLM 通过 `com.vidoc.llm` 抽象接入 OpenAI 兼容接口；yt-dlp 子进程由 `com.vidoc.ytdlp` 的并发闸门控制，超出时排队而非失败。
- 摘要任务有独立线程池并发上限（`VIDOC_SUMMARY_CONCURRENCY`），且必须低于模型账号的在途请求额度并留余量。
- 积分操作需记录流水；视频超过最大时长时必须在扣分前拒绝并删除记录。
- 支付宝支付使用官方 SDK 电脑网站支付；支付确认以异步 notify 或主动查询为准，浏览器同步跳转不可信。凭据不全时下单接口返回 503，应用必须能正常启动。
- Turnstile：线上必须启用并配置 secret；本地可关闭。生产构建要求 `NUXT_PUBLIC_SITE_URL` 为非 localhost。
- 前端 API 地址在构建期烘焙，修改 `NUXT_PUBLIC_API_BASE` 后必须重新 generate。

## 测试与验证

- 后端已有较多单元测试（实体、积分、订单、摘要、字幕、yt-dlp 等）。涉及后端逻辑的改动需补充或更新对应测试，改动后运行 `./mvnw test`。
- 前端没有测试框架；至少运行 `npm run build`（或交付目标对应的 `npm run generate`）验证类型与构建。
- 提交前确保两个子项目均可通过各自验证命令。

## 环境变量

后端本地配置见 `springboot-server/src/main/resources/application-local.yml`，前端站点/API 地址直接写在 `nuxt-web/nuxt.config.ts`。修改配置逻辑时同步更新对应说明。数据库、LLM、支付宝等真实凭据只放本地忽略文件或部署平台环境变量，不要写入 `application.yml`、代码或文档。

## 工作流说明

仓库包含 OpenSpec 技能（propose / apply / update / archive / sync / explore）。当用户要求"提议变更"或使用 OpenSpec 工作流时，遵循对应 skill 的边界：propose 只生成规划制品，不编辑业务代码；实现需等用户显式进入 apply 流程。
