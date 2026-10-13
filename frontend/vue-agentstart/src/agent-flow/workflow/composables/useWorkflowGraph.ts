import { watch, type Ref } from 'vue';

import { useWorkflowStore } from '@/stores/workflow';

/** Variable consumers must share Vue Flow's live graph, not imported snapshots. */
export function useWorkflowGraph(nodes: Readonly<Ref<any[]>>, edges: Readonly<Ref<any[]>>) {
  const store = useWorkflowStore();
  // Keep reactive node/data references; only array replacement needs rebinding.
  // This also covers graph loading, node removal, and undo/redo.
  watch([nodes, edges], ([currentNodes, currentEdges]) => {
    store.setGraph({ nodes: currentNodes, edges: currentEdges });
  }, { immediate: true, flush: 'sync' });
}
