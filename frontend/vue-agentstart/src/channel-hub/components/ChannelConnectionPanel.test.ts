import { DOMWrapper, flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ChannelConnectionPanel from './ChannelConnectionPanel.vue';

afterEach(() => {
  document.body.innerHTML = '';
});

const body = () => new DOMWrapper(document.body);

function createClient() {
  const channels = {
      listChannels: vi.fn().mockResolvedValue([
        {
          provider: 'native',
          channelId: 'dingtalk',
          name: '钉钉',
          description: '钉钉消息通道',
          version: '1.0',
          installed: true,
          enabled: true,
          runtimeStatus: 'ONLINE',
          metadata: { platformId: 'dingtalk' },
        },
      ]),
      listChannelConnections: vi.fn().mockResolvedValue([]),
      getTenantAgentBinding: vi.fn().mockResolvedValue(null),
      listChannelIdentities: vi.fn().mockResolvedValue([]),
      listChannelEvents: vi.fn().mockResolvedValue([]),
    };
  return {
    channels,
    agents: { list: vi.fn().mockResolvedValue([]) },
  } as any;
}

describe('ChannelConnectionPanel', () => {
  it('renders channel catalog cards with install status', async () => {
    const wrapper = mount(ChannelConnectionPanel, { props: { client: createClient() } });
    await flushPromises();
    expect(wrapper.text()).toContain('钉钉');
    expect(wrapper.text()).toContain('钉钉消息通道');
    expect(wrapper.text()).toContain('已接入');
    wrapper.unmount();
  });

  it('shows native callback degradation inside the channel account drawer', async () => {
    const client = createClient();
    client.channels.listChannels.mockResolvedValue([{
      provider: 'native', channelId: 'qqbot', name: 'QQ', description: 'QQ 消息通道',
      version: '1.0', installed: true, enabled: true, runtimeStatus: 'ONLINE',
      metadata: { platformId: 'qqbot' },
    }]);
    client.channels.listChannelConnections.mockResolvedValue([{
      id: 'native-1', tenantId: 'default', ownerType: 'USER', ownerId: 'employee-7',
      provider: 'native', channelId: 'qqbot', name: 'QQ 客服', desiredStatus: 'ACTIVE',
      runtimeStatus: 'ONLINE', runtimeAccountId: 'profile-7', credentialsConfigured: true,
      configVersion: 1, runtimeMetadata: { callbackWorkerRunning: false,
        callbackWorkerFailures: 2, callbackWorkerError: 'database is locked',
        pendingInboundCallbacks: 3 },
    }]);
    const wrapper = mount(ChannelConnectionPanel, { props: { client } });
    await flushPromises();
    const accountStat = wrapper.findAll('.cc-stat.is-link')
      .find((stat) => stat.text().includes('账号'))!;
    await accountStat.trigger('click');
    await flushPromises();

    expect(body().text()).toContain('QQ 账号接入');
    expect(body().text()).toContain('ONLINE · 入站回调异常');
    expect(body().text()).toContain('待回调 3');
    expect(body().find('.cad-status').attributes('title')).toBe('database is locked');
    wrapper.unmount();
  });

  it('lists configured accounts in the account drawer, not the main panel', async () => {
    const client = createClient();
    client.channels.listChannelConnections.mockResolvedValue([{
      id: 'conn-1', tenantId: 'default', ownerType: 'USER', ownerId: 'employee-1',
      provider: 'native', channelId: 'dingtalk', name: '客服一号', desiredStatus: 'ACTIVE',
      runtimeStatus: 'ONLINE', runtimeAccountId: 'bot-001', credentialsConfigured: true,
      configVersion: 1,
    }]);
    const wrapper = mount(ChannelConnectionPanel, { props: { client } });
    await flushPromises();

    // 主面板只保留账号数量统计，不再平铺账号列表
    expect(wrapper.text()).not.toContain('已配置账号');
    expect(wrapper.text()).not.toContain('客服一号');

    const accountStat = wrapper.findAll('.cc-stat.is-link')
      .find((stat) => stat.text().includes('账号'))!;
    await accountStat.trigger('click');
    await flushPromises();

    expect(body().text()).toContain('已配置账号');
    expect(body().text()).toContain('客服一号');
    expect(body().text()).toContain('bot-001');
    expect(body().text()).toContain('员工账号');
    expect(body().text()).toContain('凭证已配置');
    wrapper.unmount();
  });

  it('does not treat a disabled fallback adapter as an installed channel', async () => {
    const client = createClient();
    client.channels.listChannels.mockResolvedValue([
      {
        provider: 'native', channelId: 'mattermost', name: 'Mattermost', installed: true,
        enabled: false, runtimeStatus: 'DISABLED', metadata: { platformId: 'mattermost' },
      },
      {
        provider: 'remote', channelId: 'mattermost', name: 'Mattermost', installed: false,
        enabled: false, runtimeStatus: 'NOT_INSTALLED', metadata: { platformId: 'mattermost' },
      },
    ]);
    const wrapper = mount(ChannelConnectionPanel, { props: { client } });
    await flushPromises();
    expect(wrapper.text()).toContain('可接入渠道');
    expect(wrapper.text()).toContain('未接入');
    expect(wrapper.text()).not.toContain('已接入渠道');
    wrapper.unmount();
  });

  it('opens the account list first and only shows the form after clicking add', async () => {
    const wrapper = mount(ChannelConnectionPanel, { props: { client: createClient() } });
    await flushPromises();
    await wrapper.find('.cc-card').trigger('click');
    await flushPromises();
    expect(body().text()).toContain('钉钉 账号接入');
    expect(body().text()).toContain('+ 添加账号');
    expect(document.body.textContent).not.toContain('员工 / 所有者 ID');
    await body().find('.cad-add').trigger('click');
    await flushPromises();
    expect(document.body.textContent).toContain('添加账号');
    expect(document.body.textContent).toContain('员工 / 所有者 ID');
    wrapper.unmount();
  });

  it('opens the monitor drawer from the card message button', async () => {
    const wrapper = mount(ChannelConnectionPanel, { props: { client: createClient() } });
    await flushPromises();
    const messageButton = wrapper.find('.cc-act-monitor');
    expect(messageButton.exists()).toBe(true);
    await messageButton.trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('消息观察 · 钉钉');
    expect(wrapper.text()).toContain('该通道暂无消息');
    wrapper.unmount();
  });

  it('loads additional conversation pages without replacing the first page', async () => {
    const client = createClient();
    client.channels.listChannelConversations = vi.fn().mockResolvedValue([]);
    client.channels.getChannelConversationSummary = vi.fn().mockResolvedValue({
      total: 2, waitingHuman: 0, humanActive: 0, slaBreached: 0, unread: 0,
    });
    const conversation = (id: string, preview: string) => ({
      id, tenantId: 'default', connectionId: 'connection-1', provider: 'native',
      channelId: 'dingtalk', accountId: 'account-1', conversationId: id,
      routingPolicyVersion: 1, status: 'BOT_ACTIVE', agentPaused: false,
      lastMessagePreview: preview, unreadCount: 0, slaBreached: false, lockVersion: 1,
    });
    client.channels.pageChannelConversations = vi.fn()
      .mockResolvedValueOnce({ items: [conversation('conversation-2', '最新会话')], nextCursor: 'next-1', hasMore: true })
      .mockResolvedValueOnce({ items: [conversation('conversation-1', '较早会话')], hasMore: false });
    const wrapper = mount(ChannelConnectionPanel, { props: { client } });
    await flushPromises();
    await wrapper.find('.cc-act-monitor').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('最新会话');
    expect(wrapper.text()).toContain('加载更多会话');
    await wrapper.find('.cmd-load-more').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('最新会话');
    expect(wrapper.text()).toContain('较早会话');
    expect(client.channels.pageChannelConversations.mock.calls[1][0]).toMatchObject({ cursor: 'next-1' });
    wrapper.unmount();
  });

  it('loads a selected conversation timeline by cursor and keeps earlier pages', async () => {
    const client = createClient();
    const conversation = {
      id: 'conversation-row-1', tenantId: 'default', connectionId: 'connection-1',
      provider: 'native', channelId: 'dingtalk', accountId: 'account-1',
      conversationId: 'conversation-1', routingPolicyVersion: 1, status: 'BOT_ACTIVE',
      agentPaused: false, lastMessagePreview: '最新消息', unreadCount: 0,
      slaBreached: false, lockVersion: 1,
    };
    const event = (id: string, content: string) => ({
      id, tenantId: 'default', connectionId: 'connection-1', provider: 'native',
      channelId: 'dingtalk', accountId: 'account-1', conversationId: 'conversation-1',
      direction: 'INBOUND', content, handled: true, status: 'HANDLED',
    });
    client.channels.listChannelConversations = vi.fn().mockResolvedValue([conversation]);
    client.channels.getChannelConversationSummary = vi.fn().mockResolvedValue({
      total: 1, waitingHuman: 0, humanActive: 0, slaBreached: 0, unread: 0,
    });
    client.channels.pageChannelConversations = vi.fn().mockResolvedValue({
      items: [conversation], hasMore: false,
    });
    client.channels.pageChannelEvents = vi.fn()
      .mockResolvedValueOnce({ items: [event('event-2', '最新一页')], nextCursor: 'event-cursor-1', hasMore: true })
      .mockResolvedValueOnce({ items: [event('event-1', '更早一页')], hasMore: false });

    const wrapper = mount(ChannelConnectionPanel, { props: { client } });
    await flushPromises();
    await wrapper.find('.cc-act-monitor').trigger('click');
    await flushPromises();
    await wrapper.find('.cmd-conversation .cmd-op').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('最新一页');
    expect(wrapper.text()).toContain('加载更早消息');
    expect(client.channels.pageChannelEvents).toHaveBeenNthCalledWith(1, {
      connectionId: undefined, conversationId: 'conversation-1', cursor: undefined, limit: 50,
    });
    await wrapper.find('.cmd-load-more-messages').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('最新一页');
    expect(wrapper.text()).toContain('更早一页');
    expect(client.channels.pageChannelEvents).toHaveBeenNthCalledWith(2, {
      connectionId: undefined, conversationId: 'conversation-1', cursor: 'event-cursor-1', limit: 50,
    });
    wrapper.unmount();
  });

  it('ignores a stale timeline response after switching conversations', async () => {
    const client = createClient();
    const conversation = (id: string) => ({
      id: `row-${id}`, tenantId: 'default', connectionId: 'connection-1',
      provider: 'native', channelId: 'dingtalk', accountId: 'account-1',
      conversationId: id, routingPolicyVersion: 1, status: 'BOT_ACTIVE',
      agentPaused: false, lastMessagePreview: id, unreadCount: 0,
      slaBreached: false, lockVersion: 1,
    });
    const event = (id: string, conversationId: string, content: string) => ({
      id, tenantId: 'default', connectionId: 'connection-1', provider: 'native',
      channelId: 'dingtalk', accountId: 'account-1', conversationId,
      direction: 'INBOUND', content, handled: true, status: 'HANDLED',
    });
    const conversations = [conversation('conversation-a'), conversation('conversation-b')];
    client.channels.listChannelConversations = vi.fn().mockResolvedValue(conversations);
    client.channels.getChannelConversationSummary = vi.fn().mockResolvedValue({
      total: 2, waitingHuman: 0, humanActive: 0, slaBreached: 0, unread: 0,
    });
    client.channels.pageChannelConversations = vi.fn().mockResolvedValue({ items: conversations, hasMore: false });
    let resolveFirst!: (value: any) => void;
    const first = new Promise((resolve) => { resolveFirst = resolve; });
    client.channels.pageChannelEvents = vi.fn()
      .mockReturnValueOnce(first)
      .mockResolvedValueOnce({ items: [event('event-b', 'conversation-b', '当前会话消息')], hasMore: false });

    const wrapper = mount(ChannelConnectionPanel, { props: { client } });
    await flushPromises();
    await wrapper.find('.cc-act-monitor').trigger('click');
    await flushPromises();
    const viewButtons = wrapper.findAll('.cmd-conversation').map((row) => row.find('.cmd-op'));
    await viewButtons[0].trigger('click');
    await viewButtons[1].trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('当前会话消息');

    resolveFirst({ items: [event('event-a', 'conversation-a', '过期会话消息')], hasMore: false });
    await flushPromises();
    expect(wrapper.text()).toContain('当前会话消息');
    expect(wrapper.text()).not.toContain('过期会话消息');
    wrapper.unmount();
  });
});
