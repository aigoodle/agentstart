import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import ProviderHubShell from './ProviderHubShell.vue';
import ProviderCredentialModal from './ProviderCredentialModal.vue';
import { message } from '../../ui/message';
vi.mock('../../ui/message', () => ({ message: { success: vi.fn(), error: vi.fn() } }));

const api = vi.hoisted(() => ({
  listProviders: vi.fn(), listModels: vi.fn().mockResolvedValue([]),
  refreshCatalog: vi.fn(), synchronizeBuiltinCatalog: vi.fn(),
  getProviderCatalog: vi.fn().mockResolvedValue([]), saveProviderCredential: vi.fn().mockResolvedValue({}),
}));
vi.mock('../composables/useProviderHub', () => ({
  useProviderHub: () => api,
  modelTypeColor: () => 'blue', modelTypeLabel: (type: string) => type,
}));
const provider = {
  name: 'example', label: '示例供应商', credentialConfigured: true,
  supportedModelTypes: ['LLM'], predefinedModels: [], installedModelCount: 0,
  supportsRemoteModelListing: true,
  credentialSchema: [
    { name: 'apiKey', label: 'API Key', type: 'string', secret: true, required: true },
    { name: 'baseUrl', label: '接口地址', type: 'string', secret: false, required: false },
  ],
};
let wrapper: ReturnType<typeof mount>;
afterEach(() => { wrapper?.unmount(); document.body.innerHTML = ''; vi.clearAllMocks(); });
describe('provider management interactions', () => {
  it.each([true, false])('shows catalog synchronization feedback in a toast (success: %s)', async (success) => {
    api.listProviders.mockResolvedValue([provider]);
    if (success) api.synchronizeBuiltinCatalog.mockResolvedValueOnce(undefined);
    else api.synchronizeBuiltinCatalog.mockRejectedValueOnce(new Error('同步服务不可用'));
    wrapper = mount(ProviderHubShell, {
      global: { stubs: { DefaultModelsPanel: true, ProviderCredentialModal: true, ModelParameterDrawer: true } },
    });
    await flushPromises();
    const button = wrapper.findAll('button').find(button => button.text() === '同步预置目录')!;
    await button.trigger('click');
    await flushPromises();
    expect(api.synchronizeBuiltinCatalog).toHaveBeenCalledOnce();
    if (success) expect(message.success).toHaveBeenCalledWith('预置目录已同步；远程模型请在供应商中手动拉取。');
    else expect(message.error).toHaveBeenCalledWith('同步服务不可用');
    expect(wrapper.text()).not.toContain('预置目录已同步');
    expect(wrapper.text()).not.toContain('同步服务不可用');
    expect(button.attributes('disabled')).toBeUndefined();
  });

  it('covers existing content with a shadcn loading overlay throughout synchronization', async () => {
    let finishLoading!: (providers: typeof provider[]) => void;
    api.listProviders.mockReturnValueOnce(new Promise(resolve => { finishLoading = resolve; }));
    let complete!: () => void;
    api.synchronizeBuiltinCatalog.mockReturnValueOnce(new Promise<void>(resolve => { complete = resolve; }));
    wrapper = mount(ProviderHubShell, {
      global: { stubs: { DefaultModelsPanel: true, ProviderCredentialModal: true, ModelParameterDrawer: true } },
    });
    await wrapper.vm.$nextTick();
    expect(wrapper.attributes('aria-busy')).toBe('true');
    expect(wrapper.find('.phs-loading-overlay [data-slot="spinner"]').exists()).toBe(true);
    finishLoading([provider]);
    await flushPromises();
    expect(wrapper.find('.phs-loading-overlay').exists()).toBe(false);
    const button = wrapper.findAll('button').find(button => button.text() === '同步预置目录')!;
    await button.trigger('click');
    expect(wrapper.attributes('aria-busy')).toBe('true');
    expect(wrapper.find('.phs-content').attributes('inert')).toBeDefined();
    expect(wrapper.find('.phs-loading-overlay [data-slot="card"]').text()).toContain('正在同步预置目录');
    expect(wrapper.find('.phs-section').exists()).toBe(true);
    expect(button.attributes('disabled')).toBeDefined();
    complete();
    await flushPromises();
    expect(wrapper.attributes('aria-busy')).toBe('false');
    expect(wrapper.find('.phs-content').attributes('inert')).toBeUndefined();
    expect(wrapper.find('.phs-loading-overlay').exists()).toBe(false);
    expect(wrapper.find('.phs-loading').exists()).toBe(false);
  });

  it('filters vision capabilities separately from image and video generation types', async () => {
    api.listProviders.mockResolvedValue([provider]);
    api.getProviderCatalog.mockResolvedValue([
      { id: 'vision', source: 'predefined', model: 'vision-chat', modelType: 'LLM', features: ['VISION'], enabled: true },
      { id: 'text', source: 'predefined', model: 'text-chat', modelType: 'LLM', enabled: true },
      { id: 'image', source: 'predefined', model: 'image-generator', modelType: 'IMAGE', enabled: true },
      { id: 'video', source: 'predefined', model: 'video-generator', modelType: 'VIDEO', enabled: true },
    ]);
    wrapper = mount(ProviderHubShell, {
      attachTo: document.body,
      global: { stubs: { DefaultModelsPanel: true, ProviderCredentialModal: true, ModelParameterDrawer: true } },
    });
    await flushPromises();
    await wrapper.get('.phs-count-btn').trigger('click');
    await flushPromises();
    const filters = Array.from(document.querySelectorAll<HTMLButtonElement>('.phs-type-filters button'));
    for (const [type, model] of [['VISION', 'vision-chat'], ['IMAGE', 'image-generator'], ['VIDEO', 'video-generator']]) {
      filters.find(button => button.textContent?.includes(type!))!.click();
      await flushPromises();
      expect(document.querySelectorAll('.phs-row')).toHaveLength(1);
      expect(document.querySelector('.phs-row-name')?.textContent).toContain(model);
    }
  });

  it('opens model management for a configured provider with no models and closes the modal and dismisses the menu on outside click', async () => {
    api.listProviders.mockResolvedValue([provider]);
    wrapper = mount(ProviderHubShell, {
      attachTo: document.body,
      global: { stubs: { DefaultModelsPanel: true, ProviderCredentialModal: true, ModelParameterDrawer: true } },
    });
    await flushPromises();
    expect(wrapper.text()).not.toContain('待配置');
    await wrapper.get('.phs-count-btn').trigger('click');
    await flushPromises();
    expect(api.getProviderCatalog).toHaveBeenCalledWith('example', undefined);
    expect(document.querySelector('.phs-popover-inner')).not.toBeNull();
    expect(document.querySelector('[role=dialog]')).not.toBeNull();
    expect(document.querySelector('.as-modal__footer')?.textContent).toContain('编辑凭证');
    expect(document.querySelector('.as-modal__footer')?.textContent).toContain('删除凭证');
    const manager = document.querySelector('.phs-model-modal')!.closest<HTMLElement>('.as-modal-root')!;
    const addButton = Array.from(document.querySelectorAll<HTMLButtonElement>('.phs-popover-actions button')).find(button => button.textContent?.includes('手动添加'))!;
    addButton.click();
    await flushPromises();
    const addDialog = document.querySelector('.phs-mini-modal')!.closest<HTMLElement>('.as-modal-root')!;
    expect(Number(addDialog.style.zIndex)).toBeGreaterThan(Number(manager.style.zIndex));
    (addDialog.querySelector('.as-modal__close') as HTMLElement).click();
    await flushPromises();
    expect(document.querySelector('.phs-mini-modal')).toBeNull();
    expect(document.querySelector('.phs-model-modal')).not.toBeNull();
    (document.querySelector('.as-modal__mask') as HTMLElement).click();
    await flushPromises();
    expect(document.querySelector('.phs-popover-inner')).toBeNull();
    await wrapper.get('.phs-card-menu-btn').trigger('click');
    await flushPromises();
    expect(document.querySelector('.phs-card-menu-content')).not.toBeNull();
    const panel = document.querySelector<HTMLElement>('.phs-card-menu-overlay')!;
    expect(panel.getAttribute('data-side')).not.toBeNull();
    await new Promise(resolve => setTimeout(resolve, 10));
    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }));
    await flushPromises();
    expect(document.querySelector('.phs-card-menu-content')).toBeNull();
  });
  it('uses matching type icons and colors and filters the modal catalog', async () => {
    api.listProviders.mockResolvedValue([provider]);
    api.getProviderCatalog.mockResolvedValueOnce([
      { id: '1', source: 'predefined', model: 'chat-model', modelType: 'LLM', enabled: true },
      { id: '2', source: 'predefined', model: 'video-model', modelType: 'VIDEO', enabled: false },
    ]);
    wrapper = mount(ProviderHubShell, {
      attachTo: document.body,
      global: { stubs: { DefaultModelsPanel: true, ProviderCredentialModal: true, ModelParameterDrawer: true } },
    });
    await flushPromises();
    await wrapper.get('.phs-count-btn').trigger('click');
    await flushPromises();
    const tags = document.querySelectorAll<HTMLButtonElement>('.phs-type-filters button');
    expect(tags[1]!.textContent).toContain('💬');
    expect(tags[2]!.textContent).toContain('🎬');
    const icons = document.querySelectorAll<HTMLElement>('.phs-row-icon');
    expect(icons[1]!.textContent?.trim()).toBe('🎬');
    expect(tags[2]!.style.getPropertyValue('--phs-type-color')).toBe(icons[1]!.style.getPropertyValue('--phs-type-color'));
    expect(tags[1]!.style.getPropertyValue('--phs-type-color')).not.toBe(tags[2]!.style.getPropertyValue('--phs-type-color'));
    tags[2]!.click(); await flushPromises();
    expect(document.querySelectorAll('.phs-row')).toHaveLength(1);
    expect(document.querySelector('.phs-row-name')?.textContent).toContain('video-model');
  });
  it('reloads persisted discovery after refresh and retains it when reopened or remounted', async () => {
    api.listProviders.mockResolvedValue([provider]);
    let persisted: unknown[] = [];
    api.getProviderCatalog.mockImplementation(async () => persisted);
    api.refreshCatalog.mockImplementation(async () => {
      persisted = [{ id: 'saved', source: 'predefined', model: 'discovered-model', modelType: 'LLM', enabled: false }];
      return [];
    });
    const mountHub = () => mount(ProviderHubShell, {
      attachTo: document.body,
      global: { stubs: { DefaultModelsPanel: true, ProviderCredentialModal: true, ModelParameterDrawer: true } },
    });
    wrapper = mountHub();
    await flushPromises();
    await wrapper.get('.phs-count-btn').trigger('click');
    await flushPromises();
    const refresh = Array.from(document.querySelectorAll<HTMLButtonElement>('.phs-popover-actions button')).find(button => button.textContent?.includes('重新拉取'))!;
    refresh.click(); await flushPromises();
    expect(api.refreshCatalog).toHaveBeenCalledWith('example', undefined);
    expect(document.querySelector('.phs-row-name')?.textContent).toContain('discovered-model');
    (document.querySelector('.phs-model-modal .as-modal__close') as HTMLElement).click();
    await flushPromises();
    await wrapper.get('.phs-count-btn').trigger('click');
    await flushPromises();
    expect(document.querySelector('.phs-row-name')?.textContent).toContain('discovered-model');
    wrapper.unmount(); wrapper = mountHub();
    await flushPromises();
    await wrapper.get('.phs-count-btn').trigger('click');
    await flushPromises();
    expect(document.querySelector('.phs-row-name')?.textContent).toContain('discovered-model');
    api.getProviderCatalog.mockResolvedValue([]);
  });
  it('requires every configuration field and rejects whitespace before saving', async () => {
    wrapper = mount(ProviderCredentialModal, { props: { provider, open: true }, attachTo: document.body });
    const inputs = document.querySelectorAll('input');
    inputs[0]!.value = 'key'; inputs[0]!.dispatchEvent(new Event('input', { bubbles: true }));
    inputs[1]!.value = '   '; inputs[1]!.dispatchEvent(new Event('input', { bubbles: true }));
    const save = Array.from(document.querySelectorAll('button')).find(button => button.textContent?.trim() === '保存')!;
    save.click(); await flushPromises();
    expect(api.saveProviderCredential).not.toHaveBeenCalled();
    expect(document.body.textContent).toContain('请填写 接口地址');
    inputs[1]!.value = 'https://example.com'; inputs[1]!.dispatchEvent(new Event('input', { bubbles: true }));
    save.click(); await flushPromises();
    expect(api.saveProviderCredential).toHaveBeenCalledWith('example', { apiKey: 'key', baseUrl: 'https://example.com' }, undefined);
  });
  it('preserves existing configuration when editing only one field', async () => {
    wrapper = mount(ProviderCredentialModal, { props: { provider, open: true, editMode: true }, attachTo: document.body });
    const input = document.querySelector('input')!;
    input.value = 'replacement-key'; input.dispatchEvent(new Event('input', { bubbles: true }));
    Array.from(document.querySelectorAll('button')).find(button => button.textContent?.trim() === '保存')!.click();
    await flushPromises();
    expect(api.saveProviderCredential).toHaveBeenCalledWith('example', { apiKey: 'replacement-key' }, undefined);
  });
});
