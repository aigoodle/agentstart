import {
  Transition,
  defineComponent,
  h,
  ref,
  type PropType,
} from 'vue';

import { DialogHeader, DialogFooter } from '../shadcn/dialog';
import { useOverlayLayer } from './overlayLayer';
import { Button } from './Button';
import { Dialog, DialogContent, DialogOverlay, DialogPortal, DialogTitle } from './dialog';

type AnyRecord = Record<string, unknown>;


export const Modal = defineComponent({
  name: 'AsModal',
  inheritAttrs: false,
  props: {
    open: Boolean,
    visible: Boolean,
    title: String,
    width: { type: [String, Number], default: 520 },
    okText: { type: String, default: '确定' },
    cancelText: { type: String, default: '取消' },
    confirmLoading: Boolean,
    footer: { type: [String, Boolean, Object] as PropType<unknown>, default: undefined },
    maskClosable: { type: Boolean, default: true },
    closable: { type: Boolean, default: true },
    destroyOnClose: Boolean,
    keyboard: { type: Boolean, default: true },
    centered: Boolean,
    headless: Boolean,
    bodyStyle: [String, Object] as PropType<string | Record<string, unknown>>,
    okButtonProps: { type: Object as PropType<AnyRecord>, default: () => ({}) },
    cancelButtonProps: { type: Object as PropType<AnyRecord>, default: () => ({}) },
  },
  emits: ['update:open', 'update:visible', 'ok', 'cancel', 'afterClose'],
  setup(props, { attrs, emit, slots }) {
    const panel = ref<HTMLElement | null>(null);
    const isOpen = () => props.open || props.visible;
    const { layer, isTop: isTopModal } = useOverlayLayer(isOpen);
    const close = (event?: Event) => {
      emit('update:open', false);
      emit('update:visible', false);
      emit('cancel', event);
    };
    return () => h(Dialog, {
      open: isOpen(),
      // Dismissal is translated explicitly below to preserve cancel's Event payload.
    }, () => h(DialogPortal, { forceMount: true }, () => h(Transition, {
      name: 'as-modal-fade',
      onAfterLeave: () => emit('afterClose'),
    }, () => isOpen() ? h('div', {
      class: ['as-modal-root', { 'as-modal-root--centered': props.centered }],
      style: { zIndex: layer.value },
    }, [
      h(DialogOverlay, { asChild: true, forceMount: true }, () => h('div', {
        class: 'as-modal__mask',
        onClick: (event: Event) => { if (isTopModal() && props.maskClosable) close(event); },
      })),
      h(DialogContent, {
        asChild: true,
        forceMount: true,
        'aria-describedby': undefined,
        onEscapeKeyDown: (event: KeyboardEvent) => {
          // Keep controlled cancellation events and guard background layers.
          event.preventDefault();
          if (isTopModal() && props.keyboard) close(event);
        },
        onPointerDownOutside: (event: CustomEvent<{ originalEvent: PointerEvent }>) => {
          event.preventDefault();
        },
        onInteractOutside: (event: Event) => event.preventDefault(),
        onOpenAutoFocus: (event: Event) => {
          event.preventDefault();
          if (isTopModal()) panel.value?.focus();
        },
        onCloseAutoFocus: (event: Event) => event.preventDefault(),
      }, () => h('section', {
        ...attrs,
        ref: panel,
        class: ['as-modal', 'as-hud-frame', 'as-hud-frame--modal', attrs.class],
        role: 'dialog',
        'aria-modal': 'true',
        tabindex: -1,
        style: [{ width: typeof props.width === 'number' ? `${props.width}px` : props.width }, attrs.style as never],
      }, [
        h('div', { class: 'as-hud-modal__chrome', 'aria-hidden': 'true' }, [
          h('i', { class: 'as-hud-modal__corner as-hud-modal__corner--tl' }),
          h('i', { class: 'as-hud-modal__corner as-hud-modal__corner--tr' }),
          h('i', { class: 'as-hud-modal__corner as-hud-modal__corner--bl' }),
          h('i', { class: 'as-hud-modal__corner as-hud-modal__corner--br' }),
          h('i', { class: 'as-hud-modal__rail as-hud-modal__rail--top' }),
          h('i', { class: 'as-hud-modal__rail as-hud-modal__rail--bottom' }),
          h('i', { class: 'as-hud-modal__rail as-hud-modal__rail--left' }),
          h('i', { class: 'as-hud-modal__rail as-hud-modal__rail--right' }),
        ]),
        props.headless ? h(DialogTitle, { class: 'sr-only' }, () => props.title ?? '对话框') : h(DialogHeader, { class: 'as-modal__head flex-row' }, () => [
          h(DialogTitle, { as: 'div', class: 'as-modal__title' }, () => [
            h('span', { class: 'as-modal__title-text' }, slots.title?.() ?? [
              h('span', { class: 'as-modal-title' }, props.title),
            ]),
          ]),
          props.closable ? h(Button, { type: 'text', size: 'small', htmlType: 'button', class: 'as-modal__close', style: { position: 'absolute', right: '16px', left: 'auto', top: '12px', bottom: 'auto', margin: '0', width: '32px', height: '32px', padding: '0' }, 'aria-label': '关闭', onClick: close }, () => '×') : null,
        ]),
        h('div', { class: 'as-modal__body', style: props.bodyStyle }, slots.default?.()),
        props.footer === null || props.footer === false ? null : h(DialogFooter, { class: 'as-modal__footer' }, () => slots.footer?.() ?? [
          h(Button, { ...props.cancelButtonProps, onClick: close }, () => props.cancelText),
          h(Button, { ...props.okButtonProps, type: 'primary', loading: props.confirmLoading, onClick: () => emit('ok') }, () => props.okText),
        ]),
      ])),
    ]) : null)));
  },
}) as ReturnType<typeof defineComponent> & {
  confirm?: (options: AnyRecord) => void;
  info?: (options: AnyRecord) => void;
};
