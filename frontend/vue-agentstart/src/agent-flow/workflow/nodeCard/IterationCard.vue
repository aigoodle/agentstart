<script setup lang="ts">
// @ts-nocheck — legacy flow-designer code, pre-dates strict typing; cleanup tracked separately.
import VariableSelector from '@/components/VariableSelector.vue';
import WfField from '@/workflow/WfField.vue';

defineProps<{ nodeId?: string }>();

const formState: any = defineModel();

const ERROR_MODES = [
  { value: 'terminate', label: '终止工作流' },
  { value: 'continue', label: '跳过并继续' },
  { value: 'default', label: '设默认值继续' },
];
</script>

<template>
  <div class="wf-config-section">
    <WfField title="迭代变量" required>
      <template #tooltip>
        从上游节点选择一个列表变量，子图会对每一项执行一次。
      </template>
      <VariableSelector
        v-model="formState.iteratorVariableSelector"
        :node-id="nodeId"
        placeholder="选择要迭代的列表变量"
      />
    </WfField>

    <WfField title="聚合输出">
      <template #tooltip>
        指定子图内哪个变量作为每次迭代的返回，最终聚合成结果列表。
      </template>
      <VariableSelector
        v-model="formState.outputVariableSelector"
        :node-id="nodeId"
        placeholder="选择每次迭代的输出变量"
      />
    </WfField>
  </div>

  <div class="wf-config-section">
    <WfField title="并行度" is-subtitle>
      <template #tooltip>
        同时并发执行的迭代数。默认串行（1），设为 N 会同时运行 N 个子图。
      </template>
      <a-input-number
        v-model:value="formState.parallelSize"
        :min="1"
        :max="20"
        style="width: 100%"
        placeholder="1"
      />
    </WfField>

    <WfField title="错误处理" is-subtitle>
      <template #tooltip>
        某次子图执行失败时如何处理。
      </template>
      <a-select
        v-model:value="formState.errorMode"
        :options="ERROR_MODES"
        style="width: 100%"
      />
    </WfField>
  </div>
</template>
