<script setup lang="ts">
import type { RobotUser } from 'vue-agent-start/channel-hub'
import { MyRobotsPanel } from 'vue-agent-start/channel-hub'
import DemoPageSurface from '../components/DemoPageSurface.vue'

/** 演示身份 —— mock 种子里 robot-1 的 ownerId 即 demo-user */
const demoUser: RobotUser = {
  userId: 'demo-user',
  username: 'demo',
  realName: '演示用户',
}
</script>

<template>
  <DemoPageSurface>
    <MyRobotsPanel :user="demoUser" tenant-id="default" />
  </DemoPageSurface>
</template>
