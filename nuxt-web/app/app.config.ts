// 本仓库没有配置 git remote，docus 会从本地 git 推断出 github 信息（url 为空），
// 于是每个文档页都渲染出一个指向 "edit/main/content/..." 的 404「Edit this page」链接。
// app.config.ts 在 docus 模块设置默认值之后加载，这里直接关掉它。
export default defineAppConfig({
  github: false
})
