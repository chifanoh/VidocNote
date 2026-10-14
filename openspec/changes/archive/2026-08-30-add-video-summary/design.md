## Context

项目 Vidoc 现状：`nuxt-web/` 是 Nuxt 4.5.1 + @nuxt/ui v4 的空白脚手架，`springboot-server/` 是空目录。OpenSpec 工作区全新。动机与范围见 `proposal.md`；行为契约见 `specs/video-summary/spec.md`。本设计仅说明"怎么做"。

## Goals / Non-Goals

**Goals:**
- 让粘贴链接 → yt-dlp 抓取 → LLM 生成摘要的流水线可独立部署、可配置。
- LLM 提供商可热切换（Claude / OpenAI 兼容如 DeepSeek），业务代码零改动。
- 用户数据（历史记录）按用户隔离，认证默认启用。
- 前端零后端代码耦合：dev 通过 nitro 代理 `/api`，prod 通过反向代理。
- 免费服务部署：前端 Vercel 免费版、后端 Render 免费 Web Service、数据库免费托管 MySQL。

**Non-Goals:**
- 不引入 Whisper 音频转录（按用户选择走平台字幕抓取）。
- 不做超长转录的分块 map-reduce（MVP 截断即可，见 Risks）。
- 不做管理员后台、不做邮箱验证、不做密码找回。
- 不解决需要登录态 cookies 的平台字幕（如 B 站高清字幕），MVP 仅用公开/自动字幕。
- 不选用付费托管服务，不追求高可用/自动扩容（免费服务有额度与冷启动限制）。
- 不引入 Postgres / H2：本地与部署统一使用 MySQL（用户本地已有 MySQL 且不熟悉 psql）。

## Decisions

### D1 后端框架：Spring Boot 3.5.x + Java 17 + Maven
- 理由：后端目录已命名为 `springboot-server`，符合既定方向；Spring Boot 3.x 是当前主流。原拟 Java 21，但本机已装 JDK 17（Spring Boot 3.5 支持 17+），本地与 Docker 统一用 17，避免额外装 21；两者语言层面对本工程无差异。
- 备选：Quarkus / Micronaut（更轻但生态与团队习惯不符）；Gradle（与 Maven 相比无压倒性优势，Maven 更常见）。

### D2 认证：Spring Security + JWT（无状态）
- 理由：无状态 JWT 适合前端分离部署；Spring Security 提供标准化过滤链与 BCrypt 密码散列。
- 密钥管理：JWT 签名密钥通过环境变量 `VIDOC_JWT_SECRET` 注入，配置中仅写 `${VIDOC_JWT_SECRET}` 占位符，仓库零明文。
- 备选：Session Cookie（需会话存储与 CSRF 处理，前后端分离下更繁琐）。

### D3 yt-dlp 集成：`ProcessBuilder` 子进程，绝不走 shell
- 元数据：`yt-dlp -J --skip-download <url>`（JSON 输出，解析 title/uploader/duration/thumbnail）。
- 字幕：`yt-dlp --skip-download --write-subs --write-auto-subs --sub-langs all --sub-format vtt/srt/best --output <tmp>/%(id)s.%(ext)s <url>`，随后读取落盘字幕文件。
- 理由：用户输入 URL 不可信，`ProcessBuilder`（参数数组）杜绝 shell 注入；超时与输出体积都做硬限制。
- 备选：`yt-dlp` 的 python 绑定 / yt-dlp-java 包装库（引入额外依赖与版本耦合，直接子进程最可控）。

### D4 LLM 抽象：`LlmProvider` 接口 + 两个实现，HTTP 直连
- 接口方法：`String summarize(String transcript, SummaryMode mode)`（或返回结构化结果）。
- 实现：`ClaudeLlmProvider`（Anthropic Messages API `POST /v1/messages`）；`OpenAiCompatibleLlmProvider`（`POST /chat/completions`，兼容 DeepSeek/OpenAI，base-url 可配）。
- 用 Spring `RestClient` 直连 HTTP，不引入官方 SDK —— 两个 provider 的请求/响应都简单，直连减少依赖面。
- 配置：`vidoc.llm.provider`（`claude` | `openai-compatible`），各自 `api-key`、`base-url`、`model` 子配置；`api-key` 一律用 `${VIDOC_LLM_*}` 环境变量占位符（如 `${VIDOC_LLM_CLAUDE_API_KEY}`），不硬编码、不入库。
- 备选：引入 anthropic-java / openai-java SDK（功能多但带来版本耦合与体积）。

### D5 异步处理：`@Async` 后台执行 + 前端轮询
- `POST /api/summaries` 立即落库（status=PENDING）并触发异步任务后返回；异步任务内更新 PROCESSING → COMPLETED/FAILED。
- 前端每 2-3s 轮询 `GET /api/summaries/{id}` 直到终态。
- 理由：yt-dlp + LLM 耗时 10-60s，同步会拖垮请求线程与前端体验；轮询比 WebSocket/SSE 更简单可靠。
- 备选：SSE/WebSocket 推送（更实时但复杂度高，MVP 不值）。

### D6 存储：统一 MySQL（Spring Data JPA + MySQL 驱动/方言）
- 理由：用户本地已装 MySQL 且不熟悉 psql，本地与部署统一 MySQL 可避免"本地能跑、线上跑不了"的方言差异；JPA + MySQL 方言在本地与托管库行为一致。
- 本地：连接用户已有 MySQL 实例（环境变量 `VIDOC_DB_URL/USERNAME/PASSWORD` 注入，先 `CREATE DATABASE vidoc`）。
- 部署：免费托管 MySQL —— 推荐 **TiDB Cloud Starter**（MySQL 8.0 兼容、免信用卡、Serverless 不因闲置停机、组织级 25GB + 2500万 RU/月），备选 **Aiven MySQL 免费版**（纯正 MySQL、永久免费，但 1GB 容量、闲置可能关机需手动开机）；连接串同样经环境变量注入。
- 备选：H2（本地零配置但需方言切换与部署迁移）；Postgres（用户无 psql 环境，排除）。

### D7 前端结构：Nuxt 页面 + composables，@nuxt/ui 组件
- 页面：`index.vue`（粘贴链接 + 模式选择 + 摘要展示 + 轮询）、`history.vue`、`login.vue`/`register.vue`。
- `useAuth.ts`（JWT 存 cookie，登录态共享）、`useSummary.ts`（创建/轮询/列表/删除）。
- `nuxt.config.ts`：`runtimeConfig.public.apiBase`（env `NUXT_PUBLIC_API_BASE`）+ dev `nitro.devProxy` `/api → http://localhost:8080`。
- 理由：复用现有 @nuxt/ui 组件与布局，代码量最小。

### D8 数据模型
- `users`：id, email(唯一), password_hash, created_at。
- `video_summaries`：id, user_id(FK), video_url, title, author, thumbnail_url, duration, mode, status(PENDING/PROCESSING/COMPLETED/FAILED), summary_text, error_message, truncated(bool), created_at。
- 理由：一张记录表承载一次摘要任务的状态与结果，足够表达历史记录与轮询。

### D9 部署与配置管理（免费服务）
- 前端：**Vercel 免费版**，连接 GitHub 仓库自动构建部署 Nuxt 4。
- 后端：**Render 免费 Web Service**，Docker 镜像内置 JDK 21 与 yt-dlp（`ProcessBuilder` 调用需真实二进制）；免费档约 750 小时/月、闲置休眠有冷启动延迟。
- 数据库：免费托管 MySQL（TiDB Cloud Starter / Aiven，见 D6）；本地沿用现有 MySQL。
- 配置：所有敏感配置（LLM key、JWT secret、DB 连接串）经各平台环境变量面板注入（Render/Vercel env），仓库与镜像零明文；提供 `.env.example` 与环境变量清单；后端启动时校验必填环境变量，缺失即报清晰错误（对应 spec"凭据缺失"场景）。
- 生产连通：前端 `NUXT_PUBLIC_API_BASE` 指向 Render 后端域名；后端配置 CORS 允许前端生产域名。
- 备选：后端用 Oracle Cloud Always Free VM（更稳但需自行运维 Nginx/Java/yt-dlp，MVP 不值）。

### D10 SEO/GEO（前端可见性与 AI 友好）
- 模块：采用 `@nuxtjs/seo` 模块套件（含 sitemap / robots / schema-org / og-image / seo-utils），`npx nuxt module add seo ai-ready` 安装；`site.url` / `site.name` 由环境变量 `NUXT_PUBLIC_SITE_URL` 注入（Vercel 部署后指向正式域名）。
- 每页 TDK：用 `useSeoMeta` 按模板设置（见下表）；首页为落地页 TDK（index,follow），登录/注册/历史为功能页 TDK（noindex,follow）。
- 结构化数据：用 `useSchemaOrg` 输出 WebSite + Organization（首页）与 FAQPage（首页 FAQ 区）JSON-LD。
- robots：保持 `blockAiBots=false`（默认），确保 GPTBot / Google-Extended / PerplexityBot / ClaudeBot 等 AI 爬虫可抓取；sitemap 仅含可索引页面。
- OG 图片：准备 `public/og-default.png`（1200×630），首页 OG 标签指向绝对 URL。
- SSR：Nuxt 4 默认 `ssr: true`，meta 与 JSON-LD 出现在 HTML 源码；Vercel 部署走 Nitro serverless SSR（与 D9 一致）。
- 备选：手动逐页写 `useHead` + 单独引入 sitemap/robots 模块（套件更省且约定完整）。

**TDK 模板表**

| 页面 | title 模板（≤60 字符） | description 要点（150-160） | keywords | robots |
|---|---|---|---|---|
| `/`（首页/落地） | 视频摘要生成器 - Vidoc｜AI 一键生成视频总结 | 核心价值 + 目标用户 + 行动号召（立即使用） | 视频摘要,视频总结,AI总结,视频链接总结,Vidoc | index,follow |
| `/login` | 登录 - Vidoc | 登录后可粘贴视频链接生成摘要并管理历史 | 登录,Vidoc | noindex,follow |
| `/register` | 注册 - Vidoc | 免费注册后可粘贴视频链接生成摘要并管理历史 | 注册,Vidoc | noindex,follow |
| `/history` | 历史记录 - Vidoc | 查看与管理自己生成的视频摘要历史 | 历史记录,Vidoc | noindex,follow |

**首页落地结构**（SSR 渲染）
1. Hero：结论先行文案（前 100 字符点明"粘贴视频链接，AI 自动生成视频摘要"）+ CTA（输入框 + 生成按钮，即工具区入口）。
2. 功能特性（H2 ×3-5）：多平台支持（yt-dlp）、多摘要模式、AI 生成、历史管理。
3. 使用步骤（H2，H3 分步）：复制链接 → 粘贴并选模式 → 查看 AI 摘要。
4. FAQ（H2 + FAQPage Schema）：常见问题 3-5 条。
5. 底部工具区：重复核心输入框，降低跳出。

## Risks / Trade-offs

- **[yt-dlp 平台兼容性漂移]** 平台改版可能使字幕/元数据抓取失效 → 版本固定并文档化前置条件；失败统一转为 FAILED 并返回可读错误，便于发现。
- **[外部 URL 注入 / 子进程滥用]** URL 不可信，`ProcessBuilder` 数组传参 + 命令超时（如 120s）+ 输出体积上限 → 杜绝 shell；参数白名单化（仅把 URL 作为位置参数）。
- **[超长转录被截断，摘要不完整]** 单次 LLM 调用有 token 上限 → 明确标注 truncated=true 并在摘要中提示；后续可用分块 map-reduce 演进（Non-Goal）。
- **[无字幕视频无法总结]** 平台无字幕 → FAILED 并给出明确提示；后续可接 Whisper（Non-Goal）。
- **[LLM 提供商行为差异]** 各 provider 提示词/输出略有差异 → 统一 prompt 模板与输出归一化（要求纯文本返回）。
- **[JWT 过期/泄漏]** 前端 cookie 存 JWT → 设置合理过期时间（如 7 天）；HTTPS 部署时启用 Secure cookie（记录为部署要求）。
- **[免费服务冷启动与额度]** Render 免费档闲置休眠（首次请求慢）、750h/月上限；TiDB RU 额度耗尽会拒绝新连接、Aiven 闲置可能关机 → 明确 MVP 为低频个人使用、TiDB 设 spending cap、文档化冷启动/额度说明。
- **[环境变量缺失/拼写]** 部署时必填 env 缺失会启动失败或功能不可用 → 后端启动校验必填项并输出清晰错误；提供 `.env.example` 对照清单。

## Migration Plan

1. 本地：`CREATE DATABASE vidoc` 后配置环境变量连接，`ddl-auto: update` 自动建表。
2. 部署顺序：编写 Dockerfile（安装 yt-dlp）→ 推送镜像到 Render 并配置环境变量 → 建免费托管 MySQL（TiDB/Aiven）并配置连接串 → Vercel 部署前端并配置 env → 生产 URL 端到端验证。
3. 回滚：均为新增能力，回滚即回退 Vercel/Render 版本，无数据兼容问题。

## Open Questions

- 摘要正文是否需要支持 Markdown 渲染？（MVP 前端按纯文本展示，渲染是纯前端增强，不改变 specs，可延后。）
