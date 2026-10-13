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

// ── 颜色工具 ────────────────────────────────────────────────────────────────
function hexToRgb(hex: string): [number, number, number] {
  const n = parseInt(hex.slice(1), 16)
  return [(n >> 16) & 0xff, (n >> 8) & 0xff, n & 0xff]
}

function rgbToHsl(
  r: number,
  g: number,
  b: number,
): [number, number, number] {
  r /= 255
  g /= 255
  b /= 255
  const max = Math.max(r, g, b)
  const min = Math.min(r, g, b)
  const l = (max + min) / 2
  if (max === min) return [0, 0, l * 100]
  const d = max - min
  const s = l > 0.5 ? d / (2 - max - min) : d / (max + min)
  let h = 0
  if (max === r) h = ((g - b) / d + (g < b ? 6 : 0)) / 6
  else if (max === g) h = ((b - r) / d + 2) / 6
  else h = ((r - g) / d + 4) / 6
  return [h * 360, s * 100, l * 100]
}

function hslToRgb(
  h: number,
  s: number,
  l: number,
): [number, number, number] {
  h /= 360
  s /= 100
  l /= 100
  if (s === 0) {
    const v = Math.round(l * 255)
    return [v, v, v]
  }
  const hue2rgb = (p: number, q: number, t: number) => {
    if (t < 0) t += 1
    if (t > 1) t -= 1
    if (t < 1 / 6) return p + (q - p) * 6 * t
    if (t < 1 / 2) return q
    if (t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6
    return p
  }
  const q = l < 0.5 ? l * (1 + s) : l + s - l * s
  const p = 2 * l - q
  return [
    Math.round(hue2rgb(p, q, h + 1 / 3) * 255),
    Math.round(hue2rgb(p, q, h) * 255),
    Math.round(hue2rgb(p, q, h - 1 / 3) * 255),
  ]
}

function rgbToHex(r: number, g: number, b: number): string {
  return (
    '#' +
    [r, g, b]
      .map((v) =>
        Math.max(0, Math.min(255, Math.round(v)))
          .toString(16)
          .padStart(2, '0'),
      )
      .join('')
  )
}

const clamp = (v: number, lo: number, hi: number) =>
  Math.max(lo, Math.min(hi, v))

/** 生成主题色相关 CSS 变量（按暗色/明亮模式分别输出一套） */
function generateVars(
  hex: string,
): { dark: Record<string, string>; light: Record<string, string> } {
  const [r, g, b] = hexToRgb(hex)
  const [h, s, baseL] = rgbToHsl(r, g, b)

  // ── 暗色模式：主色直接使用用户选色 ──
  const darkPrimary = hex
  const darkHover = rgbToHex(
    ...hslToRgb(h, clamp(s, 0, 100), clamp(baseL + 12, 0, 78)),
  )
  const darkActive = rgbToHex(
    ...hslToRgb(h, clamp(s, 0, 100), clamp(baseL - 8, 28, 100)),
  )

  const dark: Record<string, string> = {
    '--color-as-primary': darkPrimary,
    '--color-as-primary-hover': darkHover,
    '--color-as-primary-active': darkActive,
    '--hud-primary': darkPrimary,
    '--hud-primary-bright': darkHover,
    '--hud-primary-deep': darkActive,
    '--kh-color-primary': darkPrimary,
    '--kh-color-primary-strong': darkActive,
    '--kh-color-primary-soft': `rgba(${r}, ${g}, ${b}, 0.12)`,
    '--kh-color-primary-outline': `rgba(${r}, ${g}, ${b}, 0.35)`,
    '--kh-color-info': darkPrimary,
    '--kh-color-info-soft': `rgba(${r}, ${g}, ${b}, 0.12)`,
    // HUD 面板装饰框色
    '--hud-frame-color': `hsla(${h.toFixed(1)}, 90%, 60%, 0.58)`,
    '--hud-frame-hot': `hsla(${h.toFixed(1)}, 95%, 72%, 0.98)`,
    '--hud-frame-soft': `hsla(${h.toFixed(1)}, 90%, 60%, 0.22)`,
    '--hud-frame-glow': `hsla(${h.toFixed(1)}, 90%, 60%, 0.42)`,
  }

  // ── 明亮模式：主色适当压暗以保证对比度 ──
  const lightPrimary = rgbToHex(
    ...hslToRgb(h, clamp(s, 0, 100), clamp(baseL - 12, 22, 100)),
  )
  const lightHover = rgbToHex(
    ...hslToRgb(h, clamp(s, 0, 100), clamp(baseL - 6, 22, 100)),
  )
  const lightActive = rgbToHex(
    ...hslToRgb(h, clamp(s, 0, 100), clamp(baseL - 20, 18, 100)),
  )
  const [lr, lg, lb] = hexToRgb(lightPrimary)

  const light: Record<string, string> = {
    '--color-as-primary': lightPrimary,
    '--color-as-primary-hover': lightHover,
    '--color-as-primary-active': lightActive,
    '--hud-primary': lightPrimary,
    '--hud-primary-bright': lightHover,
    '--hud-primary-deep': lightActive,
    '--kh-color-primary': lightPrimary,
    '--kh-color-primary-strong': lightActive,
    '--kh-color-primary-soft': `rgba(${lr}, ${lg}, ${lb}, 0.1)`,
    '--kh-color-primary-outline': `rgba(${lr}, ${lg}, ${lb}, 0.3)`,
    '--kh-color-info': lightPrimary,
    '--kh-color-info-soft': `rgba(${lr}, ${lg}, ${lb}, 0.1)`,
    '--hud-frame-color': `hsla(${h.toFixed(1)}, 75%, 42%, 0.38)`,
    '--hud-frame-hot': `hsla(${h.toFixed(1)}, 80%, 50%, 0.68)`,
    '--hud-frame-soft': `hsla(${h.toFixed(1)}, 75%, 42%, 0.2)`,
    '--hud-frame-glow': `hsla(${h.toFixed(1)}, 75%, 42%, 0.2)`,
  }

  return { dark, light }
}

// ── 样式注入 ────────────────────────────────────────────────────────────────
/** 生成要注入的 <style> 内容；color === DEFAULT_COLOR 时返回 null */
function buildStyleContent(color: string): string | null {
  if (color === DEFAULT_COLOR) return null

  const { dark, light } = generateVars(color)
  const [r, g, b] = hexToRgb(color)
  const [h] = rgbToHsl(r, g, b)

  const toLines = (obj: Record<string, string>) =>
    Object.entries(obj)
      .map(([k, v]) => `  ${k}: ${v};`)
      .join('\n')

  // 覆盖 .hud-panel 自身的框色变量定义（特异性 (0,1,0)+(0,1,0) > (0,1,0)）
  const panelOverride = `
html .hud-panel {
  --hud-frame-color: ${dark['--hud-frame-color']};
  --hud-frame-hot: ${dark['--hud-frame-hot']};
  --hud-frame-soft: ${dark['--hud-frame-soft']};
  --hud-frame-glow: ${dark['--hud-frame-glow']};
}`

  const lightPanelOverride = `
html[data-theme='light'] .hud-panel {
  --hud-frame-color: ${light['--hud-frame-color']};
  --hud-frame-hot: ${light['--hud-frame-hot']};
  --hud-frame-soft: ${light['--hud-frame-soft']};
  --hud-frame-glow: ${light['--hud-frame-glow']};
}`

  // 覆盖呼吸动画中硬编码的 rgba 值
  const breatheKeyframes = `
@keyframes hud-panel-breathe {
  0%, 100% { filter: drop-shadow(0 0 3px rgba(${r}, ${g}, ${b}, 0.35)); }
  50%      { filter: drop-shadow(0 0 5px rgba(${r}, ${g}, ${b}, 0.55)); }
}`

  // 普通视觉模式下的激活项也使用主题色
  const normalActiveDark = `
html[data-visual='normal'] .hud-nav-item.active {
  color: ${dark['--color-as-primary']};
  background: ${dark['--kh-color-primary-soft']};
}`
  const normalActiveLight = `
html[data-theme='light'][data-visual='normal'] .hud-nav-item.active {
  color: ${light['--color-as-primary']};
  background: ${light['--kh-color-primary-soft']};
}`

  // 侧边导航激活项的边框颜色跟随主题色（科技模式）
  const navActiveDark = `
html .hud-nav-item.active {
  border-color: ${dark['--hud-primary']} !important;
  box-shadow:
    0 0 10px ${dark['--kh-color-primary-soft']},
    inset 0 0 12px ${dark['--kh-color-primary-soft']} !important;
}
html .hud-nav-item.active .hud-nav-icon {
  color: ${dark['--hud-primary']} !important;
  filter: drop-shadow(0 0 5px ${dark['--kh-color-primary-outline']}) !important;
}`
  const navActiveLight = `
html[data-theme='light'] .hud-nav-item.active {
  border-color: ${light['--hud-primary']} !important;
  box-shadow:
    0 0 10px ${light['--kh-color-primary-soft']},
    inset 0 0 12px ${light['--kh-color-primary-soft']} !important;
}
html[data-theme='light'] .hud-nav-item.active .hud-nav-icon {
  color: ${light['--hud-primary']} !important;
  filter: drop-shadow(0 0 5px ${light['--kh-color-primary-outline']}) !important;
}`

  // 模态窗口边线颜色跟随主题色（使用更高特异性）
  const modalOverrideDark = `
html .as-modal.as-hud-frame--modal,
html.as-modal.as-hud-frame--modal,
html .as-drawer.as-hud-frame--drawer {
  --as-hud-modal-line: hsla(${h.toFixed(1)}, 90%, 60%, 0.72) !important;
  --as-hud-modal-hot: hsla(${h.toFixed(1)}, 95%, 72%, 0.98) !important;
  --as-hud-modal-glow: hsla(${h.toFixed(1)}, 90%, 60%, 0.48) !important;
}`
  const modalOverrideLight = `
html[data-theme='light'] .as-modal.as-hud-frame--modal,
html[data-theme='light'] .as-drawer.as-hud-frame--drawer {
  --as-hud-modal-line: hsla(${h.toFixed(1)}, 75%, 42%, 0.55) !important;
  --as-hud-modal-hot: hsla(${h.toFixed(1)}, 80%, 50%, 0.75) !important;
  --as-hud-modal-glow: hsla(${h.toFixed(1)}, 75%, 42%, 0.3) !important;
}`

  return [
    `:root {\n${toLines(dark)}\n}`,
    `html[data-theme='light'] {\n${toLines(light)}\n}`,
    breatheKeyframes,
    panelOverride,
    lightPanelOverride,
    normalActiveDark,
    normalActiveLight,
    navActiveDark,
    navActiveLight,
    modalOverrideDark,
    modalOverrideLight,
  ].join('\n')
}

function applyStyle(color: string) {
  const content = buildStyleContent(color)
  const head = document.head
  let el = document.getElementById(STYLE_ID) as HTMLStyleElement | null
  if (!content) {
    el?.remove()
    console.log('[useThemeColor] Removed style element (default color)')
    return
  }
  if (!el) {
    el = document.createElement('style')
    el.id = STYLE_ID
    head.appendChild(el)
    console.log('[useThemeColor] Created style element')
  }
  el.textContent = content
  console.log('[useThemeColor] Applied color:', color)
  console.log('[useThemeColor] Generated CSS (first 500 chars):', content?.slice(0, 500))
}

// 初始化：恢复已存的非默认色
applyStyle(state.color)

// 监听变化：更新样式并持久化
watch(
  () => state.color,
  (color) => {
    console.log('[useThemeColor] Color changed to:', color)
    applyStyle(color)
    try {
      if (color === DEFAULT_COLOR) {
        localStorage.removeItem(STORAGE_KEY)
      } else {
        localStorage.setItem(STORAGE_KEY, color)
      }
    } catch {
      /* ignore */
    }
  },
)

// ── 导出 ────────────────────────────────────────────────────────────────────
export function useThemeColor() {
  function setColor(hex: string) {
    state.color = hex
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
