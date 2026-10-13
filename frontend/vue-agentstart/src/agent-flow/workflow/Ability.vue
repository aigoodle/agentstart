<script setup lang="ts">
import { h, reactive } from 'vue';

import { PlusOutlined } from '@ant-design/icons-vue';

const formState = reactive<any>({
  prologue: '',
  context: '',
  system: '',
  selectOptions: [],
  required: false,
  memory: { windows: 1 },
});
</script>

<template>
  <div>
    <div>
      <div>
        <div>对话开场白</div>
        <div></div>
        <div><a-switch v-model:checked="formState.prologue" /></div>
      </div>
      <div>在对话型应用中，让 AI 主动说第一段话可以拉近与用户间的距离。</div>
      <div>
        <a-button :icon="h(PlusOutlined)" size="small">编辑开场白</a-button>
      </div>
    </div>
    <div>
      <div>
        <div>下一步问题建议</div>
        <div></div>
        <div><a-switch v-model:checked="formState.prologue" /></div>
      </div>
      <div>设置下一步问题建议可以让用户更好的对话。</div>
      <div>
        <a-button :icon="h(PlusOutlined)" size="small">编辑开场白</a-button>
      </div>
    </div>
    <div>
      <div>
        <div>引用和归属</div>
        <div></div>
        <div><a-switch v-model:checked="formState.prologue" /></div>
      </div>
      <div>显示源文档和生成内容的归属部分。</div>
      <div>
        <a-button :icon="h(PlusOutlined)" size="small">编辑开场白</a-button>
      </div>
    </div>
    <div>
      <div>
        <div>内容审查</div>
        <div></div>
        <div><a-switch v-model:checked="formState.prologue" /></div>
      </div>
      <div>您可以调用审查 API 或者维护敏感词库来使模型更安全地输出</div>
      <div>
        <a-button :icon="h(PlusOutlined)" size="small">编辑开场白</a-button>
      </div>
    </div>
  </div>
</template>

<style scoped></style>
