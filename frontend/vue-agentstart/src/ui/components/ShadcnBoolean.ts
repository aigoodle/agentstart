import { defineComponent, h, ref, type Component, type PropType } from 'vue';
import { Checkbox } from '../shadcn/checkbox';
import { Switch } from '../shadcn/switch';

/** Native checkbox event/model bridge; all visible interaction uses Reka UI. */
export const ShadcnBoolean = defineComponent({
  name: 'ShadcnBoolean', inheritAttrs: false,
  props: {
    modelValue: { type: null as unknown as PropType<any>, default: undefined },
    checked: { type: Boolean, default: undefined },
    value: { type: null as unknown as PropType<any>, default: 'on' },
    trueValue: { type: null as unknown as PropType<any>, default: true },
    falseValue: { type: null as unknown as PropType<any>, default: false },
    disabled: Boolean, appearance: { type: String, default: 'checkbox' },
  },
  emits: ['update:modelValue', 'change', 'input'],
  setup(props, { attrs, emit }) {
    const local = ref(false);
    const native = ref<HTMLInputElement>();
    const checked = () => props.checked ?? (Array.isArray(props.modelValue)
      ? props.modelValue.includes(props.value) : props.modelValue !== undefined ? props.modelValue === props.trueValue : local.value);
    function update(on: boolean, event?: Event) {
      local.value = on;
      const value = Array.isArray(props.modelValue)
        ? on ? [...new Set([...props.modelValue, props.value])] : props.modelValue.filter((item: unknown) => item !== props.value)
        : on ? props.trueValue : props.falseValue;
      emit('update:modelValue', value);
      if (native.value) native.value.checked = on;
      const original = event ?? new Event('change', { bubbles: true });
      if (!event) Object.defineProperty(original, 'target', { value: native.value });
      emit('change', original); emit('input', original);
    }
    return () => h('span', { class: 'as-boolean-field' }, [
      h('input', { ref: native, type: 'checkbox', hidden: true, style: { display: 'none' }, tabindex: -1, 'aria-hidden': 'true',
        checked: checked(), disabled: props.disabled, name: attrs.name, value: props.value,
        onChange: (event: Event) => update((event.target as HTMLInputElement).checked, event),
      }),
      h((props.appearance === 'switch' ? Switch : Checkbox) as Component, {
        ...attrs, type: 'button', name: undefined, modelValue: checked(), disabled: props.disabled,
        'onUpdate:modelValue': (value: boolean | 'indeterminate') => update(value === true),
      }),
    ]);
  },
});
