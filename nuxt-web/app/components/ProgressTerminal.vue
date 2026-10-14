<script setup lang="ts">
import type { TerminalStep, TerminalStepState } from '~/types'

defineProps<{
  steps: TerminalStep[]
  command?: string
}>()

const emit = defineEmits<{ clear: [] }>()

const markClass: Record<TerminalStepState, string> = {
  pending: 'text-neutral-600',
  active: 'text-emerald-400',
  done: 'text-emerald-400',
  failed: 'text-red-400'
}

const textClass: Record<TerminalStepState, string> = {
  pending: 'text-neutral-500',
  active: 'text-neutral-100',
  done: 'text-neutral-300',
  failed: 'text-red-400'
}

const markGlyph: Record<TerminalStepState, string> = {
  pending: '○',
  active: '◐',
  done: '✓',
  failed: '✕'
}
</script>

<template>
  <div class="overflow-hidden rounded-lg bg-[#0d0d0f] text-left shadow-card ring-1 ring-black/10">
    <div class="flex items-center gap-1.5 border-b border-white/10 bg-white/[0.04] px-4 py-2.5">
      <span class="size-3 rounded-full bg-[#ff5f57]" />
      <span class="size-3 rounded-full bg-[#febc2e]" />
      <span class="size-3 rounded-full bg-[#28c840]" />
      <UButton
        icon="i-material-symbols-delete-outline-rounded"
        color="error"
        variant="link"
        size="xs"
        aria-label="停止并清空"
        class="ms-auto"
        @click="emit('clear')"
      />
    </div>

    <div class="max-h-72 overflow-y-auto px-4 py-3 font-mono text-[13px] leading-6">
      <p v-if="command" class="break-all text-neutral-100">
        <span class="mr-1 select-none font-semibold text-emerald-400 before:content-['$']" />{{ command }}
      </p>

      <div v-if="steps.length" class="flow mt-2">
        <div v-for="step in steps" :key="step.key" class="flow-row" :class="step.state">
          <span class="flow-mark" :class="markClass[step.state]">
            <span :class="step.state === 'active' ? 'spin' : ''">{{ markGlyph[step.state] }}</span>
          </span>
          <span class="flow-text" :class="textClass[step.state]">{{ step.label }}</span>
        </div>
      </div>

      <p v-else-if="!command" class="select-none text-neutral-500 before:content-['$']" />
    </div>
  </div>
</template>

<style scoped>
.flow {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.flow-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.flow-mark {
  display: inline-flex;
  width: 1em;
  flex: none;
  justify-content: center;
}

.spin {
  display: inline-block;
  animation: flow-spin 1s linear infinite;
}

@keyframes flow-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
