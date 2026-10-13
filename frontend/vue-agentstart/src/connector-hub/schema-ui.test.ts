import { describe, expect, it } from 'vitest';
import { schemaOutputFields, splitPluginConfiguration, visibleSchemaFields } from './schema-ui';

describe('plugin schema UI', () => {
  it('exposes nested plugin output fields to downstream variable selectors', () => {
    const fields = schemaOutputFields({ type: 'object', properties: { video: { type: 'object', properties: { url: { type: 'string' }, duration: { type: 'integer' } } } } });
    expect(fields[0]?.children?.map(field => [field.name, field.type])).toEqual([['url', 'string'], ['duration', 'number']]);
  });
  it('orders and conditionally displays fields without executing code', () => {
    const schema = { properties: { prompt: {}, mode: {}, image: {} } };
    const ui = { order: ['mode', 'prompt', 'image'], fields: { image: { showIf: { mode: ['image'] } } } };
    expect(visibleSchemaFields(schema, ui, { mode: 'text' }).map(([key]) => key)).toEqual(['mode', 'prompt']);
    expect(visibleSchemaFields(schema, ui, { mode: 'image' }).map(([key]) => key)).toEqual(['mode', 'prompt', 'image']);
  });
  it('separates credentials from ordinary configuration while retaining required fields', () => {
    const split = splitPluginConfiguration({ properties: { apiKey: { writeOnly: true }, password: { format: 'password' }, brand: { type: 'string' } }, required: ['apiKey', 'brand'] });
    expect(Object.keys(split.credentials.properties!)).toEqual(['apiKey', 'password']);
    expect(split.credentials.required).toEqual(['apiKey']);
    expect(Object.keys(split.configuration.properties!)).toEqual(['brand']);
    expect(split.configuration.required).toEqual(['brand']);
  });
});
