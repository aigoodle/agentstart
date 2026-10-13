import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';

import AgentVersionSelect from './AgentVersionSelect.vue';

const version = (id: string, versionNumber: number, status = 'ACTIVE') => ({
  id,
  tenantId: 'tenant-1',
  appId: 'agent-1',
  versionNumber,
  status,
  publishedAt: '2026-08-20T00:00:00Z',
});

describe('AgentVersionSelect', () => {
  it('lists runnable versions and excludes disabled versions', async () => {
    const client = {
      agents: {
        listVersions: vi.fn().mockResolvedValue([
          version('version-active', 3),
          version('version-old', 2, 'SUPERSEDED'),
          version('version-disabled', 1, 'DISABLED'),
        ]),
      },
    } as any;
    const wrapper = mount(AgentVersionSelect, {
      props: { client, agentId: 'agent-1', modelValue: '' },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('v3 · 当前发布');
    expect(wrapper.text()).toContain('v2 · 历史可运行');
    expect(wrapper.text()).not.toContain('v1');
    await wrapper.find('select').setValue('version-old');
    expect(wrapper.emitted('update:modelValue')).toContainEqual(['version-old']);
  });

  it('ignores a stale version response after the selected Agent changes', async () => {
    let resolveFirst!: (value: any[]) => void;
    const first = new Promise<any[]>((resolve) => { resolveFirst = resolve; });
    const client = {
      agents: {
        listVersions: vi.fn()
          .mockReturnValueOnce(first)
          .mockResolvedValueOnce([version('version-b', 8)]),
      },
    } as any;
    const wrapper = mount(AgentVersionSelect, {
      props: { client, agentId: 'agent-a', modelValue: '' },
    });
    await wrapper.setProps({ agentId: 'agent-b' });
    await flushPromises();
    expect(wrapper.text()).toContain('v8');

    resolveFirst([version('version-a', 1)]);
    await flushPromises();
    expect(wrapper.text()).toContain('v8');
    expect(wrapper.text()).not.toContain('v1');
  });
});
