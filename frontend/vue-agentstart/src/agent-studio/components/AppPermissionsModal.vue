<script setup lang="ts">
import { ref, watch } from 'vue';
import { Button, Checkbox, Form, FormItem, Input, Modal, Select, Spin, message } from '../../ui';
import type { AgentsNamespace, AppPermissionSettings } from '../../client/agents';

const props = defineProps<{ open: boolean; appId: string; appName: string; api: AgentsNamespace }>();
const emit = defineEmits<{ 'update:open': [value: boolean]; saved: [] }>();
const settings = ref<AppPermissionSettings>({ mode: 'ALL', grants: [] });
const loading = ref(false);
const saving = ref(false);
const loaded = ref(false);
let generation = 0;
watch(() => [props.open, props.appId] as const, async ([open, id]) => {
  const current = ++generation;
  loaded.value = false;
  if (!open || !id) return;
  loading.value = true;
  try {
    const result = await props.api.getPermissions(id);
    if (current !== generation) return;
    settings.value = { mode: result.mode, grants: result.grants.map(grant => ({ ...grant })) };
    loaded.value = true;
  } catch (error: any) {
    if (current === generation) message.error(error?.message ?? '读取授权失败');
  } finally {
    if (current === generation) loading.value = false;
  }
}, { immediate: true });

async function save() {
  if (!loaded.value || saving.value) return;
  const grants = settings.value.grants.map(g => ({ ...g, subjectId: g.subjectId.trim(),
    includeDescendants: g.type === 'DEPARTMENT' && g.includeDescendants }));
  if (grants.some(g => !g.subjectId) || new Set(grants.map(g => `${g.type}:${g.subjectId}`)).size !== grants.length) {
    message.warning('请填写授权对象 ID，并移除重复对象');
    return;
  }
  saving.value = true;
  try {
    await props.api.updatePermissions(props.appId, { mode: settings.value.mode, grants });
    message.success('应用授权已保存');
    emit('saved');
    emit('update:open', false);
  } catch (error: any) {
    message.error(error?.message ?? '保存授权失败');
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <Modal :open="open" :title="`数据权限 · ${appName}`" width="720px" :mask-closable="false"
    :closable="!saving" :confirm-loading="saving" :ok-button-props="{ disabled: !loaded || loading }"
    :cancel-button-props="{ disabled: saving }" @ok="save" @cancel="emit('update:open', false)">
    <Spin :spinning="loading">
      <Form v-if="loaded" layout="vertical">
        <FormItem label="谁可以查看和使用此应用">
          <Select v-model:value="settings.mode" :options="[
            { label: '按原租户可见范围开放', value: 'ALL' },
            { label: '仅指定用户、角色或部门', value: 'RESTRICTED' },
          ]" />
        </FormItem>
        <template v-if="settings.mode === 'RESTRICTED'">
          <p class="mb-3 text-sm text-gray-500">匹配任意一条即可访问。部门可包含所有层级的下级部门；查看授权不包含应用编辑权限。</p>
          <div v-for="(grant, index) in settings.grants" :key="index" class="mb-3 flex items-center gap-2">
            <Select v-model:value="grant.type" style="width: 100px" :options="[
              { label: '用户', value: 'USER' }, { label: '角色', value: 'ROLE' },
              { label: '部门', value: 'DEPARTMENT' },
            ]" @change="grant.includeDescendants = false" />
            <Input v-model:value="grant.subjectId" class="flex-1" placeholder="业务系统中的对象 ID" :maxlength="128" />
            <Checkbox v-if="grant.type === 'DEPARTMENT'" v-model:checked="grant.includeDescendants">包含下级</Checkbox>
            <Button danger type="text" @click="settings.grants.splice(index, 1)">移除</Button>
          </div>
          <Button :disabled="settings.grants.length >= 1000" @click="settings.grants.push({ type: 'DEPARTMENT', subjectId: '', includeDescendants: false })">添加授权对象</Button>
          <p v-if="!settings.grants.length" class="mt-3 text-sm text-amber-600">尚未添加授权对象，保存后仅应用权限管理员可访问。</p>
        </template>
      </Form>
      <p v-else-if="!loading">无法读取授权配置，请关闭后重试。</p>
    </Spin>
  </Modal>
</template>
