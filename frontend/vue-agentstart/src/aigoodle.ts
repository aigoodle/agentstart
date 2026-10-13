/**
 * Lightweight entry for the vue-vben-school aigoodle pages.
 *
 * Keep this surface independent from the workflow designer: consumers that
 * only render the agent/provider/knowledge pages should not pull the complete
 * agent-flow dependency graph into their browser bundle.
 */
// CSS is imported via src/style.css (shared across all entries)

export {
  AgentStartPlugin,
  mergeAgentStartHeaders,
  setAgentStartAppearance,
  useAgentStartConfig,
} from './config';
export type {
  AgentStartAppearanceOptions,
  AgentStartHeaders,
  AgentStartPluginOptions,
  AgentStartTheme,
  AgentStartVisual,
} from './config';

export { default as ProviderApp } from './provider-hub/components/ProviderApp.vue';
export { default as KnowledgeApp } from './knowledge-hub/components/KnowledgeApp.vue';
export { default as AgentAppsPage } from './agent-studio/components/AgentAppsPage.vue';
export { default as AgentChatPage } from './agent-studio/components/AgentChatPage.vue';
