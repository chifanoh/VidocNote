## Why

用户需要一个网站：粘贴视频链接，AI 自动生成视频总结摘要。当前 Vidoc 只有 Nuxt 4 前端脚手架和空的 `springboot-server/` 目录，没有任何视频、字幕或 LLM 相关能力。这是该产品（Vidoc = video + doc）的第一个核心功能。

## What Changes

新增一个完整的"粘贴链接 → AI 摘要"全栈能力：

- **后端（springboot-server/，全新搭建）**
  - 新增 Spring Boot 工程，提供认证（JWT + 用户注册/登录）与摘要相关 REST API。
  - 新增摘要流水线：调用 **yt-dlp** 抓取视频元数据与平台字幕 → 解析字幕文本 → 调用**可配置 LLM 提供商**生成摘要。
  - 新增历史记录：每个用户可查看/删除自己的摘要记录。
- **前端（nuxt-web/）**
  - 首页：粘贴视频链接、选择摘要模式、展示视频信息与生成的摘要（异步轮询）。
  - 新增登录/注册页、历史记录页；Header 展示登录状态。
  - SEO/GEO 优化：首页改造为落地页+工具（Hero 结论先行 + 特性 + 步骤 + FAQ），每页唯一 TDK/OG/canonical 与 noindex 策略，JSON-LD（WebSite/Organization/FAQPage），robots 允许 AI 爬虫，sitemap.xml。
- **配置化 LLM**：`vidoc.llm.provider` 支持在 Claude（Anthropic Messages API）与 OpenAI 兼容接口（如 DeepSeek）之间切换，无需改代码；**API Key、JWT 密钥、数据库连接串等敏感配置一律通过环境变量注入**，仓库零明文。
- **摘要模式**：简短 / 详细 / 要点式 / 时间戳章节，通过 prompt 模板区分。
- **免费服务部署**：前端 **Vercel 免费版**、后端 **Render 免费 Web Service**（Docker 容器内置 yt-dlp）、数据库**免费托管 MySQL**（推荐 TiDB Cloud Starter，备选 Aiven）。本地开发沿用现有 MySQL，无需更换技术栈。
- **外部依赖**：运行主机需安装 `yt-dlp`（可配置路径）；LLM API Key 通过环境变量注入。

## Capabilities

### New Capabilities
- `video-summary`: 粘贴视频链接后，系统抓取视频元数据与字幕、调用可配置 LLM 生成所选模式的摘要，并支持按用户保存/查看/删除历史记录。

### Modified Capabilities

<!-- 无 —— 本项目 specs/ 目前为空，无既有能力被修改。 -->

## Impact

- **代码**：`springboot-server/`（全新 Spring Boot 工程）；`nuxt-web/`（新增页面、composables、配置）。
- **API**：新增 `AuthController`（注册/登录）与 `SummaryController`（创建/查询/删除摘要记录）。
- **外部依赖**：主机需安装 `yt-dlp`；后端新增 LLM API 调用（Claude / OpenAI 兼容）。
- **配置**：新增 `vidoc.llm.*`、`vidoc.ytdlp.*`、`vidoc.jwt.*` 等后端配置与前端 `NUXT_PUBLIC_API_BASE`，全部通过**环境变量**注入（`VIDOC_JWT_SECRET`、`VIDOC_LLM_*`、`VIDOC_DB_URL/USERNAME/PASSWORD`、`NUXT_PUBLIC_API_BASE`），提供 `.env.example` 清单，仓库零明文。
- **数据库**：新增 `users` 与 `video_summaries` 表，统一 **MySQL**（本地用现有 MySQL；部署用免费托管 MySQL）。
- **部署**：前端 Vercel、后端 Render（Docker 含 yt-dlp）、数据库免费托管 MySQL；涉及各平台环境变量配置与 CORS。
- **SEO/GEO**：前端新增 `@nuxtjs/seo` 依赖与 `NUXT_PUBLIC_SITE_URL` 站点配置；新增 `og-default.png`（1200×630）；robots.txt 与 sitemap.xml 由模块自动生成（允许 AI 爬虫，认证页 noindex）。
