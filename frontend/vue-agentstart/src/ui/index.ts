import { defineComponent, h, type App, type PropType } from 'vue';

import { Checkbox, RadioButton, RadioGroup, Segmented, Skeleton, Slider, Switch, TabPane, Tabs, Tag } from './components/Controls';
import { Dropdown, Menu, MenuItem, Popconfirm, Popover } from './components/Floating';
import { Alert as UiAlert, AlertDescription, AlertTitle } from './shadcn/alert';
import { Button } from './components/Button';
import Card from './components/Card.vue';
import { Drawer } from './components/Drawer';
import { Form, FormItem } from './components/Form';
import { Input, InputNumber, Select, SelectOption, Textarea } from './components/FormControls';
import { modalService } from './components/modalService';
import { Modal } from './components/Modal';
import { Table, TableColumn } from './components/Table';
import { Tooltip } from './components/Tooltip';
export type { TableColumn as TableColumnType } from './components/Table';

type AnyRecord = Record<string, unknown>;

const Spin = defineComponent({
  name: 'AsSpin', props: { spinning: { type: Boolean, default: true }, tip: String, size: String },
  setup: (props, { slots }) => () => h('div', { class: ['as-spin-wrap', { 'as-spin-wrap--nested': slots.default }] }, [
    slots.default?.(), props.spinning ? h('span', { class: 'as-spin' }, [h('span', { class: ['as-spinner', props.size && `as-spinner--${props.size}`] }), props.tip ? h('small', props.tip) : null]) : null,
  ]),
});

const Alert = defineComponent({
  name: 'AsAlert', props: { type: { type: String, default: 'info' }, message: String, description: String, closable: Boolean, showIcon: Boolean },
  setup: (props, { slots }) => () => h(UiAlert, { variant: props.type === 'error' ? 'destructive' : 'default', class: ['as-alert', `as-alert--${props.type}`] }, () => [
    props.showIcon ? h('span', { class: 'as-alert__icon' }, props.type === 'error' ? '!' : 'i') : null,
    h(AlertTitle, {}, () => slots.message?.() ?? props.message),
    props.description ? h(AlertDescription, {}, () => props.description) : null, slots.default?.(),
  ]),
});
const Empty = defineComponent({ name: 'AsEmpty', props: { description: { type: [String, Boolean], default: '暂无数据' } }, setup: (props, { slots }) => () => h('div', { class: 'as-empty' }, [h('div', { class: 'as-empty__image' }, slots.image?.() ?? '○'), props.description !== false ? h('p', slots.description?.() ?? props.description) : null, slots.default?.()]) });

const Row = defineComponent({ name: 'AsRow', inheritAttrs: false, props: { gutter: { type: [Number, Array], default: 0 }, justify: String, align: String, wrap: { type: Boolean, default: true } }, setup: (props, { attrs, slots }) => () => h('div', { ...attrs, class: ['as-row', attrs.class], style: [{ gap: `${Array.isArray(props.gutter) ? props.gutter[0] : props.gutter}px`, justifyContent: props.justify, alignItems: props.align, flexWrap: props.wrap ? 'wrap' : 'nowrap' }, attrs.style as never] }, slots.default?.()) });
const Col = defineComponent({ name: 'AsCol', inheritAttrs: false, props: { span: { type: Number, default: 24 }, offset: Number, push: Number, pull: Number }, setup: (props, { attrs, slots }) => () => h('div', { ...attrs, class: ['as-col', attrs.class], style: [{ flex: `0 0 ${(props.span / 24) * 100}%`, maxWidth: `${(props.span / 24) * 100}%`, marginInlineStart: props.offset ? `${(props.offset / 24) * 100}%` : undefined, position: 'relative', insetInlineStart: props.push ? `${(props.push / 24) * 100}%` : props.pull ? `-${(props.pull / 24) * 100}%` : undefined }, attrs.style as never] }, slots.default?.()) });
const Space = defineComponent({ name: 'AsSpace', inheritAttrs: false, props: { size: { type: [String, Number], default: 'small' }, direction: String, wrap: Boolean }, setup: (props, { attrs, slots }) => () => h('div', { ...attrs, class: ['as-space', attrs.class], style: [{ gap: typeof props.size === 'number' ? `${props.size}px` : ({ small: '8px', middle: '16px', large: '24px' } as AnyRecord)[props.size] as string, flexDirection: props.direction === 'vertical' ? 'column' : 'row', flexWrap: props.wrap ? 'wrap' : undefined }, attrs.style as never] }, slots.default?.()) });

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

export { message } from './message';

Object.assign(Modal, modalService);

export {
  Alert, Button, Card, Checkbox, Col, Empty, Form, FormItem, Input, InputNumber,
  Modal, Popover, Popconfirm, Dropdown, Menu, MenuItem, Drawer, Tabs, TabPane,
  RadioButton, RadioGroup, Row, Segmented, Select, SelectOption,
  Skeleton, Slider, Space, Spin, Switch, Table, TableColumn, Tag, Textarea, Tooltip,
};

export * from './shadcn';
export { ShadcnSelect as UiNativeSelect } from './components/ShadcnSelect';
export { getAgentStartColorForeground, getAgentStartAccessiblePrimary } from './color';
