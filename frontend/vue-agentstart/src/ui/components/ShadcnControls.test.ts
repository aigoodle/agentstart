import { flushPromises, mount, DOMWrapper } from '@vue/test-utils';
import { defineComponent, h, ref } from 'vue';
import { describe, expect, it, vi } from 'vitest';
import { Input as CompatInput } from './FormControls';
import { Input as UiInput } from '../shadcn/input';
import { ShadcnSelect } from './ShadcnSelect';
import { ShadcnBoolean } from './ShadcnBoolean';
import { Drawer } from './Drawer';
import { confirmAction, modalService } from './modalService';
import { Modal } from './Modal';
import { getAgentStartColorForeground } from '../color';

describe('shadcn controls and legacy bridges', () => {
  it('preserves externally supplied text and native numeric change events', async () => {
    const text = mount(UiInput, { props: { value: 'visible-app-id' } });
    const number = mount(UiInput, { props: { value: 4 }, attrs: { type: 'number' } });
    try {
      expect((text.get('input').element as HTMLInputElement).value).toBe('visible-app-id');
      await number.get('input').setValue('3');
      expect((number.emitted('change')?.at(-1)?.[0] as Event).target).toBe(number.get('input').element);
      expect((number.get('input').element as HTMLInputElement).value).toBe('3');
      await text.setProps({ value: 'updated' });
      expect((text.get('input').element as HTMLInputElement).value).toBe('updated');
    } finally { text.unmount(); number.unmount(); }
  });

  it('keeps focus available through the existing Input adapter', async () => {
    const wrapper = mount(CompatInput, { props: { value: 'Search' }, attachTo: document.body });
    try {
      (wrapper.vm as unknown as { focus: () => void }).focus();
      expect(document.activeElement).toBe(wrapper.get('input').element);
    } finally { wrapper.unmount(); }
  });

  it('selects numeric values through the visible keyboard-operated dropdown', async () => {
    const wrapper = mount(ShadcnSelect, { props: { modelValue: 1, options: [{ value: 1, label: 'First' }, { value: 2, label: 'Second' }] }, attachTo: document.body });
    try {
      wrapper.get('[role=combobox]').element.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
      await flushPromises();
      const option = new DOMWrapper(document.body).findAll('[role=option]').find(option => option.text() === 'Second')!;
      expect(option.exists()).toBe(true);
      option.element.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
      await flushPromises();
      expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([2]);
      const event = wrapper.emitted('change')?.at(-1)?.[0] as Event;
      expect((event.target as HTMLSelectElement).value).toBe('2');
    } finally { wrapper.unmount(); }
  });

  it('adds custom tags and clears all selected values', async () => {
    const wrapper = mount(ShadcnSelect, { props: { mode: 'tags', modelValue: ['LLM'], allowClear: true, options: [{ value: 'LLM', label: 'LLM' }] } });
    try {
      await wrapper.get('input').setValue('Custom');
      await wrapper.get('input').trigger('keydown', { key: 'Enter' });
      expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([['LLM', 'Custom']]);
      await wrapper.get('[aria-label="清除选择"]').trigger('click');
      expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([[]]);
    } finally { wrapper.unmount(); }
  });

  it('changes a checkbox model without submitting its surrounding form', async () => {
    const wrapper = mount(ShadcnBoolean, { props: { modelValue: false }, attrs: { type: 'checkbox' } });
    try {
      const control = wrapper.get('[role=checkbox]');
      expect(control.attributes('type')).toBe('button');
      await control.trigger('click');
      expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([true]);
      expect(((wrapper.emitted('change')?.at(-1)?.[0] as Event).target as HTMLInputElement).checked).toBe(true);
    } finally { wrapper.unmount(); }
  });

  it('shares one layer stack between Sheet drawers and nested Dialog modals', async () => {
    const drawer = ref(true), modal = ref(false);
    const wrapper = mount(defineComponent({ setup: () => () => h(Drawer, { open: drawer.value, title: 'Drawer', onClose: () => { drawer.value = false; } },
      () => [h('button', { onClick: () => { modal.value = true; } }, 'Open nested'), h(Modal, { open: modal.value, title: 'Nested', onCancel: () => { modal.value = false; } })]),
    }), { attachTo: document.body });
    try {
      await flushPromises();
      document.querySelector<HTMLButtonElement>('[data-slot=sheet-content] button')!.click();
      await flushPromises();
      expect(Number((document.querySelector('.as-modal-root') as HTMLElement).style.zIndex)).toBeGreaterThan(Number((document.querySelector('[data-slot=sheet-content]') as HTMLElement).style.zIndex));
      document.activeElement!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
      await flushPromises();
      expect(modal.value).toBe(false); expect(drawer.value).toBe(true);
      expect(document.querySelector('[data-slot=sheet-content]')!.contains(document.activeElement)).toBe(true);
    } finally { wrapper.unmount(); }
  });

  it('renders programmatic confirmations with Dialog instead of a native browser prompt', async () => {
    const native = vi.spyOn(window, 'confirm');
    const onOk = vi.fn();
    modalService.confirm({ title: 'Confirm action', content: 'Review before continuing', onOk });
    try {
      await flushPromises();
      expect(document.querySelector('.as-modal')?.textContent).toContain('Review before continuing');
      document.querySelector<HTMLButtonElement>('.as-modal__footer button:last-child')!.click();
      await flushPromises();
      expect(onOk).toHaveBeenCalledOnce();
      expect(native).not.toHaveBeenCalled();
      await new Promise(resolve => setTimeout(resolve, 100));
      expect(document.querySelector('.as-modal-root')).toBeNull();
    } finally { native.mockRestore(); }
  });

  it.each([true, false])('resolves a destructive confirmation only after the user chooses %s', async (confirmed) => {
    const result = confirmAction('删除这个条目？');
    const resolved = vi.fn();
    result.then(resolved);
    await flushPromises();
    expect(resolved).not.toHaveBeenCalled();
    const buttons = document.querySelectorAll<HTMLButtonElement>('.as-modal__footer button');
    expect(buttons[1].textContent).toContain('删除');
    buttons[confirmed ? 1 : 0].click();
    await expect(result).resolves.toBe(confirmed);
    await flushPromises();
    await new Promise(resolve => setTimeout(resolve, 100));
    expect(document.querySelector('.as-modal-root')).toBeNull();
  });

  it('chooses readable foreground colors for bright and dark accents', () => {
    expect(getAgentStartColorForeground('#00ccff')).toBe('#09090b');
    expect(getAgentStartColorForeground('#fadb14')).toBe('#09090b');
    expect(getAgentStartColorForeground('#722ed1')).toBe('#ffffff');
  });
});
