import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent, h, ref } from 'vue';
import { describe, expect, it } from 'vitest';
import { Modal } from './Modal';

const dialogs = () => Array.from(document.querySelectorAll<HTMLElement>('.as-modal-root'));
const layerOf = (title: string) => Number(dialogs().find(root => root.textContent?.includes(title))!.style.zIndex);

describe('Modal stacking', () => {
  it('assigns layers by opening order, including initially open dialogs and reopened dialogs', async () => {
    // Create the child first so DOM/component order cannot determine stacking.
    const child = mount(Modal, { props: { open: false, title: 'child' }, attachTo: document.body });
    const parent = mount(Modal, { props: { open: true, title: 'parent' }, attachTo: document.body });
    try {
      await flushPromises();
      await child.setProps({ open: true });
      expect(layerOf('child')).toBeGreaterThan(layerOf('parent'));
      await child.setProps({ open: false });
      expect(document.activeElement?.textContent).toContain('parent');
      await child.setProps({ open: true });
      expect(layerOf('child')).toBeGreaterThan(layerOf('parent'));
      // Closing a background dialog must not steal focus from the child.
      await parent.setProps({ open: false });
      expect(document.activeElement?.textContent).toContain('child');
    } finally { child.unmount(); parent.unmount(); }
  });

  it('Escape closes only the top dialog when teleported modals are nested', async () => {
    const parentOpen = ref(true);
    const childOpen = ref(true);
    const wrapper = mount(defineComponent({
      setup: () => () => h(Modal, {
        open: parentOpen.value, title: 'parent',
        onCancel: () => { parentOpen.value = false; },
      }, { default: () => h(Modal, {
        open: childOpen.value, title: 'child',
        onCancel: () => { childOpen.value = false; },
      }) }),
    }), { attachTo: document.body });
    try {
      await flushPromises();
      expect(layerOf('child')).toBeGreaterThan(layerOf('parent'));
      document.activeElement!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      await flushPromises();
      expect(childOpen.value).toBe(false);
      expect(parentOpen.value).toBe(true);
      expect(document.activeElement?.textContent).toContain('parent');
    } finally { wrapper.unmount(); }
  });
});
