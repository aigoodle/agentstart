import { describe, expect, it } from 'vitest';
import { parse } from 'vue/compiler-sfc';

// Catalog pages must compose the common Card instead of maintaining independent skins.
const catalogs = import.meta.glob([
  '../../agent-studio/components/AgentAppsPage.vue', '../../agent-studio/components/AgentCardGrid.vue',
  '../../knowledge-hub/components/DatasetCardGrid.vue', '../../channel-hub/components/ChannelConnectionPanel.vue',
  '../../channel-hub/components/MyRobotsPanel.vue', '../../mcp-hub/McpServerManager.vue',
  '../../tool-hub/ToolManager.vue', '../../skill-hub/SkillManagerApp.vue', '../../plugin-hub/PluginManagerApp.vue',
  '../../connector-hub/components/ConnectorHubApp.vue', '../../model-provider-hub/components/ProviderHubShell.vue',
  '../../model-provider-hub/components/ModelCardGrid.vue', '../../model-provider-hub/components/ProviderGallery.vue',
], { eager: true, query: '?raw', import: 'default' });
describe('catalog Card architecture', () => {
  it.each(Object.entries(catalogs))('%s uses the shared component without overriding its internal layout', (file, source) => {
    const { descriptor } = parse(source as string);
    expect(descriptor.scriptSetup?.content).toMatch(/import\s*\{[^}]*\bCard\b[^}]*\}\s*from\s*['"][.\/]+ui['"]/);
    expect(descriptor.template?.content).toContain('<Card');
    expect(descriptor.template?.content).not.toContain('<AsShadcnCard');
    for (const style of descriptor.styles) {
      expect(style.content).not.toMatch(/as-ui-card__|as-management-card__/);
      for (const rule of style.content.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
        const selector = rule[1]!.replace(/\/\*[\s\S]*?\*\//g, '').trim();
        if (/\.[\w-]*card(?![\w-])(?::(?:hover|focus-visible))?$/.test(selector)) {
          expect(rule[2], `${file}: ${selector}`).not.toMatch(/(?:padding|gap|border|background|box-shadow|min-height|height|overflow)\s*:/);
        }
      }
    }
  });
});
