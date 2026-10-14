import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import AgentStudioShell from './AgentStudioShell.vue';

vi.mock('./AppPermissionsModal.vue', () => ({ default: {
  props: ['open', 'appId', 'presentation'], emits: ['saved'],
  template: '<aside v-if="open" :data-app-id="appId" :data-presentation="presentation"><button @click="$emit(\'saved\')">saved</button></aside>',
} }));

describe('studio data permissions entry', () => {
  it('opens the inline permissions page for the application without changing the active tab', async () => {
    const wrapper = mount(AgentStudioShell, { props: { tab: 'orchestrate', agentName: 'App', appId: 'app-a' } });
    await wrapper.findAll('button').find(button => button.text() === '数据权限设置')!.trigger('click');
    expect(wrapper.find('aside[data-app-id="app-a"]').attributes('data-presentation')).toBe('inline');
    expect(wrapper.emitted('update:tab')).toBeUndefined();
    await wrapper.find('aside[data-app-id] button').trigger('click');
    expect(wrapper.emitted('permissions-saved')).toHaveLength(1);
  });

  it('requires a saved application ID before configuring permissions', () => {
    const wrapper = mount(AgentStudioShell, { props: { tab: 'orchestrate', agentName: 'App' } });
    expect(wrapper.findAll('button').find(button => button.text() === '数据权限设置')!.attributes('disabled')).toBeDefined();
  });
});
