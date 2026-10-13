import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';

import type { AgentRunClient, AgentRunEvent, AgentRunSnapshot } from '../agent-run';
import AgentRunTimeline from './AgentRunTimeline.vue';

const snapshot: AgentRunSnapshot = {
  runId: 'run-1', tenantId: 'tenant-a', agentId: 'agent-7', conversationId: 'conversation-9',
  status: 'COMPLETED', version: 3, startedAt: '2026-08-20T00:00:00.000Z',
  finishedAt: '2026-08-20T00:00:01.250Z', requestJson: '{"query":"hello"}',
  responseJson: '{"text":"done"}',
};
const event: AgentRunEvent = {
  eventId: 'event-1', runId: 'run-1', sequence: 1, type: 'TOOL_SUCCEEDED',
  payloadJson: '{"tool":"search"}', createdAt: '2026-08-20T00:00:01Z',
};

function client(): AgentRunClient {
  return {
    get: vi.fn().mockResolvedValue(snapshot),
    events: vi.fn().mockResolvedValue([]),
    resume: vi.fn(),
    resumeMany: vi.fn(),
    cancel: vi.fn(),
    async *watch() { yield [event]; return snapshot; },
    waitForSettled: vi.fn().mockResolvedValue(snapshot),
  };
}

describe('AgentRunTimeline', () => {
  it('renders durable context, duration and selectable events', async () => {
    const wrapper = mount(AgentRunTimeline, { props: { client: client(), runId: 'run-1' } });
    await flushPromises();

    expect(wrapper.text()).toContain('1.25 s');
    expect(wrapper.text()).toContain('tenant-a');
    expect(wrapper.text()).toContain('conversation-9');
    expect(wrapper.text()).toContain('TOOL_SUCCEEDED');
    expect(wrapper.text()).toContain('"query": "hello"');

    await wrapper.find('li').trigger('click');
    expect(wrapper.emitted('event-selected')?.[0]).toEqual([event]);
    wrapper.unmount();
  });

  it('renders and submits every approval from one Alibaba checkpoint', async () => {
    const waiting = {
      ...snapshot,
      status: 'WAITING_APPROVAL' as const,
      responseJson: JSON.stringify({
        pendingApprovals: [
          { approvalId: 'call-1', toolName: 'create_order', toolInput: '{"id":1}' },
          { approvalId: 'call-2', toolName: 'send_payment', toolInput: '{"amount":9}' },
        ],
      }),
    };
    const api = client();
    api.get = vi.fn().mockResolvedValue(waiting);
    api.watch = async function* () { return waiting; };
    const wrapper = mount(AgentRunTimeline, { props: { client: api, runId: 'run-batch' } });
    await flushPromises();

    expect(wrapper.text()).toContain('2 个工具等待批量审批');
    expect(wrapper.text()).toContain('create_order');
    expect(wrapper.text()).toContain('send_payment');
    await wrapper.findAll('.approval select')[1].setValue('DENY');
    await wrapper.find('.approval button').trigger('click');
    await flushPromises();
    expect(api.resumeMany).toHaveBeenCalledWith('run-batch', {
      'call-1': 'APPROVE', 'call-2': 'DENY',
    });
    wrapper.unmount();
  });

  it('supports event filtering and host-provided slots', async () => {
    const wrapper = mount(AgentRunTimeline, {
      props: { client: client(), runId: 'run-1', eventTypes: ['MODEL_COMPLETED'] },
      slots: { empty: '<div class="host-empty">由宿主渲染</div>' },
    });
    await flushPromises();

    expect(wrapper.findAll('li')).toHaveLength(0);
    expect(wrapper.find('.host-empty').text()).toBe('由宿主渲染');
    wrapper.unmount();
  });

  it('opens a replayable state inspector for a durable step event', async () => {
    const stepEvent: AgentRunEvent = {
      eventId: 'e-1', runId: 'run-1', sequence: 3, type: 'STEP_OBSERVATION',
      payloadJson: '{"kind":"OBSERVATION","observation":"evidence"}',
    };
    const api = client();
    api.watch = async function* () { yield [stepEvent]; return snapshot; };
    const wrapper = mount(AgentRunTimeline, { props: { client: api, runId: 'run-1' } });
    await flushPromises();

    await wrapper.find('li').trigger('click');

    expect(wrapper.find('.event-inspector').text()).toContain('运行状态检查');
    expect(wrapper.find('.event-inspector').text()).toContain('evidence');
    expect(wrapper.emitted('event-selected')).toHaveLength(1);
  });

  it('refreshes stale approvals after another operator wins the resume claim', async () => {
    const waiting = {
      ...snapshot, status: 'WAITING_APPROVAL' as const,
      responseJson: JSON.stringify({ pendingApproval: {
        approvalId: 'call-1', toolName: 'send_order', toolInput: '{}',
      } }),
    };
    const api = client();
    api.get = vi.fn().mockResolvedValueOnce(waiting).mockResolvedValueOnce(snapshot);
    let watches = 0;
    api.watch = () => (async function* () {
      return watches++ === 0 ? waiting : snapshot;
    })();
    const conflict = Object.assign(new Error('another operator resumed this run'), {
      status: 409, code: 'run_concurrent_update',
    });
    api.resume = vi.fn().mockRejectedValue(conflict);
    const wrapper = mount(AgentRunTimeline, { props: { client: api, runId: 'run-1' } });
    await flushPromises();

    await wrapper.find('.approval button').trigger('click');
    await flushPromises();

    expect(api.get).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('该任务已由其他操作人处理，运行状态已刷新');
    expect(wrapper.find('.approval').exists()).toBe(false);
    expect(wrapper.emitted('error')?.at(-1)?.[0]).toMatchObject({
      message: '该任务已由其他操作人处理，运行状态已刷新',
    });
    wrapper.unmount();
  });
});
