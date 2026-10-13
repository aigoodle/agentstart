<script setup>
import { Handle, Position } from '@vue-flow/core';

const props = defineProps({
  id: String,
  type: {
    type: String,
    default: 'source',
    validator: (value) => ['source', 'target'].includes(value)
  },
  position: {
    type: String,
    default: 'Right',
    validator: (value) => ['Top', 'Right', 'Bottom', 'Left'].includes(value)
  },
  handleId: {
    type: String,
    default: null
  },
  style: {
    type: Object,
    default: () => ({})
  },
  className: {
    type: String,
    default: ''
  },
  isHovered: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['connection-plus-click']);

const handleClick = (event) => {
  if (props.type === 'source') {
    emit('connection-plus-click', event, props.id, props.handleId);
  }
};

const getPositionValue = () => {
  return Position[props.position] || Position.Right;
};

const getHandleClass = () => {
  let baseClass = 'flow-node-handle';
  if (props.type === 'source') {
    baseClass += ` flow-node-handle-${props.position.toLowerCase()}`;
  }
  if (props.className) {
    baseClass += ` ${props.className}`;
  }
  if (props.isHovered && props.type === 'source') {
    baseClass += ' hovered';
  }
  return baseClass;
};
</script>

<template>
  <Handle
    :type="type"
    :position="getPositionValue()"
    :id="handleId"
    :class="getHandleClass()"
    :style="style"
    @click="handleClick"
  />
</template>

<style scoped>
/* 基础handle样式 */
.flow-node-handle {
  width: 12px;
  height: 12px;
  border: 2px solid #ffffff;
  border-radius: 50%;
  transition: all 0.3s ease;
  cursor: crosshair;
  position: absolute;
  z-index: 10;
}

/* source handle 特殊样式 */
.flow-node-handle.flow-node-handle-right {
  background: #10b981;
}

.flow-node-handle.flow-node-handle-right.hovered {
  width: 30px;
  height: 30px;
  background: #3b82f6;
  box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.2);
  cursor: pointer;
}

/* hover 时显示 + 号：用 CSS 十字形代替字符 + ，居中精确 */
.flow-node-handle.flow-node-handle-right.hovered::before,
.flow-node-handle.flow-node-handle-right.hovered::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  background: #ffffff;
  border-radius: 1px;
  z-index: 11;
  pointer-events: none;
}

/* 横向短杠 */
.flow-node-handle.flow-node-handle-right.hovered::before {
  width: 12px;
  height: 2px;
  transform: translate(-50%, -50%);
}

/* 纵向短杠 */
.flow-node-handle.flow-node-handle-right.hovered::after {
  width: 2px;
  height: 12px;
  transform: translate(-50%, -50%);
}

/* 其他方向的handle */
.flow-node-handle.flow-node-handle-top {
  background: #8b5cf6;
}

.flow-node-handle.flow-node-handle-bottom {
  background: #f59e0b;
}

.flow-node-handle.flow-node-handle-left {
  background: #ef4444;
}

/* target handle 样式 */
.flow-node-handle[data-handletype="target"] {
  background: #6b7280;
}

.flow-node-handle[data-handletype="target"]:hover {
  background: #4b5563;
  box-shadow: 0 0 0 3px rgba(107, 114, 128, 0.2);
}
</style>
