import { Teleport, defineComponent, h, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue';

export const Tooltip = defineComponent({
  name: 'AsTooltip',
  props: { title: String, placement: { type: String, default: 'top' } },
  setup(props, { slots }) {
    const anchor = ref<HTMLElement>();
    const panel = ref<HTMLElement>();
    const open = ref(false);
    const style = ref<Record<string, string>>({});
    const id = useId();
    let hovered = false;
    let focused = false;

    async function position() {
      await nextTick();
      if (!open.value || !anchor.value || !panel.value) return;
      const rect = anchor.value.getBoundingClientRect();
      const tip = panel.value.getBoundingClientRect();
      const margin = 8;
      const gap = 8;
      let side = props.placement;
      if (side.startsWith('top') && rect.top < tip.height + gap + margin) side = 'bottom';
      else if (side.startsWith('bottom') && rect.bottom + tip.height + gap > window.innerHeight - margin) side = 'top';
      else if (side.startsWith('left') && rect.left < tip.width + gap + margin) side = 'right';
      else if (side.startsWith('right') && rect.right + tip.width + gap > window.innerWidth - margin) side = 'left';
      let left = rect.left + (rect.width - tip.width) / 2;
      let top = side.startsWith('bottom') ? rect.bottom + gap : rect.top - tip.height - gap;
      if (side.startsWith('left') || side.startsWith('right')) {
        left = side.startsWith('left') ? rect.left - tip.width - gap : rect.right + gap;
        top = rect.top + (rect.height - tip.height) / 2;
      } else if (side.endsWith('Left')) left = rect.left;
      else if (side.endsWith('Right')) left = rect.right - tip.width;
      style.value = {
        left: `${Math.max(margin, Math.min(left, window.innerWidth - tip.width - margin))}px`,
        top: `${Math.max(margin, Math.min(top, window.innerHeight - tip.height - margin))}px`,
        visibility: 'visible',
      };
    }

    function update() {
      open.value = (hovered || focused) && !!(props.title || slots.title);
      if (open.value) {
        style.value = { visibility: 'hidden' };
        void position();
      }
    }
    const onViewportChange = () => { if (open.value) void position(); };
    watch(() => [props.title, props.placement], update);
    onMounted(() => {
      window.addEventListener('resize', onViewportChange);
      window.addEventListener('scroll', onViewportChange, true);
    });
    onBeforeUnmount(() => {
      window.removeEventListener('resize', onViewportChange);
      window.removeEventListener('scroll', onViewportChange, true);
    });

    return () => h('span', {
      ref: anchor,
      class: 'as-tooltip',
      'aria-describedby': open.value ? id : undefined,
      onMouseenter: () => { hovered = true; update(); },
      onMouseleave: () => { hovered = false; update(); },
      onFocusin: () => { focused = true; update(); },
      onFocusout: (event: FocusEvent) => {
        if (!anchor.value?.contains(event.relatedTarget as Node | null)) { focused = false; update(); }
      },
      onKeydown: (event: KeyboardEvent) => { if (event.key === 'Escape') open.value = false; },
    }, [
      slots.default?.(),
      open.value ? h(Teleport, { to: 'body' }, h('div', {
        ref: panel, id, role: 'tooltip', class: 'as-tooltip__panel', style: style.value,
      }, slots.title?.() ?? props.title)) : null,
    ]);
  },
});
