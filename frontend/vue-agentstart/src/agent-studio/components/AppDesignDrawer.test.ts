import { shallowMount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import AppDesignDrawer from './AppDesignDrawer.vue';
import type { AgentEntity } from '../types';

describe('application debug panel', () => {
  it.each(['workflow', 'chatflow'] as const)('keeps %s debug content inside the modal drawer', async (mode) => {
    const wrapper = shallowMount(AppDesignDrawer, {
      props: {
        open: false,
        app: null,
        chatConfig: { src: '/chat/', params: { appId: 'app-debug' } },
      },
      global: {
        renderStubDefaultSlot: true,
        stubs: {
          AsDrawer: { template: '<section data-modal-drawer><slot name="extra" /><slot /></section>' },
          Button: { template: '<button><slot /></button>' },
          teleport: false,
        },
      },
    });
    try {
      await wrapper.setProps({ open: true, app: { id: 'app-debug', name: 'Debug app', mode } as AgentEntity });
      const debug = wrapper.findAll('button').find(button => button.text() === '调试');
      expect(debug).toBeDefined();
      expect(wrapper.find('.dr-debug-panel').exists()).toBe(false);
      await debug!.trigger('click');
      expect(wrapper.find('[data-modal-drawer] .dr-debug-panel').exists()).toBe(true);
      const panel = wrapper.findComponent({ name: mode === 'workflow' ? 'WorkflowDebugPanel' : 'ChatIframePanel' });
      expect(panel.exists()).toBe(true);
      panel.vm.$emit('close');
      await wrapper.vm.$nextTick();
      expect(wrapper.find('.dr-debug-panel').exists()).toBe(false);
    } finally {
      wrapper.unmount();
    }
  });
});
