## Purpose

让用户粘贴一个视频链接后，系统自动抓取视频元数据与平台字幕，并调用可配置的 AI 提供商生成所选模式的摘要，同时为每个用户提供登录、历史摘要的查看与删除能力。

## ADDED Requirements

### Requirement: 登录注册

系统 SHALL 提供用户注册与登录能力，登录成功后签发令牌，受保护接口在后续请求中携带该令牌进行身份校验。

#### Scenario: 注册新用户
- **WHEN** 用户提交未注册过的邮箱与密码进行注册
- **THEN** 系统创建用户并返回成功，随后可用该凭据登录

#### Scenario: 注册邮箱已存在
- **WHEN** 用户使用已注册的邮箱再次注册
- **THEN** 系统返回冲突错误，不创建重复用户

#### Scenario: 未认证访问受保护接口
- **WHEN** 用户未携带有效令牌请求受保护接口（如创建摘要）
- **THEN** 系统返回 401 未授权，且不执行该操作

### Requirement: 粘贴链接生成摘要

已登录用户 SHALL 能提交一个视频链接与摘要模式，系统抓取该视频的元数据与平台字幕，并异步生成对应模式的摘要。

#### Scenario: 有效视频链接
- **WHEN** 已登录用户提交受支持平台的有效视频链接与摘要模式
- **THEN** 系统创建一条摘要记录，状态依次为 PENDING → PROCESSING → COMPLETED，并在完成后返回视频标题、作者、时长、缩略图与摘要正文

#### Scenario: 无效或不受支持的链接
- **WHEN** 用户提交无法被识别为有效视频的链接
- **THEN** 系统将该记录标记为 FAILED，并返回可读的错误原因

#### Scenario: 视频无可用字幕
- **WHEN** 用户提交的视频既无官方字幕也无自动字幕
- **THEN** 系统将该记录标记为 FAILED，并提示该视频没有可用的字幕内容

### Requirement: 摘要模式

系统 SHALL 至少支持四种摘要模式：简短、详细、要点式、时间戳章节；摘要正文 SHALL 遵循所选模式的输出形式。

#### Scenario: 切换摘要模式
- **WHEN** 用户创建摘要时选择"要点式"模式
- **THEN** 生成的摘要以要点列表形式呈现，而非连贯段落

#### Scenario: 时间戳章节模式
- **WHEN** 用户创建摘要时选择"时间戳章节"模式
- **THEN** 摘要按时间区间分章节，每章标注对应视频时间区间

### Requirement: 可配置 AI 提供商

系统 SHALL 通过配置指定使用的 AI 提供商（如 Claude 或 OpenAI 兼容接口），切换提供商配置 SHALL NOT 要求修改业务代码或对外 API。

#### Scenario: 切换提供商配置
- **WHEN** 运维修改配置将提供商从一家切换到另一家（并提供对应凭据）
- **THEN** 后续生成的摘要由新配置的提供商完成，对外 API 与响应结构保持不变

#### Scenario: 提供商凭据缺失或无效
- **WHEN** 系统配置的 AI 提供商凭据缺失或无效
- **THEN** 摘要记录被标记为 FAILED，并返回可读的配置错误原因

### Requirement: 字幕抓取与超长转录处理

系统 SHALL 优先抓取视频平台提供的字幕作为摘要输入；当字幕文本超过单次处理的长度上限时，系统 SHALL 按上限截断并在摘要中标注内容可能不完整。

#### Scenario: 超长转录截断
- **WHEN** 字幕文本超过系统配置的处理长度上限
- **THEN** 系统仍生成摘要，但仅在截断后的文本上进行，并在结果中注明输入被截断

### Requirement: 历史记录

已登录用户 SHALL 能查看自己历史生成的摘要记录、按记录查询详情，并删除自己的记录。

#### Scenario: 查看自己的历史记录
- **WHEN** 已登录用户请求摘要历史列表
- **THEN** 系统返回该用户自己的摘要记录（含状态、视频标题、时间、摘要模式），按时间倒序排列

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
