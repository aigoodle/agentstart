import { DOMWrapper, flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import GroupedModelSelect from './GroupedModelSelect.vue';

describe('GroupedModelSelect', () => {
  it('searches models in a portaled popover and emits the original model id', async () => {
    const wrapper = mount(GroupedModelSelect, { attachTo: document.body, props: {
      modelValue: 'model-1', options: [
        { id: 'model-1', label: 'gpt-4o', providerName: 'openai' },
        { id: 'model-2', label: 'qwen-max', providerName: 'dashscope' },
      ],
    } });
    try {
      await wrapper.find('.gms-trigger').trigger('click');
      await flushPromises();
      const body = new DOMWrapper(document.body);
      expect(body.find('[role="dialog"]').exists()).toBe(true);
      await body.find('input').setValue('qwen');
      expect(body.findAll('.gms-item')).toHaveLength(1);
      await body.find('.gms-item').trigger('click');
      expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['model-2']);
      expect(wrapper.emitted('change')?.at(-1)).toEqual(['model-2']);
      await flushPromises();
      expect(document.querySelector('.gms-panel')).toBeNull();
    } finally { wrapper.unmount(); }
  });
});
