import { DOMWrapper, flushPromises, mount } from '@vue/test-utils';
import { createPinia } from 'pinia';
import { describe, expect, it } from 'vitest';
import { defineComponent, h } from 'vue';
import { DialogContent, DialogRoot, DialogTitle } from 'reka-ui';
import PromptEditor from './PromptEditor.vue';
import PromptEditorTagPanel from './PromptEditorTagPanel.vue';

describe('prompt editor fullscreen inside a modal', () => {
  it('keeps the editor interactive, opens variables and exits without losing content or closing its host', async () => {
    const wrapper = mount(defineComponent({
      setup: () => () => h(DialogRoot, { open: true }, () => h(DialogContent, { 'aria-describedby': undefined }, () => [
        h(DialogTitle, {}, () => '节点配置'),
        h(PromptEditor, { modelValue: '保留内容 {{#start.name#}}' }),
      ])),
    }), { attachTo: document.body, global: { plugins: [createPinia()] } });
    await flushPromises();
    const body = new DOMWrapper(document.body);
    const editor = document.querySelector('.wf-pe-input');
    // A closed force-mounted layer must remain clickable inside the host modal.
    expect((body.get('.wf-pe').element as HTMLElement).style.pointerEvents).not.toBe('none');
    await body.get('[title="全屏（Esc 退出）"]').trigger('click');
    await flushPromises();
    const fullscreen = body.get('.wf-pe-fullscreen');
    expect(document.querySelector('.wf-pe-input')).toBe(editor);
    expect(fullscreen.element.parentElement).toBe(document.body);
    expect((fullscreen.element as HTMLElement).style.pointerEvents).toBe('auto');
    await fullscreen.get('[title="插入变量"]').trigger('click');
    await flushPromises();
    expect(body.find('.wf-tag-panel').exists()).toBe(true);
    wrapper.findComponent(PromptEditorTagPanel).vm.$emit('select', ['start', 'age']);
    await flushPromises();
    expect(wrapper.findComponent(PromptEditor).vm.getContent()).toContain('{{#start.age#}}');
    expect(body.find('.wf-tag-panel').exists()).toBe(false);
    await fullscreen.get('[title="退出全屏"]').trigger('click');
    await flushPromises();
    expect(body.find('.wf-pe-fullscreen').exists()).toBe(false);
    expect((body.get('.wf-pe').element as HTMLElement).style.pointerEvents).not.toBe('none');
    expect(document.querySelector('.wf-pe-input')).toBe(editor);
    expect(wrapper.findComponent(PromptEditor).vm.getContent()).toContain('保留内容 {{#start.name#}}');
    expect(body.find('[role="dialog"]').exists()).toBe(true);
    // Reopening and Escape must close just the fullscreen editor.
    await body.get('[title="全屏（Esc 退出）"]').trigger('click');
    await flushPromises();
    await body.get('.wf-pe-input').trigger('keydown', { key: 'Escape' });
    await flushPromises();
    expect(body.find('.wf-pe-fullscreen').exists()).toBe(false);
    expect((body.get('.wf-pe').element as HTMLElement).style.pointerEvents).not.toBe('none');
    expect(wrapper.findComponent(PromptEditor).exists()).toBe(true);
    wrapper.unmount();
    expect(document.body.style.pointerEvents).not.toBe('none');
  });
});
