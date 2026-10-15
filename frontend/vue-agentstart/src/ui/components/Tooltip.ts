import { defineComponent, h, ref } from 'vue';
import { Tooltip as UiTooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '../shadcn/tooltip';

export const Tooltip = defineComponent({
  name: 'AsTooltip', props: { title: String, placement: { type: String, default: 'top' } },
  setup(props, { slots }) {
    const open = ref(false);
    let hovered = false, focused = false;
    const update = () => { open.value = (hovered || focused) && !!(props.title || slots.title); };
    return () => h(TooltipProvider, { delayDuration: 0 }, () => h(UiTooltip, { open: open.value,
      'onUpdate:open': (value: boolean) => { if (value || !(hovered || focused)) open.value = value; },
    }, () => [
      h(TooltipTrigger, { asChild: true }, () => h('span', {
        class: 'as-tooltip',
        onMouseenter: () => { hovered = true; update(); },
        onMouseleave: () => { hovered = false; update(); },
        onFocusin: () => { focused = true; update(); },
        onFocusout: (event: FocusEvent) => { if (!(event.currentTarget as HTMLElement).contains(event.relatedTarget as Node | null)) { focused = false; update(); } },
        onKeydown: (event: KeyboardEvent) => { if (event.key === 'Escape') open.value = false; },
      }, slots.default?.())),
      h(TooltipContent, { onEscapeKeyDown: () => { hovered = false; focused = false; open.value = false; }, class: 'as-tooltip__panel as-tooltip-popup', side: props.placement.replace(/Left|Right$/, '') as 'top',
        align: props.placement.endsWith('Left') ? 'start' : props.placement.endsWith('Right') ? 'end' : 'center', sideOffset: 8,
      }, () => slots.title?.() ?? props.title),
    ]));
  },
});
