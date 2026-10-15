import { mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { reactive } from 'vue';
import HumanInputCard from './HumanInputCard.vue';

vi.mock('../../../client/vue', () => ({
  useAgentStartClient: () => ({ channels: {
    listChannels: async () => [], listChannelConnections: async () => [],
  } }),
}));
import StartNodeCard from './StartNodeCard.vue';
import DurableWaitCard from './DurableWaitCard.vue';
// @ts-expect-error Test runtime provides Node; the browser library excludes Node types.
import { readFileSync } from 'node:fs';
const compactStyles = readFileSync('src/agent-flow/workflow/_config-base.css', 'utf8');
const humanStyles = readFileSync('src/agent-flow/workflow/nodeCard/HumanInputCard.vue', 'utf8').split('<style scoped>')[1].split('</style>')[0];

let host: HTMLDivElement;
let style: HTMLStyleElement;
beforeEach(() => {
  host = document.createElement('div');
  host.className = 'wf-config-panel';
  host.innerHTML = '<div class="wf-config-body"></div>';
  style = document.createElement('style');
  style.textContent = compactStyles + humanStyles;
  document.head.append(style);
  document.body.append(host);
});
afterEach(() => { host.remove(); style.remove(); });

describe('compact node configuration panels', () => {
  it('renders the start input-variable add button at 24px', () => {
    const wrapper = mount(StartNodeCard, {
      attachTo: host.firstElementChild!,
      props: { modelValue: { mode: 'CHATFLOW', variables: [], output: [] } },
      global: { stubs: { VariableModal: true, OutputItemCard: true } },
    });
    const button = wrapper.get('button[aria-label="添加输入变量"]');
    expect(button.classes()).toContain('size-6');
    expect(getComputedStyle(button.element).height).toBe('24px');
    wrapper.unmount();
  });

  it('keeps independent fields at default size while preserving edits and multiline schema', async () => {
    const form = { correlationKey: '', timeoutSeconds: 30, timeoutStrategy: 'TIMEOUT', allowedUserIds: [], allowedRoles: [] };
    const wrapper = mount(DurableWaitCard, {
      attachTo: host.firstElementChild!,
      props: { nodeType: 'APPROVAL', modelValue: form },
    });
    const input = wrapper.get('input[placeholder]');
    expect(input.element.closest('.as-input-wrap--middle')).not.toBeNull();
    expect(getComputedStyle(input.element.closest('.as-input-wrap')!).height).not.toBe('24px');
    await input.setValue('order:123');
    expect(form.correlationKey).toBe('order:123');
    for (const select of wrapper.findAll('[data-slot="select-trigger"]')) {
      expect(select.classes()).toContain('as-select--middle');
      expect(getComputedStyle(select.element).minHeight).not.toBe('24px');
    }
    expect(wrapper.get('textarea').attributes('rows')).toBe('8');
    expect(getComputedStyle(wrapper.get('textarea').element).height).not.toBe('24px');
    wrapper.unmount();
  });
});


describe('human input theme and density', () => {
  it.each([
    ['light', '#ffffff', '#ffffff', '#2563eb', '#ffffff'],
    ['dark', '#171717', '#262626', '#a3e635', '#171717'],
    ['custom', '#102030', '#203040', '#fbbf24', '#102030'],
  ])('follows the %s host palette and keeps mode buttons compact', async (_theme, background, card, primary, onPrimary) => {
    for (const [name, value] of Object.entries({ background, card, primary, 'primary-foreground': onPrimary })) {
      host.style.setProperty(`--${name}`, value);
    }
    const form = reactive<any>({ formMode: 'FIXED', formFields: [], inputSchema: {}, presentationMode: 'CHAT_COMPONENT' });
    const wrapper = mount(HumanInputCard, {
      attachTo: host.firstElementChild!,
      props: { modelValue: form },
      global: { stubs: { PromptEditor: true, WorkflowModelPicker: true } },
    });
    const reference = document.createElement('div');
    reference.style.backgroundColor = background;
    expect(getComputedStyle(host).backgroundColor).toBe(reference.style.backgroundColor);
    const active = wrapper.get('.human-mode-button.is-active');
    expect(active.attributes('aria-pressed')).toBe('true');
    expect(getComputedStyle(active.element).height).toBe('24px');
    reference.style.backgroundColor = primary;
    expect(getComputedStyle(active.get('.human-mode-check').element).backgroundColor).toBe(reference.style.backgroundColor);
    await wrapper.findAll('button').find(button => button.text() === '添加控件')!.trigger('click');
    expect(form.formFields).toHaveLength(1);
    const title = wrapper.get('input[placeholder="例如：确认请假信息"]');
    expect(title.element.closest('.as-input-wrap--middle')).not.toBeNull();
    const groupedInput = wrapper.get('.human-field-grid .as-input-wrap');
    expect(groupedInput.classes()).toContain('as-input-wrap--small');
    expect(getComputedStyle(groupedInput.element).height).toBe('24px');
    expect(getComputedStyle(wrapper.get('.human-field-grid [data-slot="select-trigger"]').element).minHeight).toBe('24px');
    expect(getComputedStyle(wrapper.get('.human-field-card').element).padding).toBe('8px');
    expect(getComputedStyle(wrapper.get('.human-field-card').element).gap).toBe('6px');
    await wrapper.findAll('.human-mode-button').find(button => button.text().includes('AI 动态生成'))!.trigger('click');
    expect(form.formMode).toBe('AI');
    expect(wrapper.find('.human-ai-tip').exists()).toBe(true);
    expect(wrapper.find('.human-field-card').exists()).toBe(false);
    wrapper.unmount();
  });
});
