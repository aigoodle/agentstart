import { defineComponent, h, type PropType } from 'vue';

export const Input = defineComponent({
  name: 'AsInput',
  inheritAttrs: false,
  props: {
    value: { type: [String, Number], default: '' },
    allowClear: Boolean,
    disabled: Boolean,
    size: { type: String, default: 'middle' },
  },
  emits: ['update:value', 'change', 'pressEnter'],
  setup(props, { attrs, emit, slots }) {
    return () => h('span', { class: ['as-input-wrap', `as-input-wrap--${props.size}`, { 'as-input-wrap--disabled': props.disabled }, attrs.class] }, [
      slots.prefix?.(),
      h('input', {
        ...attrs,
        class: 'as-input',
        value: props.value,
        disabled: props.disabled,
        onInput: (event: Event) => emit('update:value', (event.target as HTMLInputElement).value),
        onChange: (event: Event) => emit('change', event),
        onKeydown: (event: KeyboardEvent) => { if (event.key === 'Enter') emit('pressEnter', event); },
      }),
      props.allowClear && props.value !== '' ? h('button', {
        class: 'as-input-clear', type: 'button', tabindex: -1,
        onClick: () => emit('update:value', ''),
      }, '×') : null,
      slots.suffix?.(),
    ]);
  },
});

export const Textarea = defineComponent({
  name: 'AsTextarea', inheritAttrs: false,
  props: { value: { type: [String, Number], default: '' }, autoSize: { type: [Boolean, Object], default: false }, disabled: Boolean, size: { type: String, default: 'middle' } },
  emits: ['update:value', 'change'],
  setup: (props, { attrs, emit }) => () => h('textarea', {
    ...attrs, value: props.value, disabled: props.disabled, class: ['as-textarea', `as-textarea--${props.size}`, attrs.class],
    style: [props.autoSize ? { fieldSizing: 'content' } : null, attrs.style as never],
    onInput: (event: Event) => emit('update:value', (event.target as HTMLTextAreaElement).value),
    onChange: (event: Event) => emit('change', event),
  }),
});

export const InputNumber = defineComponent({
  name: 'AsInputNumber', inheritAttrs: false,
  props: { value: { type: Number, default: undefined }, min: Number, max: Number, step: { type: Number, default: 1 }, disabled: Boolean, size: { type: String, default: 'middle' } },
  emits: ['update:value', 'change'],
  setup: (props, { attrs, emit }) => () => h('input', {
    ...attrs, type: 'number', value: props.value, min: props.min, max: props.max, step: props.step,
    disabled: props.disabled, class: ['as-input', 'as-input-number', `as-input--${props.size}`, attrs.class],
    onInput: (event: Event) => {
      const raw = (event.target as HTMLInputElement).value;
      const value = raw === '' ? undefined : Number(raw);
      emit('update:value', value); emit('change', value);
    },
  }),
});

export const SelectOption = defineComponent({
  name: 'AsSelectOption', inheritAttrs: false,
  props: { value: { type: [String, Number, Boolean], required: true }, disabled: Boolean },
  setup: (props, { attrs, slots }) => () => h('option', { ...attrs, value: String(props.value), disabled: props.disabled }, slots.default?.()),
});

export const Select = defineComponent({
  name: 'AsSelect', inheritAttrs: false,
  props: {
    value: { type: null as unknown as PropType<any>, default: undefined },
    options: { type: Array as PropType<Array<{ value: unknown; label: string; disabled?: boolean }>>, default: undefined },
    mode: String,
    placeholder: String,
    disabled: Boolean,
    allowClear: Boolean,
    size: { type: String, default: 'middle' },
  },
  emits: ['update:value', 'change', 'select', 'dropdownVisibleChange'],
  setup(props, { attrs, emit, slots }) {
    return () => h('select', {
      ...attrs,
      class: ['as-select', `as-select--${props.size}`, attrs.class],
      value: props.value as never,
      multiple: props.mode === 'multiple' || props.mode === 'tags',
      disabled: props.disabled,
      onFocus: () => emit('dropdownVisibleChange', true),
      onBlur: () => emit('dropdownVisibleChange', false),
      onChange: (event: Event) => {
        const el = event.target as HTMLSelectElement;
        const value = el.multiple ? Array.from(el.selectedOptions).map((option) => option.value) : el.value;
        emit('update:value', value); emit('change', value); emit('select', value);
      },
    }, [
      props.placeholder && (props.value === undefined || props.value === '') ? h('option', { value: '', disabled: true }, props.placeholder) : null,
      ...(props.options?.map((option) => h('option', { value: String(option.value), disabled: option.disabled }, option.label)) ?? []),
      slots.default?.(),
    ]);
  },
});
