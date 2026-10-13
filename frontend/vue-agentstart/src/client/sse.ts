/**
 * Minimal `text/event-stream` (SSE) frame parser shared by every streaming
 * consumer in this package (agent chat, workflow debug runs). It is
 * framework-neutral — no Vue imports — so it lives beside the HTTP core and is
 * safe to construct in SSR/Node.
 *
 * The server side (spring-agent-start) emits named events via Spring's
 * `SseEmitter` / `ServerSentEvent`, producing frames shaped like:
 *
 *     event: node_finished
 *     data: {"node_id":"n1","outputs":{...}}
 *     <blank line>
 *
 * This module turns a streamed `Response` (or raw `ReadableStream`) body into
 * an async sequence of `{ event, data }` records, handling chunked delivery —
 * frames may be split across network chunks, and several frames may arrive in
 * one chunk.
 *
 * Spec semantics implemented (https://html.spec.whatwg.org/#server-sent-events):
 *   - A blank line delimits events.
 *   - `event:` names the event; the name defaults to `'message'` and resets to
 *     that default for each new event (it does not persist across events).
 *   - Multiple `data:` lines within one event are joined with `'\n'`.
 *   - Lines starting with `:` are comments and are ignored.
 *   - `id:` / `retry:` and unknown fields are ignored (we do not reconnect).
 *   - An event with no `data:` is not dispatched.
 */

/** One parsed SSE frame. `data` is the raw string payload (not JSON-parsed). */
export interface SseEvent {
  /** Event name from the `event:` field; defaults to `'message'`. */
  event: string;
  /** Raw `data:` payload; multiple `data:` lines joined with `\n`. */
  data: string;
}

/** Delimiter between events: a blank line (LF or CRLF). */
const EVENT_SEPARATOR = /\r?\n\r?\n/;
/** Line splitter tolerant of LF and CRLF. */
const LINE_SEPARATOR = /\r?\n/;

function toStream(source: Response | ReadableStream<Uint8Array>): ReadableStream<Uint8Array> {
  if (typeof ReadableStream !== 'undefined' && source instanceof ReadableStream) {
    return source;
  }
  const body = (source as Response).body;
  if (!body) {
    throw new Error('readSseEvents: the source has no readable body');
  }
  return body;
}

/**
 * Parse a complete frame (the text between two blank-line delimiters) into an
 * {@link SseEvent}. Returns `null` when the frame carries no `data:` line —
 * per the spec such a frame is not dispatched.
 */
function parseFrame(frame: string): SseEvent | null {
  let event = 'message';
  const dataLines: string[] = [];
  for (const line of frame.split(LINE_SEPARATOR)) {
    if (line === '' || line.startsWith(':')) continue;
    const colon = line.indexOf(':');
    const field = colon === -1 ? line : line.slice(0, colon);
    let value = colon === -1 ? '' : line.slice(colon + 1);
    // The spec strips a single leading space after the colon.
    if (value.startsWith(' ')) value = value.slice(1);
    if (field === 'event') {
      event = value;
    } else if (field === 'data') {
      dataLines.push(value);
    }
    // `id:`, `retry:` and unknown fields are intentionally ignored.
  }
  if (dataLines.length === 0) return null;
  return { event: event || 'message', data: dataLines.join('\n') };
}

/**
 * Consume every complete event currently present in `buffer`, returning them
 * plus the trailing (possibly incomplete) remainder to keep buffering.
 */
function drain(buffer: string): { events: SseEvent[]; rest: string } {
  const events: SseEvent[] = [];
  let rest = buffer;
  let match: RegExpExecArray | null;
  while ((match = EVENT_SEPARATOR.exec(rest)) !== null) {
    const frame = rest.slice(0, match.index);
    rest = rest.slice(match.index + match[0].length);
    const parsed = parseFrame(frame);
    if (parsed) events.push(parsed);
  }
  return { events, rest };
}

/**
 * Lazily yield every SSE frame from a streaming response body.
 *
 * Usage:
 * ```ts
 * const res = await client.workflows.runGraphStream(payload, { signal });
 * for await (const { event, data } of readSseEvents(res)) {
 *   if (event === 'node_finished') applyStep(JSON.parse(data));
 * }
 * ```
 *
 * The reader lock is released when the caller stops iterating (including on
 * `break` or an abort-driven rejection), so the underlying response is never
 * left dangling.
 */
export async function* readSseEvents(
  source: Response | ReadableStream<Uint8Array>,
): AsyncGenerator<SseEvent, void, unknown> {
  const stream = toStream(source);
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const { events, rest } = drain(buffer);
      buffer = rest;
      for (const event of events) yield event;
    }
    // Flush any bytes the decoder is holding, then emit a trailing frame that
    // was never terminated by a blank line (some servers omit the final one).
    buffer += decoder.decode();
    if (buffer.length > 0) {
      const trailing = parseFrame(buffer);
      if (trailing) yield trailing;
    }
  } finally {
    reader.releaseLock();
  }
}
