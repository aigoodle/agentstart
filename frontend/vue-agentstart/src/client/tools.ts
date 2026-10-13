import type { HttpCore } from './core';

export interface ToolCatalogItem {
  name: string;
  label?: string;
  description?: string;
  inputSchema?: string | Record<string, unknown>;
  source?: string;
  category?: string;
  provider?: string;
  mcpServerId?: string;
  custom?: boolean;
  method?: string;
  url?: string;
  enabled?: boolean;
}

export interface SaveCustomToolRequest {
  name: string;
  description: string;
  method: 'DELETE' | 'GET' | 'PATCH' | 'POST' | 'PUT';
  url: string;
  inputSchema: string;
  headers?: Record<string, string>;
  enabled?: boolean;
}

export interface McpServerCatalogItem {
  id: string;
  name: string;
  transport: 'HTTP' | 'STDIO';
  enabled: boolean;
  status?: string;
  toolCount?: number;
  lastError?: string;
}

export interface ToolsNamespace {
  list(): Promise<ToolCatalogItem[]>;
  invoke(name: string, args?: Record<string, unknown>): Promise<unknown>;
  listCustom(): Promise<SaveCustomToolRequest[]>;
  saveCustom(tool: SaveCustomToolRequest): Promise<ToolCatalogItem>;
  deleteCustom(name: string): Promise<boolean>;
  listMcpServers(): Promise<McpServerCatalogItem[]>;
}

export function createToolsNamespace(core: HttpCore): ToolsNamespace {
  return {
    list: () => core.request<ToolCatalogItem[]>('/tools'),
    invoke: (name, args = {}) => core.request(`/tools/${encodeURIComponent(name)}/invoke`, {
      method: 'POST', body: JSON.stringify(args),
    }),
    listCustom: () => core.request<SaveCustomToolRequest[]>('/tools/custom'),
    saveCustom: (tool) => core.request<ToolCatalogItem>('/tools/custom', {
      method: 'POST', body: JSON.stringify(tool),
    }),
    deleteCustom: (name) => core.request<boolean>(`/tools/custom/${encodeURIComponent(name)}`, {
      method: 'DELETE',
    }),
    listMcpServers: () =>
      core.request<McpServerCatalogItem[]>('/mcp-servers'),
  };
}
