<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { useVueFlow } from '@vue-flow/core';

import Icon from '../Icon.vue';
import NodeHandle from '../NodeHandle.vue';
import NodeHoverToolbar from '../NodeHoverToolbar.vue';

const props = defineProps({
  id: String,
  data: {
    type: Object,
    default: () => ({
      label: '分类器',
      description: '文本分类节点',
      config: {
        categories: ['正面', '负面', '中性'],
        model: 'text-classification',
      },
    }),
  },
});

const emit = defineEmits(['duplicate', 'delete', 'connection-plus-click']);

const { updateNodeInternals } = useVueFlow();

const CATEGORY_COLORS = ['#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4'];
const getCategoryColor = (index) => CATEGORY_COLORS[index % CATEGORY_COLORS.length];

/** 可视化用的分类列表：兼容 `data.classes`（新表单）与 `data.config.categories`（旧数据） */
const visibleCategories = computed(() => {
  const fromClasses = (props.data.classes || [])
    .map((c) => c.name || `类别 ${c.id}`)
    .filter(Boolean);
  if (fromClasses.length > 0) return fromClasses.slice(0, 5);
  return (props.data.config?.categories || []).slice(0, 5);
});

/** 右侧输出 handle：每个分类一个；分类为空时组件模板会退化到单一默认 handle */
const sourceHandles = computed(() =>
  visibleCategories.value.map((name, index) => ({
    id: name || `case-${index}`,
    color: getCategoryColor(index),
  })),
);

// VueFlow 会缓存每个 handle 挂载时的 bounding rect，用这份缓存渲染边。
// 我们用自定义 top / transform 决定 handle 纵向位置，且 handle 数量随分类
// 变化 —— 若不主动通知 VueFlow 重新测量，边会从旧的默认锚点（右上或右中）
// 出发再折返到 DOM 里 handle 的真实位置，视觉上就是"从底部绕出去"。
// 与 ConditionNode 同样的处理：分类列表变化或组件挂载后强制重量。
watch(
  () => sourceHandles.value.map((h) => h.id).join('|'),
  async () => {
    await nextTick();
    updateNodeInternals([props.id]);
  },
);

onMounted(async () => {
  await nextTick();
  updateNodeInternals([props.id]);
});

const isHovered = ref(false);

const onMouseEnter = () => (isHovered.value = true);
const onMouseLeave = () => (isHovered.value = false);
const duplicateNode = () => emit('duplicate', props.id);
const deleteNode = () => emit('delete', props.id);
// NodeHandle 发的是 (event, nodeId, handleId) 三个参数——之前这里只声明了两个，
// nodeId 被静默塞进了 handleId，结果新建的边 sourceHandle 变成了 nodeId，
// VueFlow 找不到匹配的 handle，就退回到默认锚点（节点底部），看起来就是
// "从右侧伸出后折返到底部出来"。改成和 LLMNode 等其他节点一致的三参数签名。
const onConnectionPlusClick = (event, nodeId, handleId) => {
  emit('connection-plus-click', event, nodeId, handleId);
};
</script>

<template>
  <div
    class="wf-node classifier-node"
    @mouseenter="onMouseEnter"
    @mouseleave="onMouseLeave"
  >
    <NodeHoverToolbar
      v-if="isHovered"
      node-type="classifier"
      @duplicate="duplicateNode"
      @delete="deleteNode"
    />

    <div class="wf-node-header">
      <div class="wf-node-icon">
        <Icon name="classifier" />
      </div>
      <div class="wf-node-info">
        <div class="wf-node-title">{{ data.label }}</div>
        <div class="wf-node-subtitle">文本分类</div>
      </div>
    </div>

    <div class="wf-node-content">
      <div class="wf-node-description">{{ data.description }}</div>
      <div v-if="data.config?.categories?.length" class="wf-node-preview">
        <div class="wf-node-preview-label">分类标签</div>
        <div class="classifier-categories">
          <span
            v-for="(category, index) in visibleCategories"
            :key="index"
            class="classifier-category"
          >{{ category }}</span>
          <span
            v-if="data.config.categories.length > 3"
            class="classifier-more"
          >+{{ data.config.categories.length - 3 }} 更多</span>
        </div>
      </div>
      <div v-else class="wf-node-empty">
        <span class="wf-node-empty-text">配置分类规则</span>
      </div>
    </div>

    <NodeHandle
      :id="id"
      type="target"
      position="Left"
      :is-hovered="isHovered"
      class-name="flow-node-handle-left"
      :style="{ background: '#7c3aed', border: '2px solid #ffffff' }"
    />
    <!-- 每个分类一个 source handle。若尚未配置分类，退化到单一默认 handle 保证能连线。
     *   - top: X% 决定纵向位置；分布收缩到 20%~80% 让 handle 远离 header / 底 padding。
     *   - transform: translate(50%, -50%) 与 VueFlow position="Right" 默认一致 ——
     *     translateX(50%) 让 handle 半个身位露到节点右边缘外面（否则 handle 会缩在
     *     border 里，边线看起来像从节点底部拉出去），translateY(-50%) 把 handle 中心
     *     对齐到 top 的百分位。 -->
    <template v-if="sourceHandles.length > 0">
      <NodeHandle
        v-for="(h, index) in sourceHandles"
        :key="h.id"
        :id="id"
        :handle-id="h.id"
        type="source"
        position="Right"
        :is-hovered="isHovered"
        class-name="flow-node-handle-right"
        :style="{
          background: h.color,
          border: '2px solid #ffffff',
          top: `${20 + (60 * (index + 1)) / (sourceHandles.length + 1)}%`,
          transform: 'translate(50%, -50%)',
        }"
        @connection-plus-click="onConnectionPlusClick"
      />
    </template>
    <NodeHandle
      v-else
      :id="id"
      type="source"
      position="Right"
      :is-hovered="isHovered"
      class-name="flow-node-handle-right"
      :style="{ background: '#7c3aed', border: '2px solid #ffffff' }"
      @connection-plus-click="onConnectionPlusClick"
    />
  </div>
</template>

<style scoped>
.classifier-node {
  --wf-accent: #7c3aed;
  --wf-accent-hover: #6d28d9;
}

.classifier-categories {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
}

.classifier-category {
  padding: 2px 8px;
  font-size: 11px;
  color: #ffffff;
  background: var(--wf-accent);
  border-radius: 999px;
}

.classifier-more {
  padding: 2px 8px;
  font-size: 10px;
  color: #6b7280;
  font-style: italic;
}
</style>
