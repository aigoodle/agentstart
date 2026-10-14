import { computed, reactive, watch } from 'vue'

export type Theme = 'dark' | 'light'
export type ThemeMode = Theme | 'system'

const STORAGE_KEY = 'agent-start-theme'

function readInitial(): ThemeMode {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    if (value === 'dark' || value === 'light' || value === 'system') return value
  } catch {
    /* Storage may be unavailable in an embedded browser. */
  }
  return 'dark'
}

const media = typeof window !== 'undefined'
  ? window.matchMedia('(prefers-color-scheme: dark)')
  : null
const state = reactive({ mode: readInitial(), systemDark: media?.matches ?? true })
const theme = computed<Theme>(() => state.mode === 'system'
  ? (state.systemDark ? 'dark' : 'light')
  : state.mode)

function onSystemChange(event: MediaQueryListEvent) {
  state.systemDark = event.matches
}
media?.addEventListener('change', onSystemChange)
if (import.meta.hot) {
  import.meta.hot.dispose(() => media?.removeEventListener('change', onSystemChange))
}

watch(theme, (value) => {
  if (typeof document === 'undefined') return
  document.documentElement.classList.toggle('dark', value === 'dark')
  document.documentElement.dataset.theme = value
}, { immediate: true })

watch(() => state.mode, (value) => {
  try {
    localStorage.setItem(STORAGE_KEY, value)
  } catch {
    /* Storage may be unavailable in an embedded browser. */
  }
})

export function useTheme() {
  const mode = computed<ThemeMode>({
    get: () => state.mode,
    set: value => { state.mode = value },
  })
  function toggle() {
    state.mode = theme.value === 'dark' ? 'light' : 'dark'
  }
  function set(value: ThemeMode) {
    state.mode = value
  }
  return { theme, mode, isDark: computed(() => theme.value === 'dark'), toggle, set }
}
