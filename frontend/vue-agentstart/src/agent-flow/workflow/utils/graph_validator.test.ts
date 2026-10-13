import { describe, expect, it } from 'vitest';
import { validateWorkflowGraph } from './graph_validator';

const node = (id: string, type: string, data: Record<string, unknown> = {}) => ({ id, type, data });

describe('validateWorkflowGraph', () => {
  it('rejects cycles before publish', () => {
    const issues = validateWorkflowGraph({
      nodes: [node('s', 'START'), node('a', 'LLM'), node('e', 'END')],
      edges: [{ source: 's', target: 'a' }, { source: 'a', target: 'e' }, { source: 'e', target: 'a' }],
    });
    expect(issues.map((issue) => issue.code)).toContain('cycle_detected');
  });

  it('validates durable wait node contracts', () => {
    const issues = validateWorkflowGraph({
      nodes: [node('s', 'START'), node('w', 'WAIT_EVENT'), node('z', 'SLEEP_UNTIL', { delayMillis: 0 }), node('e', 'END')],
      edges: [{ source: 's', target: 'w' }, { source: 'w', target: 'z' }, { source: 'z', target: 'e' }],
    });
    expect(issues.map((issue) => issue.code)).toEqual(expect.arrayContaining(['correlation_key_required', 'sleep_schedule_invalid']));
  });
});
