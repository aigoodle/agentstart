/**
 * AI 业务快照读取。宿主 ai-assistant.ts 使用 setData(name, data, desc)
 * 写入 storage，并通过 host:context / host:bus_data 同步到 iframe。
 * 每个槽为 {name, data, desc, updatedAt}；独立打开时才读取本地 bus_data。
 */

import { getHostContext, isHostManaged } from './host-bridge';

export const BUS_DATA_KEY = 'bus_data';

/** 单个数据源产出的槽 (与宿主 BusDataSlot 类型对齐) */
export interface BusDataSlot<T = unknown> {
  /** 稳定英文标识, 如 scheduleRule / smartSchedule / scheduleGrid */
  name: string;
  /** 具体业务数据 */
  data: T;
  /** 字段说明字典 (供 AI 理解 data) */
  desc: string;
  /** ISO 时间戳, 每次写入时刷新 */
  updatedAt?: string;
}

/** composite 结构: 按 name 索引 */
export type BusDataComposite = Record<string, BusDataSlot>;

/** 从 localStorage 读整个 AI 上下文 composite; 无 / 解析失败返回 null */
export function readBusData(): BusDataComposite | null {
  if (typeof window === 'undefined') return null;
  if (isHostManaged) return (getHostContext()?.busData as BusDataComposite | null) ?? null;
  try {
    const raw = window.localStorage.getItem(BUS_DATA_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as BusDataComposite | null;
    if (!parsed || typeof parsed !== 'object') return null;
    return parsed;
  } catch {
    return null;
  }
}

/**
 * 组装 bus_data 相关的 Chat inputs 字段.
 * 返回一个可以直接 `...spread` 到 inputs 里的对象.
 *
 * 约定:
 *   - `bus_data`             整个 composite 的 JSON 字符串; Chat 工作流用 Code 节点
 *                            JSON.parse 后, `composite.scheduleRule.data` 等即可访问
 *   - `bus_data_updated_at`  所有槽里最新的 updatedAt (ISO), 便于判断上下文新旧
 *
 * 无 composite 时返回 {}, 保证调用方 `spread` 语义安全.
 */
export function buildBusDataInputs(): Record<string, string> {
  const comp = readBusData();
  if (!comp) return {};
  const updatedAt =
    Object.values(comp)
      .map((slot) => slot?.updatedAt ?? '')
      .filter(Boolean)
      .sort()
      .pop() ?? '';
  return {
    bus_data: JSON.stringify(comp),
    bus_data_updated_at: updatedAt,
  };
}
