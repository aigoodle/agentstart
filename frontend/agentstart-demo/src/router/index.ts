import type { Component } from 'vue'
import type { RouteRecordRaw } from 'vue-router'

import {
  ApiOutlined,
  AppstoreOutlined,
  CloudServerOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  DeploymentUnitOutlined,
  ExperimentOutlined,
  SendOutlined,
  ThunderboltOutlined,
  ToolOutlined,
} from '@ant-design/icons-vue'
import { createRouter, createWebHistory } from 'vue-router'

/** 侧边栏菜单定义（与路由一一对应，供 App.vue 外壳渲染） */
export interface MenuItem {
  path: string
  title: string
  icon: Component
  /** 详情类页面高亮所属菜单（如 /apps/:id/chat 高亮 /apps） */
  activePath?: string
  hidden?: boolean
}

export interface MenuGroup {
  group: string
  items: MenuItem[]
}

export const menuGroups: MenuGroup[] = [
  {
    group: '总控',
    items: [
      { path: '/overview', title: '总览驾驶舱', icon: DashboardOutlined },
    ],
  },
  {
    group: '智能体编排',
    items: [
      { path: '/apps', title: '应用工坊', icon: AppstoreOutlined },
      {
        path: '/apps/:id/chat',
        title: '对话调试',
        icon: SendOutlined,
        activePath: '/apps',
        hidden: true,
      }
    ],
  },
  {
    group: '知识与模型',
    items: [
      { path: '/knowledge', title: '知识库', icon: DatabaseOutlined },
      { path: '/model', title: '模型供应商', icon: CloudServerOutlined },
    ],
  },
  {
    group: '生态扩展',
    items: [
      { path: '/channels', title: '渠道接入', icon: DeploymentUnitOutlined },
      { path: '/plugins', title: '插件市场', icon: AppstoreOutlined },
      { path: '/mcp', title: 'MCP 服务', icon: ApiOutlined },
      { path: '/skills', title: '技能中心', icon: ExperimentOutlined },
      { path: '/tools', title: '工具列表', icon: ToolOutlined },
      { path: '/triggers', title: '触发器', icon: ThunderboltOutlined },
    ],
  },
]

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/overview' },
  {
    name: 'Overview',
    path: '/overview',
    component: () => import('../views/overview.vue'),
    meta: { title: '总览驾驶舱', group: '总控' },
  },
  {
    name: 'AgentApps',
    path: '/apps',
    component: () => import('../views/apps.vue'),
    meta: { title: '应用工坊', group: '智能体编排' },
  },
  {
    // 名字必须是 AgentChat —— AgentAppsPage 内部 router.push({name:'AgentChat'})
    name: 'AgentChat',
    path: '/apps/:id/chat',
    component: () => import('../views/chat.vue'),
    meta: { title: '对话调试', group: '智能体编排', activePath: '/apps' },
  },
  {
    name: 'KnowledgeList',
    path: '/knowledge',
    component: () => import('../views/knowledge.vue'),
    meta: { title: '知识库', group: '知识与模型' },
  },
  {
    // 名字对齐 web-antd 约定：库内“去配置模型”跳 ModelList
    name: 'ModelList',
    path: '/model',
    component: () => import('../views/model.vue'),
    meta: { title: '模型供应商', group: '知识与模型' },
  },
  {
    name: 'ConnectorHub',
    path: '/connectors',
    component: () => import('../views/connectors.vue'),
    meta: { title: 'Connector 中心', group: '生态扩展' },
  },
  {
    name: 'MyRobots',
    path: '/robots',
    component: () => import('../views/robots.vue'),
    meta: { title: '我的机器人', group: '生态扩展' },
  },
  {
    name: 'Channels',
    path: '/channels',
    component: () => import('../views/channels.vue'),
    meta: { title: '渠道接入', group: '生态扩展' },
  },
  {
    name: 'Plugins',
    path: '/plugins',
    component: () => import('../views/plugins.vue'),
    meta: { title: '插件市场', group: '生态扩展' },
  },
  {
    name: 'Mcp',
    path: '/mcp',
    component: () => import('../views/mcp.vue'),
    meta: { title: 'MCP 服务', group: '生态扩展' },
  },
  {
    name: 'Skills',
    path: '/skills',
    component: () => import('../views/skills.vue'),
    meta: { title: '技能中心', group: '生态扩展' },
  },
  {
    name: 'Tools',
    path: '/tools',
    component: () => import('../views/tools.vue'),
    meta: { title: '工具列表', group: '生态扩展' },
  },
  {
    name: 'Triggers',
    path: '/triggers',
    component: () => import('../views/triggers.vue'),
    meta: { title: '触发器', group: '生态扩展' },
  },
  { path: '/:pathMatch(.*)*', redirect: '/overview' },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})
