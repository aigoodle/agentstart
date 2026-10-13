<script setup lang="ts">
/**
 * 全局背景：极深暗蓝底 + 浅蓝网格 + 零星漂浮粒子光点。
 * 粒子用 canvas 绘制（数量克制，保持 B 端可读性），网格用 CSS 渐变。
 */
import { onBeforeUnmount, onMounted, ref } from 'vue'

const canvasRef = ref<HTMLCanvasElement | null>(null)
let raf = 0

interface P {
  x: number
  y: number
  r: number
  vx: number
  vy: number
  a: number
  tw: number
}

onMounted(() => {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  let particles: P[] = []

  function resize() {
    canvas!.width = window.innerWidth
    canvas!.height = window.innerHeight
    const count = Math.min(70, Math.floor((canvas!.width * canvas!.height) / 26000))
    particles = Array.from({ length: count }, () => ({
      x: Math.random() * canvas!.width,
      y: Math.random() * canvas!.height,
      r: Math.random() * 1.4 + 0.4,
      vx: (Math.random() - 0.5) * 0.12,
      vy: (Math.random() - 0.5) * 0.12,
      a: Math.random() * 0.5 + 0.15,
      tw: Math.random() * Math.PI * 2,
    }))
  }

  function frame() {
    ctx!.clearRect(0, 0, canvas!.width, canvas!.height)
    for (const p of particles) {
      p.x += p.vx
      p.y += p.vy
      p.tw += 0.015
      if (p.x < -4) p.x = canvas!.width + 4
      if (p.x > canvas!.width + 4) p.x = -4
      if (p.y < -4) p.y = canvas!.height + 4
      if (p.y > canvas!.height + 4) p.y = -4
      const alpha = p.a * (0.6 + 0.4 * Math.sin(p.tw))
      ctx!.beginPath()
      ctx!.arc(p.x, p.y, p.r, 0, Math.PI * 2)
      ctx!.fillStyle = `rgba(120, 210, 255, ${alpha})`
      ctx!.shadowColor = 'rgba(0, 204, 255, 0.8)'
      ctx!.shadowBlur = 5
      ctx!.fill()
      ctx!.shadowBlur = 0
    }
    raf = requestAnimationFrame(frame)
  }

  resize()
  frame()
  window.addEventListener('resize', resize)
  onBeforeUnmount(() => {
    window.removeEventListener('resize', resize)
    cancelAnimationFrame(raf)
  })
})
</script>

<template>
  <div class="hud-bg" aria-hidden="true">
    <div class="hud-bg-grid"></div>
    <canvas ref="canvasRef" class="hud-bg-particles"></canvas>
    <div class="hud-bg-vignette"></div>
  </div>
</template>

<style scoped>
.hud-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background:
    radial-gradient(1200px 700px at 70% -10%, rgba(0, 90, 170, 0.16), transparent 60%),
    radial-gradient(900px 600px at -10% 110%, rgba(0, 140, 200, 0.1), transparent 55%),
    #040c1a;
}

.hud-bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(0, 160, 230, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 160, 230, 0.05) 1px, transparent 1px);
  background-size: 42px 42px;
  mask-image: radial-gradient(ellipse at center, black 30%, transparent 92%);
}

.hud-bg-particles {
  position: absolute;
  inset: 0;
}

.hud-bg-vignette {
  position: absolute;
  inset: 0;
  box-shadow: inset 0 0 180px 40px rgba(1, 5, 12, 0.75);
}

/* ------------------------------------------------------------------ */
/* 明亮模式：把深蓝背景换成淡蓝/米白，隐藏粒子与网格                    */
/* ------------------------------------------------------------------ */
html[data-theme='light'] .hud-bg {
  background:
    radial-gradient(1200px 700px at 70% -10%, rgba(180, 220, 255, 0.35), transparent 60%),
    radial-gradient(900px 600px at -10% 110%, rgba(200, 230, 255, 0.25), transparent 55%),
    #f4f8fc;
}

html[data-theme='light'] .hud-bg-grid {
  background-image:
    linear-gradient(rgba(0, 120, 180, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 120, 180, 0.06) 1px, transparent 1px);
}

html[data-theme='light'] .hud-bg-particles {
  opacity: 0;
}

html[data-theme='light'] .hud-bg-vignette {
  box-shadow: inset 0 0 120px 30px rgba(200, 220, 240, 0.35);
}

/* 普通模式使用纯色应用底，不显示网格、粒子和暗角。 */
html[data-visual='normal'] .hud-bg {
  background: #141414;
}

html[data-theme='light'][data-visual='normal'] .hud-bg {
  background: #f5f5f5;
}

html[data-visual='normal'] .hud-bg-grid,
html[data-visual='normal'] .hud-bg-particles,
html[data-visual='normal'] .hud-bg-vignette {
  display: none;
}
</style>
