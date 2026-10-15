import { nextTick, onBeforeUnmount, ref, watch } from 'vue';

let nextLayer = 1200;
const layers = new Map<symbol, number>();

/** One opening-order stack shared by shadcn dialogs and sheets. */
export function useOverlayLayer(isOpen: () => boolean) {
  const id = Symbol('overlay');
  const layer = ref(1200);
  let previousFocus: HTMLElement | null = null;
  const isTop = () => layers.get(id) === Math.max(...layers.values());
  const release = () => {
    const restore = isTop();
    const target = previousFocus;
    previousFocus = null;
    layers.delete(id);
    if (restore) void nextTick(() => {
      if (!layers.has(id) && Math.max(0, ...layers.values()) < layer.value && target?.isConnected) target.focus();
    });
  };
  watch(isOpen, open => {
    if (typeof document === 'undefined') return;
    if (open) {
      layer.value = nextLayer;
      nextLayer += 10;
      layers.set(id, layer.value);
      previousFocus = document.activeElement as HTMLElement | null;
    } else release();
  }, { immediate: true });
  onBeforeUnmount(release);
  return { layer, isTop };
}
