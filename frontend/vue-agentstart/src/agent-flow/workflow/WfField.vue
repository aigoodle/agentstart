<script setup lang="ts">
import { computed, ref } from 'vue';

import {
  DownOutlined,
  QuestionCircleOutlined,
  RightOutlined,
} from '@ant-design/icons-vue';

/**
 * WfField —— 对标 Dify 的 Field 组件
 * 统一提供：title / tooltip / operations（右上角操作区）/ children 四部分
 * 减少 nodeCard 里重复的 `.wf-config-section-title` 结构
 *
 * @example
 * <WfField title="模型" required :warning="hasWarning">
 *   <ModelPickerPopover v-model="formState.model" />
 *   <template #operations>
 *     <a-button size="small">使用默认</a-button>
 *   </template>
 *   <template #tooltip>选择要调用的 LLM 模型</template>
 * </WfField>
 */

interface Props {
  title?: string;
  /** 副标题时字号收敛 */
  isSubtitle?: boolean;
  /** 是否必填（标题后加 *） */
  required?: boolean;
  /** 是否显示警告点（标题后加红点） */
  warning?: boolean;
  /** 支持折叠 */
  foldable?: boolean;
  /** 折叠默认状态 */
  defaultFold?: boolean;
  /** 内联布局：title 左、children 右（不占单独行） */
  inline?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  title: '',
  isSubtitle: false,
  required: false,
  warning: false,
  foldable: false,
  defaultFold: false,
  inline: false,
});

const folded = ref(props.defaultFold);
const showBody = computed(() => !props.foldable || !folded.value);

const toggleFold = () => {
  if (props.foldable) folded.value = !folded.value;
};
</script>

<template>
  <div class="wf-field" :class="{ 'wf-field-inline': inline }">
    <div
      v-if="title || $slots.title || $slots.operations || $slots.tooltip"
      class="wf-field-header"
      :class="{ 'wf-field-header-clickable': foldable }"
      @click="toggleFold"
    >
      <div class="wf-field-title-wrap">
        <RightOutlined
          v-if="foldable && folded"
          class="wf-field-fold-icon"
        />
        <DownOutlined
          v-else-if="foldable"
          class="wf-field-fold-icon"
        />
        <div
          class="wf-field-title"
          :class="{ 'wf-field-title-sub': isSubtitle }"
        >
          <slot name="title">{{ title }}</slot>
          <span v-if="required" class="wf-field-required">*</span>
          <span v-if="warning" class="wf-field-warning-dot" />
          <a-tooltip v-if="$slots.tooltip">
            <template #title>
              <slot name="tooltip" />
            </template>
            <QuestionCircleOutlined class="wf-field-help-icon" />
          </a-tooltip>
        </div>
      </div>
      <div v-if="$slots.operations" class="wf-field-operations" @click.stop>
        <slot name="operations" />
      </div>
    </div>
    <div v-if="showBody" class="wf-field-body" :class="{ 'wf-field-body-titled': title || $slots.title }">
      <slot />
    </div>
    <div v-if="$slots.hint && showBody" class="wf-field-hint">
      <slot name="hint" />
    </div>
  </div>
</template>

<style scoped>
.wf-field {
  display: flex;
  flex-direction: column;
}

.wf-field + .wf-field {
  margin-top: 12px;
}

.wf-field-inline {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.wf-field-inline .wf-field-body {
  flex: 0 1 auto;
}

.wf-field-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 20px;
}

.wf-field-header-clickable {
  cursor: pointer;
  user-select: none;
}

.wf-field-title-wrap {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.wf-field-fold-icon {
  font-size: 10px;
  color: #9ca3af;
}

.wf-field-title {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.4;
  color: #1f2937;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.wf-field-title-sub {
  font-size: 11px;
  color: #4b5563;
}

.wf-field-required {
  color: #ef4444;
  font-weight: 700;
}

.wf-field-warning-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  background: #ef4444;
  border-radius: 50%;
}

.wf-field-help-icon {
  font-size: 12px;
  color: #9ca3af;
  cursor: help;
}

.wf-field-help-icon:hover {
  color: #4b5563;
}

.wf-field-operations {
  display: flex;
  align-items: center;
  gap: 4px;
}

.wf-field-body-titled {
  margin-top: 6px;
}

.wf-field-hint {
  margin-top: 4px;
  font-size: 11px;
  line-height: 1.5;
  color: #9ca3af;
}
</style>
