<script setup>
import { computed, defineEmits, defineProps, ref } from 'vue';

import { getBezierPath } from '@vue-flow/core';

const props = defineProps({
  sourceX: { type: Number, required: true },
  sourceY: { type: Number, required: true },
  targetX: { type: Number, required: true },
  targetY: { type: Number, required: true },
  sourcePosition: { type: String, default: 'right' },
  targetPosition: { type: String, default: 'left' },
  style: { type: Object, default: () => ({}) },
  markerEnd: { type: String, default: '' },
  data: { type: Object, default: () => ({}) },
  id: { type: String, required: true },
  source: { type: String, required: true },
  target: { type: String, required: true },
  selected: { type: Boolean, default: false },
});

const emit = defineEmits(['edge-click']);
const hovered = ref(false);

const path = computed(() => {
  const result = getBezierPath({
    sourceX: props.sourceX,
    sourceY: props.sourceY,
    sourcePosition: props.sourcePosition,
    targetX: props.targetX,
    targetY: props.targetY,
    targetPosition: props.targetPosition,
  });
  return Array.isArray(result) ? result[0] : result;
});

const pathStyle = computed(() => {
  const baseStroke = props.style.stroke || '#94a3b8';
  const stroke = props.selected ? '#3b82f6' : hovered.value ? '#6366f1' : baseStroke;
  const strokeWidth =
    (props.selected ? 3 : hovered.value ? 2.5 : props.style.strokeWidth || 2);
  return {
    stroke,
    strokeWidth,
    fill: 'none',
    transition: 'stroke 0.15s, stroke-width 0.15s',
  };
});

function handleClick() {
  emit('edge-click', { edge: props });
}
</script>

<template>
  <g
    class="wf-edge"
    :class="{ 'is-selected': selected, 'is-hovered': hovered }"
    @mouseenter="hovered = true"
    @mouseleave="hovered = false"
  >
    <path
      :d="path"
      :style="pathStyle"
      :marker-end="markerEnd"
      class="vue-flow__edge-path"
      @click="handleClick"
    />
    <!-- 加宽的透明命中区，方便选中/悬停。
     *  pointer-events: stroke 要求 stroke 是"已 paint"的；某些浏览器把 stroke="transparent"
     *  当成未 paint 直接丢弃点击。用 pointer-events: all + 24px 宽的几何区域，既
     *  保证宽松的命中判定，也保证透明 stroke 也能吃到事件。 -->
    <path
      :d="path"
      stroke="transparent"
      stroke-width="24"
      fill="none"
      class="wf-edge-hit-area"
      @click="handleClick"
    />
  </g>
</template>

<style scoped>
.wf-edge {
  cursor: pointer;
  pointer-events: stroke;
}

.wf-edge-hit-area {
  pointer-events: all;
  cursor: pointer;
}
</style>
