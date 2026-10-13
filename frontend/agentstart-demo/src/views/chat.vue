<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { AgentChatPage } from 'vue-agent-start'

const route = useRoute()
const router = useRouter()

const agentId = computed(() => String(route.params.id ?? ''))
</script>

<template>
  <div class="view-fill chat-view">
    <div class="chat-topbar">
      <button type="button" class="chat-back" @click="router.push({ name: 'AgentApps' })">
        ‹ 返回应用列表
      </button>
      <span class="chat-sep">/</span>
      <span class="hud-label">对话调试</span>
      <span class="hud-mono chat-agent">{{ agentId }}</span>
    </div>
    <div class="hud-panel hud-embed chat-host">
      <AgentChatPage :agent-id="agentId" />
    </div>
  </div>
</template>

<style scoped>
.chat-view {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.chat-topbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: none;
}

.chat-back {
  padding: 4px 12px;
  font-size: 12px;
  color: var(--hud-text);
  background: var(--hud-panel-bg);
  border: 1px solid var(--hud-border);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}
.chat-back:hover {
  color: var(--hud-primary-bright);
  border-color: var(--hud-border-strong);
  box-shadow: var(--hud-glow);
}

.chat-sep {
  color: var(--hud-text-faint);
}

.chat-agent {
  font-size: 12px;
  color: var(--hud-primary);
}

.chat-host {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}
</style>
