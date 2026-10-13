<script setup lang="ts">
/**
 * 工具 block. Shows a summary row (count + attached chips) and a `+ 添加` action.
 * When adding, opens a Dify-style popover with a search input, category tabs,
 * and a scrollable list — this mirrors Snipaste_2026-07-09_09-02-38.
 *
 * Both the attached-tools array and the full tool inventory come from the host.
 */
import { computed, ref } from 'vue';

import type { StudioTool } from '../types';

interface Props {
  attached: string[];
  available: StudioTool[];
  maxAttached?: number;
}

const props = withDefaults(defineProps<Props>(), {
  maxAttached: 20,
});

const emit = defineEmits<{
  (e: 'update:attached', v: string[]): void;
}>();

const showPicker = ref(false);
const search = ref('');
const activeCategory = ref('全部');

const CATEGORY_TABS = ['全部', 'Connector', '插件', '自定义', '工作流', 'MCP'];

const displayName = (tool: StudioTool) => tool.label || tool.name;

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase();
  return props.available.filter((t) => {
    if (
      activeCategory.value !== '全部' &&
      (t.category ?? '插件') !== activeCategory.value
    ) {
      return false;
    }
    if (!q) return true;
    return (
      displayName(t).toLowerCase().includes(q) || t.name.toLowerCase().includes(q) ||
      (t.description ?? '').toLowerCase().includes(q)
    );
  });
});

const featured = computed(() =>
  props.available
    .slice()
    .sort((a, b) => (b.installs ?? 0) - (a.installs ?? 0))
    .slice(0, 5),
);

function isAttached(name: string) {
  return props.attached.includes(name);
}

function toggle(name: string) {
  const next = isAttached(name)
    ? props.attached.filter((n) => n !== name)
    : [...props.attached, name];
  emit('update:attached', next);
}

function remove(name: string) {
  emit(
    'update:attached',
    props.attached.filter((n) => n !== name),
  );
}
</script>

<template>
  <div class="agent-tool-card">
    <div class="tool-head">
      <div class="tool-title">
        工具
        <span class="tool-help" title="工具能让智能体调用 API、检索、代码运行">
          ⓘ
        </span>
      </div>
      <div class="tool-head-right">
        <span class="tool-count">{{ attached.length }}/{{ maxAttached }} 已挂载</span>
        <button type="button" class="tool-add" @click="showPicker = !showPicker">
          + 添加
        </button>
      </div>
    </div>

    <div v-if="attached.length === 0" class="tool-empty">
      还没挂载工具。点右上角"+ 添加"从工具箱选一个。
    </div>
    <div v-else class="tool-chip-row">
      <span
        v-for="name in attached"
        :key="name"
        class="tool-chip"
      >
        <span>🔧 {{ name }}</span>
        <button
          type="button"
          class="tool-chip-x"
          @click="remove(name)"
          title="移除"
        >
          ×
        </button>
      </span>
    </div>

    <!-- Popover picker -->
    <div v-if="showPicker" class="tool-pop">
      <div class="tool-pop-head">
        <input
          v-model="search"
          class="tool-search"
          placeholder="搜索工具..."
        />
        <button
          type="button"
          class="tool-pop-close"
          @click="showPicker = false"
        >
          ×
        </button>
      </div>
      <div class="tool-pop-tabs">
        <button
          v-for="c in CATEGORY_TABS"
          :key="c"
          type="button"
          class="tool-pop-tab"
          :class="{ 'tool-pop-tab-active': activeCategory === c }"
          @click="activeCategory = c"
        >
          {{ c }}
        </button>
      </div>
      <div v-if="featured.length > 0 && !search" class="tool-pop-section">
        <div class="tool-pop-section-title">精选推荐</div>
        <div class="tool-pop-list">
          <button
            v-for="t in featured"
            :key="`f-${t.name}`"
            type="button"
            class="tool-pop-item"
            :class="{ 'tool-pop-item-active': isAttached(t.name) }"
            @click="toggle(t.name)"
          >
            <span class="tool-pop-item-icon">{{ t.icon ?? '🔧' }}</span>
            <span class="tool-pop-item-name">{{ displayName(t) }}</span>
            <span v-if="t.installs" class="tool-pop-item-installs">
              {{ (t.installs / 1000).toFixed(0) }}k 次安装
            </span>
          </button>
        </div>
      </div>
      <div class="tool-pop-section">
        <div class="tool-pop-section-title">全部工具</div>
        <div class="tool-pop-list">
          <button
            v-for="t in filtered"
            :key="t.name"
            type="button"
            class="tool-pop-item"
            :class="{ 'tool-pop-item-active': isAttached(t.name) }"
            @click="toggle(t.name)"
          >
            <span class="tool-pop-item-icon">{{ t.icon ?? '🔧' }}</span>
            <span class="tool-pop-item-name">{{ displayName(t) }}</span>
            <span class="tool-pop-item-desc">{{ t.description }}</span>
            <span v-if="t.riskLevel" class="tool-risk">{{ t.riskLevel }}</span>
            <span v-if="t.configured === false" class="tool-unconfigured">未配置</span>
          </button>
          <div v-if="filtered.length === 0" class="tool-pop-empty">
            没有匹配的工具
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.agent-tool-card {
  position: relative;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
}
.tool-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.tool-title {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.tool-help {
  margin-left: 4px;
  color: #94a3b8;
  font-size: 11px;
  cursor: help;
}
.tool-head-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.tool-count {
  font-size: 11px;
  color: #94a3b8;
}
.tool-add {
  padding: 2px 8px;
  border: none;
  border-radius: 6px;
  background: transparent;
  font-size: 12px;
  color: #4338ca;
  cursor: pointer;
}
.tool-add:hover {
  background: #eef2ff;
}
.tool-empty {
  margin-top: 8px;
  font-size: 12px;
  color: #94a3b8;
}
.tool-chip-row {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.tool-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 6px 3px 10px;
  background: #eef2ff;
  color: #4338ca;
  border-radius: 999px;
  font-size: 12px;
}
.tool-chip-x {
  padding: 0 4px;
  border: none;
  background: transparent;
  color: inherit;
  font-size: 14px;
  cursor: pointer;
  opacity: 0.7;
}
.tool-chip-x:hover {
  opacity: 1;
}

/* Popover */
.tool-pop {
  position: absolute;
  top: 44px;
  right: 12px;
  width: 380px;
  max-height: 440px;
  overflow: hidden;
  padding: 10px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  box-shadow: 0 20px 40px rgba(15, 23, 42, 0.15);
  z-index: 10;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.tool-pop-head {
  display: flex;
  gap: 6px;
  align-items: center;
}
.tool-search {
  flex: 1;
  padding: 6px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 12px;
  outline: none;
}
.tool-search:focus {
  border-color: #6366f1;
}
.tool-pop-close {
  width: 26px;
  height: 26px;
  border: none;
  background: transparent;
  border-radius: 6px;
  color: #94a3b8;
  font-size: 16px;
  cursor: pointer;
}
.tool-pop-close:hover {
  background: #f1f5f9;
}
.tool-pop-tabs {
  display: flex;
  gap: 2px;
  border-bottom: 1px solid #f1f5f9;
}
.tool-pop-tab {
  padding: 4px 8px;
  border: none;
  background: transparent;
  border-bottom: 2px solid transparent;
  font-size: 12px;
  color: #64748b;
  cursor: pointer;
}
.tool-pop-tab-active {
  color: #4338ca;
  border-bottom-color: #4338ca;
}
.tool-pop-section-title {
  padding: 6px 4px;
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
}
.tool-pop-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 240px;
  overflow-y: auto;
}
.tool-pop-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border: none;
  background: transparent;
  border-radius: 6px;
  font-size: 12px;
  text-align: left;
  cursor: pointer;
}
.tool-pop-item:hover {
  background: #f1f5f9;
}
.tool-pop-item-active {
  background: #eef2ff;
}
.tool-pop-item-active::after {
  content: '✓';
  margin-left: auto;
  color: #4338ca;
}
.tool-pop-item-icon {
  flex-shrink: 0;
  width: 20px;
  text-align: center;
}
.tool-pop-item-name {
  color: #0f172a;
  font-weight: 500;
}
.tool-pop-item-desc {
  margin-left: auto;
  flex: 1;
  color: #94a3b8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 180px;
}
.tool-pop-item-installs {
  margin-left: auto;
  color: #94a3b8;
  font-size: 11px;
}
.tool-pop-empty {
  padding: 12px;
  text-align: center;
  color: #94a3b8;
  font-size: 12px;
}
</style>
