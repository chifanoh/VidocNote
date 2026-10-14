// EdgeOne Pages 是纯静态托管：未匹配的路径返回输出根目录的兜底文档（2026-10-03 实测是 index.html，状态 200）。
// 客户端路由页（/history、/dashboard…）和 /summary?id=<uuid> 要能硬加载就必须是「真实文件」，
// 所以把 200.html 外壳复制成对应的 html；真实文件永远优先于兜底文档。
// 404.html 保持 prerender 出的「页面未找到」，平台把它当兜底页时就是用户自己的 404 页。
// 这里只做文件复制，不注入任何脚本（2026-10-03 用户要求：不要注入脚本、不要多次跳转）。
import { copyFile, mkdir, readFile, writeFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const dist = fileURLToPath(new URL('../.output/public/', import.meta.url))
const shell = join(dist, '200.html')
const targets = ['history', 'dashboard', 'dashboard/billing', 'dashboard/plans', 'summary']

for (const path of targets) {
  const file = join(dist, path + '.html')
  await mkdir(dirname(file), { recursive: true })
  await copyFile(shell, file)
}

// 兜底文档是 index.html，它自带首页 payload（path: "/"）：Nuxt 启动时会按 payload 把未知地址改回 "/"，
// 用户就永远只看到首页。把这份 payload 里的 path 去掉（纯数据改动，不加脚本），
// 客户端就按真实地址渲染路由，「不存在的地址」落回 prerender 出的 /404 页。
const indexPath = join(dist, 'index.html')
const html = await readFile(indexPath, 'utf8')
const patched = html.replace(
  /(<script type="application\/json"[^>]*id="__NUXT_DATA__"[^>]*>)([\s\S]*?)(<\/script>)/,
  (whole, open, json, close) => {
    const data = JSON.parse(json)
    if (!Array.isArray(data) || !data[0] || !('path' in data[0])) return whole
    delete data[0].path
    return open + JSON.stringify(data) + close
  },
)
if (patched !== html) await writeFile(indexPath, patched)

console.log('[spa-fallback] 已生成客户端路由外壳：' + targets.map(t => '/' + t).join(', '))
console.log('[spa-fallback] index.html 的 payload 路径已移除：' + (patched !== html))
