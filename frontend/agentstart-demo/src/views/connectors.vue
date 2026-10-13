<script setup lang="ts">
import { ConnectorHubApp } from 'vue-agent-start/connector-hub'
import DemoPageSurface from '../components/DemoPageSurface.vue'
</script>

<template>
  <DemoPageSurface>
    <ConnectorHubApp tenant-id="default" />
  </DemoPageSurface>
</template>
