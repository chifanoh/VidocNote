## 1. 后端工程搭建

- [x] 1.1 在 `springboot-server/` 用 Maven 脚手架创建 Spring Boot 3.5.x 工程（Java 17，设计 D1 的 Java 21 因本机 JDK 而调整），含主启动类、`application.yml`（全部用 `${VIDOC_*}` 环境变量占位符：DB 连接 `VIDOC_DB_URL/USERNAME/PASSWORD`、JWT 密钥 `VIDOC_JWT_SECRET`、`VIDOC_YTDLP_PATH`、`vidoc.llm.*`），并提供 `.env.example` 与环境变量清单，验证 `mvn spring-boot:run` 在本地 MySQL 下启动且日志正常
- [x] 1.2 引入 `spring-boot-starter-data-jpa`、`mysql-connector-j`，配置 `ddl-auto: update` 与 MySQL 方言，先在本地 MySQL 创建 `vidoc` 库，验证启动后 `users`/`video_summaries` 表自动生成、日志无实体映射报错

## 2. 数据模型与仓库

- [x] 2.1 实现 `User` 实体（id、email 唯一约束、passwordHash、createdAt）与 `UserRepository`，验证通过临时单元测试可保存并按下邮箱查询
- [x] 2.2 实现 `VideoSummary` 实体（userId、videoUrl、title、author、thumbnailUrl、duration、mode、status、summaryText、errorMessage、truncated、createdAt）与 `VideoSummaryRepository`（按 userId 倒序分页），验证启动映射正确

## 3. 认证

- [x] 3.1 实现注册（`POST /api/auth/register`，BCrypt 散列、重复邮箱返回 409）与登录（`POST /api/auth/login` 返回 JWT），验证用 curl 注册+登录均拿到 token
- [x] 3.2 配置 Spring Security 过滤链：放行 `/api/auth/**`，其余 `/api/**` 校验 JWT；无效/缺失 token 返回 401，越权访问他人物品返回 403，验证 curl 各场景状态码正确

## 4. yt-dlp 集成与字幕解析

- [x] 4.1 实现 `YtDlpService`：用 `ProcessBuilder`（数组传参、命令超时、输出体积上限）调 yt-dlp 抓取元数据（`-J --skip-download`）与字幕（`--write-subs --write-auto-subs --sub-langs all --sub-format vtt/srt/best`，输出到临时目录），验证对真实 YouTube/Bilibili 链接能解析出标题/作者/时长/缩略图并得到字幕文件
- [x] 4.2 实现 `SubtitleParser`：VTT/SRT → 去时间戳合并纯文本，超长按配置上限截断并置 `truncated=true`，验证单元测试覆盖时间戳剥离、空字幕、超长截断

## 5. LLM 提供者（可配置）

- [x] 5.1 定义 `LlmProvider` 接口（入参：转录文本 + 摘要模式；出参：摘要文本）与配置绑定类（`vidoc.llm.provider`、各 provider 的 api-key/base-url/model，敏感值用 `${VIDOC_LLM_*}` 占位符），验证环境变量可注入 Bean 且缺失时给出清晰报错
- [x] 5.2 实现 `ClaudeLlmProvider`（Anthropic Messages API，RestClient 直连），验证配置为 `claude` 时对示例文本返回摘要（真调用需 `VIDOC_LLM_CLAUDE_API_KEY`）
- [x] 5.3 实现 `OpenAiCompatibleLlmProvider`（`/chat/completions`，兼容 DeepSeek），验证切换 `vidoc.llm.provider=openai-compatible` 后同一接口返回结果且响应结构不变（真调用需 `VIDOC_LLM_OPENAI_API_KEY`）

## 6. 摘要流水线

- [x] 6.1 实现 `SummaryService`：编排 元数据 → 字幕 → prompt 模板（简短/详细/要点/时间戳章节）→ LLM → 落库，验证真实链接产出 `COMPLETED` 且正文符合所选模式
- [x] 6.2 接入 `@Async` 后台执行与状态流转（PENDING→PROCESSING→COMPLETED/FAILED，失败写入 errorMessage），验证接口立即返回 PENDING、轮询最终到达终态；凭据缺失/无字幕/无效链接均返回 FAILED 与可读原因

## 7. 摘要 API 控制器

- [x] 7.1 实现 `AuthController`（注册/登录）与 `SummaryController`（`POST /api/summaries` 创建、`GET /api/summaries` 历史列表、`GET /api/summaries/{id}` 单条、`DELETE /api/summaries/{id}`），均按当前登录用户隔离，验证 curl 全链路可用且越权返回 403

## 8. 前端（nuxt-web/）

- [x] 8.1 在 `nuxt.config.ts` 配置 `runtimeConfig.public.apiBase`（读取 `NUXT_PUBLIC_API_BASE`）与 dev 代理 `/api → http://localhost:8080/api`（h3 代理会剥离挂载前缀，target 需带 `/api`），验证 dev 下页面请求经代理转发成功、生产构建时 apiBase 指向后端域名
- [x] 8.2 实现 `composables/useAuth.ts`（token 存 cookie、登录态响应式共享、刷新后从 JWT 恢复邮箱）与 `login.vue`/`register.vue`（@nuxt/ui 表单 + zod 校验），验证可注册/登录并在刷新后保持登录态
- [x] 8.3 实现 `composables/useSummary.ts`（创建、按 id 轮询到终态、列表、删除）与首页 `index.vue`（URL 输入、模式选择、视频信息与摘要展示、错误/截断提示、`?id=` 查看历史），验证真实链接端到端显示摘要
- [x] 8.4 实现 `history.vue`（历史记录列表 + 删除 + 未完成任务自动续轮询）并更新 `Header.vue`（导航/登录态/退出）、`Footer.vue`、`auth` 路由中间件，验证历史数据正确展示且删除生效

## 9. 集成验证

- [ ] 9.1 端到端验证：启动后端（`mvn spring-boot:run`）+ 前端（`npm run dev`），完整走通 注册登录 → 粘贴真实视频链接 → 选择模式 → 轮询得到摘要 → 历史列表 → 删除，验证所有 specs 中的场景（401/403/无字幕/截断提示）行为符合预期

## 10. 免费服务部署

- [ ] 10.1 编写 `springboot-server/Dockerfile`（JDK 21 + 安装 yt-dlp + 打包 jar）并本地构建，验证容器内 `yt-dlp --version` 可执行、应用能启动
- [ ] 10.2 在 Render 创建免费 Web Service（推送 Docker 镜像）并配置环境变量（`VIDOC_DB_*`、`VIDOC_JWT_SECRET`、`VIDOC_LLM_*`、`VIDOC_YTDLP_PATH`），验证部署健康检查通过且 yt-dlp 调用可用
- [ ] 10.3 创建免费托管 MySQL（推荐 TiDB Cloud Starter，备选 Aiven MySQL 免费版）并建库，把连接串写入后端环境变量，验证后端连接成功、JPA 建表与增删查正常（TiDB 做一次 MySQL 方言兼容冒烟）
- [ ] 10.4 在 Vercel 部署 `nuxt-web`（连 GitHub、配置 `NUXT_PUBLIC_API_BASE` 指向 Render 后端）、后端配置 CORS 允许前端生产域名，验证生产 URL 可访问且登录后请求不被跨域拦截
- [ ] 10.5 生产端到端验证：在部署环境完整走通 注册登录 → 粘贴真实视频链接 → 选择模式 → 轮询得到摘要 → 历史列表 → 删除；确认冷启动/额度表现符合 MVP 预期

## 11. SEO/GEO 优化

- [x] 11.1 安装 `@nuxtjs/seo`（`npx nuxt module add seo ai-ready`），在 `nuxt.config.ts` 配置 `site.url`/`site.name`（读取 `NUXT_PUBLIC_SITE_URL`），验证 SSR 首页 HTML 源码含基础 meta（charset/viewport/title）
- [x] 11.2 用 `useSeoMeta` 为每页设置唯一 TDK：index（落地页 TDK，index,follow）、login/register/history（功能页 TDK，noindex,follow），验证各页 title（50-60 字符）与 description（150-160 字符）唯一且合规
- [x] 11.3 首页落地内容改造：Hero 结论先行文案（前 100 字符点明"粘贴视频链接，AI 自动生成视频摘要"）+ 功能特性（H2）+ 使用步骤（H2/H3）+ FAQ + 底部工具区；唯一 H1、图片描述性 alt、锚文本避免"点击这里"，按 SEO 检查清单核验源码结构
- [x] 11.4 用 `useSchemaOrg` 输出结构化数据：WebSite + Organization（首页）、FAQPage（FAQ 区，Q/A 一一对应），验证 JSON-LD 出现在 SSR 源码（FAQPage 用 `defineWebPage({'@type':'FAQPage'})` + `defineQuestion` 实现，无 `defineFAQPage` 自动导入）
- [x] 11.5 OG 标签与分享图：index 设置 og:title/description/image/url/locale(zh_CN)/site_name，准备 `public/og-default.png`（1200×630，绝对路径），验证 OG 标签完整（静态图用 `defineOgImage(undefined, {}, { url: '/og-default.png' })`，非 `defineOgImage({url})`）
- [x] 11.6 robots 与 sitemap：确认 `/robots.txt` 允许全部 UA（含 GPTBot/Google-Extended/PerplexityBot/ClaudeBot）、`blockAiBots` 未开启；`/sitemap.xml` 仅含 index 等可索引页、不含认证页，验证两个端点可访问（dev 下 robots 默认屏蔽，用 `?mockProductionEnv` 验证生产规则）
- [ ] 11.7 上线后：在 Google Search Console 与百度站长平台提交 sitemap；用 PageSpeed Insights / 站点扫描复检 TDK 重复、唯一 H1、死链
