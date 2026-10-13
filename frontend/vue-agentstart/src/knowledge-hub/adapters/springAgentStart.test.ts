/**
 * Unit tests for createSpringAgentStartAdapter.
 *
 * We stub `fetch` and assert the adapter (a) sends the right URL, method,
 * headers and body, (b) unwraps the {code, message, data} envelope, and
 * (c) surfaces errors through onError + throws.
 */
import { describe, expect, it, vi } from 'vitest';

import { createSpringAgentStartAdapter } from './springAgentStart';

function envelope<T>(data: T, code = 'ok') {
  return new Response(JSON.stringify({ code, data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

function errorEnvelope(status: number, message: string, code = 'bad_request') {
  return new Response(JSON.stringify({ code, message }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('createSpringAgentStartAdapter', () => {
  it('sends listDatasets to /{baseUrl}/agent-start/datasets — /agent-start is auto-namespaced', async () => {
    const fetchSpy = vi.fn().mockResolvedValue(envelope([]));
    const api = createSpringAgentStartAdapter({
      baseUrl: '/api',
      fetch: fetchSpy as any,
    });
    await api.listDatasets();
    expect(fetchSpy).toHaveBeenCalledTimes(1);
    const call = fetchSpy.mock.calls[0]!;
    expect(call[0]).toBe('/api/agent-start/datasets');
  });

  it('strips trailing slashes from baseUrl before appending namespace', async () => {
    const fetchSpy = vi.fn().mockResolvedValue(envelope([]));
    const api = createSpringAgentStartAdapter({
      baseUrl: '/api///',
      fetch: fetchSpy as any,
    });
    await api.listDatasets();
    expect(fetchSpy.mock.calls[0]![0]).toBe('/api/agent-start/datasets');
  });

  it('defaults baseUrl to /api when the option is omitted', async () => {
    const fetchSpy = vi.fn().mockResolvedValue(envelope([]));
    const api = createSpringAgentStartAdapter({ fetch: fetchSpy as any });
    await api.listDatasets();
    expect(fetchSpy.mock.calls[0]![0]).toBe('/api/agent-start/datasets');
  });

  it('allows empty baseUrl for direct-to-backend calls (no proxy)', async () => {
    const fetchSpy = vi.fn().mockResolvedValue(envelope([]));
    const api = createSpringAgentStartAdapter({
      baseUrl: '',
      fetch: fetchSpy as any,
    });
    await api.listDatasets();
    expect(fetchSpy.mock.calls[0]![0]).toBe('/agent-start/datasets');
  });

  it('sends async headers from the provider on every request', async () => {
    const fetchSpy = vi.fn().mockResolvedValue(envelope([]));
    const api = createSpringAgentStartAdapter({
      baseUrl: '/x',
      fetch: fetchSpy as any,
      headers: async () => ({ Authorization: 'Bearer XYZ', 'X-Tenant': 'acme' }),
    });
    await api.listDatasets();
    const init = fetchSpy.mock.calls[0]![1];
    expect(init.headers.Authorization).toBe('Bearer XYZ');
    expect(init.headers['X-Tenant']).toBe('acme');
    expect(init.headers['Content-Type']).toBe('application/json');
  });

  it('unwraps the envelope on success', async () => {
    const rows = [{ id: '1', name: 'Docs', documentCount: 3 }];
    const fetchSpy = vi.fn().mockResolvedValue(envelope(rows));
    const api = createSpringAgentStartAdapter({
      baseUrl: '/x',
      fetch: fetchSpy as any,
    });
    const result = await api.listDatasets();
    expect(result).toHaveLength(1);
    expect(result[0]!.name).toBe('Docs');
  });

  it('serializes JSON body for POST', async () => {
    const created = { id: 'ds-1', name: 'x', documentCount: 0 };
    const fetchSpy = vi.fn().mockResolvedValue(envelope(created));
    const api = createSpringAgentStartAdapter({
      baseUrl: '/x',
      fetch: fetchSpy as any,
    });
    await api.createDataset({ name: 'x' });
    const init = fetchSpy.mock.calls[0]![1];
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body)).toEqual({ name: 'x' });
  });

  it('surfaces backend {message} via onError + throws', async () => {
    const onError = vi.fn();
    const fetchSpy = vi
      .fn()
      .mockResolvedValue(errorEnvelope(400, 'Name is required'));
    const api = createSpringAgentStartAdapter({
      baseUrl: '/x',
      fetch: fetchSpy as any,
      onError,
    });
    await expect(api.listDatasets()).rejects.toThrow('Name is required');
    expect(onError).toHaveBeenCalledWith('Name is required');
  });

  it('falls back to status text when the error has no JSON body', async () => {
    const onError = vi.fn();
    const fetchSpy = vi
      .fn()
      .mockResolvedValue(new Response('nope', { status: 502 }));
    const api = createSpringAgentStartAdapter({
      baseUrl: '/x',
      fetch: fetchSpy as any,
      onError,
    });
    await expect(api.listDatasets()).rejects.toThrow(/^502/);
    expect(onError).toHaveBeenCalledWith(expect.stringMatching(/^502/));
  });

  it('rejects when code !== ok in a 200 envelope', async () => {
    const fetchSpy = vi
      .fn()
      .mockResolvedValue(envelope(null, 'validation_failed'));
    // Simulate a business error: 200 status + envelope error code.
    const bodyRes = new Response(
      JSON.stringify({ code: 'validation_failed', message: 'nope' }),
      { status: 200, headers: { 'Content-Type': 'application/json' } },
    );
    fetchSpy.mockResolvedValue(bodyRes);
    const api = createSpringAgentStartAdapter({
      baseUrl: '/x',
      fetch: fetchSpy as any,
    });
    await expect(api.listDatasets()).rejects.toThrow('nope');
  });

  it('applies request timeout via AbortSignal', async () => {
    const fetchSpy = vi.fn((_url: string, init: RequestInit) => {
      return new Promise((_resolve, reject) => {
        const signal = init.signal!;
        signal.addEventListener('abort', () => reject(new Error('aborted')));
        // never resolve — we want the timeout to fire
      });
    });
    const api = createSpringAgentStartAdapter({
      baseUrl: '/x',
      fetch: fetchSpy as any,
      timeoutMs: 10,
    });
    await expect(api.listDatasets()).rejects.toThrow('aborted');
  });
});
