import { describe, expect, it, vi } from 'vitest';
import { createConnectorsNamespace } from './connectors';

describe('connectors namespace', () => {
  it('encodes connector identifiers and never sends caller-supplied tenant parameters', async () => {
    const request = vi.fn().mockResolvedValue({ success: true });
    const api = createConnectorsNamespace({ request } as any);
    await api.execute('open claw', 'qq/bot', 'send message', { text: 'hi' }, 'tenant one');
    expect(request).toHaveBeenCalledWith(
      '/connectors/open%20claw/qq%2Fbot/actions/send%20message/execute',
      { method: 'POST', body: JSON.stringify({ text: 'hi' }) },
    );
  });

  it('uses explicit installation lifecycle endpoints', async () => {
    const request = vi.fn().mockResolvedValue(undefined);
    const api = createConnectorsNamespace({ request } as any);
    await api.setInstallationEnabled('install/1', false, 'acme');
    expect(request.mock.calls[0][0]).toBe('/connector-installations/install%2F1/disable');
  });
});
