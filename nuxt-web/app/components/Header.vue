<script setup lang="ts">
const { isLoggedIn, user, logout } = useAuth()
const { openModal } = useAuthModal()

const profileOpen = ref(false)
// 手机端抽屉（右上角的展开图标）
const menuOpen = ref(false)
let hoverTimer: ReturnType<typeof setTimeout> | null = null

function onProfileHover(open: boolean) {
  if (hoverTimer) clearTimeout(hoverTimer)
  hoverTimer = setTimeout(() => {
    profileOpen.value = open
  }, open ? 120 : 60)
}

onUnmounted(() => {
  if (hoverTimer) clearTimeout(hoverTimer)
})

const NAV_LINK = 'py-2 text-base font-medium text-ink transition-colors hover:text-ink-4 focus-visible:outline-none'
const MENU_LINK = 'flex items-center gap-3 rounded-md px-3 py-2.5 text-base font-medium text-ink transition-colors hover:bg-black/[0.04] focus-visible:outline-none'
// 中间栏标签；手机上整条隐藏，改从右上角抽屉里点（lg 以下放不下）
const NAV_ITEMS = [
  { label: '文档', to: '/docs' },
  { label: '价格', to: '/#pricing' },
  { label: '常见问题', to: '/docs/reference/faq' }
]

const route = useRoute()
const showNav = computed(() => route.meta.headerNav !== false)

// 去别的页面才开新标签页；点的就是当前这一页（含页内锚点）本页跳
function linkTarget(to: string) {
  return route.path === to.split('#')[0] ? undefined : '_blank'
}

const profileItems = computed(() => [
  { label: '仪表盘', icon: 'i-material-symbols-space-dashboard', to: '/dashboard', target: linkTarget('/dashboard') },
  { label: '历史记录', icon: 'i-material-symbols-schedule', to: '/history', target: linkTarget('/history') },
  { type: 'separator' as const },
  { label: '退出登录', icon: 'i-material-symbols-logout', onSelect: logout }
])

function closeMenu() {
  menuOpen.value = false
}
</script>

<template>
  <UHeader
    title="Vidoc Note"
    class="bg-page"
    :toggle="false"
    :ui="{
      root: 'border-b-0!',
      left: 'flex flex-1 items-center gap-1.5',
      center: 'flex items-center justify-center',
      right: 'flex flex-1 items-center justify-end gap-1.5',
    }"
  >
    <template #left>
      <div data-slot="left" class="lg:flex-1 flex flex-1 items-center gap-1.5">
        <NuxtLink
          to="/"
          :target="linkTarget('/')"
          aria-label="Vidoc Note"
          data-slot="title"
          class="shrink-0 rounded-md font-bold text-xl text-highlighted outline-primary/25 focus-visible:outline-3 flex items-end gap-1.5"
        >
          <Logo />
        </NuxtLink>
      </div>
    </template>

    <template #default>
      <nav v-if="showNav" class="hidden items-center gap-5 lg:flex" aria-label="主导航">
        <NuxtLink v-for="item in NAV_ITEMS" :key="item.to" :to="item.to" :target="linkTarget(item.to)" :class="NAV_LINK">
          {{ item.label }}
        </NuxtLink>
      </nav>
    </template>

    <template #right>
      <template v-if="isLoggedIn">
        <div
          class="relative hidden lg:block"
          @mouseenter="onProfileHover(true)"
          @mouseleave="onProfileHover(false)"
        >
          <UDropdownMenu
            v-model:open="profileOpen"
            :items="profileItems"
            :portal="false"
            :modal="false"
            :external-icon="false"
            :ui="{
              item: 'data-highlighted:text-default data-highlighted:before:bg-elevated',
              itemLeadingIcon: 'text-default',
            }"
          >
            <button
              type="button"
              class="cursor-pointer rounded-full focus-visible:outline-none"
              aria-label="账户菜单"
            >
              <UAvatar
                icon="i-material-symbols-person"
                :alt="user?.email"
                color="neutral"
                size="xl"
                :ui="{ icon: 'size-6' }"
                class="cursor-pointer rounded-full transition hover:bg-black/[0.06]"
              />
            </button>
          </UDropdownMenu>
        </div>
      </template>
      <template v-else>
        <button
          type="button"
          class="hidden cursor-pointer py-2 text-base font-medium text-ink transition-colors hover:text-hover focus-visible:outline-none lg:inline-flex"
          @click="openModal()"
        >
          登录<span class="mx-1 text-ink-4">/</span>注册
        </button>
      </template>

      <button
        type="button"
        class="flex cursor-pointer items-center focus-visible:outline-none lg:hidden"
        aria-label="打开菜单"
        :aria-expanded="menuOpen"
        @click="menuOpen = true"
      >
        <UIcon name="i-material-symbols-menu" class="size-7 shrink-0 text-ink" />
      </button>
    </template>
  </UHeader>

  <!-- 手机端菜单：底部弹出，顶部用组件默认的横条（不要右侧关闭叉） -->
  <UDrawer v-model:open="menuOpen" direction="bottom">
    <template #body>
      <div class="flex min-h-full flex-col">
        <template v-if="isLoggedIn">
          <div class="flex items-center gap-3">
            <UAvatar
              icon="i-material-symbols-person"
              :alt="user?.email"
              color="neutral"
              size="xl"
              :ui="{ icon: 'size-6' }"
              class="rounded-full"
            />
            <div class="min-w-0">
              <p class="truncate text-sm font-medium text-ink">{{ user?.email }}</p>
              <p class="text-xs text-ink-4">已登录</p>
            </div>
          </div>
          <nav class="mt-3 flex flex-col gap-0.5" aria-label="账户">
            <NuxtLink to="/dashboard" :target="linkTarget('/dashboard')" :class="MENU_LINK" @click="closeMenu">
              仪表盘
            </NuxtLink>
            <NuxtLink to="/history" :target="linkTarget('/history')" :class="MENU_LINK" @click="closeMenu">
              历史记录
            </NuxtLink>
          </nav>
        </template>

        <button
          v-else
          type="button"
          :class="MENU_LINK"
          class="w-full text-left"
          @click="closeMenu(); openModal()"
        >
          登录
        </button>

        <nav
          v-if="showNav"
          class="mt-3 flex flex-col gap-0.5"
          :class="isLoggedIn ? 'border-t border-line pt-3' : ''"
          aria-label="主导航"
        >
          <NuxtLink v-for="item in NAV_ITEMS" :key="item.to" :to="item.to" :target="linkTarget(item.to)" :class="MENU_LINK" @click="closeMenu">
            {{ item.label }}
          </NuxtLink>
        </nav>

        <button
          v-if="isLoggedIn"
          type="button"
          class="mt-3 flex cursor-pointer items-center gap-3 rounded-md border-t border-line px-3 pt-3 pb-2.5 text-left text-base font-medium text-ink transition-colors hover:bg-black/[0.04] focus-visible:outline-none"
          @click="closeMenu(); logout()"
        >
          <UIcon name="i-material-symbols-logout" class="size-5 shrink-0 text-ink-4" />
          退出登录
        </button>
      </div>
    </template>
  </UDrawer>
</template>
