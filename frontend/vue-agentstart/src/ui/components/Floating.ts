import { defineComponent, getCurrentInstance, h, inject, provide, ref, type InjectionKey, type PropType } from 'vue';
import { Popover as UiPopover, PopoverContent, PopoverTrigger } from '../shadcn/popover';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '../shadcn/dropdown-menu';
import { Button } from './Button';

const DropdownKey: InjectionKey<boolean> = Symbol('AgentStartDropdown');

export const Popover = defineComponent({
  name: 'AsPopover',
  props: { open: { type: Boolean, default: undefined }, title: String, trigger: String,
    placement: { type: String, default: 'bottomLeft' }, overlayClassName: String, matchTriggerWidth: Boolean,
    flipOnOverflow: { type: Boolean, default: true },
  },
  emits: ['update:open'],
  setup(props, { slots, emit }) {
    const local = ref(false);
    const setOpen = (open: boolean) => { local.value = open; emit('update:open', open); };
    return () => h(UiPopover, { open: props.open ?? local.value, 'onUpdate:open': setOpen }, () => [
      h(PopoverTrigger, { asChild: true }, () => h('span', { class: 'as-popover',
        onMouseenter: () => { if (props.trigger === 'hover') setOpen(true); },
        onMouseleave: () => { if (props.trigger === 'hover') setOpen(false); },
      }, slots.default?.())),
      h(PopoverContent, { side: props.placement.replace(/Left|Right$/, '').toLowerCase() as 'bottom',
        align: props.placement.endsWith('Left') ? 'start' : props.placement.endsWith('Right') ? 'end' : 'center',
        avoidCollisions: props.flipOnOverflow, class: ['as-popover-popup', props.overlayClassName],
        style: props.matchTriggerWidth ? { width: 'var(--reka-popover-trigger-width)' } : undefined,
      }, () => [props.title ? h('strong', props.title) : null, slots.content?.()]),
    ]);
  },
});
export const Popconfirm = defineComponent({
  name: 'AsPopconfirm', props: { title: String, okText: { type: String, default: '确定' }, cancelText: { type: String, default: '取消' } }, emits: ['confirm', 'cancel'],
  setup(props, { slots, emit }) {
    const open = ref(false);
    return () => h(Popover, { open: open.value, 'onUpdate:open': (value: boolean) => { open.value = value; } }, {
      default: slots.default,
      content: () => [h('p', props.title), h('div', { class: 'as-popconfirm__actions' }, [
        h(Button, { size: 'small', onClick: () => { open.value = false; emit('cancel'); } }, () => props.cancelText),
        h(Button, { size: 'small', type: 'primary', onClick: () => { open.value = false; emit('confirm'); } }, () => props.okText),
      ])],
    });
  },
});
export const Menu = defineComponent({
  name: 'AsMenu', inheritAttrs: false, emits: ['click'],
  setup: (_, { attrs, slots, emit }) => () => h('div', { ...attrs, class: ['as-menu', attrs.class],
    onClick: (event: Event) => { const key = (event.target as HTMLElement).closest<HTMLElement>('[data-menu-key]')?.dataset.menuKey; if (key !== undefined) emit('click', { key, domEvent: event }); },
  }, slots.default?.()),
});
export const MenuItem = defineComponent({
  name: 'AsMenuItem', inheritAttrs: false, props: { key: [String, Number], disabled: Boolean },
  setup(props, { attrs, slots }) {
    const dropdown = inject(DropdownKey, false);
    const key = getCurrentInstance()?.vnode.key;
    return () => dropdown ? h(DropdownMenuItem, { ...attrs, disabled: props.disabled, 'data-menu-key': String(props.key ?? key ?? '') }, slots.default)
      : h(Button, { ...attrs, disabled: props.disabled, type: 'text', 'data-menu-key': String(props.key ?? key ?? '') }, slots.default);
  },
});
export const Dropdown = defineComponent({
  name: 'AsDropdown', props: { trigger: { type: [String, Array] as PropType<string | string[]>, default: 'click' } }, emits: ['openChange', 'visibleChange'],
  setup(_, { slots, emit }) {
    provide(DropdownKey, true);
    const open = ref(false);
    const update = (value: boolean) => { open.value = value; emit('openChange', value); emit('visibleChange', value); };
    return () => h(DropdownMenu, { open: open.value, 'onUpdate:open': update }, () => [
      h(DropdownMenuTrigger, { asChild: true }, () => h('span', { class: 'as-popover' }, slots.default?.())),
      h(DropdownMenuContent, { class: 'as-dropdown-popup' }, () => slots.overlay?.() ?? slots.dropdown?.()),
    ]);
  },
});
