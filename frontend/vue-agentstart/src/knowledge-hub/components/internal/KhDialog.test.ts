/**
 * Interaction tests for KhDialog — the confirm/prompt primitive that replaced
 * window.confirm / window.prompt.
 *
 * KhDialog Teleports to document.body, so we clean up between tests and query
 * the body directly rather than through the wrapper.
 */
import { mount, type VueWrapper } from '@vue/test-utils';
import { afterEach, describe, expect, it } from 'vitest';
import { defineComponent, h, nextTick } from 'vue';

import { provideKhI18n } from '../../i18n';
import KhDialog from './KhDialog.vue';

let currentWrapper: null | VueWrapper<any> = null;

afterEach(() => {
  currentWrapper?.unmount();
  currentWrapper = null;
  // Clear anything else that leaked into body (extra teleports, orphan mounts).
  document.body.innerHTML = '';
});

function mountDialog(props: Record<string, unknown> = {}) {
  const Root = defineComponent({
    setup() {
      provideKhI18n(() => 'zh-CN');
      return () => h(KhDialog, props as any);
    },
  });
  currentWrapper = mount(Root, { attachTo: document.body });
  return currentWrapper.findComponent(KhDialog);
}

describe('KhDialog', () => {
  it('renders nothing when :open is false', async () => {
    mountDialog({ open: false, title: 'X' });
    await nextTick();
    expect(document.body.querySelector('.khd-panel')).toBeNull();
  });

  it('renders the title and content when :open is true', async () => {
    mountDialog({ open: true, title: 'Delete?', content: 'Are you sure?' });
    await nextTick();
    const panel = document.body.querySelector('.khd-panel');
    expect(panel).not.toBeNull();
    expect(panel!.textContent).toContain('Delete?');
    expect(panel!.textContent).toContain('Are you sure?');
  });

  it('emits confirm with undefined when there is no prompt', async () => {
    const dlg = mountDialog({ open: true, title: 'X' });
    await nextTick();
    const okBtn = document.body.querySelector<HTMLButtonElement>(
      '.khd-btn-primary',
    );
    expect(okBtn).not.toBeNull();
    okBtn!.click();
    await nextTick();
    const emitted = dlg.emitted();
    expect(emitted.confirm).toBeDefined();
    expect(emitted.confirm![0]).toEqual([undefined]);
  });

  it('renders an input when promptDefault is set + emits prompt value on OK', async () => {
    const dlg = mountDialog({
      open: true,
      title: 'Name',
      promptDefault: 'Untitled',
    });
    await nextTick();
    const input = document.body.querySelector<HTMLInputElement>('.khd-input');
    expect(input).not.toBeNull();
    expect(input!.value).toBe('Untitled');

    input!.value = 'My KB';
    input!.dispatchEvent(new Event('input'));
    await nextTick();

    document.body
      .querySelector<HTMLButtonElement>('.khd-btn-primary')!
      .click();
    await nextTick();

    expect(dlg.emitted().confirm![0]).toEqual(['My KB']);
  });

  it('disables OK when the prompt input is empty', async () => {
    mountDialog({ open: true, title: 'Name', promptDefault: '' });
    await nextTick();
    // The button we look at is *primary* (not danger) because :danger is not set.
    const okBtn = document.body.querySelector<HTMLButtonElement>(
      '.khd-btn-primary',
    );
    expect(okBtn).not.toBeNull();
    expect(okBtn!.disabled).toBe(true);
  });

  it('emits cancel + update:open(false) when Cancel is clicked', async () => {
    const dlg = mountDialog({ open: true, title: 'X' });
    await nextTick();
    document.body
      .querySelector<HTMLButtonElement>('.khd-btn-secondary')!
      .click();
    await nextTick();
    expect(dlg.emitted().cancel).toBeDefined();
    expect(dlg.emitted()['update:open']).toEqual([[false]]);
  });

  it('uses the danger variant when :danger', async () => {
    mountDialog({ open: true, title: 'Delete?', danger: true });
    await nextTick();
    // Only the danger button should exist — no primary variant.
    expect(document.body.querySelector('.khd-btn-danger')).not.toBeNull();
    // The other button is .khd-btn-secondary (cancel), never .khd-btn-primary.
    const buttons = [
      ...document.body.querySelectorAll<HTMLButtonElement>('.khd-btn'),
    ];
    expect(buttons.some((b) => b.classList.contains('khd-btn-primary'))).toBe(
      false,
    );
    expect(buttons.some((b) => b.classList.contains('khd-btn-danger'))).toBe(
      true,
    );
  });

  it('honors okText / cancelText overrides', async () => {
    mountDialog({
      open: true,
      title: 'X',
      okText: 'Yes please',
      cancelText: 'Nope',
    });
    await nextTick();
    const buttons = [
      ...document.body.querySelectorAll<HTMLButtonElement>('.khd-btn'),
    ].map((b) => b.textContent?.trim());
    expect(buttons).toEqual(expect.arrayContaining(['Yes please', 'Nope']));
  });
});
