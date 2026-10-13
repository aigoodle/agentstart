<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { SearchOutlined } from '@ant-design/icons-vue'

import HudBackground from './components/HudBackground.vue'
import PreferencesDrawer from './components/PreferencesDrawer.vue'
import ThemeToggle from './components/ThemeToggle.vue'
import { menuGroups } from './router'

const route = useRoute()
const router = useRouter()

// ---------------------------------------------------------------------------
// 顶栏：面包屑 / 全局搜索 / 运行模式 / 时钟
// ---------------------------------------------------------------------------
const breadcrumb = computed(() => {
  const { group = '', title = '' } = route.meta as Record<string, string>
  return group ? [group, title] : [title]
})

const search = ref('')
const searchFocused = ref(false)
const searchResults = computed(() => {
  const kw = search.value.trim().toLowerCase()
  if (!kw) return []
  return menuGroups
    .flatMap((g) =>
      g.items
        .filter((i) => !i.hidden)
        .map((i) => ({ ...i, group: g.group })),
    )
    .filter(
      (i) =>
        i.title.toLowerCase().includes(kw) || i.path.toLowerCase().includes(kw),
    )
    .slice(0, 8)
})

function goto(path: string) {
  search.value = ''
  searchFocused.value = false
  router.push(path)
}

const isMock = __USE_MOCK__

const now = ref(new Date())
const timer = window.setInterval(() => (now.value = new Date()), 1000)
onBeforeUnmount(() => window.clearInterval(timer))
const clock = computed(() => {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${p(now.value.getHours())}:${p(now.value.getMinutes())}:${p(now.value.getSeconds())}`
})
const date = computed(() => {
  const d = now.value
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
})

// ---------------------------------------------------------------------------
// 侧边栏：当前激活项
// ---------------------------------------------------------------------------
function isActive(path: string, activePath?: string): boolean {
  const target = activePath ?? path
  if (target.includes(':')) {
    // /apps/:id/chat 之类的模式：按前缀匹配
    const prefix = target.split('/:')[0]
    return route.path.startsWith(prefix + '/') && route.path !== prefix
  }
  return route.path === target
}
</script>

<template>
  <div class="hud-root">
    <HudBackground />

    <aside class="hud-sidebar">
      <div class="hud-logo">
        <span class="hud-logo-mark">AS</span>
        <span class="hud-logo-text">
          <b>AGENT-START</b>
          <i>组件演示中枢 v0.2</i>
        </span>
      </div>

      <nav class="hud-nav">
        <div v-for="g in menuGroups" :key="g.group" class="hud-nav-group">
          <div class="hud-nav-title">{{ g.group }}</div>
          <template v-for="item in g.items" :key="item.path">
            <a
              v-if="!item.hidden"
              class="hud-nav-item"
              :class="{ active: isActive(item.path, item.activePath) }"
              :title="item.title"
              @click="router.push(item.path)"
            >
              <component :is="item.icon" class="hud-nav-icon" />
              <span>{{ item.title }}</span>
            </a>
          </template>
        </div>
      </nav>

      <div class="hud-sidebar-foot">
        <span class="hud-dot" :class="isMock ? 'mock' : 'live'"></span>
        {{ isMock ? 'MOCK 数据模式' : 'LIVE 后端模式' }}
      </div>
    </aside>

    <div class="hud-main">
      <header class="hud-topbar">
        <div class="hud-crumb">
          <span class="hud-crumb-sys">SYSTEM</span>
          <template v-for="(c, i) in breadcrumb" :key="c">
            <span class="hud-crumb-sep">/</span>
            <span :class="i === breadcrumb.length - 1 ? 'hud-crumb-cur' : ''">
              {{ c }}
            </span>
          </template>
        </div>

        <div class="hud-search" :class="{ focused: searchFocused }">
          <SearchOutlined />
          <input
            v-model="search"
            placeholder="搜索功能模块…"
            @focus="searchFocused = true"
            @blur="searchFocused = false"
            @keydown.escape="search = ''"
          />
          <div v-if="searchFocused && searchResults.length" class="hud-search-pop">
            <a
              v-for="r in searchResults"
              :key="r.path"
              class="hud-search-item"
              @mousedown.prevent="goto(r.path)"
            >
              <component :is="r.icon" />
              <span>{{ r.title }}</span>
              <i>{{ r.group }}</i>
            </a>
          </div>
        </div>

        <div class="hud-clock">
          <b>{{ clock }}</b>
          <i>{{ date }}</i>
        </div>

        <ThemeToggle class="hud-topbar-toggle" />
        <PreferencesDrawer class="hud-topbar-toggle" />
      </header>

      <main class="hud-content">
        <RouterView v-slot="{ Component }">
          <Transition name="hud-fade" mode="out-in">
            <component :is="Component" />
          </Transition>
        </RouterView>
      </main>
    </div>
  </div>
</template>

<style scoped>
/* ------------------------------------------------------------------ */
/* 骨架布局                                                            */
/* ------------------------------------------------------------------ */
.hud-root {
  display: flex;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.hud-sidebar {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  width: 224px;
  border-right: 1px solid rgba(0, 204, 255, 0.18);
  background: linear-gradient(180deg, rgba(4, 16, 34, 0.92), rgba(2, 10, 24, 0.96));
  backdrop-filter: blur(12px);
  box-shadow: inset -12px 0 24px -18px rgba(0, 204, 255, 0.25);
}

.hud-main {
  position: relative;
  z-index: 1;
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.hud-content {
  position: relative;
  flex: 1;
  min-width: 0;
  min-height: 0;
  padding: 14px 16px 16px;
  overflow: auto;
}

/* ------------------------------------------------------------------ */
/* Logo                                                                */
/* ------------------------------------------------------------------ */
.hud-logo {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 16px 14px 14px;
  border-bottom: 1px solid rgba(0, 204, 255, 0.14);
}

.hud-logo-mark {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  font-size: 15px;
  font-weight: 700;
  color: #04101f;
  letter-spacing: 0.5px;
  background: linear-gradient(135deg, #00e0ff, #0084ff);
  clip-path: polygon(12% 0, 100% 0, 100% 88%, 88% 100%, 0 100%, 0 12%);
  box-shadow: 0 0 14px rgba(0, 224, 255, 0.55);
}

.hud-logo-text {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.hud-logo-text b {
  font-size: 14px;
  color: #00e0ff;
  letter-spacing: 2px;
  text-shadow: 0 0 10px rgba(0, 224, 255, 0.6);
}

.hud-logo-text i {
  font-size: 11px;
  font-style: normal;
  color: #78a8d0;
}

/* ------------------------------------------------------------------ */
/* 侧边导航                                                            */
/* ------------------------------------------------------------------ */
.hud-nav {
  flex: 1;
  padding: 8px 10px;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: rgba(0, 204, 255, 0.25) transparent;
}

.hud-nav-group + .hud-nav-group {
  margin-top: 14px;
}

.hud-nav-title {
  padding: 0 8px 6px;
  font-size: 11px;
  color: #4f7ea8;
  letter-spacing: 3px;
}

.hud-nav-item {
  display: flex;
  gap: 9px;
  align-items: center;
  padding: 8px 10px;
  margin-bottom: 3px;
  font-size: 13px;
  color: #9cc8e8;
  cursor: pointer;
  user-select: none;
  border: 1px solid transparent;
  border-radius: 7px;
  transition: all 0.18s ease;
}

.hud-nav-item:hover {
  color: #d4f1ff;
  background: rgba(0, 140, 220, 0.1);
  border-color: rgba(0, 204, 255, 0.22);
}

.hud-nav-item.active {
  color: #eafcff;
  background: linear-gradient(90deg, rgba(0, 170, 255, 0.22), rgba(0, 90, 160, 0.1));
  border-color: rgba(0, 224, 255, 0.65);
  box-shadow:
    0 0 10px rgba(0, 204, 255, 0.35),
    inset 0 0 12px rgba(0, 160, 255, 0.12);
  text-shadow: 0 0 8px rgba(0, 224, 255, 0.8);
}

.hud-nav-icon {
  font-size: 15px;
  color: #58a6d6;
}

.hud-nav-item.active .hud-nav-icon {
  color: #00e0ff;
  filter: drop-shadow(0 0 5px rgba(0, 224, 255, 0.8));
}

.hud-sidebar-foot {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 12px 16px;
  font-size: 11px;
  color: #78a8d0;
  letter-spacing: 1px;
  border-top: 1px solid rgba(0, 204, 255, 0.14);
}

.hud-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.hud-dot.mock {
  background: #ffc53d;
  box-shadow: 0 0 8px rgba(255, 197, 61, 0.9);
}

.hud-dot.live {
  background: #36d399;
  box-shadow: 0 0 8px rgba(54, 211, 153, 0.9);
}

/* ------------------------------------------------------------------ */
/* 顶栏                                                                */
/* ------------------------------------------------------------------ */
.hud-topbar {
  display: flex;
  gap: 16px;
  align-items: center;
  flex-shrink: 0;
  height: 54px;
  padding: 0 16px;
  border-bottom: 1px solid rgba(0, 204, 255, 0.18);
  background: rgba(4, 14, 30, 0.75);
  backdrop-filter: blur(12px);
}

.hud-crumb {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
  font-size: 12.5px;
  color: #78a8d0;
}

.hud-crumb-sys {
  font-size: 10px;
  color: #3f6f96;
  letter-spacing: 2px;
}

.hud-crumb-sep {
  color: #2f5d80;
}

.hud-crumb-cur {
  color: #cdeeff;
  text-shadow: 0 0 8px rgba(0, 204, 255, 0.4);
}

.hud-search {
  position: relative;
  display: flex;
  gap: 8px;
  align-items: center;
  width: 260px;
  height: 32px;
  padding: 0 12px;
  margin-left: auto;
  color: #58a6d6;
  border: 1px solid rgba(0, 204, 255, 0.28);
  border-radius: 7px;
  background: rgba(0, 30, 60, 0.45);
  transition: all 0.2s ease;
}

.hud-search.focused,
.hud-search:hover {
  border-color: rgba(0, 224, 255, 0.75);
  box-shadow: 0 0 10px rgba(0, 204, 255, 0.3);
}

.hud-search input {
  flex: 1;
  min-width: 0;
  font-size: 12.5px;
  color: #d4f1ff;
  background: transparent;
  border: none;
  outline: none;
}

.hud-search input::placeholder {
  color: #4f7ea8;
}

.hud-search-pop {
  position: absolute;
  top: 38px;
  right: 0;
  left: 0;
  z-index: 50;
  padding: 6px;
  overflow: hidden;
  border: 1px solid rgba(0, 224, 255, 0.5);
  border-radius: 8px;
  background: rgba(3, 18, 38, 0.97);
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.6), 0 0 14px rgba(0, 204, 255, 0.25);
}

.hud-search-item {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 7px 9px;
  font-size: 12.5px;
  color: #b8e8ff;
  cursor: pointer;
  border-radius: 6px;
}

.hud-search-item:hover {
  background: rgba(0, 160, 255, 0.16);
}

.hud-search-item i {
  margin-left: auto;
  font-size: 10.5px;
  font-style: normal;
  color: #4f7ea8;
}

.hud-clock {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  line-height: 1.25;
}

.hud-clock b {
  font-family: 'JetBrains Mono', 'SF Mono', Consolas, monospace;
  font-size: 16px;
  font-weight: 600;
  color: #00e0ff;
  letter-spacing: 1.5px;
  text-shadow: 0 0 10px rgba(0, 224, 255, 0.55);
}

.hud-clock i {
  font-size: 10.5px;
  font-style: normal;
  color: #4f7ea8;
}

.hud-topbar-toggle {
  margin-left: 4px;
}

/* ------------------------------------------------------------------ */
/* 紧凑窗口：侧栏收为图标轨，避免主内容被固定侧栏挤出横向滚动条             */
/* ------------------------------------------------------------------ */
@media (max-width: 900px) {
  .hud-sidebar {
    width: 72px;
  }

  .hud-logo {
    justify-content: center;
    padding-inline: 8px;
  }

  .hud-logo-text,
  .hud-nav-title,
  .hud-nav-item span {
    display: none;
  }

  .hud-nav {
    padding-inline: 8px;
  }

  .hud-nav-item {
    justify-content: center;
    padding-inline: 8px;
  }

  .hud-sidebar-foot {
    justify-content: center;
    padding-inline: 8px;
    font-size: 0;
  }

  .hud-search {
    width: min(220px, 32vw);
  }
}

@media (max-width: 640px) {
  .hud-sidebar {
    width: 58px;
  }

  .hud-logo-mark {
    width: 34px;
    height: 34px;
  }

  .hud-search,
  .hud-clock {
    display: none;
  }
}

/* ------------------------------------------------------------------ */
/* 路由切换动画                                                        */
/* ------------------------------------------------------------------ */
.hud-fade-enter-active,
.hud-fade-leave-active {
  transition: opacity 0.16s ease, transform 0.16s ease;
}

.hud-fade-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.hud-fade-leave-to {
  opacity: 0;
}
</style>
