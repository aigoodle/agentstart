import { describe, expect, it, vi } from 'vitest';

import { createAgentStartClient } from './index';

describe('agents namespace', () => {
  it('round trips application grants without accepting tenant identity from the UI', async () => {
    const settings = { mode: 'RESTRICTED' as const, grants: [
      { type: 'DEPARTMENT' as const, subjectId: 'project-1', includeDescendants: true },
    ] };
    const fetchSpy = vi.fn().mockImplementation(() => Promise.resolve(new Response(
      JSON.stringify({ code: 'ok', data: settings }),
      { status: 200, headers: { 'Content-Type': 'application/json' } },
    )));
    const client = createAgentStartClient({ baseUrl: '/api', fetch: fetchSpy as any });
    expect(await client.agents.getPermissions('app/1')).toEqual(settings);
    expect(await client.agents.updatePermissions('app/1', settings)).toEqual(settings);
    expect(fetchSpy.mock.calls[0]![0]).toBe('/api/agent-start/apps/app%2F1/permissions');
    expect(fetchSpy.mock.calls[1]![1].method).toBe('PUT');
    expect(JSON.parse(fetchSpy.mock.calls[1]![1].body)).toEqual(settings);
  });

  it('keeps mutable preview separate from the published production stream', async () => {
    const fetchSpy = vi.fn().mockResolvedValue(new Response('', { status: 200 }));
    const client = createAgentStartClient({ baseUrl: '/api', fetch: fetchSpy as any });

    await client.agents.previewStream('agent/1', { query: 'hello' });

    const [url, init] = fetchSpy.mock.calls[0]!;
    expect(url).toBe('/api/agent-start/agents/agent%2F1/chat/preview/stream');
    expect(init.method).toBe('POST');
    expect(init.headers.Accept).toBe('text/event-stream');
  });

  it('uses explicit immutable version lifecycle endpoints', async () => {
    const fetchSpy = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 'ok', data: [] }), {
        status: 200, headers: { 'Content-Type': 'application/json' },
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 'ok', data: {} }), {
        status: 200, headers: { 'Content-Type': 'application/json' },
      }));
    const client = createAgentStartClient({ baseUrl: '/api', fetch: fetchSpy as any });

    await client.agents.listVersions('agent/1');
    await client.agents.rollbackVersion('agent/1', 'version/1', 'rollback');

    expect(fetchSpy.mock.calls[0]![0]).toBe('/api/agent-start/agents/agent%2F1/versions');
    expect(fetchSpy.mock.calls[1]![0]).toBe(
      '/api/agent-start/agents/agent%2F1/versions/version%2F1/rollback',
    );
  });
});
