import { ref } from 'vue';

export function useNode(emit) {
  const isHovered = ref(false);
  const isRunning = ref(false);
  const isCompleted = ref(false);

  const onMouseEnter = () => {
    isHovered.value = true;
  };

  const onMouseLeave = () => {
    isHovered.value = false;
  };

  const duplicateNode = (nodeId) => {
    emit('duplicate', nodeId);
  };

  const deleteNode = (nodeId) => {
    emit('delete', nodeId);
  };

  const onConnectionPlusClick = (event, nodeId, handleId) => {
    emit('connection-plus-click', event, nodeId, handleId);
  };

  const truncateText = (text, maxLength) => {
    if (!text) return '';
    return text.length > maxLength ? text.substring(0, maxLength) + '...' : text;
  };

  return {
    isHovered,
    isRunning,
    isCompleted,
    onMouseEnter,
    onMouseLeave,
    duplicateNode,
    deleteNode,
    onConnectionPlusClick,
    truncateText,
  };
}

export const nodeColors = {
  start: '#10b981',
  llm: '#6366f1',
  agent: '#6366f1',
  code: '#84cc16',
  condition: '#f59e0b',
  http: '#06b6d4',
  userInput: '#3b82f6',
  end: '#ef4444',
};