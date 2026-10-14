# video-summary Specification

## Purpose
让用户粘贴一个视频链接后，系统自动抓取视频元数据与平台字幕，并调用可配置的 AI 提供商生成结构化详细摘要，同时为每个用户提供登录、历史摘要的查看与删除能力。

## Requirements

### Requirement: 登录即注册

系统 SHALL 提供统一的登录能力（不再有独立的注册入口）：登录成功后签发令牌，受保护接口在后续请求中携带该令牌进行身份校验。用户提交的邮箱若尚未注册，系统直接创建账号并登录。

#### Scenario: 新邮箱首次登录
- **WHEN** 用户提交一个未注册过的邮箱与符合长度要求的密码
- **THEN** 系统创建该用户并直接返回令牌与用户信息，无需二次注册

#### Scenario: 已注册邮箱登录
- **WHEN** 用户提交已注册的邮箱与正确密码
- **THEN** 系统返回令牌与用户信息

#### Scenario: 已注册邮箱密码错误
- **WHEN** 用户提交已注册的邮箱与错误密码
- **THEN** 系统返回 401，且不创建新账号、不改动原账号

#### Scenario: 未认证访问受保护接口
- **WHEN** 用户未携带有效令牌请求受保护接口（如创建摘要）
- **THEN** 系统返回 401 未授权，且不执行该操作

### Requirement: 粘贴链接生成摘要

已登录用户 SHALL 能提交一个视频链接，系统抓取该视频的元数据与平台字幕，并异步生成摘要。

提交 SHALL 以余额不低于单条最低扣分为前提：余额不足时系统 SHALL 拒绝创建，且不产生任何副作用。任务在获取到视频时长后 SHALL 按积分阶梯扣除积分，扣分后失败 SHALL 原额退回。（积分规则见 `points` 能力。）

#### Scenario: 有效视频链接
- **WHEN** 已登录用户提交受支持平台的有效视频链接，且余额不低于单条最低扣分
- **THEN** 系统创建一条摘要记录，状态依次为 PENDING → PROCESSING → COMPLETED，并在完成后返回视频标题、作者、时长、缩略图与摘要正文

#### Scenario: 余额不足时不创建
- **WHEN** 已登录用户提交视频链接，但其积分余额低于单条最低扣分
- **THEN** 系统返回积分不足错误，不创建摘要记录，余额保持不变

#### Scenario: 无效或不受支持的链接
- **WHEN** 用户提交无法被识别为有效视频的链接
- **THEN** 系统删除该摘要记录，并向提交者返回可读的错误原因

#### Scenario: 视频无可用字幕
- **WHEN** 用户提交的视频既无官方字幕也无自动字幕
- **THEN** 系统删除该摘要记录，并提示该视频没有可用的字幕内容

#### Scenario: 失败退回积分
- **WHEN** 某次摘要已按其时长扣除积分，随后因字幕抓取或 AI 生成失败而终止
- **THEN** 该次扣分被原额退回，用户余额恢复至扣分前

### Requirement: 摘要输出形态

系统 SHALL 只提供一种摘要形态：用 Markdown（标题 + 分段）输出的结构化详细摘要，语言为简体中文；对外接口 SHALL NOT 提供摘要模式参数。

#### Scenario: 生成摘要
- **WHEN** 用户提交链接生成摘要
- **THEN** 摘要正文为 Markdown 结构化的详细摘要，包含标题与分段，不出现其他形态的输出

### Requirement: 可配置 AI 提供商

系统 SHALL 通过配置指定使用的 AI 提供商（如 Claude 或 OpenAI 兼容接口），切换提供商配置 SHALL NOT 要求修改业务代码或对外 API。

#### Scenario: 切换提供商配置
- **WHEN** 运维修改配置将提供商从一家切换到另一家（并提供对应凭据）
- **THEN** 后续生成的摘要由新配置的提供商完成，对外 API 与响应结构保持不变

#### Scenario: 提供商凭据缺失或无效
- **WHEN** 系统配置的 AI 提供商凭据缺失或无效
- **THEN** 系统删除该摘要记录，并返回可读的配置错误原因

### Requirement: 字幕抓取与超长转录处理

系统 SHALL 优先抓取视频平台提供的字幕作为摘要输入；当字幕文本超过单次处理的长度上限时，系统 SHALL 按上限截断并在摘要中标注内容可能不完整。

#### Scenario: 超长转录截断
- **WHEN** 字幕文本超过系统配置的处理长度上限
- **THEN** 系统仍生成摘要，但仅在截断后的文本上进行，并在结果中注明输入被截断

### Requirement: 单条视频时长上限

系统 SHALL 拒绝时长超过配置上限（默认 3 小时）的视频。拒绝 SHALL 发生在扣分之前：SHALL NOT 扣除任何积分，且 SHALL 按「失败不落库」删除该摘要记录（原因只落服务端日志）。时长未知时 SHALL NOT 拒绝，交由后续流程处理。

#### Scenario: 视频超过时长上限
- **WHEN** 用户提交的视频时长超过上限
- **THEN** 系统在拿到时长后立即终止该任务，删除摘要记录且一分不扣

#### Scenario: 时长恰好等于上限
- **WHEN** 视频时长恰好等于上限
- **THEN** 任务照常继续（上限按「不超过」判定）

### Requirement: 失败原因回传给用户

任务失败时会按「失败不落库」删除记录，但系统 SHALL 在**有限的时间内**保留这次失败的原因（只在服务端内存里，SHALL NOT 落库），并让用户查询该摘要时拿到该原因 —— 以一条失败态的响应代替「记录不存在」，使用户看到的是可读的原因而不是笼统的失败。原因 SHALL 涵盖：链接无效或视频无法解析、没有可用字幕、超过时长上限、积分不足、字幕抓取失败、AI 生成失败。超过保留期后 SHALL 按「记录不存在」处理。

#### Scenario: 没有字幕
- **WHEN** 用户对一个没有可用字幕的视频发起生成，随后查询这条摘要
- **THEN** 查询返回失败态与「该视频没有可用字幕」这一原因，且该次生成不扣积分

#### Scenario: 超过时长上限
- **WHEN** 用户提交的视频超过时长上限，随后查询这条摘要
- **THEN** 查询返回失败态与包含该上限的原因

#### Scenario: 原因已过期
- **WHEN** 失败发生后超过了原因的保留期再查询这条摘要
- **THEN** 返回「记录不存在」，与一条从未存在过的摘要表现一致

### Requirement: 模型输出撞上限时照旧交付

模型输出被 max_tokens 截断时，系统 SHALL 保留已生成的内容并标记「内容被截断」，SHALL NOT 判为失败丢弃 —— 这批 token 已经产生费用，丢弃等于白付。此时扣分 SHALL NOT 退回（内容已交付）。

#### Scenario: 输出被 max_tokens 截断
- **WHEN** 模型因撞上 max_tokens 提前结束（OpenAI 兼容的 `finish_reason=length` / Claude 的 `stop_reason=max_tokens`）
- **THEN** 已生成的部分照常入库，记录标记为内容已截断，用户能看到摘要与截断提示，扣分不退

#### Scenario: 截断后没有可交付的正文
- **WHEN** 响应里只剩思维链或完全为空（被截断在半截推理里）
- **THEN** 按失败处理：删除记录并退回扣分

### Requirement: 历史记录

已登录用户 SHALL 能查看自己历史生成的摘要记录、按记录查询详情，并删除自己的记录。

#### Scenario: 查看自己的历史记录
- **WHEN** 已登录用户请求摘要历史列表
- **THEN** 系统返回该用户自己的摘要记录（含状态、视频标题、时间），按时间倒序排列

#### Scenario: 删除自己的记录
- **WHEN** 已登录用户请求删除属于自己的某条摘要记录
- **THEN** 系统删除该记录并返回成功

#### Scenario: 越权访问他人记录
- **WHEN** 用户请求查看或删除不属于自己的摘要记录
- **THEN** 系统返回 403 拒绝，且不泄露记录内容

### Requirement: SEO 基础标签与 TDK

系统生成的前端页面 SHALL 具有唯一的标题（Title，50-60 字符）、描述（Description，150-160 字符）与关键词（Keywords，不超过 10 个）；这些标签以及字符编码、视口、Open Graph、canonical 等必要 meta 标签 SHALL 出现在服务端渲染返回的 HTML 源码中，而非仅由客户端脚本注入。

#### Scenario: 首页唯一 TDK
- **WHEN** 用户或爬虫请求首页
- **THEN** 返回的 HTML 源码包含唯一的 Title 与 Description（长度符合规范）及关键词，且与其他页面不同

#### Scenario: 认证页面不被收录
- **WHEN** 用户或爬虫请求登录、注册或历史记录页面
- **THEN** 页面 robots meta 标记为 noindex，不进入搜索引擎索引

#### Scenario: SSR 源码可见性
- **WHEN** 禁用客户端 JavaScript 查看任意页面源码
- **THEN** Title、Description、Keywords 与 Open Graph 标签仍出现在 HTML 源码中

### Requirement: 结构化数据（Schema.org）

系统首页 SHALL 输出 WebSite 与 Organization 类型的 JSON-LD 结构化数据，并在首页常见问题区块输出 FAQPage 结构化数据，且每一项问题与回答一一对应。

#### Scenario: 首页结构化数据
- **WHEN** 用户或爬虫请求首页
- **THEN** HTML 源码包含 WebSite 与 Organization 类型的 JSON-LD

#### Scenario: FAQ 结构化数据
- **WHEN** 首页包含常见问题区块
- **THEN** 输出 FAQPage JSON-LD，每个问题与其答案一一对应

### Requirement: 爬虫可达性与 AI 引擎友好（GEO）

系统前端 SHALL 允许主流搜索引擎与 AI 爬虫（含 GPTBot、Google-Extended、PerplexityBot、ClaudeBot 等）抓取站点，并 SHALL 提供可访问的 robots.txt 与 sitemap.xml。

#### Scenario: robots 允许 AI 爬虫
- **WHEN** 请求 /robots.txt
- **THEN** 返回允许全部 User-Agent 的规则，不屏蔽 GPTBot、Google-Extended、PerplexityBot、ClaudeBot 等 AI 爬虫

#### Scenario: sitemap 仅含可索引页面
- **WHEN** 请求 /sitemap.xml
- **THEN** 返回列出首页等可索引页面的有效 XML，登录、注册、历史记录等认证页面不包含在内

### Requirement: 首页落地内容（可抓取）

系统首页 SHALL 以服务端渲染方式呈现结构化落地内容：结论先行的主文案、功能特性、使用步骤与常见问题，并遵循单一 H1、层级化 H2/H3、图片 alt 与描述性锚文本。

#### Scenario: 结论先行
- **WHEN** 查看首页首屏内容
- **THEN** 前 100 字符内直接说明产品核心价值（粘贴视频链接，AI 自动生成视频摘要）

#### Scenario: 唯一 H1 与标题层级
- **WHEN** 查看首页 HTML 源码
- **THEN** 有且仅有一个 H1，且 H2、H3 按层级嵌套使用

#### Scenario: 图片 alt 与锚文本
- **WHEN** 首页包含图片与链接
- **THEN** 每张图片都有描述性 alt 属性，链接使用描述性锚文本而非"点击这里"等模糊词
