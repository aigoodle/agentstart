<script setup lang="ts">
/**
 * Drawer-embedded FlowDesigner glue — used by {@code AppDesignDrawer} to slot
 * a visual-workflow canvas into the "编排" tab for workflow / chatflow apps.
 *
 * Handles:
 *   • Register the {@code FlowDesigner} instance up to the drawer via the
 *     {@code registerDesigner} callback (drawer uses it for {@code getFlowInfo}
 *     / {@code reloadGraph} on save).
 *   • Watch {@code initialGraphJson} — the drawer often opens synchronously
 *     and only later awaits the saved draft; a one-shot mount hook would lock
 *     the canvas on the 3-node seed. Reloading on every meaningful change
 *     keeps the async load path correct.
 *   • Legacy graph shape upgrade — designer used to save lowercase
 *     kebab-case type names ({@code condition}, {@code user-input}); backend
 *     now standardises on UPPER_SNAKE (matching {@code NodeType}). The
 *     upgrade table maps the old names on load so existing saves still render.
 *   • Node config panel on the right — the standalone /agent-flow view has
 *     the same layout, we mirror it here.
 */
import { computed, nextTick, onMounted, ref, shallowRef, watch } from 'vue';

import FlowDesigner from '../../agent-flow/workflow/FlowDesigner.vue';
import NodeConfigCard from '../../agent-flow/workflow/NodeConfigCard.vue';

interface Props {
  initialGraphJson?: string;
  registerDesigner: (inst: any) => void;
  /**
   * Owning app id — forwarded to {@code <FlowDesigner :app-id>} so the
   * component's own top-bar 保存草稿 / 发布 buttons include it in their
   * BackendAdapter payloads. Missing appId turns those buttons into no-ops
   * with an inline error message rather than posting an orphaned row.
   */
  appId?: string;
  /**
   * Raw app mode ({@code 'workflow'} | {@code 'chatflow'} | others). Forwarded
   * up to {@link FlowDesigner} as {@code mode="WORKFLOW"} / {@code "CHATFLOW"}
   * so its {@code initGraph()} seeds the correct 3-node default — workflow
   * ends with a plain 结束 (END), chatflow ends with 直接回复 (ANSWER).
   * Missing / unknown mode defaults to {@code "WORKFLOW"}.
   */
  appMode?: string;
  workflowOptionsLoader?: () => Promise<Array<{
    appId: string;
    workflowId: string;
    name: string;
    icon?: string;
    iconBackground?: string;
    inputVariables?: Array<Record<string, unknown>>;
  }>>;
}
const props = defineProps<Props>();

const designerMode = computed(() => {
  const m = (props.appMode ?? '').toLowerCase();
  if (m === 'chatflow') return 'CHATFLOW';
  return 'WORKFLOW';
});

const designerRef = shallowRef<any>(null);
const selectedNode = ref<any>(null);

function onNodeClick(node: any) {
  selectedNode.value = node;
}
function onNodeDelete() {
  selectedNode.value = null;
}
function onCloseConfig() {
  selectedNode.value = null;
}
function onConfigDataChange({ nodeId, data }: { nodeId: string; data: any }) {
  designerRef.value?.patchNodeData?.(nodeId, data);
}

/**
 * Parse a raw graph JSON string into a VueFlow-ready object, or return
 * {@code null} when the payload is empty / invalid / a legacy backend-format
 * dump. FlowDesigner takes {@code null} as "fall back to your own initGraph()
 * seed", so the caller doesn't have to handle the empty-canvas branch.
 */
function parseGraph(json?: string): any {
  if (!json) return null;
  try {
    const parsed = JSON.parse(json);
    if (parsed?.nodes?.length && !isLegacyBackendGraph(parsed)) {
      normalizeNodeTypes(parsed);
      return parsed;
    }
  } catch {
    // fall through
  }
  return null;
}

onMounted(async () => {
  await nextTick();
  if (!designerRef.value) return;
  props.registerDesigner(designerRef.value);
  designerRef.value.reloadGraph?.(parseGraph(props.initialGraphJson));
});

/**
 * The drawer opens synchronously, then asynchronously fetches the saved
 * draft and writes it into the prop. A one-shot mount hook would leave the
 * canvas stuck on the 3-node seed ("save works but reload shows nothing").
 * Watching the prop and re-invoking {@code reloadGraph} on every meaningful
 * change closes that loop.
 *
 * <p>Only reload when the JSON actually resolves to a valid graph — an empty
 * string flip while the fetch is in flight would otherwise wipe an
 * already-loaded canvas back to the seed.</p>
 */
watch(
  () => props.initialGraphJson,
  async (json, prev) => {
    if (json === prev) return;
    const graph = parseGraph(json);
    if (graph == null) return;
    await nextTick();
    designerRef.value?.reloadGraph?.(graph);
  },
);

/**
 * The designer now saves node types as canonical backend {@code NodeType}
 * names (UPPER_SNAKE) — same identifier VueFlow uses to pick a
 * {@code #node-XXX} slot. Upgrade any pre-rename graph in place so opening
 * a legacy row still hooks up the visual palette.
 */
const LEGACY_TYPE_UPGRADE: Record<string, string> = {
  CONDITION: 'IF_ELSE',
  CLASSIFIER: 'QUESTION_CLASSIFIER',
  HTTP: 'HTTP_REQUEST',
  TEMPLATE: 'TEMPLATE_TRANSFORM',
  VARIABLE: 'VARIABLE_AGGREGATOR',
};

function normalizeNodeTypes(g: any) {
  if (!Array.isArray(g?.nodes)) return;
  for (const n of g.nodes) {
    if (typeof n?.type !== 'string') continue;
    const upper = n.type.toUpperCase().replace(/-/g, '_');
    n.type = LEGACY_TYPE_UPGRADE[upper] ?? upper;
  }
}

/**
 * Detect a *truly* legacy backend-format graph shape from earlier prototypes:
 * nodes with canonical string ids like {@code "start"}/{@code "end"} and no
 * {@code position} field. VueFlow can't render those; discard and fall back
 * to the seed.
 */
function isLegacyBackendGraph(g: any): boolean {
  if (!Array.isArray(g?.nodes)) return false;
  return g.nodes.some(
    (n: any) => !n?.position || ['end', 'start'].includes(String(n?.id ?? '')),
  );
}
</script>

<template>
  <div class="drawer-flow-host">
    <div class="drawer-flow-canvas">
      <FlowDesigner
        ref="designerRef"
        :mode="designerMode"
        :app-id="appId"
        :embedded="true"
        class="drawer-flow-fill"
        @node-click="onNodeClick"
        @node-delete="onNodeDelete"
      />
    </div>
    <div v-if="selectedNode" class="drawer-flow-panel">
      <NodeConfigCard
        :select-node="selectedNode"
        :main-data="{ appId }"
        :workflow-options-loader="workflowOptionsLoader"
        @on-close="onCloseConfig"
        @data-change="onConfigDataChange"
      />
    </div>
  </div>
</template>

<style scoped>
.drawer-flow-host {
  position: relative;
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 0;
}
.drawer-flow-canvas {
  flex: 1;
  min-width: 0;
  min-height: 0;
  height: 100%;
  position: relative;
}
.drawer-flow-fill {
  width: 100%;
  height: 100%;
}
.drawer-flow-panel {
  flex: none;
  border-left: 1px solid #e5e7eb;
  background: #fff;
  min-height: 0;
}
</style>
