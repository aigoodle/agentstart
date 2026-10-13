<script setup lang="ts">
import PromptEditor from '@/components/PromptEditor.vue';
import WfField from '@/workflow/WfField.vue';

defineProps({
  attrListGroup: {
    type: Array,
    default: () => [],
  },
  /** 当前节点 id —— 变量插入面板需要，由 NodeConfigCard 从 selectNode.id 传下来。 */
  nodeId: {
    type: String,
    default: '',
  },
});

const formState: any = defineModel();
</script>

<template>
  <PromptEditor
    class="wf-config-prompt"
    title="ANSWER"
    :node-id="nodeId"
    v-model="formState.answer"
  />

  <div class="wf-config-section">
    <WfField title="输出" foldable default-fold>
      <template #tooltip>
        Answer 节点默认输出 <code>text</code> 字段，供下游节点引用。
      </template>
      <div class="answer-output">
        <span class="answer-output-name">text</span>
        <a-tag>string</a-tag>
        <span class="answer-output-desc">回复文本</span>
      </div>
    </WfField>
  </div>
</template>

<style scoped>
.answer-output {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  background: var(--wf-config-surface-hover, #f9fafb);
  border: 1px solid #f0f0f0;
  border-radius: 6px;
}

.answer-output-name {
  font-size: 12px;
  font-weight: 500;
  color: #1f2937;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
}

.answer-output-desc {
  margin-left: auto;
  font-size: 11px;
  color: #6b7280;
}
</style>
