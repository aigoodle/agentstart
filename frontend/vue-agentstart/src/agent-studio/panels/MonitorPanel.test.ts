import { mount, flushPromises } from '@vue/test-utils';
import { describe, it, expect, vi } from 'vitest';
import MonitorPanel from './MonitorPanel.vue';

const usage = (tokens: number) => ({ calls: 2, errors: 0, promptTokens: tokens - 1, completionTokens: 1, totalTokens: tokens, costMicros: 1000000, currency: 'USD', avgLatencyMs: 100 });
function api() {
  return {
    fetchAppMetrics: vi.fn().mockResolvedValue({ totalConversations: 1, totalMessages: 2, avgInteractionsPerConversation: 2 }),
    fetchAppLlmUsage: vi.fn().mockResolvedValue(usage(15)),
    fetchAppRecentLlmCalls: vi.fn().mockResolvedValue([]),
    fetchAppLlmTrend: vi.fn().mockResolvedValue([]),
    fetchLlmUsage: vi.fn(), fetchRecentLlmCalls: vi.fn(), fetchLlmTrend: vi.fn(),
  };
}
const mountPanel = (client: any) => mount(MonitorPanel, { props: { app: { id: 'app-a', name: 'A' }, api: client }, global: { stubs: { SparkChart: true } } });

describe('application monitoring', () => {
  it('requests all usage data for the selected app and uses the configured currency', async () => {
    const client = api(); const wrapper = mountPanel(client);
    await flushPromises();
    expect(client.fetchAppLlmUsage).toHaveBeenCalledWith('app-a');
    expect(client.fetchAppRecentLlmCalls).toHaveBeenCalledWith('app-a', 10);
    expect(client.fetchAppLlmTrend).toHaveBeenCalledWith('app-a', 'HOUR');
    expect(client.fetchLlmUsage).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('当前应用模型用量');
    expect(wrapper.text()).toContain('USD 1.00');
    expect(wrapper.text()).not.toContain('全局 LLM');
    wrapper.unmount();
  });

  it('does not silently fall back to global usage when app endpoints are unavailable', async () => {
    const client = api(); delete (client as any).fetchAppLlmUsage;
    const wrapper = mountPanel(client); await flushPromises();
    expect(wrapper.text()).toContain('宿主尚未接入应用级模型用量接口');
    expect(client.fetchLlmUsage).not.toHaveBeenCalled(); wrapper.unmount();
  });

  it('keeps the newly selected app data when the old request finishes later', async () => {
    const client = api(); let resolveOld!: (value: ReturnType<typeof usage>) => void;
    client.fetchAppLlmUsage.mockImplementation((id: string) => id === 'app-a' ? new Promise(resolve => resolveOld = resolve) : Promise.resolve(usage(77)));
    const wrapper = mountPanel(client); await flushPromises();
    await wrapper.setProps({ app: { id: 'app-b', name: 'B' } }); await flushPromises();
    resolveOld(usage(999)); await flushPromises();
    const tile = wrapper.findAll('.monitor-tile').find(v => v.text().includes('总 Tokens'))!;
    expect(tile.find('.monitor-tile-value').text()).toBe('77'); wrapper.unmount();
  });

  it('shows an error instead of presenting a failed request as zero usage', async () => {
    const client = api(); client.fetchAppLlmUsage.mockRejectedValue(new Error('统计服务不可用'));
    const wrapper = mountPanel(client); await flushPromises();
    expect(wrapper.text()).toContain('统计服务不可用');
    const tile = wrapper.findAll('.monitor-tile').find(v => v.text().includes('总 Tokens'))!;
    expect(tile.find('.monitor-tile-value').text()).toBe('—'); wrapper.unmount();
  });
});
