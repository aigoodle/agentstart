import {
  Teleport,
  Transition,
  defineComponent,
  h,
  nextTick,
  onBeforeUnmount,
  ref,
  watch,
  type PropType,
} from 'vue';

import { Button } from './Button';

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
    okButtonProps: { type: Object as PropType<AnyRecord>, default: () => ({}) },
    cancelButtonProps: { type: Object as PropType<AnyRecord>, default: () => ({}) },
  },
  emits: ['update:open', 'update:visible', 'ok', 'cancel', 'afterClose'],
  setup(props, { attrs, emit, slots }) {
    const panel = ref<HTMLElement | null>(null);
    const previouslyFocused = ref<HTMLElement | null>(null);
    const close = (event?: Event) => {
      emit('update:open', false);
      emit('update:visible', false);
      emit('cancel', event);
    };
    const isOpen = () => props.open || props.visible;

    watch(isOpen, async (open) => {
      if (typeof document === 'undefined') return;
      if (open) {
        previouslyFocused.value = document.activeElement as HTMLElement | null;
        await nextTick();
        panel.value?.focus();
      } else {
        previouslyFocused.value?.focus?.();
        previouslyFocused.value = null;
      }
    });
    onBeforeUnmount(() => previouslyFocused.value?.focus?.());

    return () => h(Teleport, { to: 'body' }, h(Transition, {
      name: 'as-modal-fade',
      onAfterLeave: () => emit('afterClose'),
    }, () => isOpen() ? h('div', {
      class: ['as-modal-root', { 'as-modal-root--centered': props.centered }],
      onKeydown: (event: KeyboardEvent) => { if (props.keyboard && event.key === 'Escape') close(event); },
    }, [
      h('div', { class: 'as-modal__mask', onClick: (event: Event) => { if (props.maskClosable) close(event); } }),
      h('section', {
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
        h('header', { class: 'as-modal__head' }, [
          h('div', { class: 'as-modal__title' }, [
            h('span', { class: 'as-modal__title-text' }, slots.title?.() ?? [
              h('span', { class: 'as-modal-title' }, props.title),
            ]),
          ]),
          props.closable ? h('button', { type: 'button', class: 'as-modal__close', 'aria-label': '关闭', onClick: close }, '×') : null,
        ]),
        h('div', { class: 'as-modal__body' }, slots.default?.()),
        props.footer === null || props.footer === false ? null : h('footer', { class: 'as-modal__footer' }, slots.footer?.() ?? [
          h(Button, { ...props.cancelButtonProps, onClick: close }, () => props.cancelText),
          h(Button, { ...props.okButtonProps, type: 'primary', loading: props.confirmLoading, onClick: () => emit('ok') }, () => props.okText),
        ]),
      ]),
    ]) : null));
  },
}) as ReturnType<typeof defineComponent> & {
  confirm?: (options: AnyRecord) => void;
  info?: (options: AnyRecord) => void;
};
