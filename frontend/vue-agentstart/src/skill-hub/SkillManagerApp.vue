<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons-vue';
import { createAgentStartClient, type SkillEntity, type SkillStatus } from '../client';
import { useAgentStartClient } from '../client/vue';
import { mergeAgentStartHeaders, useAgentStartConfig } from '../config';
import { Button, Empty, Form, FormItem, Input, Card, Modal, Select, Spin, Tag, Textarea, message } from '../ui';

const config = useAgentStartConfig();
const client = useAgentStartClient() ?? createAgentStartClient({ baseUrl: config.apiBase ?? '/api', headers: () => mergeAgentStartHeaders(config.headers) });
const rows = ref<SkillEntity[]>([]); const selectedId = ref(''); const busy = ref(false); const saving = ref(false); const editing = ref(false);
const query = ref(''); const statusFilter = ref<'ALL' | SkillStatus>('ALL');
const form = reactive({ code: '', name: '', description: '', instructions: '', status: 'DRAFT' as SkillStatus, toolNamesText: '' });
const selected = computed(() => rows.value.find(item => item.id === selectedId.value));
const visibleRows = computed(() => { const keyword=query.value.trim().toLowerCase(); return rows.value.filter(item => (statusFilter.value==='ALL'||item.status===statusFilter.value)&&(!keyword||`${item.name} ${item.code} ${item.description??''}`.toLowerCase().includes(keyword))); });
function reset() { selectedId.value = ''; Object.assign(form, { code:'', name:'', description:'', instructions:'', status:'DRAFT', toolNamesText:'' }); }
function openCreate() { reset(); editing.value = true; }
function edit(skill: SkillEntity) { selectedId.value = skill.id; Object.assign(form, { code:skill.code, name:skill.name, description:skill.description ?? '', instructions:skill.instructions, status:skill.status, toolNamesText:parseTools(skill.toolNamesJson).join('\n') }); editing.value = true; }
function parseTools(json?: string) { try { const value=JSON.parse(json ?? '[]'); return Array.isArray(value)?value.map(String):[]; } catch { return []; } }
async function load() { busy.value=true; try { rows.value=await client.skills.list(); } catch(e:any){message.error(e?.message ?? 'Skill 列表加载失败');} finally{busy.value=false;} }
async function save() { const code=form.code.trim(); if(!code||!form.name.trim()||!form.instructions.trim())return void message.warning('请填写编码、名称和 Skill 指令'); if(!/^[A-Za-z][A-Za-z0-9_-]{1,99}$/.test(code))return void message.warning('Skill 编码必须以字母开头，并包含 2–100 个字母、数字、下划线或连字符'); saving.value=true; const body={ code, name:form.name.trim(), description:form.description.trim(), instructions:form.instructions.trim(), status:form.status, toolNames:form.toolNamesText.split(/[\n,]/).map(v=>v.trim()).filter(Boolean) }; try { selectedId.value?await client.skills.update(selectedId.value,body):await client.skills.create(body); editing.value=false; await load(); message.success(selectedId.value?'Skill 已更新':'Skill 已创建'); } catch(e:any){message.error(e?.message ?? 'Skill 保存失败');} finally{saving.value=false;} }
async function setStatus(status: SkillStatus) { if(!selected.value)return; await client.skills.setStatus(selected.value.id,status); await load(); const fresh=rows.value.find(v=>v.id===selected.value?.id); if(fresh)edit(fresh); message.success(status==='PUBLISHED'?'Skill 已发布':'Skill 已停用'); }
async function remove(skill: SkillEntity | undefined = selected.value) { if(!skill || !confirm(`确定删除 Skill「${skill.name}」？`))return; await client.skills.remove(skill.id); if(selectedId.value===skill.id){editing.value=false; reset();} await load(); message.success('Skill 已删除'); }
onMounted(load);
</script>
<template>
  <section class="skill-manager as-management">
    <!-- 页面头部 -->
    <div class="as-page-header">
      <div class="as-page-header-main">
        <div class="as-page-logo" aria-hidden="true">
          <span style="font-size: 18px; font-weight: bold">S</span>
        </div>
        <div class="as-page-header-text">
          <div class="as-page-title">Skill 管理</div>
          <div class="as-page-subtitle">
            可复用技能 · 场景编排 · 工具关联
          </div>
        </div>
      </div>

      <div class="as-page-header-controls">
        <Input v-model:value="query" class="as-management-search" allow-clear placeholder="搜索 Skill 名称或编码" />
        <Button type="primary" @click="openCreate"><PlusOutlined />新建 Skill</Button>
      </div>
    </div>

    <header class="as-management-toolbar">
      <div class="as-management-segments"><button v-for="item in ([['ALL','全部'],['DRAFT','草稿'],['PUBLISHED','已发布'],['DISABLED','已停用']] as const)" :key="item[0]" :class="{active:statusFilter===item[0]}" @click="statusFilter=item[0]">{{ item[1] }}</button></div>
    </header>
    <Spin :spinning="busy">
      <div v-if="!busy&&!visibleRows.length" class="as-management-empty"><Empty description="暂无符合条件的 Skill" /></div>
      <div v-else class="skill-grid as-management-grid">
        <Card variant="management"
          v-for="row in visibleRows"
          :key="row.id"
          class="skill-card"
          :title="row.name"
          :subtitle="row.code"
          :description="row.description || '暂无 Skill 简介'"
          @click="edit(row)"
          @keydown.enter="edit(row)"
        >
          <template #icon>S</template>
          <template #badge>
            <Tag :color="row.status==='PUBLISHED'?'green':row.status==='DISABLED'?'red':'orange'">{{ row.status }}</Tag>
          </template>
          <template #meta>
            <div class="skill-meta">
              <span>v{{ row.version }}</span>
              <span>{{ parseTools(row.toolNamesJson).length }} 个关联工具</span>
            </div>
          </template>
          <template #actions>
            <div class="skill-card-actions">
              <Button size="small" @click.stop="edit(row)"><EditOutlined />编辑</Button>
              <Button size="small" danger @click.stop="remove(row)"><DeleteOutlined />删除</Button>
            </div>
          </template>
        </Card>
      </div>
    </Spin>

    <Modal v-model:open="editing" :width="720" :footer="false" :mask-closable="!saving" :keyboard="!saving" :closable="!saving">
      <template #title>
        <div class="as-modal-heading as-modal-heading--with-extra">
          <div class="as-modal-title">{{ selected ? '编辑 Skill' : '创建 Skill' }}</div>
          <div class="as-modal-subtitle">定义明确的使用场景、执行步骤和输出规范</div>
          <Tag v-if="selected" class="as-modal-heading__extra">v{{ selected.version }}</Tag>
        </div>
      </template>
        <Form class="skill-editor" layout="vertical" @submit.prevent="save">
          <div class="skill-form-row"><FormItem label="编码" required><Input v-model:value="form.code" placeholder="sales_weekly_report" /></FormItem><FormItem label="状态"><Select v-model:value="form.status" :options="[{label:'草稿',value:'DRAFT'},{label:'已发布',value:'PUBLISHED'},{label:'已停用',value:'DISABLED'}]" /></FormItem></div>
          <FormItem label="名称" required><Input v-model:value="form.name" placeholder="销售周报生成规范" /></FormItem>
          <FormItem label="简介"><Textarea v-model:value="form.description" :rows="2" placeholder="说明这个 Skill 解决什么问题" /></FormItem>
          <FormItem label="Skill 指令" required><Textarea v-model:value="form.instructions" :rows="9" placeholder="说明何时使用、数据来源、步骤、校验规则和输出格式…" /></FormItem>
          <FormItem label="关联工具名" extra="每行填写一个工具名称"><Textarea v-model:value="form.toolNamesText" :rows="3" placeholder="query_sales_data" /></FormItem>
          <div class="skill-actions"><Button v-if="selected" danger @click="remove()"><DeleteOutlined />删除</Button><span></span><Button v-if="selected&&form.status!=='DISABLED'" @click="setStatus('DISABLED')">停用</Button><Button v-if="selected&&form.status!=='PUBLISHED'" @click="setStatus('PUBLISHED')">发布</Button><Button @click="editing=false">取消</Button><Button type="primary" :loading="saving" @click="save">{{ selected ? '保存修改' : '创建 Skill' }}</Button></div>
        </Form>
    </Modal>
  </section>
</template>
<style scoped>
.skill-grid {
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
}

.skill-card {
  position: relative;
  display: flex;
  min-height: 164px;
  flex-direction: column;
  padding: 14px;
  overflow: hidden;
  cursor: pointer;
}

.skill-card:hover,
.skill-card:focus-visible {
  z-index: 1;
}

.skill-card .as-management-card__description {
  min-height: 36px;
  margin: 8px 0;
  line-height: 18px;
}

.skill-card footer {
  gap: 8px;
  margin: 6px -14px -14px;
  padding: 7px 14px;
  border-top: 1px solid var(--as-mgmt-line);
  color: var(--as-mgmt-muted);
  font-size: 11px;
}

.skill-meta,
.skill-card-actions,
.skill-actions,
.skill-form-row {
  display: flex;
  align-items: center;
}

.skill-meta {
  min-width: 0;
  gap: 8px;
}

.skill-meta span + span::before {
  margin-right: 8px;
  color: var(--as-mgmt-line);
  content: '·';
}

.skill-card-actions {
  flex: 0 0 auto;
  gap: 5px;
}

.skill-card-actions :deep(.as-btn) {
  gap: 4px;
}

.skill-editor {
  padding-top: 8px;
}

.skill-form-row {
  align-items: flex-start;
  gap: 12px;
}

.skill-form-row > * {
  flex: 1;
}

.skill-form-row > *:last-child {
  max-width: 150px;
}

.skill-actions {
  gap: 8px;
  padding-top: 4px;
}

.skill-actions span {
  flex: 1;
}

@media (max-width: 600px) {
  .skill-form-row {
    display: block;
  }

  .skill-form-row > *:last-child {
    max-width: none;
  }

  .skill-card footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .skill-card-actions {
    align-self: flex-end;
  }
}
</style>
