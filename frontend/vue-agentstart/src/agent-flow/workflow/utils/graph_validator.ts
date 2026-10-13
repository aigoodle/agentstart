export interface WorkflowGraphIssue {
  code: string;
  message: string;
  nodeId?: string;
  edgeId?: string;
}

const text = (value: unknown) => typeof value === 'string' && value.trim().length > 0;

/** Fast client-side mirror of the backend P0 graph checks. The backend remains authoritative. */
export function validateWorkflowGraph(graph: any): WorkflowGraphIssue[] {
  const nodes = Array.isArray(graph?.nodes) ? graph.nodes : [];
  const edges = Array.isArray(graph?.edges) ? graph.edges : [];
  const issues: WorkflowGraphIssue[] = [];
  const ids = new Set<string>();

  for (const node of nodes) {
    const id = String(node?.id ?? '');
    if (!text(id)) issues.push({ code: 'node_id_required', message: '存在缺少 ID 的节点' });
    else if (ids.has(id)) issues.push({ code: 'duplicate_node_id', nodeId: id, message: `节点 ID 重复：${id}` });
    ids.add(id);
  }

  const starts = nodes.filter((node: any) => node.type === 'START');
  const ends = nodes.filter((node: any) => node.type === 'END');
  if (starts.length !== 1) issues.push({ code: 'start_count', message: `必须且只能有 1 个开始节点，当前为 ${starts.length} 个` });
  if (ends.length === 0) issues.push({ code: 'end_count', message: '至少需要 1 个结束节点' });

  const adjacency = new Map<string, string[]>();
  const indegree = new Map<string, number>([...ids].map((id) => [id, 0]));
  const edgeKeys = new Set<string>();
  for (const edge of edges) {
    const source = String(edge?.source ?? '');
    const target = String(edge?.target ?? '');
    if (!ids.has(source) || !ids.has(target)) {
      issues.push({ code: 'edge_endpoint_missing', edgeId: edge?.id, message: `连线 ${edge?.id || ''} 指向不存在的节点` });
      continue;
    }
    const edgeKey = `${source}\0${target}\0${edge?.sourceHandle ?? ''}`;
    if (edgeKeys.has(edgeKey)) issues.push({ code: 'duplicate_edge', edgeId: edge?.id, message: `存在重复连线：${source} → ${target}` });
    edgeKeys.add(edgeKey);
    const sourceNode = nodes.find((node: any) => String(node.id) === source);
    const isBranch = sourceNode?.type === 'IF_ELSE' || sourceNode?.type === 'QUESTION_CLASSIFIER';
    if (isBranch && !text(edge?.sourceHandle)) issues.push({ code: 'branch_handle_required', edgeId: edge?.id, message: `分支节点「${sourceNode?.data?.label || source}」的连线缺少 handle` });
    if (!isBranch && text(edge?.sourceHandle)) issues.push({ code: 'unexpected_branch_handle', edgeId: edge?.id, message: `普通节点「${sourceNode?.data?.label || source}」不能使用分支 handle` });
    adjacency.set(source, [...(adjacency.get(source) ?? []), target]);
    indegree.set(target, (indegree.get(target) ?? 0) + 1);
  }

  if (starts.some((start: any) => edges.some((edge: any) => String(edge.target) === String(start.id)))) {
    issues.push({ code: 'start_has_incoming', message: '开始节点不能有入边' });
  }
  if (ends.some((end: any) => edges.some((edge: any) => String(edge.source) === String(end.id)))) {
    issues.push({ code: 'end_has_outgoing', message: '结束节点不能有出边' });
  }

  const roots = [...indegree].filter(([, degree]) => degree === 0).map(([id]) => id);
  let visited = 0;
  while (roots.length) {
    const id = roots.shift()!;
    visited += 1;
    for (const target of adjacency.get(id) ?? []) {
      const degree = (indegree.get(target) ?? 0) - 1;
      indegree.set(target, degree);
      if (degree === 0) roots.push(target);
    }
  }
  if (visited !== ids.size) issues.push({ code: 'cycle_detected', message: '普通工作流连线存在环，可能造成永久等待' });

  if (starts.length === 1) {
    const reachable = new Set<string>();
    const queue = [String(starts[0].id)];
    while (queue.length) {
      const id = queue.shift()!;
      if (reachable.has(id)) continue;
      reachable.add(id);
      queue.push(...(adjacency.get(id) ?? []));
    }
    const unreachable = nodes.filter((node: any) => !reachable.has(String(node.id)));
    if (unreachable.length) issues.push({ code: 'unreachable_nodes', message: `存在 ${unreachable.length} 个开始节点不可达的节点` });
  }

  for (const node of nodes) {
    const data = node?.data ?? {};
    const nodeId = String(node?.id ?? '');
    if (node.type === 'VIDEO_GENERATION') {
      const model = data.model ?? {};
      if (!text(model.modelId) && !(text(model.providerName) && text(model.modelName)))
        issues.push({ code: 'video_model_required', nodeId, message: `视频生成节点「${data.label || nodeId}」需要选择视频模型` });
      if (!text(data.prompt))
        issues.push({ code: 'video_prompt_required', nodeId, message: `视频生成节点「${data.label || nodeId}」需要填写提示词` });
    }
    if (node.type === 'WAIT_EVENT' && !text(data.correlationKey)) {
      issues.push({ code: 'correlation_key_required', nodeId, message: `等待事件节点「${data.label || nodeId}」缺少 correlationKey` });
    }
    if (node.type === 'SLEEP_UNTIL') {
      const hasUntil = text(data.until);
      const hasDelay = Number(data.delayMillis) > 0;
      if (hasUntil === hasDelay) issues.push({ code: 'sleep_schedule_invalid', nodeId, message: `定时等待节点「${data.label || nodeId}」必须且只能配置绝对时间或延迟` });
      if (hasUntil && Number.isNaN(Date.parse(data.until))) issues.push({ code: 'sleep_until_invalid', nodeId, message: `定时等待节点「${data.label || nodeId}」的时间格式无效` });
    }
    if (node.type === 'APPROVAL' && data.timeoutStrategy === 'ESCALATE' && !text(data.escalationCorrelationKey)) {
      issues.push({ code: 'escalation_key_required', nodeId, message: `审批节点「${data.label || nodeId}」配置升级时必须填写升级 correlationKey` });
    }
  }
  return issues;
}
