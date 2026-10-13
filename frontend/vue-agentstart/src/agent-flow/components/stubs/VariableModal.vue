<script setup lang="ts">
import { reactive, ref } from 'vue';
import { Modal } from '../../../ui';

/**
 * 输入变量编辑模态框
 *
 * 这是 @agent-start/agent-flow 内置的基础实现，覆盖了新增/编辑一个变量的
 * 常见字段（name / label / type / required / description / default）。
 * 宿主项目可通过 `app.component('VariableModal', RealVariableModal)`
 * 全局替换为自己更强大的实现。
 *
 * 用法：`variableModalRef.value.showModal(item?)` —— 编辑传 item、新增
 * 传 undefined / {}。之前用 :main-data prop + 同步 showModal() 的写法会
 * 撞上 Vue 的父→子 prop 传递需要一个 tick 才刷新的时序问题，导致：
 *   · 第一次点编辑：表单不带出数据（读到的还是初始 prop）
 *   · 再点一次：终于带出上一次的数据
 *   · 第一次点新增：错带出上一次编辑/新增的数据
 * 现在直接把要展示的对象作为参数传进来，就地写进 form，避免绕一圈 prop
 * 更新，多快好省。
 */

const TYPES = [
  { value: 'string', label: 'String' },
  { value: 'number', label: 'Number' },
  { value: 'boolean', label: 'Boolean' },
  { value: 'array', label: 'Array' },
  { value: 'object', label: 'Object' },
  { value: 'file', label: 'File' },
];

// 保留 mainData prop 只为向后兼容宿主项目里可能存在的老用法；showModal(item)
// 传参会覆盖它。新代码不要再依赖 :main-data。
const props = defineProps<{ mainData?: any }>();
const emit = defineEmits<{ (e: 'formSubmit', v: any): void }>();

const open = ref(false);
const form = reactive<any>({
  id: undefined,
  name: '',
  label: '',
  type: 'string',
  required: false,
  description: '',
  defaultValue: '',
});

function resetForm(src: any) {
  form.id = src.id;
  form.name = src.name || '';
  form.label = src.label || '';
  form.type = src.type || 'string';
  form.required = !!src.required;
  form.description = src.description || '';
  form.defaultValue = src.defaultValue ?? '';
}

function showModal(item?: any) {
  // 优先使用调用方直接传入的 item，回退到 mainData（老 API），最后 {}。
  const src = item ?? props.mainData ?? {};
  resetForm(src);
  open.value = true;
}

function hideModal() {
  open.value = false;
}

function submit() {
  emit('formSubmit', { ...form });
  open.value = false;
}

defineExpose({ showModal, hideModal });
</script>

<template>
  <Modal
    v-model:open="open"
    :mask-closable="false"
    :title="form.id ? '编辑输入变量' : '新增输入变量'"
    width="480px"
    :ok-text="form.id ? '保存' : '添加'"
    cancel-text="取消"
    @ok="submit"
  >
    <a-form
      layout="vertical"
      :model="form"
      autocomplete="off"
      style="padding-top: 8px"
    >
      <a-form-item label="变量名" required>
        <a-input v-model:value="form.name" placeholder="例如 query" />
      </a-form-item>
      <a-form-item label="显示名">
        <a-input v-model:value="form.label" placeholder="用户看到的中文标题" />
      </a-form-item>
      <a-form-item label="类型">
        <a-select v-model:value="form.type" :options="TYPES" />
      </a-form-item>
      <a-form-item label="默认值">
        <a-input v-model:value="form.defaultValue" placeholder="留空表示无默认值" />
      </a-form-item>
      <a-form-item label="描述">
        <a-textarea
          v-model:value="form.description"
          placeholder="给填写者的说明"
          :auto-size="{ minRows: 2, maxRows: 4 }"
        />
      </a-form-item>
      <a-form-item>
        <a-checkbox v-model:checked="form.required">必填</a-checkbox>
      </a-form-item>
    </a-form>
  </Modal>
</template>
