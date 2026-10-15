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
      document.activeElement!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
      await flushPromises();
      expect(childOpen.value).toBe(false);
      expect(parentOpen.value).toBe(true);
      expect(document.activeElement?.textContent).toContain('parent');
    } finally { wrapper.unmount(); }
  });
});


describe('Modal Dialog compatibility', () => {
  it('preserves HUD chrome, title slot, width and footer while providing an accessible title', async () => {
    const wrapper = mount(Modal, {
      props: { visible: true, width: 680, footer: false, closable: false },
      slots: { title: () => h('span', 'Custom title'), default: () => h('input') },
      attachTo: document.body,
    });
    try {
      await flushPromises();
      const panel = document.querySelector<HTMLElement>('.as-modal')!;
      expect(panel.getAttribute('data-state')).toBe('open');
      expect(panel.style.width).toBe('680px');
      expect(document.getElementById(panel.getAttribute('aria-labelledby')!)?.textContent).toBe('Custom title');
      expect(panel.querySelectorAll('.as-hud-modal__corner')).toHaveLength(4);
      expect(panel.querySelectorAll('.as-hud-modal__rail')).toHaveLength(4);
      expect(panel.querySelector('.as-modal__footer')).toBeNull();
      expect(panel.querySelector('.as-modal__close')).toBeNull();
    } finally { wrapper.unmount(); }
  });

  it('respects keyboard and maskClosable and retains cancellation events', async () => {
    const wrapper = mount(Modal, {
      props: { open: true, keyboard: false, maskClosable: false },
      attachTo: document.body,
    });
    try {
      await flushPromises();
      document.activeElement!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
      await flushPromises();
      expect(wrapper.emitted('cancel')).toBeUndefined();
      // Reka installs outside-pointer listeners on the next task.
      await new Promise(resolve => setTimeout(resolve, 10));
      const mask = document.querySelector('.as-modal__mask')!;
      (mask as HTMLElement).click();
      await flushPromises();
      expect(wrapper.emitted('cancel')).toBeUndefined();
      await wrapper.setProps({ maskClosable: true });
      (mask as HTMLElement).click();
      await flushPromises();
      expect(wrapper.emitted('cancel')).toHaveLength(1);
      expect(wrapper.emitted('update:open')).toEqual([[false]]);
      expect(wrapper.emitted('update:visible')).toEqual([[false]]);
    } finally { wrapper.unmount(); }
  });

  it('traps focus in the dialog and restores the opening control on close', async () => {
    await new Promise(resolve => setTimeout(resolve, 10));
    const opener = document.createElement('button');
    document.body.append(opener);
    opener.focus();
    const wrapper = mount(Modal, { props: { open: true, title: 'Focus' }, attachTo: document.body });
    try {
      await flushPromises();
      opener.focus();
      await flushPromises();
      expect(document.querySelector('.as-modal')!.contains(document.activeElement)).toBe(true);
      await wrapper.setProps({ open: false });
      await flushPromises();
      expect(document.activeElement).toBe(opener);
    } finally { wrapper.unmount(); opener.remove(); }
  });
});
