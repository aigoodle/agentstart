import { computed, reactive, watch } from 'vue'

export type Theme = 'dark' | 'light'

const STORAGE_KEY = 'agent-start-theme'

function readInitial(): Theme {
  try {
    const v = localStorage.getItem(STORAGE_KEY)
    if (v === 'dark' || v === 'light') return v
  } catch {
    /* ignore */
  }
  return 'dark'
}

// 单例状态：所有组件共享同一份主题
const state = reactive<{ value: Theme }>({ value: readInitial() })

function apply(theme: Theme) {
  const root = document.documentElement
  if (theme === 'dark') {
    root.classList.add('dark')
    root.dataset.theme = 'dark'
  } else {
    root.classList.remove('dark')
    root.dataset.theme = 'light'
  }
}

// 初始化时立即应用一次（SSR-friendly：仅在客户端执行）
if (typeof document !== 'undefined') apply(state.value)

watch(
  () => state.value,
  (v) => {
    apply(v)
    try {
      localStorage.setItem(STORAGE_KEY, v)
    } catch {
      /* ignore */
    }
  },
)

export function useTheme() {
  const isDark = computed(() => state.value === 'dark')
  function toggle() {
    state.value = state.value === 'dark' ? 'light' : 'dark'
  }
  function set(theme: Theme) {
    state.value = theme
  }
  return { theme: computed(() => state.value), isDark, toggle, set }
}
