import {
  Teleport,
  defineComponent,
  h,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
  type App,
  type PropType,
} from 'vue';

import { Button } from './components/Button';
import Card from './components/Card.vue';
import { Drawer } from './components/Drawer';
import { Form, FormItem } from './components/Form';
import { Input, InputNumber, Select, SelectOption, Textarea } from './components/FormControls';
import { Modal } from './components/Modal';
import { Table, TableColumn } from './components/Table';
export type { TableColumn as TableColumnType } from './components/Table';

type AnyRecord = Record<string, unknown>;

let activeFloatingCloser: (() => void) | undefined;

function activateFloating(close: () => void) {
  if (activeFloatingCloser && activeFloatingCloser !== close) activeFloatingCloser();
  activeFloatingCloser = close;
}

function deactivateFloating(close: () => void) {
  if (activeFloatingCloser === close) activeFloatingCloser = undefined;
}

const Switch = defineComponent({
  name: 'AsSwitch',
  props: { checked: Boolean, disabled: Boolean, size: String, checkedChildren: String, unCheckedChildren: String },
  emits: ['update:checked', 'change'],
  setup: (props, { emit }) => () => h('button', {
    type: 'button', role: 'switch', 'aria-checked': props.checked, disabled: props.disabled,
    class: ['as-switch', { 'as-switch--checked': props.checked, 'as-switch--small': props.size === 'small' }],
    onClick: () => { emit('update:checked', !props.checked); emit('change', !props.checked); },
  }, [h('span', { class: 'as-switch__handle' }), (props.checked ? props.checkedChildren : props.unCheckedChildren) ? h('span', { class: 'as-switch__label' }, props.checked ? props.checkedChildren : props.unCheckedChildren) : null]),
});

const Checkbox = defineComponent({
  name: 'AsCheckbox', inheritAttrs: false,
  props: { checked: Boolean, disabled: Boolean }, emits: ['update:checked', 'change'],
  setup: (props, { attrs, emit, slots }) => () => h('label', { class: ['as-check', attrs.class] }, [
    h('input', { ...attrs, class: 'as-check__input', type: 'checkbox', checked: props.checked, disabled: props.disabled,
      onChange: (event: Event) => { const value = (event.target as HTMLInputElement).checked; emit('update:checked', value); emit('change', event); } }),
    h('span', { class: 'as-check__box' }), h('span', slots.default?.()),
  ]),
});

const Tag = defineComponent({
  name: 'AsTag', inheritAttrs: false, props: { color: String, closable: Boolean }, emits: ['close'],
  setup: (props, { attrs, slots, emit }) => () => h('span', { ...attrs, class: ['as-tag', props.color ? `as-tag--${props.color}` : '', attrs.class] }, [slots.default?.(), props.closable ? h('button', { type: 'button', onClick: (e: Event) => emit('close', e) }, '×') : null]),
});

const Spin = defineComponent({
  name: 'AsSpin', props: { spinning: { type: Boolean, default: true }, tip: String, size: String },
  setup: (props, { slots }) => () => h('div', { class: ['as-spin-wrap', { 'as-spin-wrap--nested': slots.default }] }, [
    slots.default?.(), props.spinning ? h('span', { class: 'as-spin' }, [h('span', { class: ['as-spinner', props.size && `as-spinner--${props.size}`] }), props.tip ? h('small', props.tip) : null]) : null,
  ]),
});

const Alert = defineComponent({ name: 'AsAlert', props: { type: { type: String, default: 'info' }, message: String, description: String, closable: Boolean, showIcon: Boolean }, setup: (props, { slots }) => () => h('div', { class: ['as-alert', `as-alert--${props.type}`], role: 'alert' }, [props.showIcon ? h('span', { class: 'as-alert__icon' }, props.type === 'error' ? '!' : 'i') : null, h('div', [h('strong', slots.message?.() ?? props.message), props.description ? h('p', props.description) : null, slots.default?.()])]) });
const Empty = defineComponent({ name: 'AsEmpty', props: { description: { type: [String, Boolean], default: '暂无数据' } }, setup: (props, { slots }) => () => h('div', { class: 'as-empty' }, [h('div', { class: 'as-empty__image' }, slots.image?.() ?? '○'), props.description !== false ? h('p', slots.description?.() ?? props.description) : null, slots.default?.()]) });

const Row = defineComponent({ name: 'AsRow', inheritAttrs: false, props: { gutter: { type: [Number, Array], default: 0 }, justify: String, align: String, wrap: { type: Boolean, default: true } }, setup: (props, { attrs, slots }) => () => h('div', { ...attrs, class: ['as-row', attrs.class], style: [{ gap: `${Array.isArray(props.gutter) ? props.gutter[0] : props.gutter}px`, justifyContent: props.justify, alignItems: props.align, flexWrap: props.wrap ? 'wrap' : 'nowrap' }, attrs.style as never] }, slots.default?.()) });
const Col = defineComponent({ name: 'AsCol', inheritAttrs: false, props: { span: { type: Number, default: 24 }, offset: Number, push: Number, pull: Number }, setup: (props, { attrs, slots }) => () => h('div', { ...attrs, class: ['as-col', attrs.class], style: [{ flex: `0 0 ${(props.span / 24) * 100}%`, maxWidth: `${(props.span / 24) * 100}%`, marginInlineStart: props.offset ? `${(props.offset / 24) * 100}%` : undefined, position: 'relative', insetInlineStart: props.push ? `${(props.push / 24) * 100}%` : props.pull ? `-${(props.pull / 24) * 100}%` : undefined }, attrs.style as never] }, slots.default?.()) });
const Space = defineComponent({ name: 'AsSpace', inheritAttrs: false, props: { size: { type: [String, Number], default: 'small' }, direction: String, wrap: Boolean }, setup: (props, { attrs, slots }) => () => h('div', { ...attrs, class: ['as-space', attrs.class], style: [{ gap: typeof props.size === 'number' ? `${props.size}px` : ({ small: '8px', middle: '16px', large: '24px' } as AnyRecord)[props.size] as string, flexDirection: props.direction === 'vertical' ? 'column' : 'row', flexWrap: props.wrap ? 'wrap' : undefined }, attrs.style as never] }, slots.default?.()) });

const Tooltip = defineComponent({ name: 'AsTooltip', props: { title: String, placement: String }, setup: (props, { slots }) => () => h('span', { class: 'as-tooltip', 'data-tip': props.title }, slots.default?.()) });
const Popover = defineComponent({
  name: 'AsPopover',
  props: {
    open: Boolean,
    title: String,
    trigger: String,
    placement: { type: String, default: 'bottomLeft' },
    overlayClassName: String,
    matchTriggerWidth: Boolean,
    flipOnOverflow: { type: Boolean, default: true },
  },
  emits: ['update:open'],
  setup(props, { slots, emit }) {
    const root = ref<HTMLElement>();
    const panel = ref<HTMLElement>();
    const local = ref(false);
    const panelStyle = ref<Record<string, string>>({});

    async function fitPanelInViewport() {
      panelStyle.value = { visibility: 'hidden' };
      await nextTick();
      const element = panel.value;
      const anchor = props.matchTriggerWidth
        ? root.value?.querySelector<HTMLElement>('.ph-mp-trigger') ?? root.value
        : root.value;
      if (!element || !anchor || typeof window === 'undefined') return;
      const triggerRect = anchor.getBoundingClientRect();
      if (props.matchTriggerWidth) {
        element.style.width = `${triggerRect.width}px`;
      }
      const panelRect = element.getBoundingClientRect();
      const margin = 8;
      const gap = props.matchTriggerWidth ? 4 : 8;
      const alignRight = props.placement.endsWith('Right');
      const preferTop = props.placement.startsWith('top');
      let left = alignRight
        ? triggerRect.right - panelRect.width
        : triggerRect.left;
      let top = preferTop
        ? triggerRect.top - panelRect.height - gap
        : triggerRect.bottom + gap;

      // Flip vertically when the preferred side has insufficient room.
      if (props.flipOnOverflow && !preferTop && top + panelRect.height > window.innerHeight - margin) {
        const above = triggerRect.top - panelRect.height - gap;
        if (above >= margin) top = above;
      } else if (props.flipOnOverflow && preferTop && top < margin) {
        const below = triggerRect.bottom + gap;
        if (below + panelRect.height <= window.innerHeight - margin) top = below;
      }

      if (props.matchTriggerWidth) left = triggerRect.left;
      else left = Math.max(margin, Math.min(left, window.innerWidth - panelRect.width - margin));
      top = props.flipOnOverflow
        ? Math.max(margin, Math.min(top, window.innerHeight - panelRect.height - margin))
        : Math.max(margin, top);
      panelStyle.value = {
        position: 'fixed',
        left: `${left}px`,
        top: `${top}px`,
        ...(props.matchTriggerWidth ? { width: `${triggerRect.width}px` } : {}),
        ...(!props.flipOnOverflow ? {
          maxHeight: `${Math.max(80, window.innerHeight - top - margin)}px`,
          overflowX: 'hidden',
          overflowY: 'auto',
        } : {}),
        visibility: 'visible',
      };
    }

    const close = () => {
      local.value = false;
      panelStyle.value = {};
      emit('update:open', false);
    };
    const setOpen = (value: boolean) => {
      local.value = value;
      emit('update:open', value);
      if (value) void fitPanelInViewport();
      else panelStyle.value = {};
    };
    const onDocumentPointerDown = (event: PointerEvent) => {
      if ((props.open || local.value) && !root.value?.contains(event.target as Node)) close();
    };
    const onViewportChange = () => {
      if (props.open || local.value) void fitPanelInViewport();
    };

    watch(() => props.open, (value) => {
      if (value) void fitPanelInViewport();
      else panelStyle.value = {};
    });
    onMounted(() => {
      document.addEventListener('pointerdown', onDocumentPointerDown);
      window.addEventListener('resize', onViewportChange);
      window.addEventListener('scroll', onViewportChange, true);
    });
    onBeforeUnmount(() => {
      document.removeEventListener('pointerdown', onDocumentPointerDown);
      window.removeEventListener('resize', onViewportChange);
      window.removeEventListener('scroll', onViewportChange, true);
    });

    return () => h('span', {
      ref: root,
      class: 'as-popover',
      onClick: () => {
        if (props.trigger === 'click' || !props.trigger) setOpen(!(props.open || local.value));
      },
    }, [
      slots.default?.(),
      (props.open || local.value) ? h(Teleport, { to: 'body' }, h('div', {
        ref: panel,
        class: [
          'as-popover__panel',
          'as-popover__panel--teleported',
          `as-popover__panel--${props.placement}`,
          props.overlayClassName,
        ],
        style: panelStyle.value,
        onClick: (event: Event) => event.stopPropagation(),
        onPointerdown: (event: PointerEvent) => event.stopPropagation(),
      }, [props.title ? h('strong', props.title) : null, slots.content?.()])) : null,
    ]);
  },
});
const Popconfirm = defineComponent({ name: 'AsPopconfirm', props: { title: String, okText: { type: String, default: '确定' }, cancelText: { type: String, default: '取消' } }, emits: ['confirm', 'cancel'], setup: (props, { slots, emit }) => { const open = ref(false); return () => h('span', { class: 'as-popover', onClick: () => { open.value = true; } }, [slots.default?.(), open.value ? h('div', { class: 'as-popover__panel', onClick: (e: Event) => e.stopPropagation() }, [h('p', props.title), h('div', { class: 'as-popconfirm__actions' }, [h(Button, { size: 'small', onClick: () => { open.value = false; emit('cancel'); } }, () => props.cancelText), h(Button, { size: 'small', type: 'primary', onClick: () => { open.value = false; emit('confirm'); } }, () => props.okText)])]) : null]); } });
const Menu = defineComponent({ name: 'AsMenu', inheritAttrs: false, emits: ['click'], setup: (_, { attrs, slots, emit }) => () => h('div', { ...attrs, class: ['as-menu', attrs.class], onClick: (event: Event) => { const key = (event.target as HTMLElement).closest<HTMLElement>('[data-menu-key]')?.dataset.menuKey; if (key !== undefined) emit('click', { key, domEvent: event }); } }, slots.default?.()) });
const MenuItem = defineComponent({ name: 'AsMenuItem', inheritAttrs: false, props: { key: [String, Number] }, setup: (props, { attrs, slots }) => () => h('button', { ...attrs, type: 'button', class: ['as-menu-item', attrs.class], 'data-menu-key': String(props.key ?? attrs.key ?? '') }, slots.default?.()) });
const Dropdown = defineComponent({ name: 'AsDropdown', props: { trigger: { type: [String, Array], default: 'click' } }, emits: ['openChange', 'visibleChange'], setup: (_, { slots, emit }) => { const root = ref<HTMLElement>(); const open = ref(false); const close = () => { if (!open.value) return; open.value = false; emit('openChange', false); emit('visibleChange', false); deactivateFloating(close); }; const setOpen = (value: boolean) => { if (value) { activateFloating(close); open.value = true; emit('openChange', true); emit('visibleChange', true); } else close(); }; const onDocumentPointerDown = (event: PointerEvent) => { if (open.value && !root.value?.contains(event.target as Node)) close(); }; onMounted(() => document.addEventListener('pointerdown', onDocumentPointerDown)); onBeforeUnmount(() => { document.removeEventListener('pointerdown', onDocumentPointerDown); deactivateFloating(close); }); return () => h('span', { ref: root, class: 'as-popover', onClick: () => setOpen(!open.value) }, [slots.default?.(), open.value ? h('div', { class: 'as-popover__panel', onClick: (event: Event) => { event.stopPropagation(); if ((event.target as HTMLElement).closest('[data-menu-key]')) close(); } }, slots.overlay?.() ?? slots.dropdown?.()) : null]); } });
const Tabs = defineComponent({ name: 'AsTabs', props: { activeKey: [String, Number], items: Array }, emits: ['update:activeKey', 'change'], setup: (props, { slots, emit }) => () => h('div', { class: 'as-tabs' }, [Array.isArray(props.items) ? h('nav', { class: 'as-tabs__nav' }, (props.items as AnyRecord[]).map((item) => h('button', { type: 'button', class: { active: item.key === props.activeKey }, onClick: () => { emit('update:activeKey', item.key); emit('change', item.key); } }, String(item.label ?? item.key)))) : null, h('div', { class: 'as-tabs__content' }, slots.default?.())]) });
const TabPane = defineComponent({ name: 'AsTabPane', props: { key: [String, Number], tab: String }, setup: (_, { slots }) => () => h('div', { class: 'as-tab-pane' }, slots.default?.()) });
const Segmented = defineComponent({ name: 'AsSegmented', props: { value: [String, Number], options: { type: Array as PropType<Array<string | number | { label: string; value: unknown }>>, default: () => [] } }, emits: ['update:value', 'change'], setup: (props, { emit }) => () => h('div', { class: 'as-segmented' }, props.options.map((item) => { const option = typeof item === 'object' ? item : { label: String(item), value: item }; return h('button', { type: 'button', class: { active: option.value === props.value }, onClick: () => { emit('update:value', option.value); emit('change', option.value); } }, option.label); })) });
const Skeleton = defineComponent({ name: 'AsSkeleton', props: { active: Boolean, loading: { type: Boolean, default: true } }, setup: (props, { slots }) => () => props.loading ? h('div', { class: ['as-skeleton', { 'as-skeleton--active': props.active }] }, [h('i'), h('i'), h('i')]) : slots.default?.() });
const Slider = defineComponent({ name: 'AsSlider', inheritAttrs: false, props: { value: { type: Number, default: 0 }, min: { type: Number, default: 0 }, max: { type: Number, default: 100 }, step: { type: Number, default: 1 } }, emits: ['update:value', 'change'], setup: (props, { attrs, emit }) => () => h('input', { ...attrs, class: ['as-slider', attrs.class], type: 'range', value: props.value, min: props.min, max: props.max, step: props.step, onInput: (e: Event) => { const value = Number((e.target as HTMLInputElement).value); emit('update:value', value); emit('change', value); } }) });

const RadioGroup = defineComponent({ name: 'AsRadioGroup', props: { value: [String, Number, Boolean] }, emits: ['update:value', 'change'], setup: (props, { slots, emit }) => () => h('div', { class: 'as-radio-group', onClick: (e: Event) => { const value = (e.target as HTMLElement).closest<HTMLElement>('[data-radio-value]')?.dataset.radioValue; if (value !== undefined) { emit('update:value', value); emit('change', { target: { value } }); } } }, slots.default?.()) });
const RadioButton = defineComponent({ name: 'AsRadioButton', props: { value: [String, Number, Boolean] }, setup: (props, { slots }) => () => h('button', { type: 'button', class: 'as-radio-button', 'data-radio-value': String(props.value) }, slots.default?.()) });

const componentMap: Record<string, ReturnType<typeof defineComponent>> = {
  AButton: Button, AInput: Input, ATextarea: Textarea, AInputNumber: InputNumber,
  ASelect: Select, ASelectOption: SelectOption, ASwitch: Switch, ACheckbox: Checkbox,
  ACard: Card, ATag: Tag, ASpin: Spin, AModal: Modal, AForm: Form, AFormItem: FormItem,
  AAlert: Alert, AEmpty: Empty, ARow: Row, ACol: Col, ASpace: Space, ATooltip: Tooltip,
  APopover: Popover, APopconfirm: Popconfirm, ADropdown: Dropdown, AMenu: Menu, AMenuItem: MenuItem,
  ADrawer: Drawer, ATabs: Tabs, ATabPane: TabPane, ASegmented: Segmented, ASkeleton: Skeleton, ASlider: Slider,
  ATable: Table, ATableColumn: TableColumn, ARadioGroup: RadioGroup, ARadioButton: RadioButton,
  AInputGroup: Space,
};

export const AgentStartUi = {
  install(app: App) {
    Object.entries(componentMap).forEach(([name, component]) => {
      // A host may already provide Ant Design's AButton/AForm/... globals.
      // Keep the host implementation and install our Tailwind-backed fallback
      // only when that component name is still available.
      if (!app.component(name)) app.component(name, component);
    });
  },
};

const toastRoot = () => {
  let root = document.querySelector<HTMLElement>('.as-message-root');
  if (!root) { root = document.createElement('div'); root.className = 'as-message-root'; document.body.appendChild(root); }
  return root;
};

function toast(type: string, content: unknown, duration = 3) {
  if (typeof document === 'undefined') return;
  const item = document.createElement('div');
  item.className = `as-message as-message--${type}`;
  item.setAttribute('role', type === 'error' ? 'alert' : 'status');
  item.textContent = String(content ?? '');
  toastRoot().appendChild(item);
  const close = () => {
    item.classList.add('as-message--leaving');
    window.setTimeout(() => item.remove(), 180);
  };
  window.setTimeout(close, Math.max(duration, 0.5) * 1000);
  return close;
}

export const message = {
  success: (content: unknown, duration?: number) => toast('success', content, duration),
  error: (content: unknown, duration?: number) => toast('error', content, duration),
  warning: (content: unknown, duration?: number) => toast('warning', content, duration),
  info: (content: unknown, duration?: number) => toast('info', content, duration),
  loading: (content: unknown, duration?: number) => toast('loading', content, duration),
};

Object.assign(Modal, {
  confirm(options: AnyRecord) {
    if (typeof window === 'undefined') return;
    if (window.confirm(String(options.title ?? options.content ?? '确认执行此操作？'))) (options.onOk as (() => void) | undefined)?.();
    else (options.onCancel as (() => void) | undefined)?.();
  },
  info(options: AnyRecord) { if (typeof window !== 'undefined') window.alert(String(options.title ?? options.content ?? '')); },
});

export {
  Alert, Button, Card, Checkbox, Col, Empty, Form, FormItem, Input, InputNumber,
  Modal, Popover, Popconfirm, Dropdown, Menu, MenuItem, Drawer, Tabs, TabPane,
  RadioButton, RadioGroup, Row, Segmented, Select, SelectOption,
  Skeleton, Slider, Space, Spin, Switch, Table, TableColumn, Tag, Textarea, Tooltip,
};
