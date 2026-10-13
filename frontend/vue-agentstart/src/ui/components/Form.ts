import { defineComponent, h } from 'vue';

export const Form = defineComponent({
  name: 'AsForm',
  inheritAttrs: false,
  props: { model: Object, layout: { type: String, default: 'vertical' } },
  emits: ['finish'],
  setup: (props, { attrs, slots, emit }) => () => h('form', {
    ...attrs,
    class: ['as-form', props.layout && `as-form--${props.layout}`, attrs.class],
    onSubmit: (event: Event) => { event.preventDefault(); emit('finish', props.model); },
  }, slots.default?.()),
});

export const FormItem = defineComponent({
  name: 'AsFormItem',
  props: {
    label: String,
    required: Boolean,
    help: String,
    extra: String,
    validateStatus: String,
  },
  setup: (props, { slots }) => () => h('label', {
    class: ['as-form-item', props.validateStatus && `as-form-item--${props.validateStatus}`],
  }, [
    props.label ? h('span', { class: 'as-form-item__label' }, [props.required ? h('i', '*') : null, props.label]) : null,
    h('span', { class: 'as-form-item__control' }, [
      slots.default?.(),
      props.help ? h('small', { class: 'as-form-item__help' }, props.help) : null,
      props.extra ? h('small', { class: 'as-form-item__extra' }, props.extra) : null,
    ]),
  ]),
});
