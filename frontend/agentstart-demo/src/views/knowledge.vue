<script setup lang="ts">
import { useRouter } from 'vue-router'
import { KnowledgeApp } from 'vue-agent-start'
import DemoPageSurface from '../components/DemoPageSurface.vue'

const router = useRouter()

/** 库内"去配置嵌入模型"提示 → 跳模型供应商页（对齐 web-antd 约定路由名） */
function onEmbeddingSetup() {
  router.push({ name: 'ModelList' })
}
</script>

<template>
  <DemoPageSurface>
    <KnowledgeApp @go-to-embedding-setup="onEmbeddingSetup" />
  </DemoPageSurface>
</template>
