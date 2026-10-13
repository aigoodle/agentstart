/**
 * @agent-start/provider-hub
 *
 * Reusable component + composable kit over the spring-agent-start model-provider
 * REST API (/api/agent-start/models, /api/agent-start/model-providers).
 *
 * The primary surface is {@link ProviderHubShell} — a Dify-parity three-section
 * settings page (模型列表 / 待配置 / 安装模型供应商) that internally uses
 * {@link ProviderCredentialModal} for the "填 key 自动拉取" flow: user saves a single
 * API key, the backend hits the vendor's /v1/models endpoint, and the user picks
 * which models to bulk-register.
 *
 * The lower-level building blocks are kept exported so consumers who want a
 * different layout (e.g. the studio "picker" popover) can compose their own:
 *
 *   ProviderHubShell        — top-level three-section shell (recommended)
 *   ProviderCredentialModal — save-and-fetch modal, standalone
 *   ProviderGallery         — provider catalog grid, no state
 *   ModelCardGrid           — model cards, grouped by provider
 *   ModelPickerPopover      — bubble picker (search + grouped/collapsible),
 *                             for workflow LLM nodes & app-studio top-right
 *   CredentialForm          — schema-driven credential form
 *
 *   useProviderHub()        — thin fetch client (providers, credentials, sync, CRUD)
 *   setProviderHubApiBase() — override the base URL when embedding elsewhere.
 *
 * Consumers alias it as `@agent-start/provider-hub` (see vite.config.mts).
 */

export { default as CredentialForm } from './components/CredentialForm.vue';
export { default as DefaultModelsPanel } from './components/DefaultModelsPanel.vue';
export { default as GroupedModelSelect } from './components/GroupedModelSelect.vue';
export type { GroupedModelSelectOption } from './components/GroupedModelSelect.vue';
export { default as ModelCardGrid } from './components/ModelCardGrid.vue';
export { default as ModelParameterDrawer } from './components/ModelParameterDrawer.vue';
export { default as ModelPickerPopover } from './components/ModelPickerPopover.vue';
export { default as ProviderCredentialModal } from './components/ProviderCredentialModal.vue';
export { default as ProviderGallery } from './components/ProviderGallery.vue';
export { default as ProviderHubShell } from './components/ProviderHubShell.vue';
export { default as ProviderIcon } from './components/ProviderIcon.vue';

export * from './composables/useProviderHub';
export * from './types';
