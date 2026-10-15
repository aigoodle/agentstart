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
const element = ref<HTMLTextAreaElement>();
const localValue = ref(props.modelValue ?? props.value ?? props.defaultValue ?? '');
watch(() => [props.modelValue, props.value], () => { localValue.value = props.modelValue ?? props.value ?? ''; });
function update(event: Event) {
  let value: string | number = (event.target as HTMLTextAreaElement).value;
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
  get element() { return element.value; },
});
</script>

<template>
  <textarea ref="element" data-slot="textarea"
    :value="String(localValue)"
    :class="cn('flex min-h-20 w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50', props.class)"
    @input="!modelModifiers?.lazy && update($event); emit('input', $event)"
    @change="modelModifiers?.lazy && update($event); emit('change', $event)"
  />
</template>
