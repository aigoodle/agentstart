import { mount, flushPromises } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { defaultRagPolicy, type DatasetDetailHub } from '../types';
import RagOperationsPanel from './RagOperationsPanel.vue';
import RagPolicyEditor from './RagPolicyEditor.vue';
import { message } from '../../ui/message';
vi.mock('../../ui/message', () => ({ message: { success: vi.fn(), error: vi.fn() } }));

function setup(withPolicy = true) {
  const policy = defaultRagPolicy();
  policy.context.maxTokens = 3200;
  const loadDataset = vi.fn().mockResolvedValue({ id: 'ds', retrievalConfigJson: JSON.stringify({ method: 'VECTOR', topK: 9, customSetting: 'keep', ...(withPolicy ? { ragPolicy: policy } : {}) }) });
  const updateDataset = vi.fn().mockResolvedValue({ id: 'ds' });
  const listRecallHistory = vi.fn().mockResolvedValue([]);
  const retrieveRag = vi.fn().mockResolvedValue({ originalQuery: 'question', queryPlan: { queries: [{ id: 'q0', query: 'question', type: 'ORIGINAL' }] }, evidence: [{ datasetId: 'ds', documentId: 'doc', segmentId: 's', content: 'Useful evidence', retrievalScore: .82, matchedQueryIds: ['q0'], metadata: { documentName: 'handbook.pdf' } }], stageLatencyMillis: { planning: 2, retrieval: 12 } });
  const retrieve = vi.fn();
  const evaluateRetrieval = vi.fn().mockResolvedValue({ metrics: {} });
  const hub = { loadDataset, updateDataset, retrieveRag, retrieve, listRecallHistory, evaluateRetrieval } as unknown as DatasetDetailHub;
  const wrapper = mount(RagOperationsPanel, { props: { datasetId: 'ds', hub } });
  return { wrapper, policy, loadDataset, updateDataset, retrieveRag, retrieve, listRecallHistory, evaluateRetrieval };
}

describe('Unified RAG workbench', () => {
  beforeEach(() => vi.clearAllMocks());
  it('uses one edited policy for both retrieval and persistence and renders the same run results', async () => {
    const { wrapper, policy, updateDataset, retrieveRag, retrieve } = setup();
    await flushPromises();
    expect(wrapper.findComponent(RagPolicyEditor).props('modelValue')).toEqual(policy);
    expect(wrapper.findAll('textarea')).toHaveLength(2);
    expect(wrapper.find('nav').exists()).toBe(false);
    const edited = { ...policy, retrieval: { ...policy.retrieval, scoreThreshold: .65 }, rerank: { ...policy.rerank, topN: 4 } };
    wrapper.findComponent(RagPolicyEditor).vm.$emit('update:modelValue', edited);
    await wrapper.find('#kh-rag-query').setValue('question');
    await wrapper.find('.kh-query-actions .kh-primary').trigger('click');
    await flushPromises();
    expect(retrieveRag).toHaveBeenCalledWith('ds', { query: 'question', policy: edited });
    expect(retrieve).not.toHaveBeenCalled();
    expect(wrapper.find('.kh-evidence').text()).toContain('Useful evidence');
    expect(wrapper.find('.kh-evidence').text()).toContain('handbook.pdf');
    expect(wrapper.find('.kh-results-column').text()).toContain('14 ms');
    await wrapper.find('header .kh-primary').trigger('click');
    await flushPromises();
    expect(updateDataset).toHaveBeenCalledWith('ds', { retrievalConfig: expect.objectContaining({ customSetting: 'keep', method: 'VECTOR', topK: 4, scoreThreshold: .65, ragPolicy: edited }) });
    expect(wrapper.emitted('saved')).toHaveLength(1);
    expect(message.success).toHaveBeenCalledWith('默认策略已保存');
    expect(wrapper.text()).not.toContain('默认策略已保存');
    wrapper.unmount();
  });

  it('applies changes from the visible controls to the only retrieval request', async () => {
    const { wrapper, retrieveRag } = setup();
    await flushPromises();
    const editor = wrapper.findComponent(RagPolicyEditor);
    const topK = editor.findAll('input[type=number]')[0];
    await topK.setValue('3');
    await topK.trigger('change');
    await editor.find('.rag-rerank input[type=checkbox]').setValue(true);
    await wrapper.find('#kh-rag-query').setValue('question');
    await wrapper.find('.kh-query-actions .kh-primary').trigger('click');
    await flushPromises();
    expect(retrieveRag).toHaveBeenCalledWith('ds', expect.objectContaining({ policy: expect.objectContaining({ rerank: expect.objectContaining({ topN: 3, enabled: true }) }) }));
    wrapper.unmount();
  });

  it('migrates existing recall settings into the single policy and uses it for history replay', async () => {
    const { wrapper, retrieveRag, listRecallHistory } = setup(false);
    await flushPromises();
    expect(wrapper.findComponent(RagPolicyEditor).props('modelValue').rerank.topN).toBe(9);
    listRecallHistory.mockResolvedValueOnce([{ id: 'h', query: 'question', hitCount: 1, at: 'now', method: 'VECTOR' }]);
    await wrapper.find('#kh-rag-query').setValue('question');
    await wrapper.find('.kh-query-actions .kh-primary').trigger('click');
    await flushPromises();
    await wrapper.find('.kh-history-row').trigger('click');
    await flushPromises();
    expect(retrieveRag).toHaveBeenCalledTimes(2);
    expect(retrieveRag.mock.calls[1][1].policy.rerank.topN).toBe(9);
    wrapper.unmount();
  });

  it('clears stale results on failure and shows an explicit no-match result on an empty successful run', async () => {
    const { wrapper, retrieveRag } = setup();
    await flushPromises();
    await wrapper.find('#kh-rag-query').setValue('question');
    await wrapper.find('.kh-query-actions .kh-primary').trigger('click');
    await flushPromises();
    retrieveRag.mockRejectedValueOnce(new Error('Retrieval failed'));
    await wrapper.find('.kh-query-actions .kh-primary').trigger('click');
    await flushPromises();
    expect(wrapper.find('[role=alert]').text()).toContain('Retrieval failed');
    expect(wrapper.find('.kh-evidence').exists()).toBe(false);
    retrieveRag.mockResolvedValueOnce({ originalQuery: 'question', queryPlan: { queries: [] }, evidence: [], stageLatencyMillis: {} });
    await wrapper.find('.kh-query-actions .kh-primary').trigger('click');
    await flushPromises();
    expect(wrapper.find('.kh-results-column').text()).toContain('没有找到匹配片段');
    wrapper.unmount();
  });

  it('evaluates with the very same policy and does not claim success when saving fails', async () => {
    const { wrapper, policy, updateDataset, evaluateRetrieval } = setup();
    await flushPromises();
    await wrapper.find('.kh-operations .kh-primary').trigger('click');
    await flushPromises();
    expect(evaluateRetrieval).toHaveBeenCalledWith('ds', expect.objectContaining({ policy, topK: policy.rerank.topN }));
    updateDataset.mockRejectedValueOnce(new Error('Save failed'));
    await wrapper.find('header .kh-primary').trigger('click');
    await flushPromises();
    expect(message.error).toHaveBeenCalledWith('Save failed');
    expect(message.success).not.toHaveBeenCalled();
    expect(wrapper.text()).not.toContain('Save failed');
    expect(wrapper.emitted('saved')).toBeUndefined();
    wrapper.unmount();
  });
});
