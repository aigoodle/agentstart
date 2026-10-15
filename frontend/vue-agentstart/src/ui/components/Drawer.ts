import { defineComponent, h, watch, type PropType } from 'vue';
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetFooter } from '../shadcn/sheet';
import { useOverlayLayer } from './overlayLayer';
type StyleValue = string | Record<string, unknown>;
export const Drawer = defineComponent({
  name: 'AsDrawer', inheritAttrs: false,
  props: {
    bodyStyle: [String, Object] as PropType<StyleValue>,
    bodyClass: [String, Array, Object] as PropType<unknown>,
    closable: { type: Boolean, default: true }, destroyOnClose: Boolean,
    keyboard: { type: Boolean, default: true }, maskClosable: { type: Boolean, default: true },
    open: Boolean, placement: { type: String as PropType<'left' | 'right'>, default: 'right' },
    title: String, width: { type: [String, Number], default: 480 },
  },
  emits: ['update:open', 'close', 'afterOpenChange'],
  setup(props, { attrs, emit, slots }) {
    const { layer, isTop } = useOverlayLayer(() => props.open);
    const close = () => { emit('update:open', false); emit('close'); };
    watch(() => props.open, open => emit('afterOpenChange', open));
    return () => h(Sheet, { open: props.open, 'onUpdate:open': (open: boolean) => { if (!open) close(); } }, () => h(SheetContent, {
      ...attrs, side: props.placement, showClose: props.closable,
      class: ['as-sheet-hud flex flex-col p-0 gap-0 text-popover-foreground max-w-[100vw] sm:max-w-[100vw]', attrs.class],
      style: [{ width: typeof props.width === 'number' ? `${props.width}px` : props.width, zIndex: layer.value }, attrs.style as never],
      overlayStyle: { zIndex: layer.value - 1 }, 'aria-describedby': undefined,
      onEscapeKeyDown: (event: KeyboardEvent) => { if (!props.keyboard || !isTop()) event.preventDefault(); },
      onInteractOutside: (event: Event) => { if (!props.maskClosable || !isTop()) event.preventDefault(); },
      onCloseAutoFocus: (event: Event) => event.preventDefault(),
    }, () => [
      h('div', { class: 'as-sheet-hud__chrome', 'aria-hidden': 'true' }),
      h('div', { class: 'as-sheet-hud__rails', 'aria-hidden': 'true' }),
      h(SheetHeader, { class: ['flex flex-row items-center justify-between gap-3 border-b border-border px-5 py-2.5', props.closable && 'pr-14'] }, () => [
        h(SheetTitle, { class: !props.title && !slots.title ? 'sr-only' : 'min-w-0 flex-1 text-base leading-6 m-0' }, () => slots.title?.() ?? props.title ?? '详情'),
        slots.extra ? h('div', { class: 'flex items-center gap-2' }, slots.extra()) : null,
      ]),
      h('div', { class: ['min-h-0 flex-1 overflow-auto p-6', props.bodyClass], style: props.bodyStyle }, slots.default?.()),
      slots.footer ? h(SheetFooter, { class: 'border-t border-border p-4' }, slots.footer) : null,
    ]));
  },
});
