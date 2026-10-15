import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import AgentPromptEditor from './AgentPromptEditor.vue';
import { Input } from '../../ui/shadcn/input';

describe('shadcn input native ref compatibility', () => {
  it('inserts a prompt variable at the current selection and restores the caret', async () => {
    const frames: FrameRequestCallback[] = [];
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => { frames.push(callback); return frames.length; });
    const wrapper = mount(AgentPromptEditor, { props: { modelValue: 'hello world again', variables: [{ key: 'name', label: 'Name', type: 'text' }] } });
    try {
      const textarea = wrapper.get('textarea').element as HTMLTextAreaElement;
      textarea.setSelectionRange(6, 11);
      await wrapper.findAll('button').find(button => button.text().includes('变量'))!.trigger('click');
      await wrapper.get('.prompt-var-item').trigger('click');
      expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['hello {{#name#}} again']);
      await wrapper.setProps({ modelValue: 'hello {{#name#}} again' });
      await flushPromises();
      frames.forEach(callback => callback(0));
      expect(textarea.selectionStart).toBe(16);
    } finally { wrapper.unmount(); vi.unstubAllGlobals(); }
  });

  it('keeps the file-input click method available to upload triggers', () => {
    const wrapper = mount(Input, { attrs: { type: 'file' } });
    try {
      const clicked = vi.spyOn(wrapper.get('input').element as HTMLInputElement, 'click');
      wrapper.vm.click();
      expect(clicked).toHaveBeenCalledOnce();
    } finally { wrapper.unmount(); }
  });
});
