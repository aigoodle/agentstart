<script setup lang="ts">
// @ts-nocheck — legacy flow-designer code, pre-dates strict typing; cleanup tracked separately.
import { h } from 'vue';

import {
  CopyOutlined,
  DeleteOutlined,
  MoreOutlined,
  PlayCircleOutlined,
} from '@ant-design/icons-vue';

/**
 * 节点悬浮工具栏 —— 挂在每个节点右上角，仅在 hover 时可见
 *
 * 视觉：一个内嵌在节点顶部内 8px 的圆角小工具条，悬空浮起，
 * 高度 22px；按钮 hover 显更暗。避免和相邻节点顶部重叠。
 *
 * 按 nodeType 分不同的动作集：
 *   - start 只有 复制
 *   - end   只有 删除
 *   - 其他  运行到此 / 复制 / 删除 / 更多
 */

interface Props {
  nodeType?: string;
  runnable?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  nodeType: '',
  runnable: true,
});

const emit = defineEmits<{
  (e: 'duplicate'): void;
  (e: 'delete'): void;
  (e: 'run'): void;
  (e: 'more'): void;
}>();

/** 不同类型的动作集 —— start 不能删；end 不能有下游、不能"运行到此" */
const actions = {
  start: ['duplicate'] as const,
  end: ['delete'] as const,
  answer: ['delete'] as const,
  default: props.runnable
    ? (['run', 'duplicate', 'delete', 'more'] as const)
    : (['duplicate', 'delete', 'more'] as const),
};

function getActions() {
  return actions[props.nodeType as keyof typeof actions] || actions.default;
}
</script>

<template>
  <!--
    容器 pointer-events: none → 空白区域的 click / mousedown 会穿透落到底层
    的 vue-flow__node，避免"点节点顶部有时不打开面板"的死区问题。
    真正的按钮再单独打开 pointer-events: auto，并各自 @click.stop /
    @mousedown.stop，避免按下 run / 复制 / 删除时被 VueFlow 当成节点选中。
  -->
  <div class="wf-hover-toolbar">
    <a-tooltip
      v-if="getActions().includes('run')"
      title="运行到此节点"
      placement="top"
    >
      <button
        class="wf-hover-btn"
        @mousedown.stop
        @click.stop="emit('run')"
      >
        <PlayCircleOutlined />
      </button>
    </a-tooltip>

    <a-tooltip
      v-if="getActions().includes('duplicate')"
      title="复制 (Ctrl+D)"
      placement="top"
    >
      <button
        class="wf-hover-btn"
        @mousedown.stop
        @click.stop="emit('duplicate')"
      >
        <CopyOutlined />
      </button>
    </a-tooltip>

    <a-tooltip
      v-if="getActions().includes('delete')"
      title="删除 (Del)"
      placement="top"
    >
      <button
        class="wf-hover-btn is-danger"
        @mousedown.stop
        @click.stop="emit('delete')"
      >
        <DeleteOutlined />
      </button>
    </a-tooltip>

    <a-tooltip
      v-if="getActions().includes('more')"
      title="更多"
      placement="top"
    >
      <button
        class="wf-hover-btn"
        @mousedown.stop
        @click.stop="emit('more')"
      >
        <MoreOutlined />
      </button>
    </a-tooltip>
  </div>
</template>

<style scoped>
.wf-hover-toolbar {
  position: absolute;
  top: -14px;
  right: 6px;
  display: flex;
  gap: 1px;
  padding: 2px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  box-shadow: 0 4px 10px rgba(15, 23, 42, 0.12);
  z-index: 30;
  /* 容器本身不吃事件，只有 .wf-hover-btn 单独打开 pointer-events；
   * 这样悬浮工具栏的空白部分不会挡住底下 VueFlow 的节点点击。 */
  pointer-events: none;
  animation: wf-hover-toolbar-fade 0.12s ease-out;
}

@keyframes wf-hover-toolbar-fade {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.wf-hover-btn {
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
  font-size: 12px;
  pointer-events: auto;
}

.wf-hover-btn:hover {
  color: #1f2937;
  background: #f3f4f6;
}

.wf-hover-btn.is-danger:hover {
  color: #dc2626;
  background: #fef2f2;
}
</style>
