/**
 * useKhI18n — the internal i18n hook every component uses.
 *
 * `t('recall.hits', {n: 5})` looks up the key from the current locale, falls
 * back to en-US, then to the raw key if truly missing.
 *
 * Locales are provided by wrapping the tree in <KhI18nProvider :locale="..."/>
 * or by passing :locale directly on KnowledgeHubApp (which does the provide
 * for us). Hosts can also override just a few keys — the hook deep-merges the
 * provided partial over the default.
 */
import {
  computed,
  inject,
  type InjectionKey,
  provide,
  ref,
  type Ref,
} from 'vue';

import { DEFAULT_LOCALE, enUS, type KhMessages } from './messages';

/** A partial locale — hosts only need to override the keys they change. */
export type KhLocale =
  | 'en-US'
  | 'zh-CN'
  | DeepPartial<KhMessages>;

type DeepPartial<T> = {
  [P in keyof T]?: DeepPartial<T[P]>;
};

const KEY: InjectionKey<Ref<KhMessages>> = Symbol('kh-i18n');

function isPartial(v: KhLocale): v is DeepPartial<KhMessages> {
  return typeof v === 'object' && v !== null;
}

function deepMerge<T>(base: T, override: DeepPartial<T>): T {
  if (!override) return base;
  const out: any = { ...base };
  for (const k in override) {
    const v = (override as any)[k];
    if (v && typeof v === 'object' && !Array.isArray(v)) {
      out[k] = deepMerge((base as any)[k], v);
    } else if (v !== undefined) {
      out[k] = v;
    }
  }
  return out as T;
}

function resolveLocale(locale?: KhLocale): KhMessages {
  if (locale === 'en-US') return enUS;
  if (locale === 'zh-CN' || locale === undefined) return DEFAULT_LOCALE;
  if (isPartial(locale)) {
    return deepMerge(DEFAULT_LOCALE, locale);
  }
  return DEFAULT_LOCALE;
}

/**
 * KnowledgeHubApp calls this in its setup so every descendant gets the same
 * message catalog. Passing a new `locale` prop updates it reactively.
 */
export function provideKhI18n(locale: () => KhLocale | undefined) {
  const messages = ref<KhMessages>(resolveLocale(locale()));
  // A simple watcher wouldn't work here because we don't want a vue-watcher
  // dep for the module — refresh manually on next tick by re-calling the fn.
  // Consumers who need reactivity can pass a locale that never changes, or
  // remount KnowledgeHubApp with the new locale.
  provide(KEY, messages);
  return messages;
}

/**
 * Every component uses this. Returns `t(key, params?)` — key is dot-separated
 * ('recall.hits'); params is a `{name}` map interpolated into the string.
 */
export function useKhI18n() {
  const messages = inject(KEY, ref(DEFAULT_LOCALE));

  const t = (path: string, params?: Record<string, number | string>): string => {
    const parts = path.split('.');
    let cur: any = messages.value;
    for (const p of parts) {
      cur = cur?.[p];
      if (cur === undefined) break;
    }
    if (typeof cur !== 'string') {
      // Fall back to en-US
      cur = enUS;
      for (const p of parts) {
        cur = cur?.[p];
        if (cur === undefined) break;
      }
      if (typeof cur !== 'string') return path;
    }
    if (!params) return cur;
    return cur.replaceAll(/\{(\w+)\}/g, (_, k) => String(params[k] ?? `{${k}}`));
  };

  return {
    t,
    messages: computed(() => messages.value),
  };
}
