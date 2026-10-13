<script setup lang="ts">
/**
 * ProviderCredentialModal — save-and-validate a provider-level API key.
 *
 * Single step, no auto-import:
 *   User fills the schema-driven credential form → clicks "保存". The backend calls
 *   the vendor's model listing endpoint as a lightweight validation (bad key → 400,
 *   no state persisted), then saves the credential AND seeds the catalog as
 *   {@code agent_model} rows with {@code enabled=false}. Individual enable/disable
 *   is done in the shell's expanded row via toggle switches — this modal is
 *   deliberately dumb.
 *
 * Emits `saved` when the credential is persisted so the parent refreshes.
 */
import { reactive, ref, watch } from 'vue';

import { Modal } from '../../ui';
import { useProviderHub } from '../composables/useProviderHub';
import type { ProviderView } from '../types';

interface Props {
  open: boolean;
  provider: ProviderView | null;
  tenantId?: string;
  /** In edit mode, secret placeholders read "留空 = 保持不变" and empty submits are skipped. */
  editMode?: boolean;
}

const props = withDefaults(defineProps<Props>(), { editMode: false });

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void;
  (e: 'saved'): void;
}>();

const { saveProviderCredential } = useProviderHub();

const creds = reactive<Record<string, unknown>>({});
const saving = ref(false);
const error = ref<string | null>(null);

watch(
  () => [props.open, props.provider?.name],
  ([o]) => {
    if (!o) return;
    Object.keys(creds).forEach((k) => delete creds[k]);
    error.value = null;
    // Preload defaults from the schema so baseUrl-like fields are ready.
    for (const f of props.provider?.credentialSchema ?? []) {
      if (!props.editMode && f.defaultValue) creds[f.name] = f.defaultValue;
    }
  },
  { immediate: true },
);

async function save() {
  if (!props.provider) return;
  error.value = null;
  const payload: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(creds)) {
    if (v !== null && v !== undefined && String(v).length > 0) payload[k] = v;
  }
  if (Object.keys(payload).length === 0) {
    error.value = props.editMode ? '至少填写一个字段' : '请填入 API 密钥';
    return;
  }
  saving.value = true;
  try {
    await saveProviderCredential(props.provider.name, payload, props.tenantId);
    emit('saved');
    emit('update:open', false);
  } catch (e: any) {
    // Backend returns the vendor's error text on 401 — surface it verbatim.
    error.value = e?.message ?? String(e);
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <Modal
    :open="open"
    class="pcm-modal"
    centered
    :width="520"
    :mask-closable="!saving"
    :keyboard="!saving"
    :closable="!saving"
    @cancel="emit('update:open', false)"
  >
      <template #title>
        <div class="pcm-title">
          <span class="pcm-title-icon">🔑</span>
          <span v-if="provider" class="as-modal-title">
            {{ editMode ? '编辑凭证' : '添加 API 密钥' }} · {{ provider.label }}
          </span>
        </div>
      </template>

      <div class="pcm-body">
        <div class="pcm-hint">
          <template v-if="provider?.supportsRemoteModelListing">
            保存时会向该供应商发起一次列表请求做校验（不会真的注册任何模型）；
            通过后可在下方展开的目录里逐个开关启用。
          </template>
          <template v-else>
            该供应商没有远程模型列表接口，请保存凭证后手动添加模型名称。
          </template>
        </div>

        <div class="pcm-form">
          <div
            v-for="f in provider?.credentialSchema ?? []"
            :key="f.name"
            class="pcm-field"
          >
            <label class="pcm-label">
              <span>{{ f.label }}</span>
              <span v-if="f.required" class="pcm-req">*</span>
              <span v-if="f.secret" class="pcm-secret">密文</span>
            </label>
            <input
              v-if="f.secret"
              type="password"
              class="pcm-input"
              autocomplete="off"
              :value="(creds[f.name] as string) ?? ''"
              :placeholder="editMode ? '留空 = 保持不变' : (f.placeholder ?? '')"
              @input="(e) => (creds[f.name] = (e.target as HTMLInputElement).value)"
            />
            <input
              v-else
              type="text"
              class="pcm-input"
              :value="(creds[f.name] as string) ?? ''"
              :placeholder="editMode ? '留空 = 保持不变' : (f.placeholder ?? '')"
              @input="(e) => (creds[f.name] = (e.target as HTMLInputElement).value)"
            />
          </div>
        </div>

        <div v-if="error" class="pcm-error">
          {{ error }}
        </div>
      </div>

      <template #footer>
      <div class="pcm-footer">
        <button
          class="pcm-btn"
          :disabled="saving"
          @click="emit('update:open', false)"
        >
          取消
        </button>
        <button
          class="pcm-btn pcm-btn-primary"
          :disabled="saving"
          @click="save"
        >
          {{ saving ? '校验中…' : '保存' }}
        </button>
      </div>
      </template>
  </Modal>
</template>

<style scoped>
.pcm-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.pcm-modal { max-height: 88vh; }
:global(.dark) .pcm-modal {
  background: #1f1f1f;
  color: #f3f4f6;
}
.pcm-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid #e5e7eb;
}
:global(.dark) .pcm-header {
  border-bottom-color: #2d2d2d;
}
.pcm-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.pcm-title-icon {
  font-size: 18px;
}
.pcm-close {
  background: transparent;
  border: none;
  font-size: 18px;
  color: #6b7280;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
}
.pcm-close:hover {
  background: #f3f4f6;
}
.pcm-body {
  padding: 16px 20px;
  overflow-y: auto;
  flex: 1;
}
.pcm-hint {
  margin-bottom: 12px;
  font-size: 12px;
  color: #6b7280;
  line-height: 1.6;
}
.pcm-error {
  padding: 8px 12px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 6px;
  color: #b91c1c;
  font-size: 12px;
  margin-top: 8px;
}
.pcm-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.pcm-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.pcm-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  color: #374151;
}
:global(.dark) .pcm-label {
  color: #d1d5db;
}
.pcm-req {
  color: #dc2626;
}
.pcm-secret {
  padding: 0 6px;
  font-size: 10px;
  border-radius: 4px;
  background: #fef3c7;
  color: #b45309;
}
.pcm-input {
  padding: 8px 10px;
  font-size: 13px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  color: #111827;
}
:global(.dark) .pcm-input {
  background: #2d2d2d;
  border-color: #3d3d3d;
  color: #f3f4f6;
}
.pcm-input:focus {
  outline: none;
  border-color: #6366f1;
}
.pcm-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px;
  border-top: 1px solid #e5e7eb;
}
:global(.dark) .pcm-footer {
  border-top-color: #2d2d2d;
}
.pcm-btn {
  padding: 6px 16px;
  font-size: 13px;
  border-radius: 6px;
  border: 1px solid #d1d5db;
  background: #fff;
  cursor: pointer;
}
.pcm-btn:hover {
  background: #f3f4f6;
}
.pcm-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.pcm-btn-primary {
  background: #6366f1;
  color: #fff;
  border-color: #6366f1;
}
.pcm-btn-primary:hover {
  background: #4f46e5;
}
</style>
