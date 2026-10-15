import { mount } from '@vue/test-utils';
import { h } from 'vue';
import { describe, expect, it, vi } from 'vitest';
import Card from './Card.vue';

describe('shared shadcn Card contract', () => {
  it('composes metadata and actions into one shadcn footer', () => {
    const wrapper = mount(Card, { props: { variant: 'management', title: '服务', description: '说明' }, slots: {
      icon: 'M', meta: 'CONNECTED', actions: () => h('button', '编辑'),
    } });
    expect(wrapper.attributes('data-slot')).toBe('card');
    expect(wrapper.findAll('[data-slot=card-footer]')).toHaveLength(1);
    expect(wrapper.find('[data-slot=card-footer]').text()).toContain('CONNECTED');
    expect(wrapper.find('[data-slot=card-footer] button').text()).toBe('编辑');
    expect(wrapper.attributes('tabindex')).toBeUndefined();
    wrapper.unmount();
  });

  it('activates an interactive card once by keyboard without activating nested controls', async () => {
    const clicked = vi.fn();
    const wrapper = mount(Card, { props: { variant: 'management', title: '应用', onClick: clicked }, slots: { actions: () => h('button', '操作') } });
    await wrapper.trigger('keydown', { key: 'Enter' });
    expect(clicked).toHaveBeenCalledOnce();
    await wrapper.find('button').trigger('keydown', { key: 'Enter' });
    expect(clicked).toHaveBeenCalledOnce();
    wrapper.unmount();
  });

  it('shares the create variant and blocks disabled activation', async () => {
    const clicked = vi.fn();
    const wrapper = mount(Card, { props: { variant: 'create', title: '新建知识库', subtitle: '导入文档', disabled: true, onClick: clicked } });
    expect(wrapper.find('[data-slot=card-header]').exists()).toBe(false);
    expect(wrapper.find('[data-slot=card-title]').text()).toBe('新建知识库');
    await wrapper.trigger('click');
    await wrapper.trigger('keydown', { key: 'Enter' });
    expect(clicked).not.toHaveBeenCalled();
    wrapper.unmount();
  });
});
