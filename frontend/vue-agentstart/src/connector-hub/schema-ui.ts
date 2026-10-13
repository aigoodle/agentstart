import type { JsonSchema } from './types';
import type { ModelType } from '../provider-hub/types';

export interface SchemaFieldUi {
  widget?: 'input' | 'textarea' | 'password' | 'select' | 'model-selector';
  modelType?: ModelType;
  placeholder?: string;
  allowVariable?: boolean;
  showIf?: Record<string, unknown[]>;
}
export interface SchemaUi { order?: string[]; fields?: Record<string, SchemaFieldUi> }

export function parseSchemaUi(value: unknown): SchemaUi {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as SchemaUi : {};
}

export function visibleSchemaFields(schema: JsonSchema, ui: SchemaUi, values: Record<string, unknown>) {
  const order = Array.isArray(ui.order) ? ui.order : [];
  return Object.entries(schema.properties ?? {}).filter(([name]) => {
    const rules = ui.fields?.[name]?.showIf;
    return !rules || Object.entries(rules).every(([key, allowed]) =>
      Array.isArray(allowed) && allowed.some(item => Object.is(values[key], item)));
  }).sort(([a], [b]) => {
    const ai = order.indexOf(a), bi = order.indexOf(b);
    return (ai < 0 ? order.length : ai) - (bi < 0 ? order.length : bi);
  });
}

export function splitPluginConfiguration(schema: JsonSchema) {
  const partition = (secret: boolean): JsonSchema => {
    const properties = Object.fromEntries(Object.entries(schema.properties ?? {}).filter(([, field]) =>
      (field.format === 'password' || field.writeOnly === true) === secret));
    return { ...schema, properties, required: schema.required?.filter(name => name in properties), additionalProperties: false };
  };
  return { credentials: partition(true), configuration: partition(false) };
}

export interface SchemaOutputField {
  name: string; type: string; label: string; value: unknown; children?: SchemaOutputField[];
}
export function schemaOutputFields(schema: JsonSchema, depth = 0): SchemaOutputField[] {
  if (depth >= 8) return [];
  const fields = schema.type === 'array' ? schema.items?.properties : schema.properties;
  return Object.entries(fields ?? {}).map(([name, field]) => {
    const type = field.type === 'integer' ? 'number' : field.type ?? 'string';
    return { name, type, label: field.title ?? field.description ?? name,
      value: field.default ?? (type === 'object' ? {} : type === 'array' ? [] : type === 'boolean' ? false : type === 'number' ? 0 : ''),
      ...(['object', 'array'].includes(type) ? { children: schemaOutputFields(field, depth + 1) } : {}) };
  });
}
