<script setup lang="ts">
import { message } from '../../ui/message';
import { ShadcnBoolean as AsShadcnBoolean } from '../../ui/components/ShadcnBoolean';

import { ShadcnSelect as AsShadcnSelect } from '../../ui/components/ShadcnSelect';
import { Input as AsShadcnInput } from '../../ui/shadcn/input';

/**
 * 「定义我的机器人」表单弹窗。
 *
 * 机器人 = 已安装消息渠道的一条账号连接(ChannelConnection),与「账号接入
 * → 添加账号」(ChannelAccountDrawer)是同一件事,差异只有两点:
 *   1. 先选择消息渠道,再由渠道的 credentialSchema / configurationSchema
 *      关联出需要配置的参数(JsonSchemaForm 动态渲染);
 *   2. ownerId 不手工填写 —— 由宿主注入的 `user.userId` 自动填充并隐藏,
 *      租户 id 同样不在界面出现。
 */
import { computed, ref, watch } from 'vue';

import type { AgentStartClient } from '../../client';
import { Button, Modal } from '../../ui';
import type {
  ChannelConnection,
  ChannelDefinition,
  RobotUser,
} from '../types';
import JsonSchemaForm from '../../ui/components/JsonSchemaForm.vue';

const props = defineProps<{
  open: boolean;
  client: AgentStartClient;
  /** 已安装的消息渠道(父级已过滤)。 */
  channels: ChannelDefinition[];
  /** 传入时为编辑模式,回填已有连接。 */
  robot?: ChannelConnection;
  /** 宿主业务系统注入的当前人员信息;仅用于保存时注入 ownerId。 */
  user?: RobotUser;
  tenantId?: string;
}>();

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void;
  (e: 'saved'): void;
}>();

const channelKey = ref('');
const form = ref({
  name: '',
  enabled: true,
});
const credentials = ref<Record<string, unknown>>({});
const channelConfig = ref<Record<string, unknown>>({});
const saving = ref(false);
const error = ref('');

const selectedChannel = computed(() =>
  props.channels.find(
    (c) => `${c.provider}:${c.channelId}` === channelKey.value,
  ),
);

function blankForm() {
  channelKey.value = props.robot
    ? `${props.robot.provider}:${props.robot.channelId}`
    : '';
  form.value = {
    name: '',
    enabled: true,
  };
  credentials.value = {};
  channelConfig.value = {};
}

function fillFrom(row: ChannelConnection) {
  form.value = {
    name: row.name,
    enabled: row.desiredStatus === 'ACTIVE',
  };
  // 凭证不回显:留空表示不覆盖
  credentials.value = {};
  channelConfig.value = {};
}

watch(
  () => [props.open, props.robot?.id],
  async () => {
    if (!props.open) return;
    error.value = '';
    blankForm();
    if (props.robot) fillFrom(props.robot);
  },
  { immediate: true },
);

// 切换渠道时重置凭证与通道配置(参数结构随渠道变化)
watch(channelKey, () => {
  credentials.value = {};
  channelConfig.value = {};
});

async function save() {
  const channel = selectedChannel.value;
  if (!channel) {
    error.value = '请选择消息渠道';
    return;
  }
  if (!form.value.name.trim()) {
    error.value = '机器人名称不能为空';
    return;
  }
  if (!props.user?.userId) {
    error.value = '缺少当前人员信息,无法保存机器人';
    return;
  }
  saving.value = true;
  error.value = '';
  try {
    await props.client.channels.saveChannelConnection({
      id: props.robot?.id,
      tenantId: props.tenantId,
      ownerType: 'USER',
      // ownerId 在此注入 —— 表单层不感知该字段
      ownerId: props.user.userId,
      provider: channel.provider,
      channelId: channel.channelId,
      name: form.value.name.trim(),
      credentials: Object.keys(credentials.value).length
        ? credentials.value
        : undefined,
      config: Object.keys(channelConfig.value).length
        ? channelConfig.value
        : undefined,
      enabled: form.value.enabled,
    });
    emit('saved');
    emit('update:open', false);
  } catch (e: any) {
    message.error(e?.message ?? '保存失败');
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <Modal
    :open="open"
    class="rfm-modal"
    centered
    :width="620"
    :title="robot ? '编辑机器人' : '定义我的机器人'"
    :mask-closable="!saving"
    :keyboard="!saving"
    :closable="!saving"
    @cancel="emit('update:open', false)"
  >
        <main class="rfm-main">
          <label class="rfm-field">
            <span>消息渠道 *</span>
            <AsShadcnSelect
              v-model="channelKey"
              class="rfm-input"
              :disabled="!!robot"
            >
              <option value="">请选择已安装的消息渠道</option>
              <option
                v-for="c in channels"
                :key="`${c.provider}:${c.channelId}`"
                :value="`${c.provider}:${c.channelId}`"
              >
                {{ c.name }}({{ c.provider }})
              </option>
            </AsShadcnSelect>
            <span v-if="robot" class="rfm-hint">
              机器人所属渠道创建后不可变更
            </span>
          </label>

          <label class="rfm-field">
            <span>机器人名称 *</span>
            <AsShadcnInput
              v-model.trim="form.name"
              class="rfm-input"
              :placeholder="`例如 ${selectedChannel?.name ?? '渠道'} 工作账号`"
            />
          </label>

          <template v-if="selectedChannel">
            <fieldset class="rfm-group">
              <legend>账号凭证</legend>
              <p>凭证会加密保存且不会回显,编辑时留空表示不修改。</p>
              <JsonSchemaForm
                v-model="credentials"
                :schema="selectedChannel.credentialSchema"
              />
            </fieldset>

            <fieldset class="rfm-group">
              <legend>通道配置</legend>
              <JsonSchemaForm
                v-model="channelConfig"
                :schema="selectedChannel.configurationSchema"
              />
            </fieldset>
          </template>
          <p v-else class="rfm-hint">选择渠道后,将显示该渠道需要配置的参数。</p>

          <p v-if="error" class="rfm-note is-danger">{{ error }}</p>
        </main>

        <template #footer>
          <label class="rfm-enable as-modal__footer-leading">
            <AsShadcnBoolean v-model="form.enabled" type="checkbox" />启用消息接收
          </label>
          <Button @click="emit('update:open', false)">
            取消
          </Button>
          <Button
            type="primary"
            :loading="saving"
            :disabled="!channelKey || !form.name.trim()"
            @click="save"
          >
            {{ robot ? '保存修改' : '创建机器人' }}
          </Button>
        </template>
  </Modal>
</template>

<style scoped>
/* 遮罩与弹窗容器 —— 对齐 ConnectorConnectionModal(ccm) 的视觉规范 */
.rfm-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1200;
}
.rfm-modal { max-height: 88vh; }
.dark .rfm-modal {
  background: var(--card);
}

.rfm-enter-active,
.rfm-leave-active {
  transition: opacity 0.18s ease;
}
.rfm-enter-active .rfm-modal,
.rfm-leave-active .rfm-modal {
  transition: transform 0.18s ease;
}
.rfm-enter-from,
.rfm-leave-to {
  opacity: 0;
}
.rfm-enter-from .rfm-modal,
.rfm-leave-to .rfm-modal {
  transform: translateY(12px) scale(0.98);
}

.rfm-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--border);
}
.rfm-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--foreground);
}
.rfm-close {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 0;
  background: none;
  font-size: 14px;
  color: var(--muted-foreground);
  border-radius: 6px;
  cursor: pointer;
  transition:
    background 0.15s ease,
    color 0.15s ease;
}
.rfm-close:hover {
  background: var(--muted);
  color: var(--foreground);
}
.rfm-enable {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--foreground);
  cursor: pointer;
}
.dark .rfm-header,
.dark .rfm-title {
  color: #f3f4f6;
}
.dark .rfm-close:hover {
  background: var(--card);
  color: #f3f4f6;
}
.dark .rfm-enable {
  color: var(--muted-foreground);
}

.rfm-main {
  flex: 1;
  overflow: auto;
  padding: 18px 20px;
  display: grid;
  gap: 16px;
}
.rfm-row {
  display: flex;
  gap: 14px;
}
.rfm-field {
  display: grid;
  gap: 7px;
  font-size: 13px;
  font-weight: 500;
  color: var(--foreground);
}
.rfm-field-grow {
  flex: 1;
}
.rfm-input {
  padding: 8px 10px;
  font-size: 13px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--background);
  color: var(--foreground);
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}
.rfm-input:focus {
  outline: none;
  border-color: var(--primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--primary) 15%, transparent);
}
.rfm-input:disabled {
  background: var(--muted);
  color: var(--muted-foreground);
  cursor: not-allowed;
}
.rfm-hint {
  font-size: 12px;
  color: var(--muted-foreground);
  margin: 0;
}
.dark .rfm-field {
  color: var(--muted-foreground);
}
.dark .rfm-input {
  background: var(--card);
  border-color: #3f3f3f;
  color: #f3f4f6;
}
.dark .rfm-input:disabled {
  background: var(--card);
  color: var(--muted-foreground);
}
.dark .rfm-hint {
  color: var(--muted-foreground);
}

.rfm-group {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 8px;
}
.rfm-group legend {
  font-size: 13px;
  font-weight: 600;
  color: var(--foreground);
  padding: 0 4px;
}
.rfm-group p {
  margin: 0;
  font-size: 12px;
  color: var(--muted-foreground);
}
.dark .rfm-group {
  border-color: #2d2d2d;
}
.dark .rfm-group legend {
  color: var(--muted-foreground);
}
.dark .rfm-group p {
  color: var(--muted-foreground);
}

.rfm-note {
  margin: 0;
  font-size: 12px;
}
.rfm-note.is-danger {
  color: #dc2626;
}

.rfm-btn {
  padding: 7px 14px;
  font-size: 13px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--background);
  color: var(--foreground);
  cursor: pointer;
  transition:
    background 0.15s ease,
    border-color 0.15s ease;
}
.rfm-btn:hover {
  background: var(--muted);
}
.rfm-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.rfm-btn-primary {
  background: var(--primary);
  border-color: var(--primary);
  color: var(--primary-foreground);
}
.rfm-btn-primary:hover {
  background: #4f52e8;
}
.dark .rfm-btn {
  background: var(--card);
  border-color: #3f3f3f;
  color: var(--muted-foreground);
}
.dark .rfm-btn:hover {
  background: var(--card);
}
.dark .rfm-btn-primary {
  background: var(--primary);
  border-color: var(--primary);
  color: var(--primary-foreground);
}
</style>
