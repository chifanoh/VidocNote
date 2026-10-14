<script setup lang="ts">
import type { NavigationMenuItem } from '@nuxt/ui'

const { user, logout } = useAuth()

const navItems: NavigationMenuItem[] = [
  { label: '返回首页', icon: 'i-material-symbols-home', to: '/' },
  { label: '用量信息', icon: 'i-material-symbols-monitoring', to: '/dashboard' },
  { label: '订阅', icon: 'i-material-symbols-workspace-premium', to: '/dashboard/plans' },
  { label: '账单', icon: 'i-material-symbols-receipt-long', to: '/dashboard/billing' }
]

// 反馈意见 / 帮助与支持：项目里还没有对应的页面或外部地址，先保持无跳转（to 为空）
const helpItems: NavigationMenuItem[] = [
  { label: '反馈意见', icon: 'i-material-symbols-chat', to: '' },
  { label: '帮助与支持', icon: 'i-material-symbols-help', to: '/docs' }
]

const accountItems = computed<NavigationMenuItem[][]>(() => [[
  { label: user.value?.email ?? '未登录', type: 'label' },
  { label: '退出登录', icon: 'i-material-symbols-logout', onSelect: () => logout() }
]])

// 导航项用「卡片底纹」：选中与悬停都是 pill 变体自带的 before:bg-elevated（浅灰）；悬停默认只有 50% 透明度，
// 用户要求两种状态同色，故用 hover:before:bg-elevated 拉平（用户指定，是「背景一律纯白」在仪表盘导航上的例外）。
// 选项之间是卡片、不是纯文字，故拉开行距避免两块底纹连成一片。
const NAV_UI = { list: 'space-y-2', link: 'hover:before:bg-elevated' }
</script>

<template>
  <UDashboardGroup>
    <UDashboardSidebar collapsible resizable :ui="{ root: 'border-line', footer: 'border-t border-line' }">
      <template #header="{ collapsed }">
        <NuxtLink v-if="!collapsed" to="/" class="flex items-center">
          <Logo />
        </NuxtLink>
        <UDashboardSidebarCollapse square :class="collapsed ? 'mx-auto' : 'ms-auto'" />
      </template>

      <template #default="{ collapsed }">
        <UNavigationMenu :collapsed="collapsed" :items="navItems" orientation="vertical" :ui="NAV_UI" />

        <UNavigationMenu :collapsed="collapsed" :items="helpItems" orientation="vertical" :ui="NAV_UI" class="mt-auto" />
      </template>

      <template #footer="{ collapsed }">
        <UDropdownMenu :items="accountItems" :content="{ align: 'start' }" class="w-full">
          <!-- 原生 button 承接触发器：reka 会把 aria / click 合并到默认插槽第一个子节点，
               直接给 UAvatar 的话这些会落到它内部那个 24px 图标上 -->
          <button
            type="button"
            class="flex w-full min-w-0 cursor-pointer items-center gap-2 rounded-md px-1 py-1 transition hover:bg-black/[0.06] focus-visible:outline-none"
            :class="collapsed ? 'justify-center' : ''"
            aria-label="账户菜单"
          >
            <UAvatar icon="i-material-symbols-person" color="neutral" size="md" class="shrink-0" />
            <!-- 邮箱只有客户端才拿得到（SSR 不调 /api/auth/me），不裹 ClientOnly 会在 hydration 时文本对不上 -->
            <ClientOnly>
              <span v-if="!collapsed" class="min-w-0 flex-1 truncate text-left text-sm text-ink-4">{{ user?.email }}</span>
            </ClientOnly>
          </button>
        </UDropdownMenu>
      </template>
    </UDashboardSidebar>

    <UDashboardPanel>
      <template #body>
        <UDashboardSidebarToggle />
        <slot />
      </template>
    </UDashboardPanel>
  </UDashboardGroup>
</template>
