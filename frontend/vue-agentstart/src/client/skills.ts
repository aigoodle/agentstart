import type { HttpCore } from './core';

export type SkillStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED';
export interface SkillEntity {
  id: string;
  tenantId?: string;
  code: string;
  name: string;
  description?: string;
  instructions: string;
  status: SkillStatus;
  toolNamesJson?: string;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
}
export interface SaveSkillRequest {
  code: string;
  name: string;
  description?: string;
  instructions: string;
  status?: SkillStatus;
  toolNames?: string[];
}
export interface SkillsNamespace {
  list(status?: SkillStatus): Promise<SkillEntity[]>;
  get(id: string): Promise<SkillEntity>;
  create(request: SaveSkillRequest): Promise<SkillEntity>;
  update(id: string, request: SaveSkillRequest): Promise<SkillEntity>;
  setStatus(id: string, status: SkillStatus): Promise<SkillEntity>;
  remove(id: string): Promise<void>;
}
export function createSkillsNamespace(core: HttpCore): SkillsNamespace {
  return {
    list: (status) => core.request(`/skills${status ? `?status=${status}` : ''}`),
    get: (id) => core.request(`/skills/${id}`),
    create: (body) => core.request('/skills', { method: 'POST', body: JSON.stringify(body) }),
    update: (id, body) => core.request(`/skills/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
    setStatus: (id, status) => core.request(`/skills/${id}/status`, { method: 'POST', body: JSON.stringify({ status }) }),
    remove: (id) => core.request(`/skills/${id}`, { method: 'DELETE' }),
  };
}
