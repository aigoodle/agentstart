<script setup lang="ts">
// @ts-nocheck — legacy flow-designer code, pre-dates strict typing; cleanup tracked separately.
import DatasetItemCard from '@/components/DatasetItemCard.vue';
import VariableSelector from '@/components/VariableSelector.vue';
import OutputItemCard from '@/workflow/OutputItemCard.vue';
import OutputStructureToggle from '@/workflow/OutputStructureToggle.vue';
import WfField from '@/workflow/WfField.vue';

defineProps<{ nodeId?: string }>();

const formState: any = defineModel();
</script>

<template>
  <div class="wf-config-section">
    <WfField title="查询变量" required>
      <template #tooltip>
        作为检索关键词的上游变量，通常是用户提问。
      </template>
      <VariableSelector
        v-model="formState.queryVariableSelector"
        :node-id="nodeId"
        placeholder="选择查询变量"
      />
    </WfField>
  </div>

  <div class="wf-config-section">
    <WfField title="数据集" required>
      <template #tooltip>
        选择一个或多个知识库；开启动态变量后可按运行时上下文切换数据集。
      </template>
      <DatasetItemCard v-model="formState.dataset" :node-id="nodeId">
        <template #dynamic>
          <VariableSelector
            v-model="formState.dataset.dynamicVariableSelector"
            :node-id="nodeId"
            placeholder="选择动态变量"
          />
        </template>
      </DatasetItemCard>
    </WfField>
  </div>

  <div class="wf-config-section">
    <WfField class="wf-output-field" title="输出变量" foldable default-fold>
      <template #operations>
        <OutputStructureToggle v-model="formState" />
      </template>
      <OutputItemCard v-model="formState" :show-title="false" />
    </WfField>
  </div>
</template>
