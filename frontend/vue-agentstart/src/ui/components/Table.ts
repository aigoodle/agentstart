import {
  defineComponent,
  h,
  type PropType,
  type VNodeChild,
} from 'vue';

export interface TableColumn<T = Record<string, unknown>> {
  align?: 'center' | 'left' | 'right';
  customRender?: (options: { index: number; record: T; text: unknown }) => VNodeChild;
  dataIndex?: keyof T | string;
  ellipsis?: boolean;
  key?: string;
  title: string;
  width?: number | string;
}

export const TableColumn = defineComponent({
  name: 'AsTableColumn',
  setup: () => () => null,
});

export const Table = defineComponent({
  name: 'AsTable',
  props: {
    columns: { type: Array as PropType<TableColumn[]>, default: () => [] },
    dataSource: { type: Array as PropType<object[]>, default: () => [] },
    loading: Boolean,
    rowKey: { type: [String, Function] as PropType<string | ((row: object) => string | number)>, default: 'key' },
    scroll: Object as PropType<{ x?: number | string; y?: number | string }>,
  },
  setup: (props, { slots }) => () => h('div', { class: 'as-table-wrap' }, [
    h('table', { class: 'as-table', style: { minWidth: typeof props.scroll?.x === 'number' ? `${props.scroll.x}px` : props.scroll?.x } }, [
      h('colgroup', props.columns.map((column) => h('col', { style: { width: typeof column.width === 'number' ? `${column.width}px` : column.width } }))),
      h('thead', [h('tr', props.columns.map((column) => h('th', { style: { textAlign: column.align } }, column.title)))]),
      h('tbody', props.dataSource.length
        ? props.dataSource.map((source, index) => {
          const record = source as Record<string, unknown>;
          return h('tr', {
            key: String(typeof props.rowKey === 'function' ? props.rowKey(source) : (record[props.rowKey] ?? index)),
          }, props.columns.map((column) => {
            const text = column.dataIndex ? record[String(column.dataIndex)] : undefined;
            return h('td', { class: { 'as-table__ellipsis': column.ellipsis }, style: { textAlign: column.align } },
              slots.bodyCell?.({ column, index, record, text }) ?? column.customRender?.({ index, record, text }) ?? String(text ?? ''));
          }));
        })
        : [h('tr', { class: 'as-table__empty' }, [h('td', { colspan: props.columns.length }, props.loading ? '加载中…' : (slots.emptyText?.() ?? '暂无数据'))])]),
    ]),
  ]),
});
