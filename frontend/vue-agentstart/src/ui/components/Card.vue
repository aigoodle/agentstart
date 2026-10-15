<script setup lang="ts">
import { Card as ShadcnCard, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from '../shadcn/card';
import { computed, useAttrs, type CSSProperties } from 'vue';

defineOptions({ name: 'AsCard', inheritAttrs: false });

interface Props {
  title?: string;
  subtitle?: string;
  description?: string;
  bordered?: boolean;
  hoverable?: boolean;
  interactive?: boolean;
  disabled?: boolean;
  compact?: boolean;
  footerDivider?: boolean;
  variant?: 'default' | 'management' | 'create';
  bodyStyle?: CSSProperties;
  bodyClass?: string | string[] | Record<string, boolean>;
}

const props = withDefaults(defineProps<Props>(), {
  bordered: true,
  hoverable: false,
  interactive: undefined,
  disabled: false,
  compact: false,
  footerDivider: true,
  variant: 'default',
});

const attrs = useAttrs();
const isInteractive = computed(() => props.interactive ?? !!attrs.onClick);

function onClickCapture(event: MouseEvent) {
  if (!props.disabled) return;
  event.preventDefault();
  event.stopImmediatePropagation();
}
function onKeydown(event: KeyboardEvent) {
  if (!isInteractive.value || props.disabled || event.target !== event.currentTarget) return;
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault();
    (event.currentTarget as HTMLElement).click();
  }
}
</script>

<template>
  <ShadcnCard v-bind="attrs" :class="['as-ui-card', attrs.class, !bordered && 'border-0', compact && 'as-ui-card--compact', (hoverable || isInteractive) && 'transition-shadow hover:shadow-md', disabled && 'opacity-50 pointer-events-none']" :data-card-variant="variant" :data-card-density="compact ? 'compact' : 'normal'" :tabindex="isInteractive && !disabled ? 0 : undefined" :aria-disabled="disabled || undefined" @click.capture="onClickCapture" @keydown="onKeydown">
    <CardContent v-if="variant === 'create'" class="as-ui-card__create">
      <span class="as-ui-card__create-icon" aria-hidden="true"><slot name="icon">+</slot></span>
      <CardTitle><slot name="title">{{ title }}</slot></CardTitle>
      <CardDescription>{{ subtitle || description }}</CardDescription>
    </CardContent>
    <CardHeader v-else-if="title || $slots.title || $slots.icon || $slots.badge || $slots.extra" class="as-ui-card__header flex flex-row items-start gap-3">
      <div v-if="$slots.icon" class="as-ui-card__icon flex size-10 shrink-0 items-center justify-center rounded-lg bg-muted"><slot name="icon" /></div>
      <div class="min-w-0 flex-1 space-y-1">
        <CardTitle><slot name="title">{{ title }}</slot></CardTitle>
        <CardDescription v-if="subtitle || $slots.subtitle"><slot name="subtitle">{{ subtitle }}</slot></CardDescription>
      </div>
      <div v-if="$slots.badge || $slots.extra" class="as-ui-card__badge flex shrink-0 items-center gap-2"><slot name="badge" /><slot name="extra" /></div>
    </CardHeader>
    <CardContent v-if="variant !== 'create' && (description || $slots.description || $slots.default)" :class="['as-ui-card__content', bodyClass]" :style="bodyStyle">
      <CardDescription v-if="description || $slots.description" class="as-ui-card__description"><slot name="description">{{ description }}</slot></CardDescription>
      <slot />
    </CardContent>
    <CardFooter v-if="$slots.meta || $slots.actions" :class="['as-ui-card__footer mt-auto', footerDivider && 'border-t']">
      <div v-if="$slots.meta" class="as-ui-card__meta text-xs text-muted-foreground"><slot name="meta" /></div>
      <div v-if="$slots.actions" class="as-ui-card__actions"><slot name="actions" /></div>
    </CardFooter>
  </ShadcnCard>
</template>
