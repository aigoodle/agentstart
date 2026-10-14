import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import ProviderHubShell from './ProviderHubShell.vue';
import ProviderCredentialModal from './ProviderCredentialModal.vue';

const api = vi.hoisted(() => ({
  listProviders: vi.fn(), listModels: vi.fn().mockResolvedValue([]),
  refreshCatalog: vi.fn(),
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
    expect(panel.style.right).toBe('auto');
    expect(panel.style.bottom).toBe('auto');
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
    expect(tags[2]!.style.background).toBe(icons[1]!.style.background);
    expect(tags[1]!.style.color).not.toBe(tags[2]!.style.color);
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
