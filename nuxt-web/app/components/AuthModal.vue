<script setup lang="ts">
import * as z from 'zod'
import type { FormSubmitEvent } from '@nuxt/ui'

const { open, redirect, closeModal } = useAuthModal()
const { login } = useAuth()
const turnstile = useTurnstile()
const turnstileEl = ref<HTMLElement | null>(null)

/** 登录即注册：后端对没注册过的邮箱直接用这个密码建号，所以密码规则按注册的来 */
const schema = z.object({
  email: z.email('请输入正确的邮箱地址'),
  password: z.string().min(6, '密码至少 6 位')
})

type Credentials = z.output<typeof schema>

const state = reactive({ email: '', password: '' })
const loading = ref(false)
const error = ref('')

// UModal 的内容是懒挂载的，所以盯元素本身而不是盯 open —— 这样不用赌 open 与 DOM 更新的先后
watch(turnstileEl, (el) => {
  if (el) {
    turnstile.mount(el)
  } else {
    turnstile.unmount()
  }
})

const canSubmit = computed(() => !turnstile.enabled.value || !!turnstile.token.value)

// 登录成功后回到原目标页（受保护路由跳转时由 app.vue 存入弹窗状态的 redirect）
async function redirectAfterAuth() {
  const target = redirect.value
  redirect.value = null
  if (target && target.startsWith('/')) {
    return navigateTo(target)
  }
}

async function onSubmit(event: FormSubmitEvent<Credentials>) {
  loading.value = true
  error.value = ''
  try {
    await login(event.data.email, event.data.password, turnstile.token.value)
    await redirectAfterAuth()
    closeModal()
  } catch (e: any) {
    error.value = e?.data?.message ?? '登录失败，请稍后重试'
  } finally {
    loading.value = false
    // token 是一次性的：这次用掉了就换新的，否则重试必被服务端拒
    turnstile.reset()
  }
}
</script>

<template>
  <UModal v-model:open="open" title="登录" :ui="{
    content: 'sm:max-w-sm divide-y-0',
    header: 'justify-center',
    wrapper: 'text-center',
    title: 'text-2xl font-semibold text-ink',
    body: 'px-6 pb-7 pt-2'
  }">
    <template #body>
      <div class="space-y-4">
        <UAlert v-if="error" color="error" variant="subtle" :title="error" />

        <UForm :schema="schema" :state="state" class="space-y-3" @submit="onSubmit">
          <UFormField name="email">
            <UInput v-model="state.email" type="email" size="xl" class="w-full" :ui="{ base: 'py-2.5' }"
              leading-icon="i-material-symbols-mail" placeholder="邮箱" aria-label="邮箱" autocomplete="email" />
          </UFormField>

          <UFormField name="password">
            <UInput v-model="state.password" type="password" size="xl" class="w-full" :ui="{ base: 'py-2.5' }"
              leading-icon="i-material-symbols-lock" placeholder="密码（至少 6 位）" aria-label="密码"
              autocomplete="current-password" />
          </UFormField>

          <div v-show="turnstile.enabled.value" ref="turnstileEl" class="flex justify-center" />
          <p v-if="turnstile.failed.value" class="text-center text-xs leading-5 text-ink-4">
            人机验证加载失败，请刷新页面后重试
          </p>

          <UButton type="submit" block size="xl" :ui="{ base: 'py-2.5' }" :loading="loading"
            :disabled="loading || !canSubmit" label="登录/注册" />
        </UForm>

        <!-- 用户协议 -->
        <div>
          <p class="text-center text-xs leading-5 text-ink-4">
            未注册过的账号我们将自动帮你注册账号
          </p>
          <p class="text-center text-xs leading-5 text-ink-4">
            登录或注册即代表同意
            <NuxtLink to="/terms" target="_blank" rel="noopener"
              class="text-ink-2 underline underline-offset-2 transition-colors hover:text-primary/75">用户协议</NuxtLink>
            与
            <NuxtLink to="/privacy" target="_blank" rel="noopener"
              class="text-ink-2 underline underline-offset-2 transition-colors hover:text-primary/75">隐私政策</NuxtLink>
          </p>
        </div>
      </div>
    </template>
  </UModal>
</template>
