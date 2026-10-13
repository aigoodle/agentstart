<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';

import { Input } from '../../ui';

interface Parameter {
  name: string;
  type: string;
  description?: string;
}

interface Props {
  parameters?: Parameter[];
  visible?: boolean;
  position?: { x: number; y: number };
}

const props = withDefaults(defineProps<Props>(), {
  parameters: () => [],
  visible: false,
  position: () => ({ x: 0, y: 0 }),
});

const emit = defineEmits<{
  close: [];
  parameterSelect: [parameter: string];
}>();

const searchKeyword = ref('');
const quickPanelInputRef = ref<HTMLInputElement>();

// 过滤后的参数列表
const filteredParameters = computed(() => {
  if (!searchKeyword.value) {
    return props.parameters;
  }
  return props.parameters.filter(
    (param) =>
      param.name?.toLowerCase().includes(searchKeyword.value.toLowerCase()) ||
      param.type?.toLowerCase().includes(searchKeyword.value.toLowerCase()),
  );
});

// 选择参数
const selectParameter = (parameter: any) => {
  emit('parameterSelect', parameter);
};

// 关闭面板
const closePanel = () => {
  searchKeyword.value = '';
  emit('close');
};

// 当面板显示时聚焦搜索框
const focusSearch = () => {
  nextTick(() => {
    quickPanelInputRef.value?.focus();
  });
};

// 监听面板显示状态
watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      searchKeyword.value = '';
      focusSearch();
    }
  },
);

defineExpose({
  focusSearch,
});
</script>

<template>
  <div
    v-if="visible"
    class="quick-panel fixed z-50 rounded-lg border border-gray-200 bg-white shadow-xl"
    :style="{
      left: `${position.x}px`,
      top: `${position.y}px`,
      transform: 'translateX(-50%)',
      minWidth: '320px',
      maxWidth: '400px',
    }"
  >
    <div class="p-4">
      <!-- 搜索框 -->
      <div class="mb-3">
        <Input
          ref="quickPanelInputRef"
          v-model:value="searchKeyword"
          placeholder="🔍 搜索参数..."
          size="small"
          class="rounded-md border-gray-300 focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
          @keydown.enter.prevent
          @keydown.escape="closePanel"
        />
      </div>

      <!-- 参数列表 -->
      <div class="max-h-64 overflow-y-auto">
        <div
          v-if="filteredParameters.length === 0"
          class="flex flex-col items-center justify-center py-8 text-gray-500"
        >
          <div class="mb-2 text-3xl">📝</div>
          <div class="text-sm">
            {{ searchKeyword ? '未找到匹配的参数' : '暂无参数' }}
          </div>
        </div>
        <div v-else class="space-y-1">
          <div
            v-for="parameter in filteredParameters"
            :key="parameter.name"
            class="group flex cursor-pointer items-center justify-between rounded-lg border border-transparent p-3 transition-all duration-200 hover:border-blue-200 hover:bg-blue-50"
            @click="selectParameter(parameter)"
            @mousedown.prevent
          >
            <div class="flex-1">
              <div class="flex items-center space-x-2">
                <div
                  class="text-sm font-semibold text-gray-800 group-hover:text-blue-700"
                >
                  {{ parameter.name }}
                </div>
                <div
                  class="rounded-full bg-gray-100 px-2 py-1 text-xs text-gray-600 group-hover:bg-blue-100 group-hover:text-blue-600"
                >
                  {{ parameter.type }}
                </div>
              </div>
              <div
                v-if="parameter.description"
                class="mt-1 text-xs text-gray-500"
              >
                {{ parameter.description }}
              </div>
            </div>
            <div class="ml-2 text-xs text-gray-400 group-hover:text-blue-500">
              ↩ 插入
            </div>
          </div>
        </div>
      </div>

      <!-- 提示信息 -->
      <div
        class="mt-4 flex items-center justify-between border-t border-gray-100 pt-3 text-xs text-gray-400"
      >
        <span>按 "/" 快速打开面板</span>
        <span>按 "Esc" 关闭</span>
      </div>
    </div>
  </div>
</template>

<style scoped></style>
