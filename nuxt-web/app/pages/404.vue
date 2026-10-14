<script setup lang="ts">
definePageMeta({ layout: false, header: false, footer: false })

const info = describePageError(404)

// 错误页不进索引；保留 follow，让爬虫顺着页面继续抓站内其它页
useSeoMeta({
  title: '页面未找到 - Vidoc Note',
  description: '你访问的页面不存在或已移动，返回首页即可继续用 Vidoc Note 生成视频摘要。',
  robots: 'noindex, follow'
})
</script>

<template>
  <AppError :info="info" />
</template>