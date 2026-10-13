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

type StyleValue = string | Record<string, unknown>;

let openDrawerCount = 0;
let previousBodyOverflow = '';

function lockPageScroll() {
  if (typeof document === 'undefined') return;
  if (openDrawerCount++ === 0) {
    previousBodyOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
  }
}

function unlockPageScroll() {
  if (typeof document === 'undefined' || openDrawerCount === 0) return;
  if (--openDrawerCount === 0) document.body.style.overflow = previousBodyOverflow;
}

export const Drawer = defineComponent({
  name: 'AsDrawer',
  inheritAttrs: false,
  props: {
    bodyStyle: [String, Object] as PropType<StyleValue>,
    bodyClass: [String, Array, Object] as PropType<unknown>,
    closable: { type: Boolean, default: true },
    destroyOnClose: Boolean,
    keyboard: { type: Boolean, default: true },
    maskClosable: { type: Boolean, default: true },
    open: Boolean,
    placement: { type: String as PropType<'left' | 'right'>, default: 'right' },
    title: String,
    width: { type: [String, Number], default: 480 },
  },
  emits: ['update:open', 'close', 'afterOpenChange'],
  setup(props, { attrs, emit, slots }) {
    const panel = ref<HTMLElement | null>(null);
    const rendered = ref(props.open);
    const previousFocus = ref<HTMLElement | null>(null);
    const scrollLocked = ref(false);
    const close = () => {
      emit('update:open', false);
      emit('close');
    };

    watch(() => props.open, async (open) => {
      if (open) {
        rendered.value = true;
        if (!scrollLocked.value) {
          lockPageScroll();
          scrollLocked.value = true;
        }
        previousFocus.value = document.activeElement as HTMLElement | null;
        await nextTick();
        panel.value?.focus();
      } else {
        if (scrollLocked.value) {
          unlockPageScroll();
          scrollLocked.value = false;
        }
        previousFocus.value?.focus?.();
        previousFocus.value = null;
      }
      emit('afterOpenChange', open);
    });
    onBeforeUnmount(() => {
      if (scrollLocked.value) unlockPageScroll();
      previousFocus.value?.focus?.();
    });

    return () => h(Teleport, { to: 'body' }, h(Transition, {
      name: `as-drawer-${props.placement}`,
      onAfterLeave: () => { if (props.destroyOnClose) rendered.value = false; },
    }, () => (props.open || (!props.destroyOnClose && rendered.value)) && props.open
      ? h('div', {
          class: 'as-drawer-root',
          onKeydown: (event: KeyboardEvent) => { if (props.keyboard && event.key === 'Escape') close(); },
        }, [
          h('div', { class: 'as-drawer__mask', onClick: () => { if (props.maskClosable) close(); } }),
          h('aside', {
            ...attrs,
            ref: panel,
            class: ['as-drawer', 'as-hud-frame', 'as-hud-frame--drawer', `as-drawer--${props.placement}`, attrs.class],
            role: 'dialog',
            'aria-modal': 'true',
            tabindex: -1,
            style: [{ width: typeof props.width === 'number' ? `${props.width}px` : props.width }, attrs.style as never],
          }, [
            h('div', { class: 'as-hud-drawer__chrome', 'aria-hidden': 'true' }, [
              h('i', { class: 'as-hud-drawer__rail as-hud-drawer__rail--top' }),
              h('i', { class: 'as-hud-drawer__rail as-hud-drawer__rail--bottom' }),
              h('i', { class: 'as-hud-drawer__rail as-hud-drawer__rail--left' }),
              h('i', { class: 'as-hud-drawer__rail as-hud-drawer__rail--right' }),
            ]),
            (props.title || slots.title || slots.extra || props.closable) ? h('header', { class: 'as-drawer__header' }, [
              h('div', { class: 'as-drawer__title' }, slots.title?.() ?? props.title),
              h('div', { class: 'as-drawer__extra' }, [slots.extra?.(), props.closable ? h('button', { class: 'as-drawer__close', type: 'button', 'aria-label': '关闭', onClick: close }, '×') : null]),
            ]) : null,
            h('div', { class: ['as-drawer__body', props.bodyClass], style: props.bodyStyle }, slots.default?.()),
            slots.footer ? h('footer', { class: 'as-drawer__footer' }, slots.footer()) : null,
          ]),
        ])
      : null));
  },
});
