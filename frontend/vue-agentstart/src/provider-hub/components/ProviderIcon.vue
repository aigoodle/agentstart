<script setup lang="ts">
/**
 * ProviderIcon — brand SVG for each model provider.
 *
 * Resolution order (first match wins):
 *   1. {@code svg}     — raw SVG markup, typically the {@code svg_icon} column
 *                        the backend returns for user-defined providers.
 *   2. {@code iconUrl} — direct URL, e.g. {@code /api/agent-start/providers/{name}/icon}
 *                        or a CDN link.
 *   3. Built-in file   — a matching {@code ../svg/<name>.svg} discovered at
 *                        build time via {@code import.meta.glob}.
 *   4. Fallback tile   — neutral gray rounded-square with the first letter.
 *
 * Raw SVG markup is emitted as a {@code data:image/svg+xml} URL and rendered
 * through {@code <img>}. Browsers load SVGs referenced by {@code <img>} in
 * "image mode", which disables scripts, external references, and event
 * handlers — so DB-sourced markup cannot execute XSS.
 */
import { computed } from 'vue';

interface Props {
  /** Provider name — case-insensitive, matches ModelProvider.getName(). */
  name: string;
  /** Rendered pixel size (both width and height). */
  size?: number | string;
  /** Optional radius override. Defaults to 6. */
  radius?: number;
  /** Raw SVG markup, e.g. the backend {@code svg_icon} field. */
  svg?: string;
  /** Direct icon URL, e.g. {@code /api/agent-start/providers/{name}/icon}. */
  iconUrl?: string;
}

const props = withDefaults(defineProps<Props>(), { size: 32 });

// Eagerly resolve every SVG under ../svg/ to a URL string. Vite returns a
// map like { "../svg/openai.svg": "/assets/openai.<hash>.svg", ... }; we
// re-key it by lowercased filename (no extension) for O(1) lookup.
const svgUrlModules = import.meta.glob('../svg/*.svg', {
  eager: true,
  query: '?url',
  import: 'default',
}) as Record<string, string>;

const builtinIcons: Record<string, string> = {};
for (const [path, url] of Object.entries(svgUrlModules)) {
  const file = path.split('/').pop() ?? '';
  const stem = file.replace(/\.svg$/i, '').toLowerCase();
  builtinIcons[stem] = url;
}

// Provider names in the DB may differ from filenames. Everything on the right
// must exist in builtinIcons (otherwise the alias silently falls back to the
// letter tile, which is still fine).
const aliases: Record<string, string> = {
  claude: 'anthropic',
  google: 'gemini',
  'azure-openai': 'azure',
  'amazon-bedrock': 'bedrock',
};

const key = computed(() => (props.name || '').toLowerCase());

const svgDataUrl = computed(() => {
  const raw = props.svg?.trim();
  if (!raw) return null;
  // Strip XML prolog / doctype — they're valid in files but noisy inside a
  // data URL and some browsers dislike doctype in <img> sources.
  const cleaned = raw
    .replace(/^<\?xml[^?]*\?>\s*/i, '')
    .replace(/<!DOCTYPE[^>]*>\s*/i, '');
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(cleaned)}`;
});

const resolvedUrl = computed(() => {
  if (svgDataUrl.value) return svgDataUrl.value;
  if (props.iconUrl) return props.iconUrl;
  const k = key.value;
  return builtinIcons[k] ?? builtinIcons[aliases[k] ?? ''] ?? null;
});

const radius = computed(() => props.radius ?? 6);
const fallbackChar = computed(() =>
  props.name ? props.name[0]!.toUpperCase() : '?',
);
</script>

<template>
  <img
    v-if="resolvedUrl"
    :src="resolvedUrl"
    :width="size"
    :height="size"
    :alt="name"
    class="provider-icon"
    :style="{ borderRadius: `${radius}px` }"
    loading="lazy"
    decoding="async"
  />

  <svg
    v-else
    :width="size"
    :height="size"
    viewBox="0 0 32 32"
    role="img"
    :aria-label="name"
  >
    <rect width="32" height="32" :rx="radius" fill="#475569" />
    <text
      x="16"
      y="21"
      text-anchor="middle"
      font-family="ui-sans-serif, system-ui, sans-serif"
      font-size="15"
      font-weight="700"
      fill="#fff"
    >
      {{ fallbackChar }}
    </text>
  </svg>
</template>

<style scoped>
.provider-icon {
  display: inline-block;
  object-fit: contain;
  vertical-align: middle;
}
</style>
