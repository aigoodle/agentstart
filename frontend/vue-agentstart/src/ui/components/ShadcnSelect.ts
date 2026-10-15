import { computed, defineComponent, Fragment, h, ref, type PropType, type VNode } from 'vue';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../shadcn/select';
import { Button } from '../shadcn/button';
import { Input } from '../shadcn/input';

interface Option { value: unknown; label: string; disabled?: boolean; attrs?: Record<string, unknown> }
function labelOf(node: VNode): string {
  if (typeof node.children === 'string') return node.children;
  if (Array.isArray(node.children)) return (node.children as VNode[]).map(labelOf).join('');
  const slots = node.children as { default?: () => VNode[] } | null;
  return slots?.default?.().map(labelOf).join('') ?? '';
}
function optionsOf(nodes: VNode[]): Option[] {
  return nodes.flatMap(node => {
    if (node.type === Fragment || node.type === 'optgroup') return optionsOf((node.children ?? []) as VNode[]);
    if (node.type === 'option' || (typeof node.type === 'object' && (node.type as { name?: string }).name === 'AsSelectOption')) {
      return [{ value: node.props?.value ?? labelOf(node), label: labelOf(node), disabled: !!node.props?.disabled, attrs: node.props ?? {} }];
    }
    return [];
  });
}

/** shadcn Select with a native form bridge for existing v-model/change handlers. */
export const ShadcnSelect = defineComponent({
  name: 'ShadcnSelect', inheritAttrs: false,
  props: {
    modelValue: { type: null as unknown as PropType<any>, default: undefined },
    value: { type: null as unknown as PropType<any>, default: undefined },
    options: Array as PropType<Option[]>,
    multiple: Boolean, mode: String, disabled: Boolean, allowClear: Boolean, placeholder: String,
    modelModifiers: Object as PropType<{ number?: boolean }>,
  },
  emits: ['update:modelValue', 'update:value', 'change', 'select', 'dropdownVisibleChange'],
  setup(props, { attrs, emit, slots, expose }) {
    const native = ref<HTMLSelectElement>();
    const trigger = ref<any>();
    const draft = ref('');
    const local = ref<unknown>();
    const multiple = computed(() => props.multiple || props.mode === 'multiple' || props.mode === 'tags');
    const current = () => props.modelValue ?? props.value ?? local.value;
    function change(value: unknown, event?: Event) {
      local.value = value;
      emit('update:modelValue', value);
      emit('update:value', value);
      if (native.value && !event) {
        const values = multiple.value ? (Array.isArray(value) ? value : []) : [value];
        Array.from(native.value.options).forEach(option => { option.selected = values.some(item => String(item) === option.value); });
        const changeEvent = new Event('change', { bubbles: true });
        Object.defineProperty(changeEvent, 'target', { value: native.value });
        emit('change', changeEvent);
        emit('select', value);
      } else {
        emit('change', event);
        emit('select', value);
      }
    }
    expose({ focus: () => trigger.value?.$el?.focus(), get element() { return native.value } });
    return () => {
      const options = props.options ? [...props.options] : optionsOf(slots.default?.() ?? []);
      if (props.mode === 'tags') for (const value of Array.isArray(current()) ? current() : []) {
        if (!options.some(option => option.value === value)) options.push({ value, label: String(value) });
      }
      const keys = options.map((_, index) => `option-${index}`);
      const selected = multiple.value
        ? options.flatMap((option, index) => Array.isArray(current()) && current().includes(option.value) ? [keys[index]] : [])
        : keys[options.findIndex(option => option.value === current() || String(option.value) === String(current()))];
      const update = (value: unknown) => {
        const indexes = (Array.isArray(value) ? value : [value]).map(key => keys.indexOf(String(key))).filter(index => index >= 0);
        change(multiple.value ? indexes.map(index => options[index].value) : options[indexes[0]]?.value);
      };
      return h('span', { class: 'as-select-field', style: attrs.style as never }, [
        h('select', {
          ref: native, hidden: true, style: { display: 'none' }, 'aria-hidden': 'true', tabindex: -1,
          class: attrs.class, name: attrs.name, multiple: multiple.value, disabled: props.disabled,
          onChange: (event: Event) => {
            const element = event.target as HTMLSelectElement;
            const read = (option?: HTMLOptionElement) => {
              const value = option ? options.find(item => String(item.value) === option.value)?.value ?? option.value : undefined;
              return props.modelModifiers?.number && value !== '' && value != null && !Number.isNaN(Number(value)) ? Number(value) : value;
            };
            change(element.multiple ? Array.from(element.selectedOptions).map(read) : read(element.selectedOptions[0] ?? element.options[0]), event);
          },
        }, options.map(option => h('option', { ...option.attrs, value: String(option.value), disabled: option.disabled,
          selected: multiple.value ? Array.isArray(current()) && current().includes(option.value) : String(current()) === String(option.value),
        }, option.label))),
        h(Select, {
          modelValue: selected, multiple: multiple.value, disabled: props.disabled,
          'onUpdate:modelValue': update,
          'onUpdate:open': (open: boolean) => emit('dropdownVisibleChange', open),
        }, () => [
          h(SelectTrigger, { ...attrs, style: undefined, ref: trigger, class: ['as-select-trigger', attrs.class] }, () => h(SelectValue, { placeholder: props.placeholder ?? options.find(option => option.value === '')?.label ?? '请选择' })),
          h(SelectContent, { class: 'as-select-popup' }, () => options.map((option, index) => h(SelectItem, { key: keys[index], value: keys[index], disabled: option.disabled }, () => option.label))),
        ]),
        props.allowClear && current() != null && current() !== '' ? h(Button, { variant: 'ghost', size: 'icon-sm', 'aria-label': '清除选择', disabled: props.disabled, onClick: () => change(multiple.value ? [] : undefined) }, () => '×') : null,
        props.mode === 'tags' ? h(Input, { modelValue: draft.value, placeholder: '输入后回车添加', disabled: props.disabled,
          'onUpdate:modelValue': (value: string | number) => { draft.value = String(value); },
          onKeydown: (event: KeyboardEvent) => {
            if (event.key !== 'Enter' || !draft.value.trim()) return;
            event.preventDefault();
            change([...new Set([...(Array.isArray(current()) ? current() : []), draft.value.trim()])]); draft.value = '';
          },
        }) : null,
      ]);
    };
  },
});
