import { createApp } from 'vue';
import { toast as sonnerToast } from 'vue-sonner';
import Toaster from './shadcn/sonner/Sonner.vue';

function ensureToaster() {
  if (document.querySelector('.agent-start-toaster')) return;
  let root = document.querySelector<HTMLElement>('.as-toaster-root');
  if (!root) {
    root = document.createElement('div'); root.className = 'as-toaster-root'; document.body.append(root);
    createApp(Toaster, { position: 'top-center', closeButton: true, style: {
      '--normal-bg': 'var(--popover)', '--normal-text': 'var(--popover-foreground)', '--normal-border': 'var(--border)',
    } }).mount(root);
  }
}
function toast(type: 'success' | 'error' | 'warning' | 'info' | 'loading', content: unknown, duration = 3) {
  if (typeof document === 'undefined') return;
  ensureToaster();
  const id = sonnerToast[type](String(content ?? ''), { duration: Math.max(duration, .5) * 1000 });
  return () => sonnerToast.dismiss(id);
}
export const message = {
  success: (content: unknown, duration?: number) => toast('success', content, duration),
  error: (content: unknown, duration?: number) => toast('error', content, duration),
  warning: (content: unknown, duration?: number) => toast('warning', content, duration),
  info: (content: unknown, duration?: number) => toast('info', content, duration),
  loading: (content: unknown, duration?: number) => toast('loading', content, duration),
};
