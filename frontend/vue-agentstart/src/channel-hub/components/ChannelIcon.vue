<script setup lang="ts">
import { computed } from 'vue';

import type { Component } from 'vue';
import {
  DingtalkOutlined,
  QqOutlined,
  SlackOutlined,
  WechatOutlined,
  WhatsAppOutlined,
} from '@ant-design/icons-vue';

const props = withDefaults(
  defineProps<{
    /** 平台标识（一般为 metadata.platformId ?? channelId，小写） */
    platformId?: string;
    channelId?: string;
    name?: string;
    /** 通道 metadata，支持后端下发 iconUrl / svgIcon 覆盖内置图标 */
    metadata?: Record<string, unknown>;
    size?: number;
    radius?: number;
  }>(),
  { platformId: '', channelId: '', name: '', size: 40, radius: 11 },
);

/* antd 官方品牌图标可直接覆盖的平台 */
const BRAND_ICONS: Record<string, Component> = {
  dingtalk: DingtalkOutlined,
  dingding: DingtalkOutlined,
  qq: QqOutlined,
  slack: SlackOutlined,
  wechat: WechatOutlined,
  weixin: WechatOutlined,
  wecom: WechatOutlined,
  whatsapp: WhatsAppOutlined,
};

/* 平台品牌色：前景色 + 柔和底色；未收录的平台走默认 indigo */
const TINTS: Record<string, { color: string; bg: string }> = {
  dingtalk: { color: '#0082ff', bg: '#e8f3ff' },
  dingding: { color: '#0082ff', bg: '#e8f3ff' },
  qq: { color: '#12b7f5', bg: '#e5f7fe' },
  slack: { color: '#611f69', bg: '#f6eff7' },
  wechat: { color: '#07c160', bg: '#e6f9ef' },
  weixin: { color: '#07c160', bg: '#e6f9ef' },
  wecom: { color: '#2f7cf6', bg: '#eaf2fe' },
  whatsapp: { color: '#25d366', bg: '#e9fbf0' },
  feishu: { color: '#6d28d9', bg: '#ede9fe' },
  lark: { color: '#6d28d9', bg: '#ede9fe' },
  telegram: { color: '#0284c7', bg: '#e0f2fe' },
  discord: { color: '#4338ca', bg: '#e0e7ff' },
};
const DEFAULT_TINT = { color: '#4f46e5', bg: '#eef2ff' };

const key = computed(() => (props.platformId || props.channelId || '').toLowerCase());
const tint = computed(() => TINTS[key.value] ?? DEFAULT_TINT);

// 后端可直接下发图标：svgIcon（原始 SVG 标记）优先，其次 iconUrl
const svgIcon = computed(() => {
  const svg = props.metadata?.svgIcon;
  return typeof svg === 'string' && svg.includes('<svg')
    ? `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
    : '';
});
const iconUrl = computed(() => {
  const url = props.metadata?.iconUrl;
  return typeof url === 'string' && /^(https?:)?\/\//.test(url) ? url : '';
});

const brandIcon = computed(() => BRAND_ICONS[key.value]);
const letter = computed(() =>
  (props.name || props.channelId || key.value || '?').trim().slice(0, 1).toUpperCase(),
);

const boxStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  borderRadius: `${props.radius}px`,
  color: tint.value.color,
  background: tint.value.bg,
}));
</script>

<template>
  <span class="ci" :style="boxStyle">
    <img
      v-if="svgIcon || iconUrl"
      class="ci-img"
      :src="svgIcon || iconUrl"
      :alt="name || platformId"
      :style="{ borderRadius: `${radius}px` }"
    />
    <component
      :is="brandIcon"
      v-else-if="brandIcon"
      class="ci-brand"
      :style="{ fontSize: `${Math.round(size * 0.52)}px` }"
    />
    <span v-else class="ci-letter" :style="{ fontSize: `${Math.round(size * 0.42)}px` }">
      {{ letter }}
    </span>
  </span>
</template>

<style scoped>
.ci {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
  line-height: 1;
}
.ci-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.ci-brand {
  line-height: 1;
}
.ci-letter {
  font-weight: 700;
  line-height: 1;
}
</style>
