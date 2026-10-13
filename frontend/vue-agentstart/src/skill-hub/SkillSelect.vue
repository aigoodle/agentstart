<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { createAgentStartClient, type SkillEntity } from '../client';
import { useAgentStartClient } from '../client/vue';
import { mergeAgentStartHeaders, useAgentStartConfig } from '../config';

defineProps<{ disabled?: boolean }>();
const model = defineModel<string[]>({ default: () => [] });
const config = useAgentStartConfig();
const client = useAgentStartClient() ?? createAgentStartClient({ baseUrl: config.apiBase ?? '/api', headers: () => mergeAgentStartHeaders(config.headers) });
const skills = ref<SkillEntity[]>([]);
const loading = ref(false);
const error = ref('');
onMounted(async () => {
  loading.value = true;
  try { skills.value = await client.skills.list('PUBLISHED'); }
  catch (e: any) { error.value = e?.message ?? 'Skill 加载失败'; }
  finally { loading.value = false; }
});
</script>
<template>
  <div class="skill-select">
    <select v-model="model" multiple :disabled="disabled || loading">
      <option v-for="skill in skills" :key="skill.id" :value="skill.id">{{ skill.name }} · v{{ skill.version ?? 1 }}</option>
    </select>
    <small v-if="loading">正在加载 Skill…</small>
    <small v-else-if="error" class="error">{{ error }}</small>
    <small v-else>运行时会把已发布 Skill 的业务规范追加到 Agent 系统提示词。</small>
  </div>
</template>
<style scoped>
.skill-select select{width:100%;min-height:92px;padding:6px;border:1px solid #e5e7eb;border-radius:8px;background:#fff}.skill-select small{display:block;margin-top:6px;color:#8b93a7}.skill-select .error{color:#ef4444}
</style>
