import { describe, expect, it, vi } from 'vitest';

import { createHttpCore } from './core';

function response() {
  return new Response(JSON.stringify({ code: 'ok', data: {} }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('http core tenant boundary', () => {
  it('does not send a browser-controlled tenant header by default', async () => {
    const fetch = vi.fn().mockResolvedValue(response());
    const core = createHttpCore({ fetch: fetch as any, getTenant: () => 'tenant-a' });

    await core.request('/health');

    const headers = new Headers(fetch.mock.calls[0][1].headers);
    expect(headers.has('X-Tenant-Id')).toBe(false);
    expect(await core.tenant()).toBe('tenant-a');
  });

  it('supports an explicit legacy tenant-header compatibility mode', async () => {
    const fetch = vi.fn().mockResolvedValue(response());
    const core = createHttpCore({
      fetch: fetch as any,
      getTenant: () => 'tenant-a',
      sendTenantHeader: true,
    });

    await core.request('/health');

    const headers = new Headers(fetch.mock.calls[0][1].headers);
    expect(headers.get('X-Tenant-Id')).toBe('tenant-a');
  });
});
