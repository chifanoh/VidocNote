# Vidoc 前端设计规范（DESIGN）

> 前端唯一设计基准。技术栈：Nuxt 4 + @nuxt/ui v4 + Tailwind CSS v4（CSS-first，`@theme static` + `:root` 覆盖）。
> 令牌在 `nuxt-web/app/assets/css/main.css`。

## 1. 设计语言

**极简编辑风**：所有背景纯白 `#fff`、纯黑唯一强调色、6px 圆角、柔和大阴影、细边框、大量留白。
品牌橙已废除，**全站唯一彩色是强调蓝 `#1a74ff`**（文本悬停 / 目录选中）。
仅有的深色元素：进度终端（`bg-[#0d0d0f]`）、Footer（`bg-footer: #292b2f`）、**骨架屏灰条**（`USkeleton` 默认 `bg-elevated`，白色看不见，别"修正"）。

## 2. 色彩令牌

| 令牌 | 值 | 用途 |
|---|---|---|
| `--color-page` / `bg-page` | `#fff` | **全站所有背景**，禁止渐变/灰底；层次用 `border-line` + `shadow-card` |
| `text-ink` ~ `text-ink-5` | `#000` / `#1a1a1a` / `#333` / `#8a8a8a` / `#ababab` | 主文本 / 标题 / 中阶 / 弱文本 / 占位符 |
| `border-line` / `border-line-strong` | `#f0ebe5` / `#d8d8d8` | 常规 / 强调边框 |
| `--color-hover` / `hover:text-hover` | `#1a74ff` | **所有可点击文本**的悬停色 + 目录当前小节高亮 |
| `--ui-primary` | `#000` | 主按钮纯黑、选中态；hover `#1a1a1a` |
| `--ui-radius` | `0.375rem` | 全局 6px 圆角 |

`--ui-bg` / `--ui-text` 必须在 `main.css` 的 `:root` 接管（否则 @nuxt/ui 默认吐 slate 灰正文）。没覆盖的 `--ui-text-muted` / `--ui-border` 有意保留 @nuxt/ui 中性色区分层次。

## 3. 排版 / 阴影 / 圆角

- 字体栈以 `MiSans` → `PingFang SC` → `Microsoft YaHei` 开头；终端用 `font-mono`。
- 标题一律 `font-semibold` + `tracking-normal`；正文 `text-sm leading-6 text-ink-3`。
- 卡片统一 `shadow-card`（`0 12px 40px rgba(0,0,0,0.06)`）；圆角统一 6px，圆形元素 `rounded-full`。

## 4. 组件规范

### 卡片配方（统一）
```
rounded-lg border border-line bg-page p-6 shadow-card
```

### Logo
图形 `/logo/logo.png`（源图仅 64×64，渲染固定 `size-8`，别放大）+ `vidoc` 小写 `text-xl font-bold` + `NOTE` 黑底白字块 `text-xs font-normal`。`leading-none` 必须写在各自 span 上（工具类自带 line-height 会盖父级行高）。`favicon.ico` 目前是 PNG 改名，待重新生成多尺寸 ICO。

### Header
- `UHeader bg-page :toggle="false"`，三块布局：左 `#title` Logo、中 `#default` 槽主导航（`/docs` 文档恒显；`/#pricing` 价格 `hidden sm:inline-flex`；`/docs/reference/faq` 常见问题 `hidden md:inline-flex`，窄屏中心栏只塞得下一项）、右 `#right` 登录态。
- UHeader 默认 `center: 'hidden lg:flex'`（窄屏看不见），三栏必须覆盖：`:ui="{ root: 'border-b-0!', left: 'flex flex-1 items-center gap-1.5', center: 'flex items-center justify-center', right: 'flex flex-1 items-center justify-end gap-1.5' }"` —— 两侧都 `flex-1` 中间才真正居中。
- 未登录右侧：纯文字「登录 / 注册」原生 `<button>`，`py-2 text-base text-ink hover:text-hover`，不加横向 padding（右沿对齐验收）；斜杠单独 `<span class="mx-1 text-ink-4">/</span>`。
- 登录后：40px `UAvatar size="xl"`，hover `bg-black/[0.06]`，无边框。头像下拉悬停触发（120ms 开 / 60ms 关，`portal:false modal:false`），每项必带图标；条目文字恒纯黑、悬停只换实心底纹（`:ui` 覆盖 `data-highlighted` 系列，写法须与组件默认类逐字一致）。触发器必须是原生 `<button>` 包住 `UAvatar`（reka 把 attrs 合并到第一个子节点，直接放 UAvatar 会落到内部 svg 上），不画焦点环（悬停收起会误触 focus-visible）。
- 首访横幅 `SignupBanner`：SSR 直接输出 + `useHead` 注入 head 判定脚本首绘前藏显；关闭记忆键 `vidoc-signup-banner`（换键 = 关过的人再见）。黑底 `bg-primary` 在本项目是黑，必须 `bg-page` + 三槽 `text-ink` 逐个覆盖；CTA 不用 `actions` 数组（固定渲染黑砖 UButton），用插槽自写 button。

### 输入框 / 按钮
- focus 无黑色 outline，边框保持暖灰。
- 主按钮纯黑，登录注册 `block`。次级 `UButton color="neutral" variant="ghost"` **带 label 的一律加 `class="text-ink-3 hover:text-hover"`**；不适用：`color="error"` 删除按钮、纯图标按钮。终端清空键是例外中的例外：`error` + `link` 变体（红、悬停变淡）。
- `UButton` 被 flex-col 拉伸后内容贴左，需 `justify-center`。
- 实测坑：强制 `:hover` 后等 ~350ms 再读 computed color（transition-colors 过渡）。

### 历史列表卡片
- `UBlogPosts` post 挂 `variant:'naked'`（必须挂 post 对象层级）再套卡片配方。
- 三档响应式：`≤768` 纵向 → `≥769` 横向（图 `w-1/3`）→ lg grid `[minmax(0,1fr)_minmax(0,2fr)]`。**断点写 `min-[769px]:` 不写 `md:`**（768 含在堆叠档）。
- UP 徽章是 CSS mask + `currentColor`（`<img>` 里的 SVG 不继承页面颜色）；改 mask 后必须截图确认（404 会退化成实心色块）。
- 标题 `line-clamp-2`。删除按钮两套按断点互斥，均 `relative z-10`（整卡是 absolute 链接会盖住按钮）。
- 卡片不展示任何状态（失败/进行中的记录后端直接删）。

### FAQ 手风琴
`UAccordion` 问题与答案默认同色，必须 `:ui="{ label: 'text-ink', body: 'text-ink-4' }"`。

### 进度终端
- 常驻固定步骤流（非日志流）：三步恒可见，状态 `◐` 旋转 / `✓` / `○` / `✕` 红；布局写在 scoped，颜色走工具类。
- 步骤文案唯一来源 `app/utils/summary-steps.ts`，后端只发 key；状态推导纯函数 `buildSummarySteps(status, stepKey)`。
- 失败原因不进终端（后端只写日志），反馈 = 当前步变红 + toast。

### 徽章 / 提示 / toast
- 徽章 `UBadge color="success|error|warning|neutral" variant="soft"`；内联提示 `UAlert variant="subtle"`。
- 失败走 toast（右下、error、4s）；**全局无倒计时进度条**：`app.vue` 的 `<UApp :toaster="{ progress: false }">`（必须挂 toaster，不是 toast）。

### 异常 / 空态（AppError）
- 唯一出口 `AppError`，文案唯一来源 `app/utils/api-error.ts`；有 `statusCode` 走错误页，纯空态只出提示卡。
- 文案铁律：403 与 404 合并成同一句「记录不存在或已删除」（防 id 试探）；401 →「请先登录后查看」+ 自动弹登录框，登录成功后页面须 `watch(isLoggedIn)` 重跑加载。
- 未知路径走真实路由 `/404`（`error.vue` 里 `clearError({ redirect: '/404' })`）。**别在 pages/ 里加 catch-all**——靠 docus layer 的 catch-all 抛 404，抢过来会绕开整套跳转。
- 覆盖字号时 `sm:` 档必须跟着写（twMerge 只合并同前缀）。

### 骨架屏
- 唯一原子 `USkeleton`；**必须与真实内容形状同构**，不做通用灰块。阅读页骨架渲染在单列容器之外（撑满真实三栏）。
- 历史页首屏条数按视口算（`fitSkeletonCount`），不写死。
- a11y：外层 `role="status" aria-label="加载中"`，重复块 `aria-hidden`。

### 仪表盘
- `layouts/dashboard.vue`；三页 `definePageMeta({ layout:'dashboard', header:false, footer:false, middleware:'auth' })`，登出入口补在侧边栏底部。
- 侧边栏导航项选中/悬停都是**浅灰底纹**（`pill` 变体 + `hover:before:bg-elevated` 拉平），**是蓝悬停铁律的唯一例外，别推广**。
- 侧边栏邮箱裹 `<ClientOnly>`（防 hydration 不匹配）。
- 积分页：只展示合计不展示分项，但「订阅积分当期有效」提示必须在；流水不引入红绿配色；事由文案前端映射（`app/utils/points.ts`，后端只发 key）。

## 5. 布局

**页面左右边界唯一来源是 `UContainer`**（1280px + `px-4 sm:px-6 lg:px-8`），Header 同款 → 天然对齐。**页面代码不得再套 `max-width` 或左右 `padding`**；内层量度用 `mx-auto max-w-3xl/4xl/5xl`（不加 `px-*`）。

- 对齐验收：`getBoundingClientRect()` 量 Header logo 左沿 = 内容节左沿（1440 实测 103.6 / 390 实测 16）。
- 节间距 `pb-16`（末节 `pb-24`）；卡片 grid `gap-6`。
- `UPageHero` 自带量度必须用 `:ui` 逐前缀清干净（`sm:`/`lg:` 档都要写）。
- Footer 深底；例外：`/history` 与法务页不渲染 Footer / Header（法务页从登录弹窗新标签打开）。
- 阅读页与仪表盘刻意不套 `UContainer`（reader 全宽 / dashboard fixed 全视口壳）。

## 6. 交互规范

- **可点击文本悬停一律 `hover:text-hover`**；例外：仪表盘侧边栏导航（灰底纹）、Header 的「文档」入口（用户指定用黑灰：常态黑 `text-ink`、悬停灰 `hover:text-ink-4`，不显蓝）。
- 按钮与头像悬停保持组件默认不改蓝（例外见 §4 次级 ghost 按钮规则）。
- 进行中用进度终端步骤流 / `UProgress`；单条操作失败走 toast，不顶掉整页。
- 进行中任务可从终端右上终止（停轮询 + 解锁按钮）。
- 全局禁右键 + 拦 F12/组合键（`block-devtools.client.ts`）：input/textarea/contenteditable 必须放行；这是路障不是安全边界，真实防线在后端。DevTools 停靠检测看**视口差值增量**而非绝对值。

## 7. 实现约定

1. **用令牌禁裸色值**；新颜色先进 `main.css` 再用；不得引入品牌彩色。
2. 图标统一 `i-material-symbols-*`（静态扫描打包，新图标须出现在源码）。品牌/平台图形放 `public/logo/`：纯装饰用 `<img>`，要跟文字色用 CSS mask + `currentColor`。
3. 新页面复用现有骨架：Hero → 工具区 → 特性/步骤/FAQ → Footer。
4. 所有背景恒纯白 `bg-page`；仅终端与 Footer 深底例外，禁止渐变/灰底。
5. @nuxt/ui 覆盖走 CSS 令牌（`:root`/`@theme`），组件内 `:ui` 微调，不写内联 style。`UTable` 主题写死中性色，须在 `:ui` 里逐个换项目令牌。
6. 异常/空态统一 `AppError`，新增错误分支只加映射，不在页面另写文案。
