<script setup lang="ts">
// @ts-nocheck — legacy flow-designer code, pre-dates strict typing; cleanup tracked separately.
import { onMounted, ref, watch } from 'vue';

import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons-vue';

const emit = defineEmits(['update']);

const visible = ref<boolean>(false);
const model: any = defineModel();
const formState: any = ref({});

function treeDataToSchema(treeData: any) {
  const result: any = {
    type: 'object',
    properties: {},
    required: [],
    additionalProperties: false,
  };

  treeData.forEach((item: any) => {
    const property: any = {
      type: item.type,
      description: item.description,
      label: item.description,
    };

    if (item.type === 'object' && item.children) {
      property.additionalProperties = false;
      property.properties =
        item.children.length > 0
          ? treeDataToSchema(item.children).properties
          : {};
      property.required = [];
    }
    result.properties[item.name] = property;
  });

  return result;
}

function schemaToTreeData(schema: any): any[] {
  if (!schema.properties) return [];

  return Object.entries(schema.properties).map(
    ([name, prop]: [string, any]) => {
      const item: any = {
        description: prop.description || '',
        name,
        type: prop.type,
      };

      if (prop.type === 'object' && prop.properties) {
        item.children = schemaToTreeData(prop);
      }

      return item;
    },
  );
}

const treeData = ref([
  {
    description: '分类',
    name: 'category',
    type: 'string',
  },
  {
    description: '意图',
    name: 'intention',
    type: 'string',
  },

  {
    description: '参数',
    name: 'parameter',
    type: 'object',
    children: [],
  },
]);

const toAddItem = (item: any) => {
  visible.value = true;
};
const toEditItem = (item: any) => {
  formState.value = { ...item };
  visible.value = true;
};
const removeItem = (item: any) => {};
const submitForm = () => {
  visible.value = false;
};
const closePanel = () => {
  visible.value = false;
};

// 监听 model -> 更新 treeData
watch(
  model,
  (newData) => {
    treeData.value = schemaToTreeData(newData);
  },
  { deep: true },
);

watch(
  treeData,
  (newData) => {
    emit('update', newData);
    model.value = treeDataToSchema(newData);
  },
  { deep: true },
);

onMounted(() => {
  emit('update', treeData.value);
  // model.value = treeDataToSchema(treeData.value);
});
</script>

<template>
  <div>
    <a-table
      size="small"
      :data-source="treeData"
      :row-selection="rowSelection"
      :pagination="false"
    >
      <a-table-column data-index="name" title="名称" />
      <a-table-column data-index="description" title="描述" />
      <a-table-column data-index="type" title="类型" />
      <a-table-column data-index="action">
        <template #title>
          <span>操作</span>
          <a class="vben-link" @click="toAddItem">
            <PlusOutlined />
          </a>
        </template>
      </a-table-column>
      <template #bodyCell="{ column, text, record }">
        <template v-if="column.dataIndex === 'name'">
          <a class="vben-link" @click="toEditItem(record)">
            {{ text }}
          </a>
        </template>
        <template v-if="column.dataIndex === 'action'">
          <div class="flex gap-2">
            <a-popconfirm title="确定要删除吗?" @confirm="removeItem(record)">
              <a class="vben-link text-red-800">
                <MinusCircleOutlined />
              </a>
            </a-popconfirm>
            <a
              class="vben-link"
              v-if="record.type === 'object'"
              @click="toAddItem(record)"
            >
              <PlusOutlined />
            </a>
          </div>
        </template>
      </template>
    </a-table>
    <div class="w-full text-center">
      <a-popover v-model:open="visible" title="结构化数据定义" trigger="click">
        <template #content>
          <div>
            <a-form
              :model="formState"
              name="basic"
              :label-col="{ span: 6 }"
              :wrapper-col="{ span: 16 }"
              autocomplete="off"
              layout="horizontal"
            >
              <a-form-item label="名称" name="name">
                <a-input v-model:value="formState.name" />
              </a-form-item>
              <a-form-item label="描述" name="modelId">
                <a-input v-model:value="formState.description" />
              </a-form-item>
              <a-form-item label="类型" name="type">
                <a-input v-model:value="formState.type" />
              </a-form-item>
              <a-form-item label="示例" name="type">
                <a-input v-model:value="formState.example" />
              </a-form-item>
            </a-form>
            <div class="flex justify-end gap-2 pb-2 text-right">
              <a-button size="small" @click="closePanel"> 关闭 </a-button>
              <a-button type="primary" size="small" @click="submitForm">
                确定
              </a-button>
            </div>
          </div>
        </template>
        <a-button />
      </a-popover>
    </div>
  </div>
</template>

<style scoped></style>
