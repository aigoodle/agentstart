import { DOMWrapper, flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ChannelAccountDrawer from './ChannelAccountDrawer.vue';

afterEach(() => {
  document.body.innerHTML = '';
});

function mountDrawer() {
  const client = {
    proxyBase: '/api',
    namespace: '/agent-start',
    rootUrl: '/api/agent-start',
    agents: {
      listVersions: vi.fn().mockResolvedValue([{
        id: 'version-2', tenantId: 'tenant-1', appId: 'agent-1', versionNumber: 2,
        status: 'ACTIVE', publishedAt: '2026-08-20T00:00:00Z',
      }]),
    },
    channels: {
      saveChannelConnection: vi.fn().mockResolvedValue({}),
      getChannelConnectionConfiguration: vi.fn().mockResolvedValue({
        credentials: {}, config: {}, configuredSecretFields: [],
      }),
      saveEmployeeAgentBinding: vi.fn().mockResolvedValue({}),
    },
  } as any;
  const wrapper = mount(ChannelAccountDrawer, {
    attachTo: document.body,
    props: {
      open: true,
      client,
      tenantId: 'tenant-1',
      agents: [{ id: 'agent-1', name: '客服 Agent' }],
      connections: [],
      channel: {
        provider: 'native', channelId: 'qqbot', name: 'QQBot', version: '1',
        installed: true, enabled: true, runtimeStatus: 'ONLINE',
      },
    },
  });
  return { client, wrapper };
}

describe('ChannelAccountDrawer employee-owned account', () => {
  it('shows the exact native callback path for a saved webhook account', async () => {
    const { wrapper } = mountDrawer();
    await wrapper.setProps({
      connections: [{
        id: 'connection-7', tenantId: 'tenant-1', ownerId: 'employee-1', ownerType: 'USER',
        provider: 'native', channelId: 'qqbot', name: 'QQ 客服', desiredStatus: 'ACTIVE',
        runtimeStatus: 'ONLINE', runtimeAccountId: 'runtime-account-7', credentialsConfigured: true,
        configVersion: 1, runtimeMetadata: { transport: 'webhook' },
      }],
    });

    const body = new DOMWrapper(document.body);
    expect(body.find('.cad-callback').text()).toBe(
      '/api/agent-start/channel-events/native/qqbot/runtime-account-7',
    );
    expect(body.text()).toContain('复制回调地址');
    expect(body.text()).toContain('已就绪 · 等待平台回调');
  });

  it('saves employee credentials without binding an Agent or application', async () => {
    const { client, wrapper } = mountDrawer();
    await new DOMWrapper(document.body).find('.cad-add').trigger('click');
    await flushPromises();
    const dialog = document.body.querySelector('.cad-form-dialog')!;
    const inputs = dialog.querySelectorAll('input');
    const textInputs = Array.from(inputs).filter((input) => input.type === 'text');
    textInputs[0].value = 'employee-1';
    textInputs[0].dispatchEvent(new Event('input'));
    textInputs[1].value = '员工 QQ';
    textInputs[1].dispatchEvent(new Event('input'));
    await flushPromises();
    (dialog.querySelector('.cad-btn-primary') as HTMLButtonElement).click();
    await flushPromises();

    expect(client.channels.saveChannelConnection).toHaveBeenCalledWith(
      expect.objectContaining({ ownerId: 'employee-1', name: '员工 QQ' }),
    );
    const request = client.channels.saveChannelConnection.mock.calls[0][0];
    expect(request).not.toHaveProperty('agentId');
    expect(request).not.toHaveProperty('agentVersionId');
    expect(client.channels.saveEmployeeAgentBinding).not.toHaveBeenCalled();
  });

  it('creates a tenant account without asking for a personal owner', async () => {
    const { client, wrapper } = mountDrawer();
    await wrapper.setProps({
      channel: {
        provider: 'native', channelId: 'wecom', name: '企业微信', version: '1',
        installed: true, enabled: true, runtimeStatus: 'ONLINE',
        metadata: { accountModel: {
          scope: 'TENANT', instancePolicy: 'MULTIPLE',
          identityBridge: { enabled: true, mode: 'OAUTH' },
        } },
      },
    });
    await new DOMWrapper(document.body).find('.cad-add').trigger('click');
    await flushPromises();
    const dialog = document.body.querySelector('.cad-form-dialog')!;
    expect(dialog.textContent).not.toContain('员工 / 所有者 ID');
    expect(dialog.textContent).toContain('运行时身份关联');
    const name = dialog.querySelector('.cad-fields input') as HTMLInputElement;
    name.value = '总部企业微信';
    name.dispatchEvent(new Event('input'));
    await flushPromises();
    (dialog.querySelector('.cad-btn-primary') as HTMLButtonElement).click();
    await flushPromises();

    expect(client.channels.saveChannelConnection).toHaveBeenCalledWith(
      expect.objectContaining({ ownerType: 'TENANT', ownerId: undefined, name: '总部企业微信' }),
    );
  });

  it('loads safe editable values and marks configured secrets without revealing them', async () => {
    const { client, wrapper } = mountDrawer();
    client.channels.getChannelConnectionConfiguration.mockResolvedValue({
      credentials: { appId: 'visible-app-id' },
      config: { transport: 'websocket' },
      configuredSecretFields: ['clientSecret'],
    });
    await wrapper.setProps({
      channel: {
        provider: 'native', channelId: 'qqbot', name: 'QQBot', version: '1',
        installed: true, enabled: true, runtimeStatus: 'ONLINE',
        credentialSchema: JSON.stringify({ type: 'object', properties: {
          appId: { type: 'string', title: 'App ID' },
          clientSecret: { type: 'string', title: 'Secret', writeOnly: true },
        } }),
      },
      connections: [{
        id: 'connection-1', tenantId: 'tenant-1', ownerId: 'employee-1', ownerType: 'USER',
        provider: 'native', channelId: 'qqbot', name: 'QQ', desiredStatus: 'ACTIVE',
        runtimeStatus: 'ONLINE', runtimeAccountId: 'runtime-1', credentialsConfigured: true,
        configVersion: 1,
      }],
    });
    await new DOMWrapper(document.body).findAll('.cad-link').find(button => button.text() === '编辑')!.trigger('click');
    await flushPromises();

    const dialog = document.body.querySelector('.cad-form-dialog')!;
    expect((dialog.querySelector('input[value="visible-app-id"]') as HTMLInputElement)).toBeTruthy();
    const secret = dialog.querySelector('input[type="password"]') as HTMLInputElement;
    expect(secret.value).toBe('');
    expect(secret.placeholder).toBe('已配置，留空表示不修改');
  });
});
