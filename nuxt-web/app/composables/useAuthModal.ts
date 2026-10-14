/**
 * 登录/注册模态框
 */
export function useAuthModal() {
  const open = useState<boolean>('auth_modal_open', () => false)
  const redirect = useState<string | null>('auth_modal_redirect', () => null)

  function openModal(target: string | null = null) {
    redirect.value = target
    open.value = true
  }

  function closeModal() {
    open.value = false
    redirect.value = null
  }

  return { open, redirect, openModal, closeModal }
}
