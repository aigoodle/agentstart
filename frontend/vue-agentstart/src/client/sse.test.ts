/**
 * Unit tests for readSseEvents — the shared SSE frame parser.
 *
 * Cases mirror real-world delivery: multiple frames per chunk, frames split
 * across chunks, multi-line data, comments, event-name defaulting, an
 * unterminated trailing frame, and CRLF line endings.
 */
import { describe, expect, it } from 'vitest';

import { readSseEvents, type SseEvent } from './sse';

function streamFrom(chunks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  return new ReadableStream<Uint8Array>({
    start(controller) {
      for (const chunk of chunks) controller.enqueue(encoder.encode(chunk));
      controller.close();
    },
  });
}

function sseResponse(chunks: string[]): Response {
  return new Response(streamFrom(chunks), {
    headers: { 'Content-Type': 'text/event-stream' },
  });
}

async function collect(
  source: Response | ReadableStream<Uint8Array>,
): Promise<SseEvent[]> {
  const events: SseEvent[] = [];
  for await (const event of readSseEvents(source)) events.push(event);
  return events;
}

describe('readSseEvents', () => {
  it('parses multiple complete frames delivered in one chunk', async () => {
    const events = await collect(
      sseResponse([
        'event: workflow_started\ndata: {"run_id":"r1"}\n\n' +
          'event: node_finished\ndata: {"node_id":"n1"}\n\n',
      ]),
    );
    expect(events).toEqual([
      { event: 'workflow_started', data: '{"run_id":"r1"}' },
      { event: 'node_finished', data: '{"node_id":"n1"}' },
    ]);
  });

  it('reassembles a frame split across chunk boundaries', async () => {
    const events = await collect(
      sseResponse(['even', 't: step\nda', 'ta: {"a":1}\n\n']),
    );
    expect(events).toEqual([{ event: 'step', data: '{"a":1}' }]);
  });

  it('joins multiple data: lines with a newline', async () => {
    const events = await collect(
      sseResponse(['event: message\ndata: line1\ndata: line2\n\n']),
    );
    expect(events).toEqual([{ event: 'message', data: 'line1\nline2' }]);
  });

  it('ignores comment lines', async () => {
    const events = await collect(
      sseResponse([': keep-alive\nevent: step\ndata: {"ok":true}\n\n']),
    );
    expect(events).toEqual([{ event: 'step', data: '{"ok":true}' }]);
  });

  it('defaults the event name to message and resets it per frame', async () => {
    const events = await collect(
      sseResponse(['event: named\ndata: {"a":1}\n\n', 'data: {"b":2}\n\n']),
    );
    expect(events).toEqual([
      { event: 'named', data: '{"a":1}' },
      { event: 'message', data: '{"b":2}' },
    ]);
  });

  it('flushes a trailing frame that lacks the final blank line', async () => {
    const events = await collect(
      sseResponse(['event: result\ndata: {"done":true}\n\nevent: tail\ndata: end']),
    );
    expect(events).toEqual([
      { event: 'result', data: '{"done":true}' },
      { event: 'tail', data: 'end' },
    ]);
  });

  it('handles CRLF line endings', async () => {
    const events = await collect(
      sseResponse(['event: step\r\ndata: {"crlf":true}\r\n\r\n']),
    );
    expect(events).toEqual([{ event: 'step', data: '{"crlf":true}' }]);
  });

  it('accepts a bare ReadableStream as the source', async () => {
    const events = await collect(streamFrom(['data: raw\n\n']));
    expect(events).toEqual([{ event: 'message', data: 'raw' }]);
  });

  it('skips frames without a data field', async () => {
    const events = await collect(
      sseResponse(['event: noop\n\nevent: step\ndata: kept\n\n']),
    );
    expect(events).toEqual([{ event: 'step', data: 'kept' }]);
  });
});
