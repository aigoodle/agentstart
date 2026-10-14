import { flushPromises, mount } from '@vue/test-utils';
import { h } from 'vue';
import { describe, expect, it, vi } from 'vitest';
import { Tooltip } from './Tooltip';

describe('Tooltip', () => {
  it('escapes clipping ancestors, flips at the top edge and follows scrolling', async () => {
    const wrapper = mount(Tooltip, {
      props: { title: '保存草稿' }, slots: { default: () => h('button', '保存') }, attachTo: document.body,
    });
    let top = 2;
    vi.spyOn(wrapper.element, 'getBoundingClientRect').mockImplementation(() => ({
      left: 100, right: 140, top, bottom: top + 24, width: 40, height: 24,
    } as DOMRect));
    try {
      await wrapper.trigger('mouseenter');
      await flushPromises();
      const tip = document.querySelector<HTMLElement>('[role="tooltip"]')!;
      expect(tip.parentElement).toBe(document.body);
      expect(wrapper.element.contains(tip)).toBe(false);
      expect(tip.textContent).toBe('保存草稿');
      expect(tip.style.top).toBe('34px');
      top = 100;
      window.dispatchEvent(new Event('scroll'));
      await flushPromises();
      expect(tip.style.top).toBe('92px');
      await wrapper.trigger('mouseleave');
      expect(document.querySelector('[role="tooltip"]')).toBeNull();
    } finally { wrapper.unmount(); vi.restoreAllMocks(); }
  });

  it('shows on keyboard focus, dismisses on Escape and cleans up on unmount', async () => {
    const wrapper = mount(Tooltip, {
      props: { title: '放大' }, slots: { default: () => h('button', '+') }, attachTo: document.body,
    });
    try {
      await wrapper.trigger('focusin');
      await flushPromises();
      expect(wrapper.attributes('aria-describedby')).toBe(document.querySelector('[role="tooltip"]')?.id);
      await wrapper.trigger('keydown', { key: 'Escape' });
      expect(document.querySelector('[role="tooltip"]')).toBeNull();
      await wrapper.trigger('focusin');
    } finally { wrapper.unmount(); }
    expect(document.querySelector('[role="tooltip"]')).toBeNull();
  });
});
