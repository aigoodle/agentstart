import { describe, expect, it, vi } from 'vitest';
import { createChannelsNamespace } from './channels';

describe('channels namespace', () => {
  it('uses trusted current-tenant binding endpoints when the host omits tenantId', async () => {
    const request = vi.fn().mockResolvedValue(undefined);
    const api = createChannelsNamespace({ request } as any);

    await api.getTenantAgentBinding();
    await api.saveTenantAgentBinding(undefined, { defaultAgentId: 'agent-1' });

    expect(request.mock.calls[0][0]).toBe('/tenant-agent-bindings/current');
    expect(request.mock.calls[1]).toEqual([
      '/tenant-agent-bindings/current',
      { method: 'PUT', body: JSON.stringify({ defaultAgentId: 'agent-1' }) },
    ]);
  });

  it('encodes stable conversation page filters and cursor', async () => {
    const request = vi.fn().mockResolvedValue({ items: [], hasMore: false });
    const api = createChannelsNamespace({ request } as any);

    await api.pageChannelConversations({
      provider: 'native', channelId: 'qqbot', cursor: 'cursor+/=', limit: 50,
    });

    expect(request.mock.calls[0][0]).toBe(
      '/channel-conversations/page?provider=native&channelId=qqbot&cursor=cursor%2B%2F%3D&limit=50',
    );
  });

  it('encodes channel audit filters without accepting a tenant parameter', async () => {
    const request = vi.fn().mockResolvedValue([]);
    const api = createChannelsNamespace({ request } as any);

    await api.listChannelAudits({
      action: 'CHANNEL MESSAGE REPLY', resourceType: 'CHANNEL_EVENT',
      resourceId: 'event/1', actorId: 'user+1', outcome: 'SUCCESS', limit: 25,
    });

    expect(request.mock.calls[0][0]).toBe(
      '/channel-audits?action=CHANNEL%20MESSAGE%20REPLY&resourceType=CHANNEL_EVENT&resourceId=event%2F1&actorId=user%2B1&outcome=SUCCESS&limit=25',
    );
  });

  it('uses bounded dead-letter operations without a tenant parameter', async () => {
    const request = vi.fn().mockResolvedValue([]);
    const api = createChannelsNamespace({ request } as any);
    await api.listChannelDeadLetters(50);
    await api.replayChannelDeadLetters(['event/1']);
    expect(request.mock.calls[0][0]).toBe('/channel-dead-letters?limit=50');
    expect(request.mock.calls[1]).toEqual(['/channel-dead-letters/replay', {
      method: 'POST', body: JSON.stringify({ eventIds: ['event/1'] }),
    }]);
  });
});
