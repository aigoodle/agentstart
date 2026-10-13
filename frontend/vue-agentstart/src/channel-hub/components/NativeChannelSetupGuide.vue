<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{ channelId?: string; accountId?: string }>();

const guides: Record<string, { mode: string; steps: string[] }> = {
  qqbot: { mode: '官方签名 Webhook', steps: ['在 QQ 开放平台开启消息回调', '配置公网 HTTPS 回调地址', '平台 op=13 校验会自动返回 Ed25519 签名'] },
  feishu: { mode: '事件订阅 Webhook', steps: ['订阅 im.message.receive_v1', '配置 Verification Token', '为应用开通读取和发送消息权限'] },
  dingtalk: { mode: '官方 Stream', steps: ['机器人消息接收模式选择 Stream', '配置 Client ID 和 Client Secret', '无需公网回调地址'] },
  wecom: { mode: '官方 WebSocket 长连接', steps: ['在企业微信管理后台创建智能机器人', '填写机器人 Bot ID 和 Secret', '保存后服务端自动认证、保活并接收消息，无需配置回调地址'] },
  email: { mode: 'IMAP + SMTP', steps: ['为邮箱开启 IMAP/SMTP', '建议使用独立应用密码', '未读邮件进入工作流，结果回复原发件人'] },
  webhook: { mode: 'HTTP JSON', steps: ['入站请求使用 X-Agent-Start-Token', '消息体需包含 messageId、senderId 和 content', '出站请求发送到 outboundUrl'] },
};

const guide = computed(() => guides[props.channelId ?? '']);
const callbackPath = computed(() =>
  `/agent-start/channel-events/native/${props.channelId || '{channelId}'}/${props.accountId || '{accountId}'}`,
);
const needsCallback = computed(() => ['qqbot', 'feishu', 'webhook'].includes(props.channelId ?? ''));
</script>

<template>
  <aside v-if="guide" class="native-guide">
    <div><strong>接入方式</strong><span>{{ guide.mode }}</span></div>
    <code v-if="needsCallback">{{ callbackPath }}</code>
    <ol><li v-for="step in guide.steps" :key="step">{{ step }}</li></ol>
  </aside>
</template>

<style scoped>
.native-guide{margin:0 0 16px;padding:12px 14px;border:1px solid #dbeafe;border-radius:10px;background:#f8fbff;color:#334155;font-size:13px}.native-guide>div{display:flex;justify-content:space-between;gap:12px}.native-guide span{color:#2563eb}.native-guide code{display:block;margin-top:9px;padding:7px 9px;border-radius:6px;background:#eaf2ff;overflow-wrap:anywhere}.native-guide ol{margin:9px 0 0;padding-left:20px}.native-guide li+li{margin-top:4px}
</style>
