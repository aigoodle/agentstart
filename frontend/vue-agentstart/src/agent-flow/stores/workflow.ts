import { defineStore } from 'pinia';

interface WorkflowState {
  graph: any; // 字典数据缓存
  selectDataCache: any; // 下拉数据缓存
}
export const useWorkflowStore = defineStore('workflow', {
  state: (): WorkflowState => ({
    graph: {},
    selectDataCache: {},
  }),
  getters: {},
  actions: {
    setGraph(graph: any) {
      this.graph = graph;
    },
    getNodes() {
      return this.graph.nodes;
    },
    getStartNode() {
      return this.getNodeById('1');
    },
    getStartNodeVariable() {
      const startNode: any = this.getStartNode();
      const data = startNode.data;
      const items: any = [];
      if (data.triggersEnabled) {
        const triggers = data.structOutput.data;
        items.push(...triggers);
      } else {
        items.push({
          type: 'string',
          name: 'query',
          value: '',
          label: '用户查询',
        });
      }
      return items;
    },
    getNodeById(id: string) {
      const nodes = this.graph.nodes || [];
      for (const node of nodes) {
        if (node.id === id) {
          return node;
        }
      }
      return {};
    },
    getParentNodeList(nodeId: string, items: any = []) {
      const edges = this.graph.edges || [];
      const parentEdges = edges.filter((edge: any) => edge.target === nodeId);
      parentEdges.forEach((edge: any) => {
        const parentNode = this.getNodeById(edge.source);
        if (
          parentNode?.id &&
          !items.some((item: any) => item.id === parentNode.id)
        ) {
          items.unshift(parentNode);
          this.getParentNodeList(parentNode.id, items);
        }
      });
      return items;
    },
    getOutputItemsTree(nodeId: string, items: any = []) {
      const nodeList = this.getParentNodeList(nodeId);
      for (const node of nodeList) {
        const output = node.data.output;
        items.push(this.getNodeById(node));
      }
    },
  },
});
