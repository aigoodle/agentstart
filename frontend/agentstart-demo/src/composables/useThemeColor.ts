import { getAgentStartColorForeground, getAgentStartAccessiblePrimary } from 'vue-agent-start'
import { computed, reactive, watch } from 'vue'

const STYLE_ID = 'as-theme-color-vars'
const STORAGE_KEY = 'agent-start-theme-color'
const DEFAULT_COLOR = '#00ccff'

export const PRESET_COLORS = [
  '#00ccff', // 默认青
  '#1677ff', // 蓝色
  '#722ed1', // 紫色
  '#eb2f96', // 洋红
  '#f5222d', // 红色
  '#fa8c16', // 橙色
  '#fadb14', // 黄色
  '#52c41a', // 绿色
  '#13c2c2', // 青色
  '#2f54eb', // 极客蓝
]

// ── 单例状态 ───────────────────────────────────────────────────────────────
const state = reactive<{ color: string }>({ color: readStored() })

function readStored(): string {
  try {
    const v = localStorage.getItem(STORAGE_KEY)
    if (v && /^#[0-9a-fA-F]{6}$/.test(v)) return v
  } catch {
    /* ignore */
  }
  return DEFAULT_COLOR
}

// Accent updates the same semantic tokens in both color schemes.
function applyStyle(color: string) {
  document.getElementById(STYLE_ID)?.remove()
  const root = document.documentElement
  const primary = getAgentStartAccessiblePrimary(color)
  root.style.setProperty('--primary', primary)
  root.style.setProperty('--ring', primary)
  root.style.setProperty('--primary-foreground', getAgentStartColorForeground(primary))
}
applyStyle(state.color)
watch(() => state.color, color => {
  applyStyle(color)
  try { localStorage.setItem(STORAGE_KEY, color) } catch { /* unavailable storage */ }
})

// ── 导出 ────────────────────────────────────────────────────────────────────
export function useThemeColor() {
  function setColor(hex: string) {
    if (/^#[0-9a-fA-F]{6}$/.test(hex)) state.color = hex
  }
  function reset() {
    state.color = DEFAULT_COLOR
  }
  return {
    color: computed(() => state.color),
    presets: PRESET_COLORS,
    defaultColor: DEFAULT_COLOR,
    setColor,
    reset,
  }
}
