import { mount } from '@vue/test-utils';
import { createPinia } from 'pinia';
import { describe, expect, it } from 'vitest';
import { defineComponent, h, nextTick } from 'vue';
import { useVueFlow } from '@vue-flow/core';

import PromptEditorTagPanel from './PromptEditorTagPanel.vue';
import { useWorkflowGraph } from '../workflow/composables/useWorkflowGraph';

describe('live workflow variables', () => {
  it('positions the teleported panel at its trigger as soon as it opens', async () => {
    const trigger = document.createElement('button');
    trigger.getBoundingClientRect = () => ({
      left: 240,
      top: 120,
      right: 280,
      bottom: 150,
      width: 40,
      height: 30,
      x: 240,
      y: 120,
      toJSON: () => ({}),
    });
    document.body.appendChild(trigger);

    const wrapper = mount(PromptEditorTagPanel, {
      props: { show: false, nodeId: 'http', targetElement: trigger },
      global: { plugins: [createPinia()] },
      attachTo: document.body,
    });

    await wrapper.setProps({ show: true });
    await nextTick();

    const panel = document.body.querySelector<HTMLElement>('.wf-tag-panel')!;
    expect(panel.style.left).toBe('240px');
    expect(panel.style.top).toBe('156px');
    expect(panel.style.left).not.toBe('-9999px');

    wrapper.unmount();
    trigger.remove();
  });

  it('updates an open picker after parameter edits, graph replacement, and connection changes', async () => {
    let flow!: ReturnType<typeof useVueFlow>;
    const wrapper = mount(defineComponent({
      setup() {
        flow = useVueFlow();
        useWorkflowGraph(flow.getNodes, flow.getEdges);
        return () => h(PromptEditorTagPanel, { show: true, nodeId: 'http' });
      },
    }), { global: { plugins: [createPinia()], stubs: { teleport: true } } });

    const start = {
      id: '1', type: 'START', position: { x: 0, y: 0 },
      data: { id: '1', label: '开始', variables: [], output: [] },
    };
    flow.setNodes([
      start,
      { id: 'code', position: { x: 100, y: 0 }, data: { label: '代码', output: [] } },
      { id: 'http', position: { x: 200, y: 0 }, data: {} },
    ]);
    flow.setEdges([{ id: 'a', source: '1', target: 'code' }, { id: 'b', source: 'code', target: 'http' }]);
    await nextTick();
    expect(wrapper.text()).toContain('开始');

    flow.updateNode('1', { data: { ...flow.findNode('1')!.data, variables: [{ name: 'customer', label: '客户姓名', type: 'string' }] } });
    await nextTick();
    expect(wrapper.text()).toContain('客户姓名');
    await wrapper.findAll('.wf-tag-item').find(item => item.text().includes('客户姓名'))!.trigger('click');
    expect(wrapper.findComponent(PromptEditorTagPanel).emitted('select')?.[0]?.[0]).toEqual(['1', 'customer']);

    flow.findNode('1')!.data.variables[0] = { name: 'age', label: '客户年龄', type: 'number' };
    flow.updateNode('code', { data: { label: '代码', output: [{ name: 'result', label: '处理结果', type: 'object', children: [{ name: 'value', label: '结果值', type: 'string' }] }] } });
    await nextTick();
    expect(wrapper.text()).not.toContain('客户姓名');
    expect(wrapper.text()).toContain('客户年龄');
    expect(wrapper.text()).toContain('number');
    expect(wrapper.text()).toContain('处理结果');
    await wrapper.find('.wf-tag-expand').trigger('click');
    expect(wrapper.text()).toContain('结果值');

    const snapshot = JSON.parse(JSON.stringify(flow.getNodes.value));
    flow.findNode('1')!.data.variables.splice(0, 1);
    await nextTick();
    expect(wrapper.text()).not.toContain('客户年龄');
    flow.setNodes(snapshot);
    await nextTick();
    expect(wrapper.text()).toContain('客户年龄');

    flow.setEdges([]);
    await nextTick();
    expect(wrapper.text()).not.toContain('客户年龄');
    expect(wrapper.text()).not.toContain('处理结果');
    flow.setEdges([{ id: 'direct', source: '1', target: 'http' }]);
    await nextTick();
    expect(wrapper.text()).toContain('客户年龄');
    flow.removeNodes('1');
    await nextTick();
    expect(wrapper.text()).not.toContain('客户年龄');
    wrapper.unmount();
  });
});
