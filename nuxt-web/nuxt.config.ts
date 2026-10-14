const siteUrl = process.env.NUXT_PUBLIC_SITE_URL
if (process.env.NODE_ENV === 'production' && (!siteUrl || siteUrl.includes('localhost'))) {
  throw new Error('[vidoc] 生产构建必须设置 NUXT_PUBLIC_SITE_URL（形如 https://vidoc.example.com）')
}

// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: false },
  modules: ['@nuxt/ui', '@nuxtjs/seo', 'nuxt-ai-ready', '@nuxtjs/mdc'],
  css: ['~/assets/css/main.css'],

  // 引入 Docus 文档
  extends: ['docus'],

  // 摘要正文按 md 编辑器「预览模式」渲染
  mdc: {
    headings: {
      anchorLinks: false
    }
  },

  colorMode: { preference: 'light', fallback: 'light' },

  runtimeConfig: {
    public: {
      apiBase: process.env.NUXT_PUBLIC_API_BASE || '',
      turnstileSiteKey: process.env.NUXT_PUBLIC_TURNSTILE_SITE_KEY || ''
    }
  },

  nitro: {
    devProxy: {
      '/api': {
        target: process.env.NUXT_API_PROXY_TARGET || 'http://localhost:8080/api',
        changeOrigin: true
      }
    },
    prerender: {
      crawlLinks: true,
      // /docs/** 站内没有可见入口（Header/Footer 未改动），只能显式列出；
      // 侧栏与「上一页/下一页」渲染出的内链再由 crawlLinks 兜底补发现。
      routes: [
        '/docs',
        '/docs/guide/quickstart',
        '/docs/guide/how-it-works',
        '/docs/guide/supported-platforms',
        '/docs/guide/credits-and-plans',
        '/docs/guide/history-and-summary',
        '/docs/reference/faq',
        '/docs/reference/troubleshooting',
        '/docs/reference/privacy-and-data'
      ],
      // docus 会给每个文档页 addPrerenderPath('/raw<path>.md')，但 /raw 只在 vercel
      // preset 下注册，静态构建里不存在，直接忽略。注意 nitro 的 ignore 是纯前缀匹配
      // （matchesIgnorePattern 用 path.startsWith），写 glob 无效。
      ignore: ['/history', '/dashboard', '/dashboard/**', '/summary', '/404.md', '/raw']
    }
  },

  // @nuxt/icon：
  // 1) 把本地图标端点从 /api/_nuxt_icon 挪到 /_nuxt_icon，避免被上方 /api devProxy
  //    转发到后端（后端未启动时图标请求挂起、全部加载失败）；
  // 2) 开启 clientBundle.scan，把源码中 i-material-symbols-* 与 @nuxt/ui 内部
  //    lucide 默认图标静态打进 bundle，SSR/客户端同步可用、无需网络请求
  //    （服务器端 fetch.native 无法解析相对 URL，否则 SSR 图标加载必然失败）。
  icon: {
    localApiEndpoint: '/_nuxt_icon',
    clientBundle: {
      scan: true
    }
  },

  fonts: {
    providers: {
      google: false,
      googleicons: false
    },
    priority: []
  },

  ogImage: {
    enabled: false
  },

  // SEO / GEO 站点配置
  site: {
    url: siteUrl || 'http://localhost:3000',
    name: 'Vidoc Note - AI 视频摘要生成器',
    description: '粘贴视频链接，AI 自动生成视频摘要',
    defaultLocale: 'zh-CN'
  },

  aiReady: {
    llmsTxtCacheSeconds: 0
  },

  // sitemap
  sitemap: {
    exclude: ['/history', '/dashboard', '/dashboard/**', '/summary', '/summary/**', '/404'],
    autoLastmod: true,
    discoverImages: false
  },

  app: {
    head: {
      htmlAttrs: { lang: 'zh-CN' },
      charset: 'utf-8',
      viewport: 'width=device-width, initial-scale=1',
      titleTemplate: '%s',
      meta: [{ name: 'theme-color', content: '#ffffff' }]
    }
  }
})
