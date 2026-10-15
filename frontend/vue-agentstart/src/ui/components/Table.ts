import { defineComponent, h, type PropType, type VNodeChild } from 'vue';
import { Table as UiTable, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../shadcn/table';

export interface TableColumn<T = Record<string, unknown>> {
  align?: 'center' | 'left' | 'right';
  customRender?: (options: { index: number; record: T; text: unknown }) => VNodeChild;
  dataIndex?: keyof T | string;
  ellipsis?: boolean;
  key?: string;
  title: string;
  width?: number | string;
}
export const TableColumn = defineComponent({ name: 'AsTableColumn', setup: () => () => null });
export const Table = defineComponent({
  name: 'AsTable',
  props: {
    columns: { type: Array as PropType<TableColumn[]>, default: () => [] },
    dataSource: { type: Array as PropType<object[]>, default: () => [] }, loading: Boolean,
    rowKey: { type: [String, Function] as PropType<string | ((row: object) => string | number)>, default: 'key' },
    scroll: Object as PropType<{ x?: number | string; y?: number | string }>,
  },
  setup: (props, { slots }) => () => h(UiTable, { class: 'as-data-table', style: { minWidth: typeof props.scroll?.x === 'number' ? `${props.scroll.x}px` : props.scroll?.x } }, () => [
    h('colgroup', props.columns.map(column => h('col', { style: { width: typeof column.width === 'number' ? `${column.width}px` : column.width } }))),
    h(TableHeader, {}, () => h(TableRow, {}, () => props.columns.map(column => h(TableHead, { style: { textAlign: column.align } }, () => column.title)))),
    h(TableBody, {}, () => props.dataSource.length ? props.dataSource.map((source, index) => {
      const record = source as Record<string, unknown>;
      return h(TableRow, { key: String(typeof props.rowKey === 'function' ? props.rowKey(source) : record[props.rowKey] ?? index) }, () => props.columns.map(column => {
        const text = column.dataIndex ? record[String(column.dataIndex)] : undefined;
        return h(TableCell, { class: { 'as-data-table__ellipsis': column.ellipsis }, style: { textAlign: column.align } }, () =>
          slots.bodyCell?.({ column, index, record, text }) ?? column.customRender?.({ index, record, text }) ?? String(text ?? ''));
      }));
    }) : h(TableRow, { class: 'as-data-table__empty' }, () => h(TableCell, { colspan: props.columns.length || 1 }, () => props.loading ? '加载中…' : slots.emptyText?.() ?? '暂无数据'))),
  ]),
});
