import { mount, flushPromises } from '@vue/test-utils';
import { describe, it, expect, vi } from 'vitest';
import { defineComponent } from 'vue';
import DatasetBasicInfoModal from './DatasetBasicInfoModal.vue';
import { Modal } from '../../ui';

function setup(save = vi.fn().mockResolvedValue({ id: 'ds' })) {
  const wrapper = mount(DatasetBasicInfoModal, {
    props: { open: true, dataset: { id: 'ds', name: '手册', description: '原描述' }, save },
    global: { stubs: { AsModal: defineComponent({ name: 'AsModal', template: '<div><slot /></div>' }) } },
  });
  return { wrapper, save };
}
describe('知识库基本信息编辑', () => {
  it('回填信息并仅提交去除首尾空格的名称和描述，支持清空描述', async () => {
    const { wrapper, save } = setup();
    expect((wrapper.get('input').element as HTMLInputElement).value).toBe('手册');
    await wrapper.get('input').setValue(' 新名称 ');
    await wrapper.get('textarea').setValue('');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(save).toHaveBeenCalledExactlyOnceWith('ds', { name: '新名称', description: '' });
    expect(wrapper.emitted('saved')?.[0]?.[0]).toMatchObject({ id: 'ds', name: '新名称', description: '' });
    expect(wrapper.emitted('update:open')).toEqual([[false]]);
  });
  it('拒绝空名称，保存失败保留输入并允许重试', async () => {
    const { wrapper, save } = setup(vi.fn().mockRejectedValueOnce(new Error('服务不可用')).mockResolvedValue({ id: 'ds' }));
    await wrapper.get('input').setValue('   ');
    await wrapper.get('form').trigger('submit');
    expect(save).not.toHaveBeenCalled();
    await wrapper.get('input').setValue('重试名称');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(wrapper.get('[role=alert]').text()).toBe('服务不可用');
    expect(wrapper.emitted('update:open')).toBeUndefined();
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(save).toHaveBeenCalledTimes(2);
    expect(wrapper.emitted('saved')).toHaveLength(1);
  });
  it('保存过程中阻止重复提交和关闭', async () => {
    let resolve!: (value: { id: string; name: string }) => void;
    const { wrapper, save } = setup(vi.fn(() => new Promise(r => { resolve = r; })) as any);
    await wrapper.get('form').trigger('submit');
    await wrapper.get('form').trigger('submit');
    wrapper.findComponent(Modal).vm.$emit('cancel');
    expect(save).toHaveBeenCalledTimes(1);
    expect(wrapper.emitted('update:open')).toBeUndefined();
    resolve({ id: 'ds', name: '手册' });
    await flushPromises();
    expect(wrapper.emitted('update:open')).toEqual([[false]]);
  });
});
