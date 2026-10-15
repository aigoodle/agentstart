<script setup>
import { ShadcnRange as AsShadcnRange } from '../../ui/components/ShadcnRange';

import { Input as AsShadcnInput } from '../../ui/shadcn/input';
import { Textarea as AsShadcnTextarea } from '../../ui/shadcn/textarea';
import { ShadcnSelect as AsShadcnSelect } from '../../ui/components/ShadcnSelect';
import { Button as AsShadcnButton } from '../../ui/shadcn/button';

import { reactive, ref, watch } from 'vue';

import Icon from './Icon.vue';

const props = defineProps({
  node: {
    type: Object,
    required: true,
  },
});

const emit = defineEmits(['update', 'delete']);

const localData = reactive({
  id: props.node.id,
  label: props.node.data?.label || '',
  description: props.node.data?.description || '',
  config: { ...props.node.data?.config },
});

const headerKeys = ref(Object.keys(localData.config.headers || {}));

watch(
  () => props.node,
  (newNode) => {
    localData.id = newNode.id;
    localData.label = newNode.data?.label || '';
    localData.description = newNode.data?.description || '';
    localData.config = { ...newNode.data?.config };
    headerKeys.value = Object.keys(localData.config.headers || {});
  },
  { deep: true },
);

const updateData = () => {
  emit('update', {
    data: {
      label: localData.label,
      description: localData.description,
      config: localData.config,
    },
  });
};

const addHeader = () => {
  if (!localData.config.headers) {
    localData.config.headers = {};
  }
  const newKey = `header-${Date.now()}`;
  localData.config.headers[newKey] = '';
  headerKeys.value.push(newKey);
  updateData();
};

const removeHeader = (key) => {
  delete localData.config.headers[key];
  headerKeys.value = headerKeys.value.filter((k) => k !== key);
  updateData();
};

const updateHeaders = () => {
  const newHeaders = {};
  headerKeys.value.forEach((key, index) => {
    if (key) {
      newHeaders[key] = Object.values(localData.config.headers)[index] || '';
    }
  });
  localData.config.headers = newHeaders;
  updateData();
};

const addCondition = () => {
  if (!localData.config.conditions) {
    localData.config.conditions = [];
  }
  localData.config.conditions.push({ expression: '' });
  updateData();
};

const removeCondition = (index) => {
  localData.config.conditions.splice(index, 1);
  updateData();
};
</script>

<template>
  <div class="node-config">
    <!-- 基本信息 -->
    <div class="config-section">
      <div class="section-title">基本信息</div>
      <div class="form-group">
        <label>节点名称</label>
        <AsShadcnInput
          v-model="localData.label"
          @input="updateData"
          class="form-input"
          placeholder="输入节点名称"
        />
      </div>
      <div class="form-group">
        <label>节点描述</label>
        <AsShadcnTextarea
          v-model="localData.description"
          @input="updateData"
          class="form-textarea"
          placeholder="输入节点描述"
          rows="2"
        ></AsShadcnTextarea>
      </div>
    </div>

    <!-- LLM 节点配置 -->
    <div class="config-section" v-if="node.type === 'LLM'">
      <div class="section-title">模型配置</div>
      <div class="form-group">
        <label>模型选择</label>
        <AsShadcnSelect
          v-model="localData.config.model"
          @change="updateData"
          class="form-select"
        >
          <option value="gpt-3.5-turbo">GPT-3.5 Turbo</option>
          <option value="gpt-4">GPT-4</option>
          <option value="gpt-4-turbo">GPT-4 Turbo</option>
          <option value="claude-3-sonnet">Claude 3 Sonnet</option>
          <option value="claude-3-opus">Claude 3 Opus</option>
        </AsShadcnSelect>
      </div>
      <div class="form-group">
        <label>温度 ({{ localData.config.temperature }})</label>
        <AsShadcnRange
          type="range"
          min="0"
          max="2"
          step="0.1"
          v-model="localData.config.temperature"
          @input="updateData"
          class="form-range"
        />
      </div>
      <div class="form-group">
        <label>最大令牌数</label>
        <AsShadcnInput
          type="number"
          v-model="localData.config.max_tokens"
          @input="updateData"
          class="form-input"
          min="1"
          max="4000"
        />
      </div>
      <div class="form-group">
        <label>系统提示词</label>
        <AsShadcnTextarea
          v-model="localData.config.prompt"
          @input="updateData"
          class="form-textarea"
          placeholder="输入系统提示词..."
          rows="4"
        ></AsShadcnTextarea>
      </div>
    </div>

    <!-- HTTP 节点配置 -->
    <div class="config-section" v-if="node.type === 'HTTP_REQUEST'">
      <div class="section-title">HTTP 配置</div>
      <div class="form-group">
        <label>请求方法</label>
        <AsShadcnSelect
          v-model="localData.config.method"
          @change="updateData"
          class="form-select"
        >
          <option value="GET">GET</option>
          <option value="POST">POST</option>
          <option value="PUT">PUT</option>
          <option value="DELETE">DELETE</option>
          <option value="PATCH">PATCH</option>
        </AsShadcnSelect>
      </div>
      <div class="form-group">
        <label>请求 URL</label>
        <AsShadcnInput
          v-model="localData.config.url"
          @input="updateData"
          class="form-input"
          placeholder="https://api.example.com/endpoint"
        />
      </div>
      <div class="form-group">
        <label>请求头</label>
        <div class="headers-editor">
          <div
            v-for="(value, key, index) in localData.config.headers"
            :key="index"
            class="header-item"
          >
            <AsShadcnInput
              v-model="headerKeys[index]"
              @input="updateHeaders"
              class="header-key"
              placeholder="键"
            />
            <AsShadcnInput
              v-model="localData.config.headers[key]"
              @input="updateData"
              class="header-value"
              placeholder="值"
            />
            <AsShadcnButton variant="ghost" @click="removeHeader(key)" class="remove-btn">×</AsShadcnButton>
          </div>
          <AsShadcnButton variant="ghost" @click="addHeader" class="add-btn">+ 添加请求头</AsShadcnButton>
        </div>
      </div>
      <div
        class="form-group"
        v-if="['POST', 'PUT', 'PATCH'].includes(localData.config.method)"
      >
        <label>请求体</label>
        <AsShadcnTextarea
          v-model="localData.config.body"
          @input="updateData"
          class="form-textarea"
          placeholder="JSON 格式的请求体..."
          rows="4"
        ></AsShadcnTextarea>
      </div>
    </div>

    <!-- 代码节点配置 -->
    <div class="config-section" v-if="node.type === 'CODE'">
      <div class="section-title">代码配置</div>
      <div class="form-group">
        <label>编程语言</label>
        <AsShadcnSelect
          v-model="localData.config.language"
          @change="updateData"
          class="form-select"
        >
          <option value="python">Python</option>
          <option value="javascript">JavaScript</option>
          <option value="typescript">TypeScript</option>
          <option value="java">Java</option>
          <option value="go">Go</option>
        </AsShadcnSelect>
      </div>
      <div class="form-group">
        <label>代码内容</label>
        <AsShadcnTextarea
          v-model="localData.config.code"
          @input="updateData"
          class="form-textarea code-editor"
          placeholder="输入代码..."
          rows="10"
        ></AsShadcnTextarea>
      </div>
    </div>

    <!-- 知识检索节点配置 -->
    <div class="config-section" v-if="node.type === 'KNOWLEDGE_RETRIEVAL'">
      <div class="section-title">知识检索配置</div>
      <div class="form-group">
        <label>数据集</label>
        <AsShadcnSelect
          v-model="localData.config.dataset"
          @change="updateData"
          class="form-select"
        >
          <option value="">选择数据集</option>
          <option value="knowledge-base-1">知识库 1</option>
          <option value="knowledge-base-2">知识库 2</option>
          <option value="faq-dataset">FAQ 数据集</option>
        </AsShadcnSelect>
      </div>
      <div class="form-group">
        <label>Top K</label>
        <AsShadcnInput
          type="number"
          v-model="localData.config.top_k"
          @input="updateData"
          class="form-input"
          min="1"
          max="10"
        />
      </div>
      <div class="form-group">
        <label>查询表达式</label>
        <AsShadcnInput
          v-model="localData.config.query"
          @input="updateData"
          class="form-input"
          placeholder="输入查询表达式..."
        />
      </div>
    </div>

    <!-- 条件节点配置 -->
    <div class="config-section" v-if="node.type === 'IF_ELSE'">
      <div class="section-title">条件配置</div>
      <div class="conditions-editor">
        <div
          v-for="(condition, index) in localData.config.conditions"
          :key="index"
          class="condition-item"
        >
          <AsShadcnInput
            v-model="condition.expression"
            @input="updateData"
            class="form-input"
            placeholder="条件表达式"
          />
          <AsShadcnButton variant="ghost" @click="removeCondition(index)" class="remove-btn">×</AsShadcnButton>
        </div>
        <AsShadcnButton variant="ghost" @click="addCondition" class="add-btn">+ 添加条件</AsShadcnButton>
      </div>
    </div>

    <!-- 回复节点配置 -->
    <div class="config-section" v-if="node.type === 'ANSWER'">
      <div class="section-title">回复配置</div>
      <div class="form-group">
        <label>回复内容</label>
        <AsShadcnTextarea
          v-model="localData.config.text"
          @input="updateData"
          class="form-textarea"
          placeholder="输入回复内容..."
          rows="4"
        ></AsShadcnTextarea>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="config-actions">
      <AsShadcnButton variant="ghost" @click="$emit('delete')" class="btn btn-danger">
        <Icon name="close" />
        删除节点
      </AsShadcnButton>
    </div>
  </div>
</template>

<style scoped>
.node-config {
  height: 100%;
  overflow-y: auto;
}

.config-section {
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border);
}

.config-section:last-child {
  border-bottom: none;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--foreground);
  margin-bottom: 12px;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  font-size: 12px;
  font-weight: 500;
  color: var(--foreground);
  margin-bottom: 6px;
}

.form-input,
.form-select,
.form-textarea {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: 6px;
  font-size: 13px;
  transition: border-color 0.2s;
}

.form-input:focus,
.form-select:focus,
.form-textarea:focus {
  outline: none;
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}

.form-textarea {
  resize: vertical;
  min-height: 60px;
}

.form-textarea.code-editor {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 12px;
}

.form-range {
  width: 100%;
  margin: 8px 0;
}

.headers-editor,
.conditions-editor {
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 12px;
  background: var(--muted);
}

.header-item,
.condition-item {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
}

.header-item:last-child,
.condition-item:last-child {
  margin-bottom: 0;
}

.header-key,
.header-value {
  flex: 1;
  padding: 6px 8px;
  border: 1px solid var(--border);
  border-radius: 4px;
  font-size: 12px;
}

.remove-btn {
  background: #ef4444;
  color: white;
  border: none;
  border-radius: 4px;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 14px;
}

.remove-btn:hover {
  background: #dc2626;
}

.add-btn {
  background: var(--muted);
  color: var(--foreground);
  border: 1px dashed var(--border);
  border-radius: 4px;
  padding: 8px 12px;
  font-size: 12px;
  cursor: pointer;
  width: 100%;
  transition: all 0.2s;
}

.add-btn:hover {
  background: var(--muted);
  border-color: #9ca3af;
}

.config-actions {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--border);
}

.btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  width: 100%;
  justify-content: center;
}

.btn-danger {
  background: #fef2f2;
  color: #dc2626;
  border: 1px solid #fecaca;
}

.btn-danger:hover {
  background: #fecaca;
  border-color: #f87171;
}
</style>
