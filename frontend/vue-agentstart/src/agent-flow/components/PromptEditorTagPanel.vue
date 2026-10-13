<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';

import { DownOutlined, RightOutlined } from '@ant-design/icons-vue';

import { useWorkflowStore } from '@/stores/workflow';
import workflow_utils from '@/workflow/utils/workflow_utils';

/**
 * 快速插入变量面板
 *
 * 作用：在 PromptEditor 里输入 `/` 或点击 "+" 按钮触发，弹出上游节点的输出变量
 * 列表；选中一项后 emit('select', [nodeId, ...path], tagMeta, node)，宿主
 * 组件负责实际把变量插入到文本里。
 *
 * 定位策略：给定 targetElement 时，紧贴其下方；空间不足则翻到上方。
 * viewport 边界会自动收敛。
 */

const props = defineProps({
  show: { type: Boolean, default: false },
  nodeId: { type: String, default: '' },
  /** 传入 targetElement 后自动跟随其位置 */
  targetElement: { type: Object as any, default: () => null },
  /** slash 模式下父组件维护高亮索引；插入 tag 也会用到 */
  isSlashMode: { type: Boolean, default: false },
  selectedIndex: { type: Number, default: 0 },
  /** slash 后已经输入的过滤字符 */
  search: { type: String, default: '' },
  /** 传入固定位置时优先使用 targetElement，两个都没给才用 position */
  position: { type: Object, default: () => ({ top: '0px', left: '0px' }) },
});

const emit = defineEmits(['close', 'select']);

const panelRef = ref<HTMLElement | null>(null);
// 首次测量完成前放在视口外，既不会闪到左上角，也不需要额外的
// visibility 响应式状态（后者容易与 Teleport 更新形成递归渲染）。
const panelPos = ref({ top: '-9999px', left: '-9999px' });
const expandedKeys = ref<Set<string>>(new Set());
const workflowStore = useWorkflowStore();
const nodeList = computed<any[]>(() => props.nodeId
  ? workflowStore.getParentNodeList(props.nodeId)
  : []);

/** ---------- 定位 ---------- */
function updatePosition() {
  // SSR guard: also invoked from an immediate watch during setup.
  if (typeof window === 'undefined') return;
  if (!props.targetElement) {
    panelPos.value = {
      top: props.position?.top ?? '0px',
      left: props.position?.left ?? '0px',
    };
    return;
  }
  const rect = props.targetElement.getBoundingClientRect();
  const panelH = panelRef.value?.offsetHeight || 320;
  const panelW = panelRef.value?.offsetWidth || 300;
  const vh = window.innerHeight;
  const vw = window.innerWidth;

  let top = rect.bottom + 6;
  let left = rect.left;
  if (top + panelH > vh) top = Math.max(10, rect.top - panelH - 6);
  if (left + panelW > vw) left = Math.max(10, vw - panelW - 10);

  panelPos.value = {
    top: `${Math.max(10, top)}px`,
    left: `${Math.max(10, left)}px`,
  };
}

/** ---------- 搜索/展开 ---------- */
function getNodeOutputList(node: any) {
  return workflow_utils.getOutputList(node.data) ?? [];
}

function hasChildren(tag: any) {
  return tag?.children && tag.children.length > 0;
}

function toggleExpand(key: string) {
  const next = new Set(expandedKeys.value);
  if (next.has(key)) next.delete(key);
  else next.add(key);
  expandedKeys.value = next;
}

/** 对单个节点的输出根据 search 过滤 */
function filterOutputs(list: any[]): any[] {
  const q = props.search?.trim().toLowerCase();
  if (!q) return list;
  const match = (t: any) =>
    (t.label && t.label.toLowerCase().includes(q)) ||
    (t.description && t.description.toLowerCase().includes(q)) ||
    (t.name && t.name.toLowerCase().includes(q));

  return list
    .map((t: any) => {
      if (match(t)) return t;
      if (hasChildren(t)) {
        const kids = filterOutputs(t.children);
        if (kids.length > 0) return { ...t, children: kids };
      }
      return null;
    })
    .filter(Boolean);
}

/** slash 模式下 flatten 用于键盘导航时命中 selectedIndex 位置 */
const flatFiltered = computed(() => {
  const arr: any[] = [];
  for (const node of nodeList.value) {
    const outputs = filterOutputs(getNodeOutputList(node));
    for (const t of outputs) {
      arr.push({ ...t, __nodeId: node.id });
      if (hasChildren(t) && expandedKeys.value.has(t.name)) {
        for (const c of t.children) {
          arr.push({ ...c, __nodeId: node.id, __parentName: t.name });
        }
      }
    }
  }
  return arr;
});

/** ---------- 事件 ---------- */
function selectTag(variableSelector: string[], tag: any, node: any) {
  emit('select', variableSelector, tag, node);
}

function close() {
  emit('close');
}

function onDocMouseDown(e: MouseEvent) {
  if (!panelRef.value) return;
  if (!panelRef.value.contains(e.target as Node)) close();
}

/** ESC 快捷键关闭面板；capture 阶段拦截，避免宿主编辑器把事件吃掉。 */
function onDocKeyDown(e: KeyboardEvent) {
  if (!props.show) return;
  if (e.key === 'Escape') {
    e.preventDefault();
    e.stopPropagation();
    close();
  }
}

/** ---------- 生命周期 ---------- */
onMounted(() => {
  document.addEventListener('mousedown', onDocMouseDown);
  document.addEventListener('keydown', onDocKeyDown, true);
  window.addEventListener('scroll', updatePosition, true);
  window.addEventListener('resize', updatePosition);
  if (props.show) updatePosition();
});

onUnmounted(() => {
  document.removeEventListener('mousedown', onDocMouseDown);
  document.removeEventListener('keydown', onDocKeyDown, true);
  window.removeEventListener('scroll', updatePosition, true);
  window.removeEventListener('resize', updatePosition);
});

watch(() => props.show, (show) => {
  if (show) updatePosition();
  else panelPos.value = { top: '-9999px', left: '-9999px' };
}, { flush: 'post' });

// slash 输入模式会持续替换光标锚点；面板已经打开时同步跟随。
watch(() => props.targetElement, () => {
  if (props.show) updatePosition();
}, { flush: 'post' });

watch(() => props.position, updatePosition);

/** 判断当前 selectedIndex 对应的项是否命中，给项加 active */
function isActive(nodeId: string, parentName: string | null, name: string) {
  if (!props.isSlashMode) return false;
  const item = flatFiltered.value[props.selectedIndex];
  if (!item) return false;
  return (
    item.__nodeId === nodeId &&
    (item.__parentName ?? null) === parentName &&
    item.name === name
  );
}
</script>

<template>
  <Teleport to="body">
  <div
    v-if="show"
    ref="panelRef"
    class="wf-tag-panel hud-panel"
    :style="panelPos"
    @mousedown.stop
  >
    <div class="wf-tag-panel-header">
      <div class="wf-tag-panel-title">快速插入变量</div>
      <button class="wf-tag-panel-close" @click="close">
        <svg viewBox="0 0 24 24" width="14" height="14">
          <path
            fill="currentColor"
            d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"
          />
        </svg>
      </button>
    </div>

    <div class="wf-tag-panel-body">
      <div v-if="nodeList.length === 0" class="wf-tag-panel-empty">
        当前节点没有上游变量。请先连接一个上游节点。
      </div>

      <div v-for="node in nodeList" :key="node.id" class="wf-tag-group">
        <div class="wf-tag-group-title">{{ node.data?.label || node.id }}</div>
        <div class="wf-tag-list">
          <template
            v-for="item in filterOutputs(getNodeOutputList(node))"
            :key="item.name"
          >
            <div
              class="wf-tag-item"
              :class="{ 'is-active': isActive(node.id, null, item.name) }"
              @mousedown.prevent
              @click.prevent="selectTag([node.id, item.name], item, node)"
            >
              <span
                v-if="hasChildren(item)"
                class="wf-tag-expand"
                @click.stop="toggleExpand(item.name)"
              >
                <RightOutlined v-if="!expandedKeys.has(item.name)" />
                <DownOutlined v-else />
              </span>
              <span v-else class="wf-tag-expand-placeholder"></span>

              <span class="wf-tag-icon" :data-type="item.type">
                {{ item.type?.[0]?.toUpperCase() || '·' }}
              </span>
              <span class="wf-tag-label">
                {{ item.label || item.name }}
              </span>
              <span v-if="item.type" class="wf-tag-type">{{ item.type }}</span>
            </div>

            <template v-if="hasChildren(item) && expandedKeys.has(item.name)">
              <div
                v-for="child in item.children"
                :key="`${item.name}-${child.name}`"
                class="wf-tag-item wf-tag-item-child"
                :class="{ 'is-active': isActive(node.id, item.name, child.name) }"
                @mousedown.prevent
                @click.prevent="
                  selectTag([node.id, item.name, child.name], child, node)
                "
              >
                <span class="wf-tag-expand-placeholder"></span>
                <span class="wf-tag-icon" :data-type="child.type">
                  {{ child.type?.[0]?.toUpperCase() || '·' }}
                </span>
                <span class="wf-tag-label">
                  {{ child.label || child.name }}
                </span>
                <span v-if="child.type" class="wf-tag-type">{{ child.type }}</span>
              </div>
            </template>
          </template>
        </div>
      </div>
    </div>
  </div>
  </Teleport>
</template>

<style scoped>
.wf-tag-panel {
  position: fixed;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  width: 300px;
  max-height: 360px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.16);
  overflow: hidden;
  font-size: 13px;
  color: #1f2937;
}

.wf-tag-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #f9fafb;
  border-bottom: 1px solid #f0f0f0;
}

.wf-tag-panel-title {
  font-size: 12px;
  font-weight: 600;
  color: #4b5563;
  letter-spacing: 0.3px;
  text-transform: uppercase;
}

.wf-tag-panel-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  color: #6b7280;
  background: transparent;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.wf-tag-panel-close:hover {
  background: #e5e7eb;
  color: #1f2937;
}

.wf-tag-panel-body {
  flex: 1;
  min-height: 0;
  padding: 4px 0;
  overflow-y: auto;
  background: #ffffff;
}

.wf-tag-panel-body::-webkit-scrollbar {
  width: 6px;
}
.wf-tag-panel-body::-webkit-scrollbar-thumb {
  background: #d1d5db;
  border-radius: 3px;
}

.wf-tag-panel-empty {
  padding: 20px 16px;
  text-align: center;
  font-size: 12px;
  color: #9ca3af;
}

.wf-tag-group {
  padding: 4px 0;
}

.wf-tag-group + .wf-tag-group {
  border-top: 1px solid #f3f4f6;
}

.wf-tag-group-title {
  padding: 6px 14px 4px;
  font-size: 11px;
  font-weight: 600;
  color: #6b7280;
  letter-spacing: 0.4px;
  text-transform: uppercase;
}

.wf-tag-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  cursor: pointer;
  transition: background 0.12s;
}

.wf-tag-item:hover,
.wf-tag-item.is-active {
  background: #eef2ff;
}

.wf-tag-item.is-active {
  color: #4338ca;
}

.wf-tag-item-child {
  padding-left: 34px;
}

.wf-tag-expand {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  color: #9ca3af;
}

.wf-tag-expand:hover {
  color: #4338ca;
}

.wf-tag-expand-placeholder {
  width: 14px;
  height: 14px;
}

.wf-tag-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  font-size: 10px;
  font-weight: 700;
  color: #ffffff;
  background: #6366f1;
  border-radius: 4px;
  text-transform: uppercase;
}

.wf-tag-icon[data-type='string'] {
  background: #3b82f6;
}
.wf-tag-icon[data-type='number'] {
  background: #f59e0b;
}
.wf-tag-icon[data-type='boolean'] {
  background: #10b981;
}
.wf-tag-icon[data-type='array'] {
  background: #a855f7;
}
.wf-tag-icon[data-type='object'] {
  background: #6b7280;
}
.wf-tag-icon[data-type='file'] {
  background: #ef4444;
}

.wf-tag-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wf-tag-type {
  flex: none;
  font-size: 11px;
  color: #9ca3af;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
}
</style>
