import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import { defineComponent, h } from 'vue';

import { AgentStartClientKey } from '../../../client/vue';
import TriggersItemCard from './TriggersItemCard.vue';

const SelectStub = defineComponent({
  props: ['value'],
  emits: ['change', 'update:value'],
  setup(props, { emit, slots, attrs }) {
    return () => h('select', {
      ...attrs,
      value: props.value,
      onChange: (event: Event) => {
        const value = (event.target as HTMLSelectElement).value;
        emit('change', value);
        emit('update:value', value);
      },
    }, slots.default?.());
  },
});

describe('TriggersItemCard', () => {
  it('loads message channels and persists the selected channel trigger', async () => {
    const channels = {
      listChannels: vi.fn().mockResolvedValue([
        { provider: 'native', channelId: 'wecom', name: '企业微信', enabled: true },
        { provider: 'native', channelId: 'qqbot', name: 'QQBot', enabled: true },
        { provider: 'native', channelId: 'disabled', name: '已停用渠道', enabled: true, runtimeStatus: 'DISABLED' },
      ]),
      listChannelConnections: vi.fn().mockResolvedValue([]),
    };
    const model = { triggersEnabled: true, triggers: {
      type: 'connector', connectionId: 'legacy-employee-account', connectionName: '旧员工账号',
    } };
    const wrapper = mount(TriggersItemCard, {
      props: { modelValue: model, 'onUpdate:modelValue': () => undefined },
      global: {
        provide: { [AgentStartClientKey as symbol]: { channels } },
        stubs: {
          'a-select': SelectStub,
          'a-select-option': defineComponent({
            props: ['value'],
            setup(props, { slots }) { return () => h('option', { value: props.value }, slots.default?.()); },
          }),
          'a-switch': true,
          'a-button': true,
          'a-input': true,
          'a-input-number': true,
          'a-textarea': true,
        },
      },
    });

    await flushPromises();
    expect(channels.listChannels).toHaveBeenCalledOnce();
    expect(channels.listChannelConnections).toHaveBeenCalledOnce();
    expect(wrapper.text()).toContain('企业微信');
    expect(wrapper.text()).not.toContain('Telegram');
    expect(wrapper.text()).not.toContain('已停用渠道');

    await wrapper.findAll('select')[0].setValue('native:wecom');
    await flushPromises();
    expect(model.triggers).toMatchObject({
      type: 'connector',
      provider: 'native',
      channelId: 'wecom',
      channelName: '企业微信',
    });
    expect(model.triggers.connectionId).toBe('');
    expect((model.triggers as any).accountScope).toBe('ALL');
  });

  it('can scope a message connector trigger to one connected account', async () => {
    const channels = {
      listChannels: vi.fn().mockResolvedValue([
        { provider: 'native', channelId: 'wecom', name: '企业微信', enabled: true },
      ]),
      listChannelConnections: vi.fn().mockResolvedValue([
        { id: 'account-1', provider: 'native', channelId: 'wecom', name: '客服账号', desiredStatus: 'ACTIVE' },
        { id: 'account-2', provider: 'native', channelId: 'wecom', name: '已停用账号', desiredStatus: 'DISABLED' },
      ]),
    };
    const model = { triggersEnabled: true, triggers: { type: 'connector' } };
    const wrapper = mount(TriggersItemCard, {
      props: { modelValue: model, 'onUpdate:modelValue': () => undefined },
      global: {
        provide: { [AgentStartClientKey as symbol]: { channels } },
        stubs: {
          'a-select': SelectStub,
          'a-select-option': defineComponent({
            props: ['value'],
            setup(props, { slots }) { return () => h('option', { value: props.value }, slots.default?.()); },
          }),
          'a-switch': true, 'a-button': true, 'a-input': true,
          'a-input-number': true, 'a-textarea': true,
        },
      },
    });

    await flushPromises();
    await wrapper.findAll('select')[0].setValue('native:wecom');
    await wrapper.findAll('select')[1].setValue('SPECIFIC');
    await wrapper.findAll('select')[2].setValue('account-1');
    await flushPromises();

    expect(wrapper.text()).toContain('客服账号');
    expect(wrapper.text()).not.toContain('已停用账号');
    expect(model.triggers).toMatchObject({
      provider: 'native', channelId: 'wecom', channelName: '企业微信',
      connectionId: 'account-1', connectionName: '客服账号',
    });
  });
});
