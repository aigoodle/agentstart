import { flushPromises, shallowMount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import KnowledgeHubApp from './KnowledgeHubApp.vue';
import CreateDatasetWizard from './CreateDatasetWizard.vue';
import KhDialog from './internal/KhDialog.vue';
import DatasetDetailDrawer from './DatasetDetailDrawer.vue';
import type { KnowledgeHubApi } from '../types';

describe('empty knowledge base creation', () => {
  it('creates without an embedding model using the supported technique and opens the result', async () => {
    const createDataset = vi.fn().mockResolvedValue({ id: 'empty-dataset' });
    const api = {
      listDatasets: vi.fn().mockResolvedValue([]),
      listEmbeddingModels: vi.fn().mockResolvedValue([]),
      updateDataset: vi.fn(),
      createDataset,
    } as unknown as KnowledgeHubApi;
    const wrapper = shallowMount(KnowledgeHubApp, {
      props: { api },
      global: { stubs: { DatasetBasicInfoModal: { template: '<div />', inheritAttrs: false } } },
    });
    await flushPromises();
    wrapper.findComponent(CreateDatasetWizard).vm.$emit('skip-to-empty');
    await flushPromises();
    wrapper.findAllComponents(KhDialog).find(dialog => dialog.props('open'))!.vm.$emit('confirm', ' 小智AI机器人使用手册 ');
    await flushPromises();
    expect(createDataset).toHaveBeenCalledExactlyOnceWith({
      name: '小智AI机器人使用手册', indexingTechnique: 'HIGH_QUALITY',
    });
    expect(wrapper.findComponent(DatasetDetailDrawer).props()).toMatchObject({
      open: true, datasetId: 'empty-dataset',
    });
    wrapper.unmount();
  });
});
