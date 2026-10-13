import { createAgentStartClient, type AgentStartClientOptions } from '../../client';

/**
 * Convenience helper — creates an SDK client and returns just the
 * `connectors` namespace. Handy when a host only needs connector features
 * without wiring up the full client.
 */
export function useConnectorHub(options: AgentStartClientOptions = {}) {
  return createAgentStartClient(options).connectors;
}
