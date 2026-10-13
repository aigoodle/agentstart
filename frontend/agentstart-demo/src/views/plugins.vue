<script setup lang="ts">
import { PluginManagerApp } from 'vue-agent-start/plugin-hub'
import DemoPageSurface from '../components/DemoPageSurface.vue'
</script>

<template>
  <DemoPageSurface>
    <PluginManagerApp tenant-id="default" title="插件市场" />
  </DemoPageSurface>
</template>
