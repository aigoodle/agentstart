import { mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import { describe, expect, it, vi } from 'vitest';

import { AgentStartUi } from '../../ui';
import type { GroupedProviderView } from '../types';
import ModelPickerPopover from './ModelPickerPopover.vue';

const groups: GroupedProviderView[] = [
  {
    id: 'qwen',
    provider: 'qwen',
    label: '通义千问',
    modelList: [
      {
        id: 'qwen::qwen-plus::LLM',
        providerName: 'qwen',
        modelName: 'qwen-plus',
        modelType: 'LLM',
      },
    ],
  },
];

describe('ModelPickerPopover', () => {
  it('keeps the settings popover open while selecting a workflow LLM model', async () => {
    const wrapper = mount(ModelPickerPopover, {
      props: {
        groups,
        autoLoadDefault: false,
        showParams: false,
        placement: 'bottomLeft',
        matchTriggerWidth: true,
      },
      global: { plugins: [AgentStartUi] },
      attachTo: document.body,
    });

    const trigger = wrapper.get('.ph-mp-trigger');
    vi.spyOn(trigger.element, 'getBoundingClientRect').mockReturnValue({
      x: 120,
      y: 144,
      top: 144,
      right: 440,
      bottom: 180,
      left: 120,
      width: 320,
      height: 36,
      toJSON: () => ({}),
    });
    await trigger.trigger('click');
    await nextTick();
    expect(document.querySelector('.ph-mp')).not.toBeNull();
    const settingsPanel = document.querySelector<HTMLElement>('.as-popover__panel--bottomLeft');
    expect(settingsPanel).not.toBeNull();
    expect(settingsPanel?.style.left).toBe('120px');
    expect(settingsPanel?.style.top).toBe('184px');
    expect(settingsPanel?.style.width).toBe('320px');

    (document.querySelector('.ph-mp-model-card') as HTMLElement).click();
    await nextTick();
    expect(document.querySelector('.ph-mp')).not.toBeNull();
    expect(document.querySelector('.ph-mp-dropdown')).not.toBeNull();

    (document.querySelector('.ph-mp-item') as HTMLElement).click();
    await nextTick();
    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toMatchObject({
      providerName: 'qwen',
      modelProvider: 'qwen',
      provider: 'qwen',
      modelName: 'qwen-plus',
      modelType: 'LLM',
    });
    expect(document.querySelector('.ph-mp')).not.toBeNull();
    wrapper.unmount();
  });
});
