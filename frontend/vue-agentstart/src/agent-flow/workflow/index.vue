<script setup lang="ts">
import { h, reactive, ref } from 'vue';

import { PlusOutlined } from '@ant-design/icons-vue';

import { getWorkflow } from '@/adapter/backend';

const qw: Record<any, any> = reactive({});

const items: any = ref([]);

function loadData() {
  getWorkflow(qw).then((res) => {
    items.value = res.data;
  });
}

loadData();
</script>

<template>
  <div>
    <div>
      <a-row>
        <a-col :span="8"><div>自动保存 09:13:03 已发布 15 小时前</div></a-col>
        <a-col :span="14">
          <a-space>
            <a-button :icon="h(PlusOutlined)">会话变量</a-button>
            <a-button :icon="h(PlusOutlined)">环境变量</a-button>
            <a-button :icon="h(PlusOutlined)">预览</a-button>
            <a-button :icon="h(PlusOutlined)">功能</a-button>
            <a-button :icon="h(PlusOutlined)">发布</a-button>
            <a-button type="primary" :icon="h(PlusOutlined)">发布</a-button>
            <a-button :icon="h(PlusOutlined)">版本历史</a-button>
          </a-space>
        </a-col>
      </a-row>
    </div>
    <div></div>
    <div>
      <a-row>
        <a-col :span="18" :push="6">
          <a-space wrap />
        </a-col>
        <a-col :span="6" :pull="18">
          <a-space wrap />
        </a-col>
      </a-row>
    </div>
  </div>
</template>

<style scoped></style>
