import { inject, type InjectionKey } from 'vue';

import type { MaybePromise } from './client';
import { AgentStartUi } from './ui';

export type AgentStartHeaders =
  | Record<string, string>
  | (() => MaybePromise<Record<string, string>>);

export type AgentStartTheme = 'light' | 'dark';
export type AgentStartVisual = 'normal' | 'tech';

export interface AgentStartAppearanceOptions {
  /** Light/dark color scheme. Applied to the target's data-theme attribute. */
  theme?: AgentStartTheme;
  /** Normal product chrome or the built-in HUD/technology chrome. */
  visual?: AgentStartVisual;
  /** Any valid CSS color used as the component library's accent color. */
  primaryColor?: string;
}

export interface AgentStartPluginOptions {
  /** Default backend proxy prefix inherited by all top-level components. */
  apiBase?: string;
  /** Global headers. Functions are evaluated before every request. */
  headers?: AgentStartHeaders;
  /** Optional global UI appearance. Defaults remain controlled by CSS. */
  appearance?: AgentStartAppearanceOptions;
}

const AgentStartConfigKey: InjectionKey<AgentStartPluginOptions> = Symbol(
  'agent-start.Config',
);

/** Global Vue plugin used with `app.use(AgentStartPlugin, options)`. */
export const AgentStartPlugin = {
  install(
    app: import('vue').App,
    options: AgentStartPluginOptions = {},
  ) {
    app.use(AgentStartUi);
    app.provide(AgentStartConfigKey, options);
    if (options.appearance) setAgentStartAppearance(options.appearance);
  },
};

/**
 * Apply or update the public appearance contract at runtime.
 *
 * Drawer/Modal overlays teleport to `body`, so the document element is the
 * default target. A custom target is useful for embedded, non-teleported UI.
 */
export function setAgentStartAppearance(
  appearance: AgentStartAppearanceOptions,
  target?: HTMLElement,
): void {
  const root = target ?? (typeof document === 'undefined' ? undefined : document.documentElement);
  if (!root) return;

  if (appearance.theme) {
    root.dataset.theme = appearance.theme;
    // A number of existing product components support Tailwind's conventional
    // `.dark` ancestor in addition to data-theme, so keep both contracts synced.
    root.classList.toggle('dark', appearance.theme === 'dark');
  }
  if (appearance.visual) root.dataset.visual = appearance.visual;
  if (appearance.primaryColor) {
    const color = appearance.primaryColor;
    root.style.setProperty('--color-as-primary', color);
    root.style.setProperty('--color-as-primary-hover', `color-mix(in srgb, ${color} 82%, white)`);
    root.style.setProperty('--color-as-primary-active', `color-mix(in srgb, ${color} 82%, black)`);
    root.style.setProperty('--as-theme-primary', color);
    root.style.setProperty('--kh-color-primary', color);
    root.style.setProperty('--kh-color-primary-strong', `color-mix(in srgb, ${color} 82%, black)`);
    root.style.setProperty('--kh-color-primary-soft', `color-mix(in srgb, ${color} 12%, transparent)`);
    root.style.setProperty('--kh-color-primary-outline', `color-mix(in srgb, ${color} 35%, transparent)`);
    root.style.setProperty('--kh-color-info', color);
    root.style.setProperty('--kh-color-info-soft', `color-mix(in srgb, ${color} 12%, transparent)`);
  }
}

export function useAgentStartConfig(): AgentStartPluginOptions {
  return inject(AgentStartConfigKey, {});
}

async function evaluateHeaders(
  value?: AgentStartHeaders,
): Promise<Record<string, string>> {
  if (!value) return {};
  return (typeof value === 'function' ? await value() : value) ?? {};
}

/** Global headers are applied first; component-local headers override them. */
export async function mergeAgentStartHeaders(
  globalHeaders?: AgentStartHeaders,
  localHeaders?: AgentStartHeaders,
): Promise<Record<string, string>> {
  return {
    ...(await evaluateHeaders(globalHeaders)),
    ...(await evaluateHeaders(localHeaders)),
  };
}
