import { flushPromises, mount } from '@vue/test-utils';
import { h } from 'vue';
import { describe, expect, it } from 'vitest';
import { Tooltip } from './Tooltip';

describe('Tooltip', () => {
  it('renders through a portal outside clipping ancestors and closes on pointer leave', async () => {
    const wrapper = mount(Tooltip, {
      props: { title: '保存草稿' }, slots: { default: () => h('button', '保存') }, attachTo: document.body,
    });
    try {
      await wrapper.get('.as-tooltip').trigger('mouseenter');
      await flushPromises();
      const tip = document.querySelector<HTMLElement>('[role="tooltip"]')!;
      expect(document.body.contains(tip)).toBe(true);
      expect(wrapper.element.contains(tip)).toBe(false);
      expect(tip.textContent).toBe('保存草稿');
      expect(document.querySelector('.as-tooltip__panel')).not.toBeNull();
      await wrapper.get('.as-tooltip').trigger('mouseleave');
      await flushPromises();
      expect(document.querySelector('[role="tooltip"]')).toBeNull();
    } finally { wrapper.unmount();  }
  });

  it('shows on keyboard focus, dismisses on Escape and cleans up on unmount', async () => {
    const wrapper = mount(Tooltip, {
      props: { title: '放大' }, slots: { default: () => h('button', '+') }, attachTo: document.body,
    });
    try {
      await wrapper.get('.as-tooltip').trigger('focusin');
      await flushPromises();
      expect(wrapper.get('.as-tooltip').attributes('aria-describedby')).toBe(document.querySelector('[role="tooltip"]')?.id);
      await wrapper.get('.as-tooltip').trigger('keydown', { key: 'Escape' });
      await flushPromises();
      expect(document.querySelector('[role="tooltip"]')).toBeNull();
      await wrapper.get('.as-tooltip').trigger('focusin');
    } finally { wrapper.unmount(); }
    expect(document.querySelector('[role="tooltip"]')).toBeNull();
  });
});
