<script setup lang="ts">
import { computed } from 'vue';
import { Button } from '../shadcn/button';
import { Select, SelectTrigger, SelectValue, SelectContent, SelectItem } from '../shadcn/select';
const props = withDefaults(defineProps<{ total: number; page: number; pageSize: number; pageSizes?: number[] }>(), { pageSizes: () => [10, 20, 50] });
const emit = defineEmits<{ 'page-change': [page: number]; 'page-size-change': [size: number] }>();
const pages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)));
</script>
<template>
  <nav aria-label="分页" class="as-pagination">
    <span class="as-pagination-total">共 {{ total }} 条</span>
    <div class="as-pagination-size">
      <Select :model-value="String(pageSize)" @update:model-value="value => emit('page-size-change', Number(value))">
        <SelectTrigger aria-label="每页条数" class="h-8 w-full text-xs"><SelectValue /></SelectTrigger>
        <SelectContent><SelectItem v-for="size in pageSizes" :key="size" :value="String(size)">{{ size }} 条/页</SelectItem></SelectContent>
      </Select>
    </div>
    <Button variant="outline" size="sm" :disabled="page <= 1" @click="emit('page-change', page - 1)">上一页</Button>
    <span aria-live="polite">{{ page }} / {{ pages }}</span>
    <Button variant="outline" size="sm" :disabled="page >= pages" @click="emit('page-change', page + 1)">下一页</Button>
  </nav>
</template>
<style scoped>
.as-pagination { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: 10px; padding: 14px 0; color: var(--muted-foreground); font-size: 12px; }
.as-pagination-size { flex: 0 0 112px; width: 112px; }
.as-pagination-total { margin-right: auto; }
</style>
