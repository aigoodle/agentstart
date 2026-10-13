import { useWorkflowStore } from '@/stores/workflow';
import langUtils from '@/utils/langUtils';
import nodeCardForm from '@/workflow/utils/node_card_form';
import { synchronizeStartTriggerOutputs } from '@/workflow/utils/start_trigger_outputs';

export default {
  conditionOperators: [
    { value: 'CONTAINS', label: '包含' },
    { value: 'NOT_CONTAINS', label: '不包含' },
    { value: 'STARTS_WITH', label: '开始是' },
    { value: 'ENDS_WITH', label: '结束是' },
    { value: 'IS', label: '是' },
    { value: 'IS_NOT', label: '不是' },
    { value: 'IS_EMPTY', label: '为空' },
    { value: 'IS_NOT_EMPTY', label: '不为空' },
  ],
  agentStrategy: [
    { value: 'AGENT_REACT', label: 'Agent React' },
    { value: 'AGENT_FUNCTION_CALLING', label: 'Agent Function Calling' },
    { value: 'MCP_FUNCTION_CALLING', label: 'Mcp Function Calling' },
  ],
  getOperator(value: string) {
    for (const it of this.conditionOperators) {
      if (it.value === value) {
        return it.label;
      }
    }
  },
  initNodeDefaultData: (nodes: any) => {
    function deepMerge(obj1: any, obj2: any) {
      const result = { ...obj1 };

      for (const key in obj2) {
        if (Object.prototype.hasOwnProperty.call(obj2, key)) {
          result[key] =
            typeof obj2[key] === 'object' &&
            obj2[key] !== null &&
            !Array.isArray(obj2[key])
              ? deepMerge(obj1[key] || {}, obj2[key])
              : obj2[key];
        }
      }

      return result;
    }

    if (nodes && nodes.length > 0) {
      for (const node of nodes) {
        const configFormData = nodeCardForm[node.type];
        const nodeData = node.data;
        const configData = langUtils.clone(configFormData);
        nodeData.id = node.id;
        // nodeData.structOutput = undefined;
        nodeData.headers = undefined;
        const newData = deepMerge(configData, nodeData);
        Object.assign(node.data, newData);
      }
    }
  },
  getOutputItem(data: any, name: string) {
    const outputList = this.getOutputList(data);
    const findRecursively = (items: any[]): any => {
      if (!items) {
        return [];
      }
      for (const item of items) {
        if (item.name === name) {
          return item;
        }
        if (item.children && Array.isArray(item.children)) {
          const found = findRecursively(item.children);
          if (found) return found;
        }
      }
      return undefined;
    };

    return findRecursively(outputList);
  },
  getOutputList: (data: any) => {
    const outputList = [];
    if (!data) {
      return [];
    }

    if (data.triggersEnabled) {
      // START connector outputs must be available even before its config panel
      // has ever been opened; all downstream variable pickers call this path.
      synchronizeStartTriggerOutputs(data);
    }
    if (data.structOutput && data.structOutputEnabled) {
      outputList.push(...data.structOutput?.data);
    } else if (data.output && Array.isArray(data.output)) {
      outputList.push(...data.output);
    }
    if (data.triggersEnabled && !data.structOutputEnabled) {
      outputList.push(...data.structOutput?.data);
    }
    if (data.id === '1') {
      outputList.push(...data.variables);
    }
    return outputList;
  },
  getVariableLabel(variableSelector: any[]) {
    if (!variableSelector || variableSelector.length === 0) {
      return '';
    }
    // Resolve Pinia only when a component invokes this method. Resolving the
    // store at module evaluation time runs before a host can call
    // `app.use(pinia)` and makes importing the component library fail.
    const workflowStore = useWorkflowStore();
    const list = variableSelector;
    const node = workflowStore.getNodeById(list[0]);
    const labels = [];
    if (node && node.data) {
      labels.push(node.data.label);
    }
    for (let i = 1; i < list.length; i++) {
      const v = list[i];
      const item = this.getOutputItem(node.data, v);
      if (item) {
        labels.push(item.label || item.description);
      }
    }
    return labels.join('.');
  },
  splitExpressions(input: string) {
    const result: any[] = [];
    if (!input) {
      return result;
    }

    // 定义匹配 {{#...#}} 格式的正则表达式
    const pattern = /\{\{#.*?#\}\}/g;
    let lastEnd = 0;
    let match;

    while ((match = pattern.exec(input)) !== null) {
      // 添加表达式左侧的文本
      const left = input.substring(lastEnd, match.index);
      if (left) {
        result.push(left);
      }

      // 添加表达式本身
      const expression = match[0];
      result.push(expression);

      lastEnd = pattern.lastIndex;
    }

    // 添加最后一个表达式右侧的剩余文本
    const right = input.slice(Math.max(0, lastEnd));
    if (right) {
      result.push(right);
    }

    return result;
  },
};
