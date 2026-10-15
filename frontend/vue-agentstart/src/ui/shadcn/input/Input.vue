<script setup lang="ts">
import { ref, watch, type HTMLAttributes } from 'vue';
import { cn } from '../utils';

const props = withDefaults(defineProps<{
  defaultValue?: string | number;
  modelValue?: string | number | boolean | null;
  value?: string | number | boolean | null;
  modelModifiers?: { number?: boolean; trim?: boolean; lazy?: boolean };
  class?: HTMLAttributes['class'];
}>(), { modelValue: undefined, value: undefined });
const emit = defineEmits<{
  (e: 'update:modelValue', value: string | number): void;
  (e: 'input', event: Event): void;
  (e: 'change', event: Event): void;
}>();
const element = ref<HTMLInputElement>();
const localValue = ref(props.modelValue ?? props.value ?? props.defaultValue ?? '');
watch(() => [props.modelValue, props.value], () => { localValue.value = props.modelValue ?? props.value ?? ''; });
function update(event: Event) {
  if ((event.target as HTMLInputElement).type === 'file') return;
  let value: string | number = (event.target as HTMLInputElement).value;
  if (props.modelModifiers?.trim) value = value.trim();
  if (props.modelModifiers?.number && value !== '' && !Number.isNaN(Number(value))) value = Number(value);
  localValue.value = value;
  emit('update:modelValue', value);
}
defineExpose({
  focus: (options?: FocusOptions) => element.value?.focus(options),
  select: () => element.value?.select(),
  click: () => element.value?.click(),
  setSelectionRange: (start: number, end: number, direction?: 'forward' | 'backward' | 'none') => element.value?.setSelectionRange(start, end, direction),
  getBoundingClientRect: () => element.value?.getBoundingClientRect(),
  get selectionStart() { return element.value?.selectionStart; },
  set selectionStart(value: number | null | undefined) { if (element.value) element.value.selectionStart = value ?? 0; },
  get selectionEnd() { return element.value?.selectionEnd; },
  set selectionEnd(value: number | null | undefined) { if (element.value) element.value.selectionEnd = value ?? 0; },
  get value() { return element.value?.value; },
  set value(value: string | undefined) { localValue.value = value ?? ''; if (element.value) element.value.value = value ?? ''; },
  get files() { return element.value?.files; },
  get element() { return element.value; },
});
</script>

<template>
  <input ref="element" data-slot="input"
    v-bind="$attrs.type === 'file' ? {} : { value: localValue }"
    :class="cn('flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50', props.class)"
    @input="!modelModifiers?.lazy && update($event); emit('input', $event)"
    @change="modelModifiers?.lazy && update($event); emit('change', $event)"
  />
</template>
