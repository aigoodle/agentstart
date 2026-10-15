<script setup lang="ts">
import { ref } from 'vue'
import {
  UiButton, UiCard, UiCardHeader, UiCardTitle, UiCardDescription, UiCardContent, UiCardFooter,
  UiInput, UiTextarea, UiLabel, UiNativeSelect, UiCheckbox, UiSwitch, UiSlider,
  UiTabs, UiTabsList, UiTabsTrigger, UiTabsContent, UiBadge, UiSeparator,
  Modal, Drawer, Button, InputNumber, Select, JsonSchemaForm,
} from 'vue-agent-start'
import DemoPageSurface from '../components/DemoPageSurface.vue'

const name = ref('AgentStart')
const description = ref('使用同一套组件和主题令牌。')
const provider = ref(1)
const tags = ref<string[]>(['LLM'])
const enabled = ref(true)
const checked = ref(false)
const temperature = ref([0.7])
const count = ref(3)
const modalOpen = ref(false)
const drawerOpen = ref(false)
const nestedOpen = ref(false)
const saved = ref('')
const schemaValue = ref({ retries: 3, enabled: true, strategy: 'DIRECT' })
const providers = [{ value: 1, label: 'OpenAI' }, { value: 2, label: '阿里云百炼' }, { value: 3, label: '暂未接入', disabled: true }]
const schema = { type: 'object', properties: {
  retries: { type: 'integer', title: '重试次数', minimum: 0 },
  enabled: { type: 'boolean', title: '启用' },
  strategy: { type: 'string', title: '查询策略', enum: ['DIRECT', 'MULTI_QUERY'] },
} }
function save() { saved.value = `已保存 ${name.value} · ${providers.find(item => item.value === provider.value)?.label}`; modalOpen.value = false }
</script>

<template>
  <DemoPageSurface>
    <div class="ui-demo">
      <header><h1>UI 组件</h1><p>在右上角切换明亮、暗黑、系统主题，以及普通／科技风格。</p></header>
      <UiTabs default-value="form">
        <UiTabsList><UiTabsTrigger value="form">表单组件</UiTabsTrigger><UiTabsTrigger value="overlay">弹窗与抽屉</UiTabsTrigger></UiTabsList>
        <UiTabsContent value="form">
          <div class="ui-demo-grid">
            <UiCard>
              <UiCardHeader><UiCardTitle>输入与选择</UiCardTitle><UiCardDescription>文本、数字、选择、标签使用同一个视觉系统。</UiCardDescription></UiCardHeader>
              <UiCardContent class="ui-demo-fields">
                <UiLabel for="ui-name">名称</UiLabel><UiInput id="ui-name" v-model="name" placeholder="输入名称" />
                <UiLabel for="ui-description">说明</UiLabel><UiTextarea id="ui-description" v-model="description" />
                <UiLabel>模型供应商</UiLabel><UiNativeSelect v-model="provider" :options="providers" placeholder="选择供应商" />
                <UiLabel>能力标签</UiLabel><Select v-model:value="tags" mode="tags" :options="[{value:'LLM',label:'LLM'},{value:'Embedding',label:'Embedding'}]" allow-clear />
                <UiLabel>重试次数</UiLabel><InputNumber v-model:value="count" :min="0" :max="10" />
                <UiSeparator />
                <div class="ui-demo-inline"><UiSwitch id="ui-enabled" v-model="enabled" /><UiLabel for="ui-enabled">启用服务</UiLabel></div>
                <div class="ui-demo-inline"><UiCheckbox id="ui-confirm" v-model="checked" /><UiLabel for="ui-confirm">确认设置</UiLabel></div>
                <UiLabel>Temperature · {{ temperature[0] }}</UiLabel><UiSlider v-model="temperature" :min="0" :max="2" :step="0.1" />
              </UiCardContent>
              <UiCardFooter><UiBadge variant="secondary">供应商编号：{{ provider }}</UiBadge></UiCardFooter>
            </UiCard>
            <UiCard>
              <UiCardHeader><UiCardTitle>按钮与动态表单</UiCardTitle><UiCardDescription>支持现有接口，使用新的基础组件。</UiCardDescription></UiCardHeader>
              <UiCardContent class="ui-demo-fields">
                <div class="ui-demo-inline"><UiButton>主要操作</UiButton><UiButton variant="outline">次要操作</UiButton><UiButton variant="destructive">删除</UiButton></div>
                <div class="ui-demo-inline"><UiButton variant="ghost">轻量操作</UiButton><UiButton disabled>不可用</UiButton><Button loading>加载中</Button></div>
                <UiSeparator /><JsonSchemaForm v-model="schemaValue" :schema="schema" />
                <p class="ui-demo-result">{{ JSON.stringify(schemaValue) }}</p>
              </UiCardContent>
            </UiCard>
          </div>
        </UiTabsContent>
        <UiTabsContent value="overlay">
          <UiCard><UiCardHeader><UiCardTitle>Dialog 与 Sheet</UiCardTitle><UiCardDescription>保留 HUD 周边线条，并统一焦点、Escape 和遮罩行为。</UiCardDescription></UiCardHeader>
            <UiCardContent class="ui-demo-inline"><UiButton @click="modalOpen = true">打开 Modal</UiButton><UiButton variant="outline" @click="drawerOpen = true">打开抽屉</UiButton></UiCardContent>
          </UiCard>
        </UiTabsContent>
      </UiTabs>
      <p v-if="saved" role="status">{{ saved }}</p>
      <Modal v-model:open="modalOpen" title="编辑设置" @ok="save">
        <UiLabel for="ui-modal-name">名称</UiLabel><UiInput id="ui-modal-name" v-model="name" />
        <UiLabel>模型供应商</UiLabel><UiNativeSelect v-model="provider" :options="providers" />
      </Modal>
      <Drawer v-model:open="drawerOpen" title="服务设置" :width="440">
        <UiLabel>说明</UiLabel><UiTextarea v-model="description" />
        <UiButton @click="nestedOpen = true">在抽屉中打开弹窗</UiButton>
        <Modal v-model:open="nestedOpen" title="嵌套弹窗" @ok="nestedOpen = false"><p>Escape 只关闭最上层，关闭后焦点回到抽屉。</p></Modal>
      </Drawer>
    </div>
  </DemoPageSurface>
</template>
<style scoped>
.ui-demo { padding: 24px; color: var(--foreground); }
.ui-demo header { margin-bottom: 24px; }
.ui-demo h1 { font-size: 24px; margin: 0 0 8px; }
.ui-demo header p, .ui-demo-result { color: var(--muted-foreground); }
.ui-demo-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(340px, 100%), 1fr)); gap: 20px; }
.ui-demo-fields { display: grid; gap: 12px; }
.ui-demo-inline { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.ui-demo-result { overflow-wrap: anywhere; font-size: 12px; }
</style>
