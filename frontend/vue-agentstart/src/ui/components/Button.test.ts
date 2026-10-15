import { mount } from '@vue/test-utils';
import { h } from 'vue';
import { describe, expect, it } from 'vitest';
import { PlusOutlined } from '@ant-design/icons-vue';
import { Button } from './Button';
import EndNodeCard from '../../agent-flow/workflow/nodeCard/EndNodeCard.vue';

describe('small icon buttons', () => {
  it('renders the workflow add action as a compact button and preserves its click', async () => {
    let clicks = 0;
    const wrapper = mount(Button, {
      props: { size: 'small', type: 'primary', icon: h(PlusOutlined) },
      attrs: { onClick: () => clicks++ },
    });
    const button = wrapper.get('button');
    expect(button.classes()).toContain('size-6');
    expect(button.classes()).toContain('p-0');
    expect(button.classes()).not.toContain('px-3');
    await button.trigger('click');
    expect(clicks).toBe(1);
  });

  it('applies the compact size to the end node output add button', () => {
    const wrapper = mount(EndNodeCard, {
      props: { modelValue: { output: [] } },
    });
    const button = wrapper.get('.wf-field-operations button');
    expect(button.classes()).toContain('size-6');
    expect(button.classes()).toContain('p-0');
  });

  it('keeps text buttons at the standard small size', () => {
    const wrapper = mount(Button, {
      props: { size: 'small', icon: h(PlusOutlined) },
      slots: { default: '添加字段' },
    });
    expect(wrapper.get('button').classes()).toContain('h-8');
    expect(wrapper.get('button').classes()).not.toContain('size-6');
  });
});
