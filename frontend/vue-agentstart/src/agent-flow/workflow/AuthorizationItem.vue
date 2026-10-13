<script setup lang="ts">
import VarSelectField from './VarSelectField.vue';

defineProps({
  name: {
    type: String,
    default: '',
  },
  /** 当前节点 id —— API Key Value 支持插入上游变量时需要。 */
  nodeId: {
    type: String,
    default: '',
  },
});

const formState: any = defineModel();
</script>

<template>
  <div class="wf-auth">
    <div class="wf-auth-row">
      <div class="wf-auth-col">
        <div class="wf-auth-label">认证类型</div>
        <a-radio-group
          v-model:value="formState.auth_type"
          button-style="solid"
          size="small"
        >
          <a-radio-button value="none">无</a-radio-button>
          <a-radio-button value="api_key">API Key</a-radio-button>
        </a-radio-group>
      </div>
      <div v-if="formState.auth_type === 'api_key'" class="wf-auth-col">
        <div class="wf-auth-label">鉴权前缀</div>
        <a-radio-group
          v-model:value="formState.api_key_header_prefix"
          button-style="solid"
          size="small"
        >
          <a-radio-button value="basic">Basic</a-radio-button>
          <a-radio-button value="bearer">Bearer</a-radio-button>
          <a-radio-button value="custom">Custom</a-radio-button>
        </a-radio-group>
      </div>
    </div>

    <div v-if="formState.auth_type === 'api_key'" class="wf-auth-grid">
      <div class="wf-auth-col">
        <div class="wf-auth-label">Header Key</div>
        <a-input
          v-model:value="formState.api_key_header"
          placeholder="Authorization"
          size="small"
        />
      </div>
      <div class="wf-auth-col">
        <div class="wf-auth-label">API Key Value</div>
        <VarSelectField
          v-model="formState.api_key_value"
          :node-id="nodeId"
          placeholder="选择变量"
          size="small"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.wf-auth {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.wf-auth-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.wf-auth-col {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  flex: 1;
}

.wf-auth-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.wf-auth-label {
  font-size: 11px;
  font-weight: 500;
  color: #6b7280;
}
</style>
