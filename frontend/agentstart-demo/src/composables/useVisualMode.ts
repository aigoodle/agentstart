import { computed, reactive, watch } from 'vue'

export type VisualMode = 'normal' | 'tech'

const STORAGE_KEY = 'agent-start-visual-mode'

function readInitial(): VisualMode {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    if (value === 'normal' || value === 'tech') return value
  } catch {
    /* ignore */
  }
  return 'tech'
}

const state = reactive<{ value: VisualMode }>({ value: readInitial() })

function apply(mode: VisualMode) {
  document.documentElement.dataset.visual = mode
}

if (typeof document !== 'undefined') apply(state.value)

watch(
  () => state.value,
  (value) => {
    apply(value)
    try {
      localStorage.setItem(STORAGE_KEY, value)
    } catch {
      /* ignore */
    }
  },
)

export function useVisualMode() {
  const isTech = computed(() => state.value === 'tech')
  function toggle() {
    state.value = state.value === 'tech' ? 'normal' : 'tech'
  }
  function set(mode: VisualMode) {
    state.value = mode
  }
  return { mode: computed(() => state.value), isTech, toggle, set }
}
