import { flushPromises, mount } from '@vue/test-utils';
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
        showParams: true,
        placement: 'bottomLeft',
        matchTriggerWidth: true,
      },
      global: { plugins: [AgentStartUi] },
      attachTo: document.body,
    });

    expect(wrapper.get('.as-popover').classes()).toContain('w-full');
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
    expect(document.querySelector('.ph-mp.hud-panel')).toBeNull();
    expect(document.querySelector('.ph-mp-close')).toBeNull();
    const settingsPanel = document.querySelector<HTMLElement>('.as-popover-popup');
    expect(settingsPanel).not.toBeNull();
    expect(settingsPanel?.classList.contains('hud-popover')).toBe(true);
    expect(settingsPanel?.classList.contains('border')).toBe(false);
    expect(settingsPanel?.style.width).toBe('var(--reka-popover-trigger-width)');

    const parameters = settingsPanel?.querySelector('.ph-mp-params-block');
    expect(parameters).not.toBeNull();
    const modelButton = document.querySelector<HTMLButtonElement>('button.gms-trigger');
    expect(modelButton).not.toBeNull();
    modelButton!.click();
    await nextTick();
    expect(document.querySelector('.ph-mp')).not.toBeNull();
    const dropdown = document.querySelector('.gms-panel');
    expect(dropdown).not.toBeNull();
    expect(settingsPanel?.contains(dropdown)).toBe(false);
    expect(settingsPanel?.querySelector('.ph-mp-params-block')).toBe(parameters);
    expect(document.querySelectorAll('.as-popover-popup')).toHaveLength(1);

    (document.querySelector('.gms-item') as HTMLElement).click();
    await nextTick();
    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toMatchObject({
      providerName: 'qwen',
      modelProvider: 'qwen',
      provider: 'qwen',
      modelName: 'qwen-plus',
      modelType: 'LLM',
    });
    expect(document.querySelector('.ph-mp')).not.toBeNull();
    await flushPromises();
    expect(document.querySelector('.gms-panel')).toBeNull();
    expect(modelButton?.getAttribute('aria-expanded')).toBe('false');
    wrapper.unmount();
  });
});
