<script setup lang="ts">
import { ref, watch } from 'vue';
import { Button, Checkbox, Form, FormItem, Input, Modal, Drawer, Select, Spin, message } from '../../ui';
import type { AgentsNamespace, AppPermissionSettings } from '../../client/agents';

const props = defineProps<{ open: boolean; appId: string; appName: string; api: AgentsNamespace; tenantId?: string; presentation?: 'modal' | 'drawer' | 'inline' }>();
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
  settings.value = { mode: 'ALL', grants: [] };
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
  if (grants.some(g => !g.subjectId || g.subjectId.length > 128) || new Set(grants.map(g => `${g.type}:${g.subjectId}`)).size !== grants.length) {
    message.warning('请填写 1–128 字符的授权对象 ID，并移除重复对象');
    return;
  }
  const current = generation;
  const appId = props.appId;
  saving.value = true;
  try {
    await props.api.updatePermissions(appId, { mode: settings.value.mode, grants });
    if (current !== generation) return;
    message.success('数据权限已保存');
    emit('saved');
    if (props.presentation !== 'inline') emit('update:open', false);
  } catch (error: any) {
    message.error(error?.message ?? '保存授权失败');
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <component :is="presentation === 'inline' ? 'section' : presentation === 'drawer' ? Drawer : Modal"
    :class="{ 'permission-page': presentation === 'inline' }" :open="open" :title="`数据权限 · ${appName}`" :width="'min(720px, 100vw)'" :keyboard="!saving" :mask-closable="false"
    :closable="!saving" :confirm-loading="saving" :ok-button-props="{ disabled: !loaded || loading }"
    :cancel-button-props="{ disabled: saving }" @ok="save" @cancel="emit('update:open', false)">
    <header v-if="presentation === 'inline'" class="permission-heading">
      <h2>数据权限设置</h2>
      <p>配置「{{ appName }}」的查看和使用范围</p>
    </header>
    <Spin :spinning="loading">
      <Form v-if="loaded" layout="vertical">
        <div class="permission-scope">
          <strong>租户范围：{{ tenantId || '应用所属租户' }}</strong>
          <p>授权始终受应用租户范围约束，不会扩大到其他租户。查看和使用授权不包含应用编辑权限。</p>
        </div>
        <FormItem label="谁可以查看和使用此应用">
          <Select v-model:value="settings.mode" :options="[
            { label: '租户范围内开放', value: 'ALL' },
            { label: '指定部门、角色或人员', value: 'RESTRICTED' },
          ]" />
        </FormItem>
        <template v-if="settings.mode === 'RESTRICTED'">
          <strong>授权对象（{{ settings.grants.length }} / 1000）</strong>
          <p class="mb-3 text-sm text-gray-500">匹配任意一条即可访问。部门可包含所有层级的下级部门；查看授权不包含应用编辑权限。</p>
          <div v-for="(grant, index) in settings.grants" :key="index" class="permission-grant">
            <Select v-model:value="grant.type" style="width: 100px" :options="[
              { label: '人员', value: 'USER' }, { label: '角色', value: 'ROLE' },
              { label: '部门', value: 'DEPARTMENT' },
            ]" @change="grant.includeDescendants = false" />
            <Input v-model:value="grant.subjectId" class="flex-1" :placeholder="grant.type === 'DEPARTMENT' ? '部门 ID' : grant.type === 'ROLE' ? '角色 ID' : '人员 ID'" :aria-label="`第 ${index + 1} 条授权对象 ID`" :maxlength="128" />
            <Checkbox v-if="grant.type === 'DEPARTMENT'" v-model:checked="grant.includeDescendants">包含下级</Checkbox>
            <Button danger type="text" @click="settings.grants.splice(index, 1)">移除</Button>
          </div>
          <Button :disabled="settings.grants.length >= 1000" @click="settings.grants.push({ type: 'DEPARTMENT', subjectId: '', includeDescendants: false })">添加授权对象</Button>
          <p v-if="!settings.grants.length" class="mt-3 text-sm text-amber-600">尚未添加授权对象，保存后仅应用权限管理员可访问。</p>
        </template>
      </Form>
      <p v-else-if="!loading">无法读取授权配置，请关闭后重试。</p>
    </Spin>
    <div v-if="presentation === 'inline'" class="permission-page-actions">
      <Button type="primary" :loading="saving" :disabled="!loaded || loading || saving" @click="save">保存数据权限</Button>
    </div>
    <template v-if="presentation === 'drawer'" #footer>
      <div class="permission-footer">
        <Button :disabled="saving" @click="emit('update:open', false)">取消</Button>
        <Button type="primary" :loading="saving" :disabled="!loaded || loading || saving" @click="save">保存数据权限</Button>
      </div>
    </template>
  </component>
</template>

<style scoped>
.permission-page { padding: 28px; max-width: 960px; width: 100%; margin: 0 auto; box-sizing: border-box; }
.permission-heading { margin-bottom: 24px; }
.permission-heading h2 { margin: 0; font-size: 20px; font-weight: 600; }
.permission-heading p { margin: 8px 0 0; color: var(--as-text-secondary, #64748b); font-size: 13px; }
.permission-page-actions { display: flex; justify-content: flex-end; margin-top: 24px; padding-top: 20px; border-top: 1px solid var(--as-border, #e5e7eb); }
.permission-scope { padding: 14px 16px; margin-bottom: 20px; border: 1px solid #dbeafe; border-radius: 10px; background: #eff6ff; color: #1e40af; }
.permission-scope p { margin: 8px 0 0; font-size: 13px; line-height: 1.6; }
.permission-grant { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-bottom: 12px; }
.permission-footer { display: flex; justify-content: flex-end; gap: 8px; }
</style>
