import { Button as ShadcnButton } from '../shadcn/button';
import { defineComponent, h, isVNode, type Component, type PropType, type VNode } from 'vue';

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
    icon: { type: [Object, Function] as PropType<Component | VNode>, default: undefined },
  },
  setup(props, { attrs, slots }) {
    return () => {
      const children = slots.default?.() ?? [];
      const compactIcon = props.size === 'small' && !!props.icon && children.length === 0;
      return h(ShadcnButton, {
        variant: props.danger && props.type === 'primary' ? 'destructive' : ({ primary: 'default', default: 'outline', dashed: 'outline', text: 'ghost', link: 'link' } as const)[props.type as 'primary'] ?? 'outline',
        size: ({ small: 'sm', middle: 'default', large: 'lg' } as const)[props.size as 'small'] ?? 'default',
        'aria-busy': props.loading || undefined,
        ...attrs,
        type: props.htmlType,
        disabled: props.disabled || props.loading,
        class: [compactIcon && 'size-6 p-0', props.type === 'dashed' ? 'border-dashed' : '', props.danger && props.type !== 'primary' ? 'text-destructive hover:text-destructive' : '', props.block && 'w-full', attrs.class],
      }, () => [
        props.loading ? h('span', { class: 'as-spinner as-spinner--small', 'aria-hidden': 'true' }) : props.icon ? (isVNode(props.icon) ? props.icon : h(props.icon)) : null,
        children,
      ]);
    };
  },
});
