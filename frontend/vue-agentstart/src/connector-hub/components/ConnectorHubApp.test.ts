import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import ConnectorHubApp from './ConnectorHubApp.vue';

describe('ConnectorHubApp', () => {
  it('loads and renders the provider-neutral connector catalog', async () => {
    const connectors = {
      list: vi.fn().mockResolvedValue([{ key: { provider: 'native', connectorId: 'email' }, name: '邮件', description: '发送邮件', version: '1.0', actions: [{ id: 'send', name: '发送', inputSchema: '{"type":"object"}', riskLevel: 'WRITE' }] }]),
      listInstallations: vi.fn().mockResolvedValue([{ id: 'i1', tenantId: 'default', provider: 'native', connectorId: 'email', enabled: true }]),
      listConnections: vi.fn().mockResolvedValue([]),
    };
    const wrapper = mount(ConnectorHubApp, { props: { client: { connectors } as any }, global: { stubs: { ConnectorActionTestDrawer: true, ConnectorConnectionModal: true, ConnectorDetailDrawer: true } } });
    await flushPromises();
    expect(wrapper.text()).toContain('邮件');
    expect(wrapper.text()).toContain('发送邮件');
    expect(wrapper.text()).toContain('已启用');
  });

  it('keeps channel account management outside the connector hub', async () => {
    const connectors = {
      list: vi.fn().mockResolvedValue([]), listInstallations: vi.fn().mockResolvedValue([]),
      listConnections: vi.fn().mockResolvedValue([]),
    };
    const wrapper = mount(ConnectorHubApp, { props: { client: { connectors } as any }, global: { stubs: { ConnectorActionTestDrawer: true, ConnectorConnectionModal: true, ConnectorDetailDrawer: true } } });
    await flushPromises();
    expect(wrapper.text()).not.toContain('消息渠道');
    expect(wrapper.text()).not.toContain('账号接入');
    expect(wrapper.text()).toContain('业务连接器');
  });

  it('loads connector execution records', async () => {
    const connectors = {
      list: vi.fn().mockResolvedValue([]), listInstallations: vi.fn().mockResolvedValue([]),
      listConnections: vi.fn().mockResolvedValue([]),
      listExecutions: vi.fn().mockResolvedValue([{
        id: 'run-1', provider: 'native', connectorId: 'email', actionId: 'send',
        status: 'SUCCESS', durationMs: 12, createdAt: '2026-08-20T10:00:00',
      }]),
    };
    const wrapper = mount(ConnectorHubApp, { props: { client: { connectors } as any }, global: { stubs: { ConnectorActionTestDrawer: true, ConnectorConnectionModal: true, ConnectorDetailDrawer: true } } });
    await flushPromises();
    await wrapper.findAll('button').find((button) => button.text() === '执行审计')!.trigger('click');
    await flushPromises();
    expect(connectors.listExecutions).toHaveBeenCalledWith({ tenantId: undefined, limit: 100 });
    expect(wrapper.text()).toContain('email / send');
    expect(wrapper.text()).toContain('SUCCESS');
  });
});
