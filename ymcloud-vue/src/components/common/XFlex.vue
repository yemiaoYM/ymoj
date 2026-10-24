<script lang="ts">
import { computed, defineComponent, Fragment, h, isVNode, type CSSProperties, type PropType, type VNodeChild } from 'vue';

type FlexSize = 'small' | 'medium' | 'large' | number | readonly [number, number];

// 数字间距只接受有限非负值，避免 NaN、Infinity 或非法 CSS 进入布局。
function toGap(value: unknown): number {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0 ? value : 0;
}

// 空插槽不生成容器；不展开或复制 VNode，交给 Vue 保持 Fragment 的 key 和状态。
function hasContent(children: VNodeChild[]): boolean {
  return children.some((child) => {
    if (Array.isArray(child)) {
      return hasContent(child);
    }
    if (isVNode(child) && child.type === Fragment) {
      return Array.isArray(child.children) && hasContent(child.children);
    }
    return child != null && typeof child !== 'boolean';
  });
}

/**
 * XFlex：用于工具栏、表单操作区、卡片内容等横向或纵向排列。
 * 只生成一个 div，不包装子项；通过原生 Flex 和 gap 完成布局。
 *
 * 用法：
 * <x-flex align="center" justify="space-between" :size="16">...</x-flex>
 * <x-flex vertical :size="8">...</x-flex>
 * <x-flex :wrap="false" :size="[16, 8]">...</x-flex>
 *
 * align/justify 对应 CSS 对齐属性，justify 默认为 start。
 * inline 使用 inline-flex；vertical 使用纵向排列；reverse 反转排列方向。
 * wrap 默认为 true，但纵向始终不换行。
 * size 默认 medium：水平 12px、垂直 8px；small 为 8/4px，large 为 16/12px。
 * 数字 size 同时设置两个方向，数组顺序为 [水平, 垂直]；非法数字间距按 0 处理。
 * class/style、事件、ARIA 按 Vue 规则透传，外部 style 可覆盖默认样式。
 * 没有内容时不占位；插槽中的条件注释仍保留容器。使用 render 让插槽每次只求值一次。
 */
export default defineComponent({
  name: 'XFlex',
  props: {
    align: String as PropType<CSSProperties['alignItems']>,
    justify: { type: String as PropType<CSSProperties['justifyContent']>, default: 'start' },
    inline: Boolean,
    vertical: Boolean,
    reverse: Boolean,
    size: { type: [String, Number, Array] as PropType<FlexSize>, default: 'medium' },
    wrap: { type: Boolean, default: true },
  },
  setup(props, { slots }) {
    const gap = computed(() => {
      const size = props.size;
      if (typeof size === 'number') {
        return `${toGap(size)}px`;
      }
      if (Array.isArray(size)) {
        return `${toGap(size[1])}px ${toGap(size[0])}px`;
      }
      if (size === 'small') {
        return '4px 8px';
      }
      return size === 'large' ? '12px 16px' : '8px 12px';
    });
    const style = computed<CSSProperties>(() => ({
      display: props.inline ? 'inline-flex' : 'flex',
      flexDirection: props.vertical
        ? (props.reverse ? 'column-reverse' : 'column')
        : (props.reverse ? 'row-reverse' : 'row'),
      justifyContent: props.justify,
      flexWrap: props.wrap && !props.vertical ? 'wrap' : 'nowrap',
      alignItems: props.align,
      gap: gap.value,
    }));

    return () => {
      const children = slots.default?.() ?? [];
      return hasContent(children) ? h('div', { role: 'none', class: 'x-flex', style: style.value }, children) : null;
    };
  },
});
</script>
