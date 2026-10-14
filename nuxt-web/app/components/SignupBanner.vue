<script setup lang="ts">
const { isLoggedIn } = useAuth()
const { openModal } = useAuthModal()
const route = useRoute()

/** 关闭记忆的键。换过键名 = 之前点过关掉的人也会再见一次，别随意复用旧键 */
const CLOSED_KEY = 'vidoc-signup-banner'

/**
 * 横幅直接进 SSR HTML（首绘就在，不用等水合），该不该藏交给 <head> 里的内联脚本在**首次绘制前**判：
 * 不在首页、cookie 里有 token（已登录）、或关过 —— 这三件事在 head 阶段就能读到。
 * 水合后的可见性由下面的 computed 决定，两处条件必须一致。
 */
useHead({
  script: [{
    key: 'signup-banner-prehydrate',
    tagPosition: 'head',
    innerHTML: `(function(){try{var hide=location.pathname!=='/'||/(?:^|;\\s*)vidoc_token=/.test(document.cookie)||localStorage.getItem('${CLOSED_KEY}')==='true';if(hide){document.documentElement.setAttribute('data-signup-banner','hide')}}catch(e){}})()`
  }],
  style: [{
    key: 'signup-banner-style',
    tagPosition: 'head',
    innerHTML: 'html[data-signup-banner="hide"] .signup-banner{display:none!important}'
  }]
})

// 初值与 SSR 保持一致（都当作「没关过」）以免水合不匹配；真实值挂载后再补
const closed = ref(false)
onMounted(() => {
  try {
    closed.value = localStorage.getItem(CLOSED_KEY) === 'true'
  } catch {
    closed.value = false // 隐私模式下读不到，当作没关过
  }
})

const visible = computed(() => !closed.value && !isLoggedIn.value && route.path === '/')

function close() {
  closed.value = true
  try {
    localStorage.setItem(CLOSED_KEY, 'true')
  } catch {
    // 写不进去就只在本次会话里关掉
  }
}
</script>

<template>
  <UBanner
    v-if="visible"
    icon="i-material-symbols-redeem"
    title="新用户注册赠送 100 体验积分"
    close
    class="signup-banner border-b border-line bg-page"
    :ui="{ title: 'text-ink', icon: 'text-ink', close: 'text-ink-3 hover:text-hover' }"
    @close="close"
  >
    <template #actions>
      <button
        type="button"
        class="shrink-0 text-sm font-medium text-ink underline underline-offset-2 hover:text-hover"
        @click="openModal()"
      >
        免费注册
      </button>
    </template>
  </UBanner>
</template>
