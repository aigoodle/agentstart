import { cloneVNode, defineComponent, h, type PropType } from 'vue';
import { Switch as UiSwitch } from '../shadcn/switch';
import { Badge } from '../shadcn/badge';
import { Skeleton as UiSkeleton } from '../shadcn/skeleton';
import { Slider as UiSlider } from '../shadcn/slider';
import { Tabs as UiTabs, TabsContent, TabsList, TabsTrigger } from '../shadcn/tabs';
import { ToggleGroup, ToggleGroupItem } from '../shadcn/toggle-group';
import { RadioGroupRoot, RadioGroupItem } from 'reka-ui';
import { ShadcnBoolean } from './ShadcnBoolean';
import { Button } from '../shadcn/button';

type Option = string | number | { label: string; value: unknown };
export const Switch = defineComponent({
  name: 'AsSwitch', inheritAttrs: false,
  props: { checked: Boolean, disabled: Boolean, size: String, checkedChildren: String, unCheckedChildren: String },
  emits: ['update:checked', 'change'],
  setup: (props, { attrs, emit }) => () => h(UiSwitch, {
    ...attrs, modelValue: props.checked, disabled: props.disabled,
    'onUpdate:modelValue': (value: boolean) => { emit('update:checked', value); emit('change', value); },
  }),
});
export const Checkbox = defineComponent({
  name: 'AsCheckbox', inheritAttrs: false, props: { checked: Boolean, disabled: Boolean },
  emits: ['update:checked', 'change'],
  setup: (props, { attrs, slots, emit }) => () => h('label', { class: ['as-check-label', attrs.class] }, [
    h(ShadcnBoolean, { ...attrs, checked: props.checked, disabled: props.disabled,
      'onUpdate:modelValue': (value: boolean) => emit('update:checked', value),
      onChange: (event: Event) => emit('change', event),
    }), slots.default?.(),
  ]),
});
const badgeColors: Record<string, string> = {
  blue: 'blue', geekblue: 'indigo', purple: 'purple', cyan: 'cyan',
  orange: 'orange', warning: 'orange', green: 'green', success: 'green',
  red: 'red', error: 'red', magenta: 'pink', pink: 'pink', gold: 'orange',
};
export const Tag = defineComponent({
  name: 'AsTag', inheritAttrs: false, props: { color: String, closable: Boolean }, emits: ['close'],
  setup: (props, { attrs, slots, emit }) => () => h(Badge, {
    ...attrs, variant: props.color === 'red' || props.color === 'error' ? 'destructive' : 'secondary',
    class: ['as-ui-badge', attrs.class], 'data-color': badgeColors[props.color ?? ''],
  }, () => [slots.default?.(), props.closable ? h(Button, { variant: 'ghost', size: 'icon-sm', 'aria-label': '移除', onClick: (event: Event) => emit('close', event) }, () => '×') : null]),
});
export const Skeleton = defineComponent({
  name: 'AsSkeleton', props: { active: Boolean, loading: { type: Boolean, default: true } },
  setup: (props, { slots }) => () => props.loading ? h('div', { class: 'as-skeleton-stack' }, [1,2,3].map(() => h(UiSkeleton, { class: 'h-4 w-full' }))) : slots.default?.(),
});
export const Slider = defineComponent({
  name: 'AsSlider', inheritAttrs: false,
  props: { value: { type: Number, default: 0 }, min: { type: Number, default: 0 }, max: { type: Number, default: 100 }, step: { type: Number, default: 1 }, disabled: Boolean },
  emits: ['update:value', 'change'],
  setup: (props, { attrs, emit }) => () => h(UiSlider, { ...attrs, ...props, modelValue: [props.value],
    'onUpdate:modelValue': (value: number[] | undefined) => { if (value?.length) { emit('update:value', value[0]); emit('change', value[0]); } },
  }),
});
export const Segmented = defineComponent({
  name: 'AsSegmented', props: { value: [String, Number, Boolean], disabled: Boolean, options: { type: Array as PropType<Option[]>, default: () => [] } },
  emits: ['update:value', 'change'],
  setup: (props, { emit }) => () => h(ToggleGroup, { type: 'single', disabled: props.disabled, variant: 'outline', modelValue: String(props.value ?? ''),
    'onUpdate:modelValue': (value: unknown) => {
      const option = props.options.map(item => typeof item === 'object' ? item : { value: item, label: String(item) }).find(item => String(item.value) === value);
      if (option) { emit('update:value', option.value); emit('change', option.value); }
    },
  }, () => props.options.map(item => { const option = typeof item === 'object' ? item : { label: String(item), value: item }; return h(ToggleGroupItem, { value: String(option.value) }, () => option.label); })),
});
export const Tabs = defineComponent({
  name: 'AsTabs', props: { activeKey: [String, Number], items: Array as PropType<{ key: string | number; label: string }[]> },
  emits: ['update:activeKey', 'change'],
  setup: (props, { slots, emit }) => () => {
    const panes = slots.default?.() ?? [];
    const items = props.items ?? panes.filter(pane => pane.key != null).map(pane => ({ key: pane.key as string | number, label: String(pane.props?.tab ?? pane.key) }));
    return h(UiTabs, { modelValue: props.activeKey == null ? undefined : String(props.activeKey), defaultValue: String(items[0]?.key ?? ''),
      'onUpdate:modelValue': (value: string | number) => { const key = items.find(item => String(item.key) === String(value))?.key ?? value; emit('update:activeKey', key); emit('change', key); },
    }, () => [
      h(TabsList, {}, () => items.map(item => h(TabsTrigger, { value: String(item.key) }, () => item.label))),
      ...panes.map(pane => pane.key == null ? pane : cloneVNode(pane, { paneValue: String(pane.key) })),
    ]);
  },
});
export const TabPane = defineComponent({
  name: 'AsTabPane', props: { key: [String, Number], paneValue: String, tab: String },
  setup: (props, { slots }) => () => h(TabsContent, { value: props.paneValue ?? String(props.key ?? '') }, slots.default),
});
export const RadioGroup = defineComponent({
  name: 'AsRadioGroup', props: { value: [String, Number, Boolean] }, emits: ['update:value', 'change'],
  setup: (props, { slots, emit }) => () => h(RadioGroupRoot, {
    modelValue: String(props.value ?? ''), class: 'as-radio-group',
    'onUpdate:modelValue': (value: unknown) => { emit('update:value', value); emit('change', { target: { value } }); },
  }, slots.default),
});
export const RadioButton = defineComponent({
  name: 'AsRadioButton', props: { value: [String, Number, Boolean], disabled: Boolean },
  setup: (props, { slots }) => () => h(RadioGroupItem, { value: String(props.value), disabled: props.disabled, asChild: true },
    () => h(Button, { variant: 'outline', class: 'data-[state=checked]:bg-primary data-[state=checked]:text-primary-foreground' }, slots.default)),
});
