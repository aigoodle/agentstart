import { createApp, defineComponent, h, ref, type VNodeChild } from 'vue';
import { Button } from './Button';
import { Modal } from './Modal';
import { message } from '../message';

type Options = Record<string, unknown>;
function show(options: Options, info: boolean) {
  if (typeof document === 'undefined') return;
  const root = document.createElement('div'); document.body.append(root);
  const open = ref(true), loading = ref(false);
  const app = createApp(defineComponent({
    setup() {
      const cancel = () => { if (loading.value) return; (options.onCancel as (() => void) | undefined)?.(); open.value = false; };
      const ok = async () => {
        if (loading.value) return;
        loading.value = true;
        try { await (options.onOk as (() => unknown) | undefined)?.(); open.value = false; }
        catch (error) { message.error(error instanceof Error ? error.message : String(error)); }
        finally { loading.value = false; }
      };
      const dispose = () => { app.unmount(); root.remove(); };
      return () => h(Modal, {
        ...options, open: open.value, title: String(options.title ?? (info ? '提示' : '确认操作')),
        confirmLoading: loading.value, closable: !loading.value, keyboard: !loading.value,
        okButtonProps: { ...(options.okType === 'danger' ? { danger: true } : {}), ...(options.okButtonProps as Options ?? {}) },
        cancelButtonProps: { disabled: loading.value }, maskClosable: options.maskClosable ?? false,
        onCancel: cancel, onOk: ok, onAfterClose: dispose,
      }, {
        default: () => options.content as VNodeChild,
        ...(info ? { footer: () => h(Button, { type: 'primary', loading: loading.value, onClick: ok }, () => String(options.okText ?? '确定')) } : {}),
      });
    },
  }));
  app.mount(root);
}
export const modalService = { confirm: (options: Options) => show(options, false), info: (options: Options) => show(options, true) };

/** Await the shared shadcn Dialog before performing a destructive action. */
export function confirmAction(content: string): Promise<boolean> {
  if (typeof document === 'undefined') return Promise.resolve(false);
  return new Promise(resolve => {
    modalService.confirm({
      title: '确认删除', content, okText: '删除', cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => resolve(true), onCancel: () => resolve(false),
    });
  });
}
