<script setup lang="ts">
/**
 * 编排 tab body. Two-column: left = editor sections stack, right = debug slot.
 * Above both, a toolbar with:
 *   - "编排" heading (implicit — nav shows it)
 *   - right cluster: [Agent 设置] [模型选择] [发布]
 *
 * The individual sections (提示词, 变量, 知识库, 元数据过滤, 工具, 视觉) can be
 * driven via props, or the host can drop its own via the default slot to fully
 * customize. Both flows are supported so old callers can stay declarative.
 */
import ModelPickerPopover from '../../provider-hub/components/ModelPickerPopover.vue';
import type { SelectedModel } from '../../provider-hub/types';

import type {
  PromptVariable,
  StudioKnowledge,
  StudioModelOption,
  StudioTool,
} from '../types';
import AgentPromptEditor from './AgentPromptEditor.vue';
import AgentToolsPanel from './AgentToolsPanel.vue';
import AgentVariablesPanel from './AgentVariablesPanel.vue';

interface Props {
  prompt: string;
  variables: PromptVariable[];
  attachedTools: string[];
  availableTools?: StudioTool[];
  knowledge?: StudioKnowledge[];
  visionEnabled?: boolean;
  metadataFilter?: string;
  /**
   * @deprecated Legacy dropdown data — the top-right picker now uses
   * {@link ModelPickerPopover} which pulls the catalog itself. Kept only so
   * existing declarative callers still compile.
   */
  models?: StudioModelOption[];
  /** Current selection surfaced by the popover picker. */
  modelSelection?: SelectedModel;
  /** Multi-tenant deployments pass this through to the picker's data fetch. */
  tenantId?: string;
  /** Toggle the "发布" button loading state. */
  publishing?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  availableTools: () => [],
  knowledge: () => [],
  visionEnabled: false,
  metadataFilter: '禁用',
  models: () => [],
  modelSelection: () => ({}),
  publishing: false,
});

const emit = defineEmits<{
  (e: 'update:prompt', v: string): void;
  (e: 'update:variables', v: PromptVariable[]): void;
  (e: 'update:attachedTools', v: string[]): void;
  (e: 'update:knowledge', v: StudioKnowledge[]): void;
  (e: 'update:visionEnabled', v: boolean): void;
  (e: 'update:metadataFilter', v: string): void;
  (e: 'update:modelSelection', v: SelectedModel): void;
  (e: 'publish'): void;
  (e: 'openSettings'): void;
  (e: 'addKnowledge'): void;
  (e: 'generatePrompt'): void;
}>();

function onPickModel(sel: SelectedModel) {
  emit('update:modelSelection', sel);
}

function removeKnowledge(id: string) {
  emit(
    'update:knowledge',
    props.knowledge.filter((k) => k.id !== id),
  );
}
</script>

<template>
  <div class="orch">
    <!-- Toolbar -->
    <header class="orch-top">
      <h2 class="orch-top-title">编排</h2>
      <div class="orch-top-actions">
        <button
          type="button"
          class="orch-btn orch-btn-ghost"
          @click="emit('openSettings')"
        >
          ⚙ Agent 设置
        </button>
        <div class="orch-model">
          <ModelPickerPopover
            :model-value="modelSelection"
            model-type="LLM"
            :tenant-id="tenantId"
            placement="bottomRight"
            :width="380"
            placeholder="选择模型"
            @update:model-value="onPickModel"
          />
        </div>
        <button
          type="button"
          class="orch-btn orch-btn-primary"
          :disabled="publishing"
          @click="emit('publish')"
        >
          {{ publishing ? '发布中...' : '发布' }}
        </button>
      </div>
    </header>

    <!-- Two-column body -->
    <div class="orch-body">
      <!-- LEFT: editor sections -->
      <section class="orch-left">
        <slot name="prompt">
          <AgentPromptEditor
            :model-value="prompt"
            :variables="variables"
            @update:model-value="emit('update:prompt', $event)"
            @generate="emit('generatePrompt')"
          />
        </slot>

        <slot name="variables">
          <AgentVariablesPanel
            :list="variables"
            @update:list="emit('update:variables', $event)"
          />
        </slot>

        <!-- 知识库 -->
        <slot name="knowledge">
          <div class="orch-card">
            <div class="orch-card-head">
              <div class="orch-card-title">
                知识库
                <span class="orch-help" title="给模型接入知识库作为上下文">
                  ⓘ
                </span>
              </div>
              <button
                type="button"
                class="orch-card-btn"
                @click="emit('addKnowledge')"
              >
                + 添加
              </button>
            </div>
            <div v-if="knowledge.length === 0" class="orch-hint">
              你可以让模型引用知识库作为上下文。
            </div>
            <div v-else class="orch-know-list">
              <div
                v-for="k in knowledge"
                :key="k.id"
                class="orch-know-row"
              >
                <span class="orch-know-icon">📚</span>
                <span class="orch-know-name">{{ k.name }}</span>
                <span v-if="k.documentCount != null" class="orch-know-count">
                  {{ k.documentCount }} 文档
                </span>
                <button
                  type="button"
                  class="orch-know-x"
                  @click="removeKnowledge(k.id)"
                >
                  ×
                </button>
              </div>
            </div>
          </div>
        </slot>

        <!-- 元数据过滤 (inline row, matches Dify) -->
        <div class="orch-inline-card">
          <div class="orch-inline-label">
            元数据过滤
            <span class="orch-help" title="限制检索到的文档必须匹配指定元数据">
              ⓘ
            </span>
          </div>
          <select
            class="orch-inline-select"
            :value="metadataFilter"
            @change="emit('update:metadataFilter', ($event.target as HTMLSelectElement).value)"
          >
            <option value="禁用">禁用</option>
            <option value="自动">自动</option>
            <option value="手动">手动</option>
          </select>
        </div>

        <!-- 工具 -->
        <slot name="tools">
          <AgentToolsPanel
            :attached="attachedTools"
            :available="availableTools"
            @update:attached="emit('update:attachedTools', $event)"
          />
        </slot>

        <!-- 视觉 -->
        <div class="orch-inline-card">
          <div class="orch-inline-label">
            <span class="orch-vision-icon">👁</span>
            视觉
            <span class="orch-help" title="允许模型识别用户上传的图片">
              ⓘ
            </span>
          </div>
          <div class="orch-inline-right">
            <button type="button" class="orch-inline-ghost">⚙ 设置</button>
            <label class="orch-switch">
              <input
                type="checkbox"
                :checked="visionEnabled"
                @change="emit('update:visionEnabled', ($event.target as HTMLInputElement).checked)"
              />
              <span class="orch-switch-slider" />
            </label>
          </div>
        </div>
      </section>

      <!-- RIGHT: debug slot -->
      <aside class="orch-right">
        <slot name="debug" />
      </aside>
    </div>
  </div>
</template>

<style scoped>
.orch {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

/* Toolbar */
.orch-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
}
.orch-top-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
}
.orch-top-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.orch-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  font-size: 12px;
  color: #475569;
  cursor: pointer;
  transition: background 0.15s;
}
.orch-btn:hover:not(:disabled) {
  background: #f1f5f9;
}
.orch-btn-primary {
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  border-color: transparent;
  color: #fff;
  font-weight: 500;
}
.orch-btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #4f46e5, #4338ca);
}
.orch-btn-primary:disabled {
  background: #cbd5e1;
  cursor: not-allowed;
}
.orch-btn-ghost {
  background: transparent;
}

/* Model picker — 复用 provider-hub 的 ModelPickerPopover，触发块尺寸对齐 toolbar */
.orch-model {
  min-width: 240px;
  max-width: 320px;
}
.orch-model :deep(.ph-mp-trigger) {
  width: 100%;
  min-height: 34px;
  padding: 4px 10px;
}

/* Body columns */
.orch-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 0;
}
.orch-left {
  padding: 16px 20px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.orch-right {
  padding: 16px 16px 16px 4px;
  overflow: hidden;
  min-height: 0;
}

/* Generic card */
.orch-card {
  padding: 12px 14px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
}
.orch-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.orch-card-title {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.orch-card-btn {
  padding: 2px 8px;
  border: none;
  border-radius: 6px;
  background: transparent;
  font-size: 12px;
  color: #4338ca;
  cursor: pointer;
}
.orch-card-btn:hover {
  background: #eef2ff;
}
.orch-help {
  margin-left: 4px;
  color: #94a3b8;
  font-size: 11px;
  cursor: help;
}
.orch-hint {
  font-size: 12px;
  color: #94a3b8;
}
.orch-know-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.orch-know-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  background: #f8fafc;
  border-radius: 8px;
  font-size: 12px;
}
.orch-know-icon {
  font-size: 14px;
}
.orch-know-name {
  color: #0f172a;
  font-weight: 500;
}
.orch-know-count {
  margin-left: 4px;
  color: #94a3b8;
  font-size: 11px;
}
.orch-know-x {
  margin-left: auto;
  padding: 0 6px;
  border: none;
  background: transparent;
  color: #94a3b8;
  font-size: 14px;
  cursor: pointer;
}
.orch-know-x:hover {
  color: #dc2626;
}

/* Inline single-row card */
.orch-inline-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
}
.orch-inline-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.orch-inline-select {
  padding: 3px 8px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #fff;
  font-size: 12px;
  color: #0f172a;
}
.orch-inline-right {
  display: flex;
  align-items: center;
  gap: 6px;
}
.orch-inline-ghost {
  padding: 3px 8px;
  border: none;
  border-radius: 6px;
  background: transparent;
  font-size: 12px;
  color: #64748b;
  cursor: pointer;
}
.orch-inline-ghost:hover {
  background: #f1f5f9;
}
.orch-vision-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  background: #4f46e5;
  border-radius: 4px;
  color: #fff;
  font-size: 10px;
}

/* Switch */
.orch-switch {
  position: relative;
  width: 32px;
  height: 18px;
  display: inline-block;
}
.orch-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}
.orch-switch-slider {
  position: absolute;
  inset: 0;
  background: #cbd5e1;
  border-radius: 999px;
  cursor: pointer;
  transition: background 0.15s;
}
.orch-switch-slider::before {
  content: '';
  position: absolute;
  width: 14px;
  height: 14px;
  top: 2px;
  left: 2px;
  background: #fff;
  border-radius: 50%;
  transition: transform 0.15s;
}
.orch-switch input:checked + .orch-switch-slider {
  background: #4f46e5;
}
.orch-switch input:checked + .orch-switch-slider::before {
  transform: translateX(14px);
}
</style>
