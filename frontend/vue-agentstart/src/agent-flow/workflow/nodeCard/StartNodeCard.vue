<script setup lang="ts">
import { h, ref, watchEffect } from 'vue';

import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  UserSwitchOutlined,
} from '@ant-design/icons-vue';

import TriggersItemCard from '@/components/stubs/TriggersItemCard.vue';
import VariableModal from '@/components/stubs/VariableModal.vue';
import OutputItemCard from '@/workflow/OutputItemCard.vue';
import WfField from '@/workflow/WfField.vue';
import { synchronizeStartTriggerOutputs } from '@/workflow/utils/start_trigger_outputs';

defineProps<{ nodeId?: string }>();

const formState: any = defineModel();

if (!Array.isArray(formState.value.sysVariables)) formState.value.sysVariables = [];
for (const variable of [
  { name: 'user_id', type: 'String' },
  { name: 'tenant_id', type: 'String' },
]) {
  if (!formState.value.sysVariables.some((item: any) => item?.name === variable.name)) {
    formState.value.sysVariables.push(variable);
  }
}

const variableModalRef = ref();

watchEffect(() => {
  synchronizeStartTriggerOutputs(formState.value);
});

// 直接把 item 作为参数交给 showModal，同一次同步调用里就把表单初值写进
// 弹窗内部的 form；再也不通过 :main-data prop 中转，避免 prop 需要下一
// 个 tick 才刷新导致的"第一次点开表单是空的 / 是上次的"那种时序 bug。
function toEditVariable(item?: any) {
  variableModalRef.value?.showModal?.(item ?? {});
}

function removeVariable(index: number) {
  formState.value.variables.splice(index, 1);
}

const variableFormSubmit = (data: any) => {
  if (!Array.isArray(formState.value.variables)) formState.value.variables = [];
  const items = formState.value.variables;
  if (data.id) {
    const index = items.findIndex((it: any) => it.id === data.id);
    if (index === -1) items.push(data);
    else items[index] = { ...data };
  } else {
    data.id = Date.now();
    items.push(data);
  }
};
</script>

<template>
  <VariableModal
    ref="variableModalRef"
    @form-submit="variableFormSubmit"
  />

  <div v-if="formState.mode === 'WORKFLOW'" class="wf-config-section">
    <WfField title="触发器">
      <template #tooltip>
        定义工作流被外部（HTTP / 定时 / 事件）触发的方式。
      </template>
      <TriggersItemCard v-model="formState" />
    </WfField>
  </div>

  <div class="wf-config-section">
    <WfField title="输入变量">
      <template #tooltip>
        这里定义的变量会在整个流程中可用，通过 <code v-pre>{{#sys.xxx#}}</code> 或 <code>/</code> 引用。
      </template>
      <template #operations>
        <a-button
          :icon="h(PlusOutlined)"
          size="small"
          type="primary"
          @click="toEditVariable({})"
        />
      </template>

      <div
        v-if="!formState.variables || formState.variables.length === 0"
        class="start-empty"
      >
        尚未配置输入变量，点击右上角 + 添加
      </div>

      <div class="start-var-list">
        <div
          v-for="(item, index) in formState.variables"
          :key="item.id ?? index"
          class="start-var-item"
        >
          <div class="start-var-left">
            <UserSwitchOutlined class="start-var-icon" />
            <div>
              <div class="start-var-label">{{ item.label || item.name }}</div>
              <div class="start-var-sub">{{ item.name }}</div>
            </div>
          </div>
          <div class="start-var-right">
            <a-tag>{{ item.type }}</a-tag>
            <a-tag v-if="item.required" color="orange">必填</a-tag>
            <a-button
              :icon="h(EditOutlined)"
              size="small"
              type="text"
              @click="toEditVariable(item)"
            />
            <a-button
              :icon="h(DeleteOutlined)"
              size="small"
              type="text"
              danger
              @click="removeVariable(index)"
            />
          </div>
        </div>
      </div>
    </WfField>
  </div>

  <div class="wf-config-section">
    <OutputItemCard v-model="formState" readonly force-structured />
  </div>

  <div class="wf-config-section">
    <WfField title="系统变量" foldable default-fold>
      <template #tooltip>
        运行时由引擎注入的只读变量，通过 <code v-pre>{{#sys.xxx#}}</code> 引用。
      </template>
      <div class="start-var-list">
        <div
          v-for="(item, index) in formState.sysVariables"
          :key="index"
          class="start-var-item start-var-item-readonly"
        >
          <div class="start-var-left">
            <div class="start-var-sys">sys</div>
            <div class="start-var-label">{{ item.name }}</div>
          </div>
          <a-tag>{{ item.type }}</a-tag>
        </div>
      </div>
    </WfField>
  </div>
</template>

<style scoped>
.start-empty {
  padding: 12px;
  text-align: center;
  font-size: 12px;
  color: #9ca3af;
  background: var(--wf-config-surface-hover, #f9fafb);
  border: 1px dashed #e5e7eb;
  border-radius: 6px;
}

.start-var-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.start-var-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  background: var(--wf-config-surface-hover, #f9fafb);
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  transition: background 0.15s;
}

.start-var-item:hover {
  background: var(--wf-config-surface-hover, #f9fafb);
}

.start-var-item-readonly {
  background: var(--wf-config-surface, #ffffff);
}

.start-var-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.start-var-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.start-var-icon {
  font-size: 16px;
  color: #6366f1;
}

.start-var-label {
  font-size: 12px;
  font-weight: 500;
  color: #1f2937;
}

.start-var-sub {
  font-size: 11px;
  color: #9ca3af;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
}

.start-var-sys {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 20px;
  font-size: 10px;
  font-weight: 600;
  color: #ffffff;
  background: #6b7280;
  border-radius: 4px;
}
</style>
