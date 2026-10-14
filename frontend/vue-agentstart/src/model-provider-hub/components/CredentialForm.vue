<script setup lang="ts">
/**
 * CredentialForm — schema-driven form for provider credentials.
 *
 * Consumes a provider's `credentialSchema` and renders each field as either a
 * password input (when secret) or plain text. Uses v-model on a caller-owned
 * record so the parent can decide validation + submission.
 */
import type { CredentialField } from '../types';

interface Props {
  /** The provider's field schema, from GET /model-providers. */
  fields: CredentialField[];
  /** The credential values (parent-owned). */
  modelValue: Record<string, unknown>;
  /** Show "leave empty to keep current" placeholder — used in the edit flow. */
  editMode?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  editMode: false,
});

const emit = defineEmits<{
  (e: 'update:modelValue', v: Record<string, unknown>): void;
}>();

function set(name: string, value: unknown) {
  const next = { ...props.modelValue, [name]: value };
  emit('update:modelValue', next);
}
</script>

<template>
  <div class="ph-form">
    <div v-for="f in fields" :key="f.name" class="ph-field">
      <label class="ph-label">
        <span>{{ f.label }}</span>
        <span v-if="f.required" class="ph-req">*</span>
        <span v-if="f.secret" class="ph-secret">密文</span>
      </label>
      <input
        v-if="f.secret"
        type="password"
        class="ph-input"
        autocomplete="off"
        :value="modelValue[f.name] as string ?? ''"
        :placeholder="editMode ? '留空 = 保持不变' : (f.placeholder ?? '')"
        @input="(e) => set(f.name, (e.target as HTMLInputElement).value)"
      />
      <input
        v-else
        type="text"
        class="ph-input"
        :value="modelValue[f.name] as string ?? ''"
        :placeholder="editMode ? '留空 = 保持不变' : (f.placeholder ?? '')"
        @input="(e) => set(f.name, (e.target as HTMLInputElement).value)"
      />
    </div>
  </div>
</template>

<style scoped>
.ph-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.ph-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ph-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  color: #374151;
}
.ph-req {
  color: #dc2626;
}
.ph-secret {
  padding: 0 6px;
  font-size: 10px;
  border-radius: 4px;
  background: #fef3c7;
  color: #b45309;
}
.ph-input {
  padding: 6px 10px;
  font-size: 13px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  transition: border-color 0.15s ease;
}
.ph-input:focus {
  outline: none;
  border-color: #6366f1;
}
</style>
