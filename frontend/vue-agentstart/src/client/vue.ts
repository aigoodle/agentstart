/**
 * Vue bindings for the unified client: provide/inject plumbing so a host can
 * register one client per app and let deeply nested components pick it up
 * without prop-drilling.
 *
 *   // host bootstrap
 *   import { installAgentStartClient, createAgentStartClient } from 'vue-agent-start';
 *   app.use(installAgentStartClient, createAgentStartClient({ baseUrl: '/api' }));
 *
 *   // anywhere in setup()
 *   const client = useAgentStartClient();
 */
import { inject, provide, type App, type InjectionKey, type Plugin } from 'vue';
import type { AgentStartClient } from './index';

export const AgentStartClientKey: InjectionKey<AgentStartClient> = Symbol(
  'agent-start.AgentStartClient',
);

/** Component-level provide (call inside setup()). */
export function provideAgentStartClient(client: AgentStartClient): void {
  provide(AgentStartClientKey, client);
}

/**
 * Inject the app-level client. Returns `undefined` when none was installed —
 * callers fall back to constructing one from their own props.
 */
export function useAgentStartClient(): AgentStartClient | undefined {
  return inject(AgentStartClientKey, undefined);
}

/**
 * Vue plugin: `app.use(installAgentStartClient, client)`. Provided at the app
 * level so every component in the tree can `useAgentStartClient()`.
 */
export const installAgentStartClient: Plugin<[AgentStartClient]> = {
  install(app: App, client: AgentStartClient) {
    app.provide(AgentStartClientKey, client);
  },
};
