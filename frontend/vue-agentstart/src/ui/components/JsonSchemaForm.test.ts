import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import JsonSchemaForm from './JsonSchemaForm.vue';

describe('JsonSchemaForm', () => {
  it('renders a capability-filtered model selector and emits the selected model object', async () => {
    const wrapper = mount(JsonSchemaForm, {
      props: {
        schema: { type: 'object', properties: { model: { type: 'object' } } },
        uiSchema: { fields: { model: { widget: 'model-selector', modelType: 'VIDEO' } } },
      },
      global: { stubs: { ModelPickerPopover: { name: 'ModelPickerPopover', props: ['modelType'], template: '<button>{{ modelType }}</button>' } } },
    });
    const picker = wrapper.findComponent({ name: 'ModelPickerPopover' });
    expect(picker.props('modelType')).toBe('VIDEO');
    const selected = { modelId: 'video-model', providerName: 'qwen', modelName: 'wan2.6-t2v' };
    picker.vm.$emit('update:modelValue', selected);
    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toEqual({ model: selected });
  });
  it('renders schema fields and emits typed values', async () => {
    const wrapper = mount(JsonSchemaForm, { props: { schema: JSON.stringify({ type: 'object', properties: { retries: { type: 'integer', title: '重试次数' } } }) } });
    expect(wrapper.text()).toContain('重试次数');
    await wrapper.get('input[type="number"]').setValue('3');
    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toEqual({ retries: 3 });
  });

  it('masks only fields explicitly marked write-only', () => {
    const wrapper = mount(JsonSchemaForm, {
      props: {
        schema: {
          type: 'object',
          properties: {
            appId: { type: 'string', title: 'App ID' },
            clientSecret: { type: 'string', title: 'Client Secret', writeOnly: true },
          },
        },
      },
    });
    const inputs = wrapper.findAll('input');
    expect(inputs[0]?.attributes('type')).toBe('text');
    expect(inputs[1]?.attributes('type')).toBe('password');
  });

  it('does not force workflow users to edit raw JSON when no fields are declared', () => {
    const wrapper = mount(JsonSchemaForm, {
      props: {
        schema: JSON.stringify({ type: 'object', additionalProperties: true }),
        allowAdvanced: false,
        emptyText: '连接器未声明字段',
      },
    });
    expect(wrapper.text()).toContain('连接器未声明字段');
    expect(wrapper.find('textarea').exists()).toBe(false);
  });
});
