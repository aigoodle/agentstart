<script setup lang="ts">
import type {
  ConnectorAction,
  ConnectorConnection,
  ConnectorDefinition,
  ConnectorInstallation,
} from '../types';

defineProps<{
  open: boolean;
  connector?: ConnectorDefinition;
  installation?: ConnectorInstallation;
  connections: ConnectorConnection[];
}>();

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void;
  (e: 'test', a: ConnectorAction): void;
  (e: 'connection', c?: ConnectorConnection): void;
  (e: 'toggle'): void;
}>();

function riskClass(level?: string) {
  switch ((level ?? '').toUpperCase()) {
    case 'READ':
      return 'is-read';
    case 'DESTRUCTIVE':
      return 'is-destructive';
    default:
      return 'is-write';
  }
}
</script>

<template>
  <Transition name="cdd">
    <div v-if="open" class="cdd-mask" @click.self="emit('update:open', false)">
      <aside class="cdd-drawer">
        <header class="cdd-header">
          <div class="cdd-header-info">
            <span class="cdd-icon">{{ connector?.icon || '🔌' }}</span>
            <div class="cdd-header-text">
              <b>{{ connector?.name }}</b>
              <small>
                {{ connector?.key.provider }} /
                {{ connector?.key.connectorId }}
              </small>
            </div>
          </div>
          <button class="cdd-close" @click="emit('update:open', false)">
            ✕
          </button>
        </header>

        <main class="cdd-main">
          <p class="cdd-desc">{{ connector?.description }}</p>

          <div class="cdd-meta">
            <span>版本 {{ connector?.version }}</span>
            <span>{{ connector?.trustLevel }}</span>
            <span>{{ connector?.license || 'License 未声明' }}</span>
          </div>

          <section class="cdd-section">
            <div class="cdd-section-head">
              <h3>连接配置</h3>
              <button
                class="cdd-btn cdd-btn-primary"
                @click="emit('connection')"
              >
                + 新建连接
              </button>
            </div>
            <div v-if="connections.length === 0" class="cdd-empty">
              尚未配置连接
            </div>
            <button
              v-for="c in connections"
              :key="c.id"
              class="cdd-row"
              @click="emit('connection', c)"
            >
              <span class="cdd-row-main">
                <b>{{ c.name }}</b>
                <small>{{ c.status }}</small>
              </span>
              <span class="cdd-row-side">
                {{ c.credentialsConfigured ? '凭证已配置' : '未配置凭证' }} ›
              </span>
            </button>
          </section>

          <section class="cdd-section">
            <h3 class="cdd-section-title">Actions</h3>
            <button
              v-for="a in connector?.actions || []"
              :key="a.id"
              class="cdd-row"
              @click="emit('test', a)"
            >
              <span class="cdd-row-main">
                <b>{{ a.name }}</b>
                <small>{{ a.description }}</small>
              </span>
              <span :class="['cdd-risk', riskClass(a.riskLevel)]">
                {{ a.riskLevel || 'WRITE' }} · 测试 ›
              </span>
            </button>
          </section>
        </main>

        <footer class="cdd-footer">
          <button class="cdd-btn" @click="emit('toggle')">
            {{ installation?.enabled ? '禁用 Connector' : '启用 Connector' }}
          </button>
        </footer>
      </aside>
    </div>
  </Transition>
</template>

<style scoped>
/* -------- 遮罩与抽屉容器 -------- */
.cdd-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.35);
  z-index: 1100;
}
.cdd-drawer {
  position: absolute;
  right: 0;
  top: 0;
  width: min(600px, 94vw);
  height: 100%;
  display: grid;
  grid-template-rows: auto 1fr auto;
  overflow: auto;
  background: #fff;
  box-shadow: -6px 0 22px rgba(15, 23, 42, 0.14);
}
:global(.dark) .cdd-drawer {
  background: #1f1f1f;
}

/* 进出场动画 */
.cdd-enter-active,
.cdd-leave-active {
  transition: opacity 0.18s ease;
}
.cdd-enter-active .cdd-drawer,
.cdd-leave-active .cdd-drawer {
  transition: transform 0.18s ease;
}
.cdd-enter-from,
.cdd-leave-to {
  opacity: 0;
}
.cdd-enter-from .cdd-drawer,
.cdd-leave-to .cdd-drawer {
  transform: translateX(48px);
}

/* -------- 头部 -------- */
.cdd-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid #e5e7eb;
}
.cdd-header-info {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.cdd-icon {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  background: #eef2ff;
  border-radius: 10px;
}
.cdd-header-text {
  display: grid;
  gap: 2px;
  min-width: 0;
}
.cdd-header-text b {
  font-size: 15px;
  color: #111827;
}
.cdd-header-text small {
  font-size: 12px;
  color: #9ca3af;
}
.cdd-close {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 0;
  background: none;
  font-size: 14px;
  color: #9ca3af;
  border-radius: 6px;
  cursor: pointer;
  transition:
    background 0.15s ease,
    color 0.15s ease;
}
.cdd-close:hover {
  background: #f3f4f6;
  color: #111827;
}
:global(.dark) .cdd-header {
  border-bottom-color: #2d2d2d;
}
:global(.dark) .cdd-icon {
  background: rgba(99, 102, 241, 0.15);
}
:global(.dark) .cdd-header-text b {
  color: #f3f4f6;
}
:global(.dark) .cdd-close:hover {
  background: #2d2d2d;
  color: #f3f4f6;
}

/* -------- 主体 -------- */
.cdd-main {
  padding: 20px;
  display: grid;
  align-content: start;
  gap: 20px;
}
.cdd-desc {
  margin: 0;
  font-size: 13px;
  color: #6b7280;
  line-height: 1.6;
}
:global(.dark) .cdd-desc {
  color: #9ca3af;
}

.cdd-meta {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.cdd-meta span {
  padding: 3px 10px;
  font-size: 12px;
  color: #374151;
  background: #f3f4f6;
  border-radius: 10px;
}
:global(.dark) .cdd-meta span {
  background: #2d2d2d;
  color: #d1d5db;
}

.cdd-section {
  display: grid;
  gap: 8px;
}
.cdd-section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.cdd-section h3,
.cdd-section-title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: #374151;
}
:global(.dark) .cdd-section h3,
:global(.dark) .cdd-section-title {
  color: #d1d5db;
}

/* -------- 行式条目（连接 / Action） -------- */
.cdd-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  text-align: left;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    background 0.15s ease;
}
.cdd-row:hover {
  border-color: #a5b4fc;
  background: #fafafa;
}
.cdd-row-main {
  display: grid;
  gap: 2px;
  min-width: 0;
}
.cdd-row-main b {
  font-size: 13px;
  font-weight: 600;
  color: #111827;
}
.cdd-row-main small {
  font-size: 12px;
  color: #9ca3af;
}
.cdd-row-side {
  flex-shrink: 0;
  font-size: 12px;
  color: #6b7280;
}
:global(.dark) .cdd-row {
  background: #2d2d2d;
  border-color: #3d3d3d;
}
:global(.dark) .cdd-row:hover {
  border-color: #6366f1;
  background: #333;
}
:global(.dark) .cdd-row-main b {
  color: #f3f4f6;
}
:global(.dark) .cdd-row-side {
  color: #9ca3af;
}

/* -------- 风险等级 -------- */
.cdd-risk {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
}
.cdd-risk.is-read {
  color: #059669;
}
.cdd-risk.is-write {
  color: #b45309;
}
.cdd-risk.is-destructive {
  color: #dc2626;
}
:global(.dark) .cdd-risk.is-read {
  color: #34d399;
}
:global(.dark) .cdd-risk.is-write {
  color: #fbbf24;
}
:global(.dark) .cdd-risk.is-destructive {
  color: #f87171;
}

/* -------- 空状态 -------- */
.cdd-empty {
  padding: 20px;
  text-align: center;
  font-size: 13px;
  color: #9ca3af;
  border: 1px dashed #e5e7eb;
  border-radius: 8px;
}
:global(.dark) .cdd-empty {
  border-color: #3d3d3d;
}

/* -------- 按钮 -------- */
.cdd-btn {
  padding: 7px 14px;
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  background: #fff;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  cursor: pointer;
  transition:
    background 0.15s ease,
    border-color 0.15s ease;
}
.cdd-btn:hover {
  background: #f3f4f6;
}
.cdd-btn-primary {
  color: #fff;
  background: #6366f1;
  border-color: #6366f1;
}
.cdd-btn-primary:hover {
  background: #4f46e5;
}
:global(.dark) .cdd-btn {
  background: #2d2d2d;
  border-color: #3d3d3d;
  color: #f3f4f6;
}
:global(.dark) .cdd-btn:hover {
  background: #3d3d3d;
}
:global(.dark) .cdd-btn-primary {
  background: #6366f1;
  border-color: #6366f1;
}
:global(.dark) .cdd-btn-primary:hover {
  background: #818cf8;
}

/* -------- 底部 -------- */
.cdd-footer {
  display: flex;
  justify-content: flex-end;
  padding: 14px 20px;
  border-top: 1px solid #e5e7eb;
}
:global(.dark) .cdd-footer {
  border-top-color: #2d2d2d;
}
</style>
