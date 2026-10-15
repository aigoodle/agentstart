import { defineComponent, h, ref } from 'vue';
import { Slider } from '../shadcn/slider';

export const ShadcnRange = defineComponent({
  name: 'ShadcnRange', inheritAttrs: false,
  props: {
    modelValue: Number, value: [Number, String], min: [Number, String], max: [Number, String],
    step: [Number, String], disabled: Boolean,
  },
  emits: ['update:modelValue', 'input', 'change'],
  setup(props, { attrs, emit }) {
    const native = ref<HTMLInputElement>();
    const local = ref(0);
    function update(value: number, event?: Event) {
      local.value = value; emit('update:modelValue', value);
      if (native.value) native.value.value = String(value);
      const original = event ?? new Event('input', { bubbles: true });
      if (!event) Object.defineProperty(original, 'target', { value: native.value });
      emit('input', original); emit('change', original);
    }
    return () => h('span', { class: 'as-range-field' }, [
      h('input', { ref: native, type: 'range', hidden: true, style: { display: 'none' }, 'aria-hidden': 'true', tabindex: -1,
        value: props.modelValue ?? props.value ?? local.value, name: attrs.name, disabled: props.disabled,
        min: props.min, max: props.max, step: props.step,
        onInput: (event: Event) => update(Number((event.target as HTMLInputElement).value), event),
      }),
      h(Slider, { ...attrs, modelValue: [Number(props.modelValue ?? props.value ?? local.value)],
        min: Number(props.min ?? 0), max: Number(props.max ?? 100), step: Number(props.step ?? 1), disabled: props.disabled,
        'onUpdate:modelValue': (value: number[] | undefined) => { if (value?.length) update(value[0]); },
      }),
    ]);
  },
});
