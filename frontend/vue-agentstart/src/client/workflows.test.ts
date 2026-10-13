/**
 * Unit tests for client.workflows.runGraphStream — the SSE dry-run endpoint.
 *
 * Mirrors the knowledge-hub adapter test style: stub `fetch`, assert the URL,
 * method, headers and body, and that the raw `Response` is handed back to the
 * caller (who owns body streaming), rather than being envelope-unwrapped.
 */
import { describe, expect, it, vi } from 'vitest';

import { createAgentStartClient } from './index';

function sseResponse() {
  return new Response('event: workflow_started\ndata: {}\n\n', {
    status: 200,
    headers: { 'Content-Type': 'text/event-stream' },
  });
}

describe('workflows.runGraphStream', () => {
  it('posts to /workflows/run-graph/stream with Accept: text/event-stream and returns the raw Response', async () => {
    const fetchSpy = vi.fn().mockResolvedValue(sseResponse());
    const client = createAgentStartClient({ baseUrl: '/api', fetch: fetchSpy as any });
    const req = { graph: { nodes: [] }, data: { q: 1 } };

    const res = await client.workflows.runGraphStream(req);

    expect(fetchSpy).toHaveBeenCalledTimes(1);
    const [url, init] = fetchSpy.mock.calls[0]!;
    expect(url).toBe('/api/agent-start/workflows/run-graph/stream');
    expect(init.method).toBe('POST');
    expect(init.headers.Accept).toBe('text/event-stream');
    expect(JSON.parse(init.body)).toEqual(req);
    // `raw` hands back the Response itself — not the envelope `data`.
    expect(res).toBeInstanceOf(Response);
    expect(res.headers.get('Content-Type')).toBe('text/event-stream');
  });

  it('propagates the caller-owned abort signal without racing a timeout', async () => {
    const fetchSpy = vi.fn().mockResolvedValue(sseResponse());
    const client = createAgentStartClient({ baseUrl: '/api', fetch: fetchSpy as any });
    const controller = new AbortController();

    await client.workflows.runGraphStream({ graph: {} }, { signal: controller.signal });

    const [, init] = fetchSpy.mock.calls[0]!;
    expect(init.signal).toBe(controller.signal);
  });
});

describe('workflow durable run controls', () => {
  it('encodes run ids and sends pause/cancel reasons', async () => {
    const fetchSpy = vi.fn().mockImplementation(() => Promise.resolve(new Response(
      JSON.stringify({ code: 'ok', data: true }),
      { status: 200, headers: { 'Content-Type': 'application/json' } },
    )));
    const client = createAgentStartClient({ baseUrl: '/api', fetch: fetchSpy as any });
    await client.workflows.pauseRun('run/a', '人工检查');
    await client.workflows.cancelRun('run/a', '用户停止');
    expect(fetchSpy.mock.calls[0]![0]).toBe('/api/agent-start/workflow-runs/run%2Fa/pause');
    expect(JSON.parse(fetchSpy.mock.calls[0]![1].body)).toEqual({ reason: '人工检查' });
    expect(fetchSpy.mock.calls[1]![0]).toBe('/api/agent-start/workflow-runs/run%2Fa/cancel');
  });

  it('sends idempotent durable-wait signals', async () => {
    const body = { runId: 'r1', status: 'SUCCEEDED', success: true };
    const fetchSpy = vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'ok', data: body }), {
      status: 200, headers: { 'Content-Type': 'application/json' },
    }));
    const client = createAgentStartClient({ baseUrl: '/api', fetch: fetchSpy as any });
    const request = { resumeToken: 'secret', eventId: 'event-1', payload: { approved: true } };
    await client.workflows.signalRun('r1', request);
    expect(fetchSpy.mock.calls[0]![0]).toBe('/api/agent-start/workflow-runs/r1/signal');
    expect(JSON.parse(fetchSpy.mock.calls[0]![1].body)).toEqual(request);
  });
});
