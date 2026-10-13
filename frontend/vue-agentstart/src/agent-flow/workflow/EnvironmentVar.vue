<script setup lang="ts">
import { h } from 'vue';

import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
} from '@ant-design/icons-vue';

const userItems = [{ name: 'sys.query', type: 'String' }];
</script>

<template>
  <div>
    <div>
      <div>
        会话变量用于存储 LLM
        需要的上下文信息，如用户偏好、对话历史等。它是可读写的。查看文档了解更多。
      </div>
      <div><a-button :icon="h(PlusOutlined)" size="small" /></div>
    </div>
    <div><a-button :icon="h(PlusOutlined)" size="small" />添加变量</div>
    <div>
      <div v-for="(item, index) in userItems" :key="index">
        <div>
          <span>{x}</span>
          <span>{{ item.name }}</span>
        </div>
        <div>
          <span>必填</span>
          <span>String</span>
          <span><a-button :icon="h(EditOutlined)" size="small" /></span>
          <span><a-button :icon="h(DeleteOutlined)" size="small" /></span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped></style>
