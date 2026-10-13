import { defineComponent, h, type PropType, type VNode } from 'vue';

function nodes(slot?: () => VNode[]) {
  return slot?.() ?? [];
}

function plainText(children: VNode[]): string {
  return children
    .flatMap((child) => {
      if (typeof child.children === 'string') return [child.children];
      if (Array.isArray(child.children)) return [plainText(child.children as VNode[])];
      return [];
    })
    .join('');
}

export const Button = defineComponent({
  name: 'AsButton',
  inheritAttrs: false,
  props: {
    type: { type: String, default: 'default' },
    size: { type: String, default: 'middle' },
    danger: Boolean,
    loading: Boolean,
    disabled: Boolean,
    block: Boolean,
    htmlType: { type: String, default: 'button' },
    icon: { type: [Object, Function] as PropType<unknown>, default: undefined },
  },
  setup(props, { attrs, slots }) {
    return () => h('button', {
      ...attrs,
      type: props.htmlType,
      disabled: props.disabled || props.loading,
      class: ['as-btn', `as-btn--${props.type}`, `as-btn--${props.size}`, {
        'as-btn--danger': props.danger,
        'as-btn--loading': props.loading,
        'as-btn--block': props.block,
        'as-btn--icon-only': !plainText(nodes(slots.default)).trim(),
      }, attrs.class],
    }, [
      props.loading ? h('span', { class: 'as-spinner as-spinner--small', 'aria-hidden': 'true' }) : props.icon ? h(props.icon as never) : null,
      slots.default?.(),
    ]);
  },
});
