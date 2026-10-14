import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import AppPermissionsModal from './AppPermissionsModal.vue';

vi.mock('../../ui', () => ({
  Modal: { props: ['open', 'okButtonProps'], emits: ['ok'], template: '<div><slot /><button :disabled="okButtonProps.disabled" @click="$emit(\'ok\')">save</button></div>' },
  Drawer: { props: ['open'], template: '<aside><slot /><slot name="footer" /></aside>' },
  Spin: { template: '<div><slot /></div>' },
  Form: { template: '<div><slot /></div>' },
  FormItem: { template: '<div><slot /></div>' },
  Button: { template: '<button><slot /></button>' },
  Checkbox: { template: '<div />' }, Input: { template: '<div />' }, Select: { template: '<div />' },
  message: { error: vi.fn(), success: vi.fn(), warning: vi.fn() },
}));

describe('application permission editor', () => {
  it('saves permissions from the right drawer', async () => {
    const settings = { mode: 'ALL', grants: [] };
    const api = { getPermissions: vi.fn().mockResolvedValue(settings), updatePermissions: vi.fn().mockResolvedValue(settings) };
    const wrapper = mount(AppPermissionsModal, { props: { open: true, appId: 'a', appName: 'App', tenantId: 'tenant-a', presentation: 'drawer', api: api as any } });
    await flushPromises();
    expect(wrapper.find('aside').exists()).toBe(true);
    expect(wrapper.text()).toContain('租户范围：tenant-a');
    await wrapper.findAll('button').find(button => button.text() === '保存数据权限')!.trigger('click');
    await flushPromises();
    expect(api.updatePermissions).toHaveBeenCalledWith('a', settings);
    expect(wrapper.emitted('update:open')).toEqual([[false]]);
  });

  it('renders inline and keeps the settings page open after saving', async () => {
    const settings = { mode: 'ALL', grants: [] };
    const api = { getPermissions: vi.fn().mockResolvedValue(settings), updatePermissions: vi.fn().mockResolvedValue(settings) };
    const wrapper = mount(AppPermissionsModal, { props: { open: true, appId: 'a', appName: 'App', presentation: 'inline', api: api as any } });
    await flushPromises();
    expect(wrapper.find('section.permission-page').exists()).toBe(true);
    expect(wrapper.find('aside').exists()).toBe(false);
    await wrapper.findAll('button').find(button => button.text() === '保存数据权限')!.trigger('click');
    await flushPromises();
    expect(api.updatePermissions).toHaveBeenCalledWith('a', settings);
    expect(wrapper.emitted('saved')).toHaveLength(1);
    expect(wrapper.emitted('update:open')).toBeUndefined();
  });

  it('never overwrites permissions when loading fails', async () => {
    const api = { getPermissions: vi.fn().mockRejectedValue(new Error('forbidden')), updatePermissions: vi.fn() };
    const wrapper = mount(AppPermissionsModal, { props: { open: true, appId: 'a', appName: 'App', api: api as any } });
    await flushPromises();
    expect(wrapper.find('button').attributes('disabled')).toBeDefined();
    await wrapper.find('button').trigger('click');
    expect(api.updatePermissions).not.toHaveBeenCalled();
  });

  it('preserves descendant inheritance and saves the complete settings', async () => {
    const settings = { mode: 'RESTRICTED', grants: [{ type: 'DEPARTMENT', subjectId: 'project', includeDescendants: true }] };
    const api = { getPermissions: vi.fn().mockResolvedValue(settings), updatePermissions: vi.fn().mockResolvedValue(settings) };
    const wrapper = mount(AppPermissionsModal, { props: { open: true, appId: 'a', appName: 'App', api: api as any } });
    await flushPromises();
    const buttons = wrapper.findAll('button');
    await buttons[buttons.length - 1]!.trigger('click');
    await flushPromises();
    expect(api.updatePermissions).toHaveBeenCalledWith('a', settings);
    expect(wrapper.emitted('saved')).toHaveLength(1);
  });
});
