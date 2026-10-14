import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';

import RecallTestingPanelV2 from './RecallTestingPanelV2.vue';

describe('RecallTestingPanelV2 result rendering', () => {
  it('renders returned content and score in the document', () => {
    const wrapper = mount(RecallTestingPanelV2, {
      props: { hits: [{ segmentId: 'segment-1', documentName: 'upload.txt', content: 'Reference number is 42.', score: 0.661 }] },
      global: { stubs: { RetrievalConfigPopover: true } },
    });
    expect(wrapper.find('.kh-recall-hit-body').exists()).toBe(true);
    expect(wrapper.find('.kh-recall-hit-body').text()).toBe('Reference number is 42.');
    expect(wrapper.text()).toContain('0.661');
    wrapper.unmount();
  });

  it('renders guidance when no result exists', () => {
    const wrapper = mount(RecallTestingPanelV2, {
      global: { stubs: { RetrievalConfigPopover: true } },
    });
    expect(wrapper.find('.kh-recall-right-empty').text()).toContain('召回测试结果显示在这里');
    wrapper.unmount();
  });
});
