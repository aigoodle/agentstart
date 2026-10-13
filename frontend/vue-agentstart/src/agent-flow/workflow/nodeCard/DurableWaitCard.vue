<script setup lang="ts">
import { computed } from 'vue';
import WfField from '../WfField.vue';

const props = defineProps<{ nodeType: string }>();
const formState: any = defineModel();
const schemaText = computed({
  get: () => JSON.stringify(formState.value.inputSchema || { type: 'object', properties: {} }, null, 2),
  set: (value: string) => { try { formState.value.inputSchema = JSON.parse(value); } catch { /* keep last valid schema */ } },
});
</script>

<template>
  <div v-if="nodeType !== 'SLEEP_UNTIL'" class="wf-config-section">
    <WfField v-if="nodeType === 'WAIT_EVENT' || nodeType === 'HUMAN_INPUT' || nodeType === 'APPROVAL'" title="Correlation Key" :required="nodeType === 'WAIT_EVENT'">
      <a-input v-model:value="formState.correlationKey" placeholder="例如 order:{{#var.orderId#}}；留空时由运行自动生成" />
    </WfField>
    <WfField title="输入 Schema (JSON)"><a-textarea v-model:value="schemaText" :rows="8" /></WfField>
    <WfField title="超时（秒）"><a-input-number v-model:value="formState.timeoutSeconds" :min="0" style="width:100%" /></WfField>
  </div>
  <div v-if="nodeType === 'APPROVAL'" class="wf-config-section">
    <WfField title="超时策略"><a-select v-model:value="formState.timeoutStrategy" :options="[{value:'TIMEOUT',label:'超时终止'},{value:'ESCALATE',label:'升级审批'}]" style="width:100%" /></WfField>
    <WfField v-if="formState.timeoutStrategy === 'ESCALATE'" title="升级 Correlation Key" required><a-input v-model:value="formState.escalationCorrelationKey" /></WfField>
    <WfField title="允许用户 ID"><a-select v-model:value="formState.allowedUserIds" mode="tags" placeholder="输入后回车" style="width:100%" /></WfField>
    <WfField title="允许角色"><a-select v-model:value="formState.allowedRoles" mode="tags" placeholder="输入后回车" style="width:100%" /></WfField>
  </div>
  <div v-if="nodeType === 'SLEEP_UNTIL'" class="wf-config-section">
    <WfField title="绝对唤醒时间"><a-input v-model:value="formState.until" placeholder="ISO-8601，例如 2026-08-22T01:00:00Z" /></WfField>
    <WfField title="或延迟（毫秒）"><a-input-number v-model:value="formState.delayMillis" :min="1" style="width:100%" /></WfField>
    <div class="wf-config-hint">只能配置其中一项；等待期间不占用执行线程。</div>
  </div>
</template>
