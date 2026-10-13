import { DOMWrapper, flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import MyRobotsPanel from './MyRobotsPanel.vue';

afterEach(() => {
  document.body.innerHTML = '';
  vi.unstubAllGlobals();
});

const ROBOT = {
  id: 'robot-1',
  tenantId: 'tenant-a',
  ownerId: 'u-7', ownerType: 'USER', provider: 'native', channelId: 'qqbot',
  desiredStatus: 'ACTIVE', runtimeStatus: 'ONLINE', runtimeAccountId: 'qq-1',
  credentialsConfigured: true, configVersion: 1,
  name: '售后小助手',
  icon: '🤖',
  iconBackground: '#FFEAD5',
  description: '处理售后咨询',
  welcomeMessage: '你好,我是售后小助手',
  agentId: 'agent-1',
  agentName: '客服 Agent',
  createdByName: '张三',
  updatedAt: '2026-08-20T10:00:00',
};

function makeClient(overrides: Record<string, any> = {}) {
  const channels = {
    listChannelConnections: vi.fn().mockResolvedValue([{ ...ROBOT }]),
    listChannels: vi.fn().mockResolvedValue([{
      provider: 'native', channelId: 'qqbot', name: 'QQBot', installed: true,
      enabled: true, runtimeStatus: 'UP', credentialSchema: '{"type":"object"}',
    }]),
    saveChannelConnection: vi.fn().mockResolvedValue({ ...ROBOT }),
    deleteChannelConnection: vi.fn().mockResolvedValue(undefined),
    getEmployeeAgentBinding: vi.fn().mockResolvedValue(null),
    saveEmployeeAgentBinding: vi.fn().mockResolvedValue({}),
    ...overrides,
  };
  return {
    channels,
    agents: {
      list: vi.fn().mockResolvedValue([{ id: 'agent-1', name: '客服 Agent' }]),
    },
  } as any;
}

const USER = { userId: 'u-7', username: 'zhangsan', realName: '张三' };

function mountPanel(client: any) {
  return mount(MyRobotsPanel, {
    props: { client, user: USER, tenantId: 'tenant-a' },
  });
}

function modalBody() {
  return new DOMWrapper(document.body);
}

describe('MyRobotsPanel', () => {
  it('renders robot cards and never exposes userId / tenantId', async () => {
    const client = makeClient();
    const wrapper = mountPanel(client);
    await flushPromises();

    expect(client.channels.listChannelConnections).toHaveBeenCalledWith('tenant-a', 'u-7');
    expect(wrapper.text()).toContain('售后小助手');
    expect(wrapper.text()).toContain('处理售后咨询');
    expect(wrapper.text()).toContain('qqbot · 员工账号');
    expect(wrapper.text()).toContain('张三');
    // 归属与租户字段只参与请求,绝不出现在界面上
    expect(wrapper.text()).not.toContain('u-7');
    expect(wrapper.text()).not.toContain('tenant-a');
    expect(wrapper.text()).not.toContain('userId');
    expect(wrapper.text()).not.toContain('tenantId');
  });

  it('creates a robot with userId / tenantId injected outside the form', async () => {
    const client = makeClient();
    const wrapper = mountPanel(client);
    await flushPromises();

    await wrapper
      .findAll('button')
      .find((b) => b.text() === '定义机器人')!
      .trigger('click');
    await flushPromises();

    // 表单里不存在 userId / 租户输入项
    const modal = modalBody();
    const labels = modal.findAll('.rfm-field').map((f) => f.text());
    expect(labels.join(' ')).not.toContain('userId');
    expect(modal.find('input[name="tenantId"]').exists()).toBe(false);

    await modal.find('select.rfm-input').setValue('native:qqbot');
    await modal.find('input.rfm-input').setValue('售前导购');
    await modal
      .findAll('button')
      .find((b) => b.text() === '创建机器人')!
      .trigger('click');
    await flushPromises();

    expect(client.channels.saveChannelConnection).toHaveBeenCalledWith(
      expect.objectContaining({ name: '售前导购', ownerId: 'u-7', tenantId: 'tenant-a' }),
    );
    // 保存后刷新列表
    expect(client.channels.listChannelConnections).toHaveBeenCalledTimes(2);
  });

  it('edits an existing robot through update()', async () => {
    const client = makeClient();
    const wrapper = mountPanel(client);
    await flushPromises();

    await wrapper
      .findAll('button')
      .find((b) => b.text() === '编辑')!
      .trigger('click');
    await flushPromises();

    const modal = modalBody();
    const nameInput = modal.find('input.rfm-input');
    expect((nameInput.element as HTMLInputElement).value).toBe('售后小助手');
    await nameInput.setValue('售后小助手 Pro');
    await modal
      .findAll('button')
      .find((b) => b.text() === '保存修改')!
      .trigger('click');
    await flushPromises();

    expect(client.channels.saveChannelConnection).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'robot-1', name: '售后小助手 Pro' }),
    );
  });

  it('deletes a robot after confirmation', async () => {
    vi.stubGlobal('confirm', vi.fn(() => true));
    const client = makeClient();
    const wrapper = mountPanel(client);
    await flushPromises();

    await wrapper
      .findAll('button')
      .find((b) => b.text() === '删除')!
      .trigger('click');
    await flushPromises();

    expect(client.channels.deleteChannelConnection).toHaveBeenCalledWith('robot-1', 'tenant-a');
    expect(client.channels.listChannelConnections).toHaveBeenCalledTimes(2);
  });
});
