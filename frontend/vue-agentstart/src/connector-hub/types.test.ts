import { describe, expect, it } from 'vitest';
import { parseJsonSchema } from './types';
describe('parseJsonSchema', () => {
  it('parses valid schemas and safely falls back for an invalid provider schema', () => {
    expect(parseJsonSchema('{"type":"object","required":["token"]}').required).toEqual(['token']);
    expect(parseJsonSchema('not-json').additionalProperties).toBe(true);
  });
});
