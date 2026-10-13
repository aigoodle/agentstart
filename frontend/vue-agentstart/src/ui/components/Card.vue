<script setup lang="ts">
import { computed, useAttrs, useSlots, type CSSProperties } from 'vue';

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
  variant?: 'default' | 'management';
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
const slots = useSlots();
const isManagement = computed(() => props.variant === 'management');
const isInteractive = computed(() => props.interactive ?? isManagement.value);

function onClickCapture(event: MouseEvent) {
  if (!props.disabled) return;
  event.preventDefault();
  event.stopImmediatePropagation();
}
</script>

<template>
  <article
    v-if="isManagement"
    v-bind="attrs"
    class="as-management-card"
    :class="[
      attrs.class,
      {
        'as-management-card--compact': compact,
        'as-management-card--interactive': isInteractive && !disabled,
        'as-management-card--disabled': disabled,
        'as-management-card--footer-plain': !footerDivider,
        'as-management-card--borderless': !bordered,
      },
    ]"
    :tabindex="isInteractive && !disabled ? 0 : undefined"
    :aria-disabled="disabled || undefined"
    @click.capture="onClickCapture"
  >
    <header v-if="title || $slots.title || $slots.subtitle || $slots.icon || $slots.badge" class="as-management-card__head">
      <div v-if="$slots.icon" class="as-management-card__icon"><slot name="icon" /></div>
      <div class="as-management-card__title">
        <h3><slot name="title">{{ title }}</slot></h3>
        <small v-if="$slots.subtitle || subtitle"><slot name="subtitle">{{ subtitle }}</slot></small>
      </div>
      <div v-if="$slots.badge" class="as-management-card__badge"><slot name="badge" /></div>
    </header>
    <div v-if="$slots.description || description" class="as-management-card__description">
      <slot name="description">{{ description }}</slot>
    </div>
    <div v-if="$slots.default" class="as-management-card__content" :class="bodyClass" :style="bodyStyle"><slot /></div>
    <footer v-if="$slots.meta || $slots.actions" class="as-management-card__footer">
      <div class="as-management-card__meta"><slot name="meta" /></div>
      <div class="as-management-card__actions"><slot name="actions" /></div>
    </footer>
  </article>

  <section
    v-else
    v-bind="attrs"
    class="as-card"
    :class="[
      attrs.class,
      {
        'as-card--borderless': !bordered,
        'as-card--hoverable': hoverable,
        'as-card--interactive': isInteractive && !disabled,
        'as-card--disabled': disabled,
      },
    ]"
    :tabindex="isInteractive && !disabled ? 0 : undefined"
    :aria-disabled="disabled || undefined"
    @click.capture="onClickCapture"
  >
    <header v-if="title || $slots.title || $slots.extra" class="as-card__head">
      <div class="as-card__title"><slot name="title">{{ title }}</slot></div>
      <div v-if="$slots.extra" class="as-card__extra"><slot name="extra" /></div>
    </header>
    <div class="as-card__body" :class="bodyClass" :style="bodyStyle"><slot /></div>
    <footer v-if="$slots.actions" class="as-card__actions"><slot name="actions" /></footer>
  </section>
</template>
