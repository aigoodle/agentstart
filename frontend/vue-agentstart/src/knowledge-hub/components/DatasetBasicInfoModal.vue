<script setup lang="ts">
import { ref, watch } from 'vue';
import { Modal } from '../../ui';
import { Input } from '../../ui/shadcn/input';
import { Textarea } from '../../ui/shadcn/textarea';
import type { DatasetCardItem, DatasetSummary } from '../types';

const props = defineProps<{
  open: boolean;
  dataset: DatasetCardItem | null;
  save: (id: string, patch: { name: string; description: string }) => Promise<DatasetSummary>;
}>();
const emit = defineEmits<{
  (e: 'update:open', open: boolean): void;
  (e: 'saved', dataset: DatasetSummary): void;
}>();
const name = ref('');
const description = ref('');
const saving = ref(false);
const error = ref('');
watch(() => [props.open, props.dataset] as const, () => {
  if (!props.open) return;
  name.value = props.dataset?.name ?? '';
  description.value = props.dataset?.description ?? '';
  error.value = '';
}, { immediate: true });
function close() {
  if (!saving.value) emit('update:open', false);
}
async function submit() {
  if (!props.dataset || saving.value) return;
  if (!name.value.trim()) {
    error.value = '请输入知识库名称';
    return;
  }
  const target = props.dataset;
  const patch = { name: name.value.trim(), description: description.value.trim() };
  saving.value = true;
  error.value = '';
  try {
    const updated = await props.save(target.id, patch);
    emit('saved', { ...target, ...updated, ...patch });
    emit('update:open', false);
  } catch (e) {
    error.value = e instanceof Error ? e.message : '保存失败，请重试';
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <Modal :open="open" title="编辑基本信息" :width="520" ok-text="保存修改" cancel-text="取消"
    :cancel-button-props="{ disabled: saving }" :confirm-loading="saving" :closable="!saving" :mask-closable="!saving" :keyboard="!saving"
    :ok-button-props="{ disabled: !name.trim() || saving }" @ok="submit" @cancel="close"
    @update:open="(value: boolean) => { if (!value) close(); }">
    <form class="kh-basic-info-form" @submit.prevent="submit">
      <p class="kh-basic-info-hint">完善知识库信息，方便识别和查找。</p>
      <label for="kh-basic-name">知识库名称 <span aria-hidden="true">*</span></label>
      <Input id="kh-basic-name" v-model="name" :disabled="saving" placeholder="请输入知识库名称" required />
      <label for="kh-basic-description">描述 <span class="kh-basic-info-optional">选填</span></label>
      <Textarea id="kh-basic-description" v-model="description" :disabled="saving" :rows="5" placeholder="介绍知识库的内容与用途" />
      <p v-if="error" class="kh-basic-info-error" role="alert">{{ error }}</p>
    </form>
  </Modal>
</template>

<style scoped>
.kh-basic-info-form { display: flex; flex-direction: column; gap: 12px; padding: 8px 0; }
.kh-basic-info-hint { margin: 0 0 8px; color: var(--kh-color-text-muted); font-size: 13px; }
.kh-basic-info-form label { font-size: 13px; font-weight: 500; color: var(--kh-color-text-primary); }
.kh-basic-info-form label > span:first-child { color: var(--kh-color-danger); }
.kh-basic-info-form label .kh-basic-info-optional { margin-left: 6px; font-weight: 400; color: var(--kh-color-text-muted); }
.kh-basic-info-error { margin: 0; font-size: 13px; color: var(--kh-color-danger); }
</style>
