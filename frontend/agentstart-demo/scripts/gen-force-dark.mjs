/**
 * 从 vue-agent-start 的 dist/style.css 中扫描所有硬编码浅色（白底/深字/浅边框/靛蓝强调），
 * 生成 html.dark[data-theme="dark"] 前缀的强制暗色覆盖层 → src/styles/hud-force-dark.css
 *
 * 为什么需要：组件库大量 scoped SFC 样式（knowledge-hub 向导、AppDesignDrawer、
 * FlowDesigner 面板、ChannelHub 等）直接写死了 slate/indigo 浅色 hex，
 * .dark 类与 [data-theme=dark] 令牌翻转覆盖不到它们。
 *
 * 前缀 html.dark[data-theme="dark"] 贡献 (0,2,1) 特异性，严格高于任何
 * `.x[data-v-…]` / `.x[data-v-…] .y[data-v-…]` 原选择器，保证覆盖生效。
 *
 * 库重新构建后可重跑：node scripts/gen-force-dark.mjs
 */
import { readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

const distCss = resolve(import.meta.dirname, '../../vue-agentstart/dist/style.css')
const outFile = resolve(import.meta.dirname, '../src/styles/hud-force-dark.css')

// 先整体剥离注释：dist 里存在包含花括号的注释（如示例代码），
// 若留到按 '}' 切块时会把注释拦腰截断，碎片混进选择器（lightningcss 直接报
// Invalid dangling combinator，浏览器则会静默丢弃整组规则）。
const css = readFileSync(distCss, 'utf-8').replace(/\/\*[\s\S]*?\*\//g, '')

// hex → HUD 变量映射（按属性类别）
const BG_MAP = [
  [/^#(fff|ffffff|fefefe)\b/i, 'var(--hud-surface)'],
  [/^white$/i, 'var(--hud-surface)'],
  [/^#(f8fafc|f9fafb|fafafa|f5f5f5)\b/i, 'var(--hud-sunken)'],
  [/^#(f1f5f9|f3f4f6|e5e7eb|f5f6ff)\b/i, 'var(--hud-fill)'],
  [/^#(eef2ff|e0e7ff|f0f9ff|ecfeff|f0fdf4|e6f4ff|eff6ff)\b/i, 'var(--hud-accent-soft)'],
  [/^#(fef3c7|fffbe6|fff7e6|fde68a)\b/i, 'var(--hud-warning-soft)'],
  [/^#(fff1f0|fff2f0|fef2f2|fee2e2)\b/i, 'var(--hud-danger-soft)'],
  [/^#(f6ffed|dcfce7)\b/i, 'var(--hud-success-soft)'],
]
const TXT_MAP = [
  [/^#(0f172a|111827|1f2937|172033|1e293b|000|000000)\b/i, 'var(--hud-text-strong)'],
  [/^#(334155|374151)\b/i, 'var(--hud-text)'],
  [/^#475569\b/i, 'var(--hud-text-muted)'],
]
const BORDER_MAP = [
  [/^#(e2e8f0|cbd5e1|d1d5db|e5e7eb|f0f0f0|f1f5f9|d9d9d9|e4e4e7|ddd|ccc)\b/i, 'var(--hud-border)'],
]
const ACCENT_TXT = [[/^#(6366f1|4f46e5|4338ca|818cf8|2563eb|1d4ed8)\b/i, 'var(--hud-primary)']]
const ACCENT_BORDER = [[/^#(6366f1|4f46e5|c7d2fe|a5b4fc|93c5fd)\b/i, 'var(--hud-border-strong)']]
const ACCENT_BG = [[/^#(6366f1|4f46e5)\b/i, 'var(--hud-primary)']]

function mapValue(prop, rawValue) {
  const value = rawValue.trim()
  // 仅处理“单色值”声明，渐变/多值简写跳过（少数装饰性场景，保留原样无害）
  if (prop === 'background' || prop === 'background-color') {
    for (const [re, to] of BG_MAP) if (re.test(value)) return to
    for (const [re, to] of ACCENT_BG) if (re.test(value)) return to
  } else if (prop === 'color') {
    for (const [re, to] of TXT_MAP) if (re.test(value)) return to
    for (const [re, to] of ACCENT_TXT) if (re.test(value)) return to
  } else if (/^border(-top|-bottom|-left|-right)?(-color)?$/.test(prop) || prop === 'border-color') {
    // border 简写：`1px solid #e2e8f0`
    const m = value.match(/^(.*?)(#[0-9a-fA-F]{3,8})$/)
    if (m) {
      for (const [re, to] of BORDER_MAP) if (re.test(m[2])) return `${m[1]}${to}`
      for (const [re, to] of ACCENT_BORDER) if (re.test(m[2])) return `${m[1]}${to}`
    }
    for (const [re, to] of BORDER_MAP) if (re.test(value)) return to
  } else if (prop === 'outline' || prop === 'outline-color') {
    const m = value.match(/^(.*?)(#[0-9a-fA-F]{3,8})$/)
    if (m) {
      for (const [re, to] of ACCENT_BORDER) if (re.test(m[2])) return `${m[1]}${to}`
    }
  }
  return null
}

/** 清理选择器：去掉注释、scoped 属性、多余空白 */
function cleanSelector(sel) {
  let s = sel.replace(/\/\*[\s\S]*?\*\//g, ' ')
  s = s.replace(/\[data-v-[a-f0-9]+\]/g, '')
  s = s.replace(/\s+/g, ' ').trim()
  return s
}

/** 防御：清理后仍带注释碎片/反引号的选择器一律丢弃 */
function isSaneSelector(s) {
  return !/[/*`]/.test(s)
}

const PREFIX = "html.dark[data-theme='dark'] "

// declSet(string) → Set<selector>
const grouped = new Map()
let ruleCount = 0

const chunks = css.split('}')
for (const chunk of chunks) {
  const brace = chunk.indexOf('{')
  if (brace <= 0) continue
  let sel = chunk.slice(0, brace)
  const body = chunk.slice(brace + 1)

  sel = cleanSelector(sel)
  if (!sel || !isSaneSelector(sel)) continue
  if (sel.startsWith('@') || sel === 'from' || sel === 'to' || /^\d+%/.test(sel)) continue
  if (/^[*>]|^:root|^html|\.dark(\s|$|,|\.|:)/.test(sel)) continue
  if (!sel.startsWith('.') && !sel.startsWith('#') && !sel.startsWith('[')) {
    // 元素选择器（pre/button/li 等）只保留带类上下文的，纯元素选择器跳过避免误伤宿主
    if (!/\s/.test(sel) && !sel.includes('>')) continue
  }

  const out = []
  for (const declRaw of body.split(';')) {
    const idx = declRaw.indexOf(':')
    if (idx <= 0) continue
    const prop = declRaw.slice(0, idx).trim().toLowerCase()
    const value = declRaw.slice(idx + 1).trim()
    if (!prop || !value || prop.startsWith('--') || prop.startsWith('*')) continue
    const mapped = mapValue(prop, value)
    if (mapped) out.push(`${prop}: ${mapped}`)
  }
  if (out.length === 0) continue

  ruleCount++
  const key = out.join('; ')
  if (!grouped.has(key)) grouped.set(key, new Set())
  const set = grouped.get(key)
  for (const part of sel.split(',')) {
    const p = part.trim()
    if (p && isSaneSelector(p)) set.add(PREFIX + p)
  }
}

let text = `/* 由 scripts/gen-force-dark.mjs 自动生成 —— 请勿手改。
 * 强制暗色层：把组件库 scoped 样式里写死的浅色 hex 重映射为 HUD 暗色变量。
 * 共处理 ${ruleCount} 条规则。生成时间：${new Date().toISOString()} */\n\n`

for (const [decls, selectors] of grouped) {
  if (selectors.size === 0) continue
  text += `${[...selectors].join(',\n')} {\n  ${decls.split('; ').join(';\n  ')};\n}\n\n`
}

writeFileSync(outFile, text)
console.log(`✓ ${outFile}\n  rules matched: ${ruleCount}, grouped: ${grouped.size}, bytes: ${text.length}`)
