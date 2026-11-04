<template>
  <div style="width: 100%; height: 100%;">
    <calendar-heatmap
        :values="processedValues"
        :dark-mode="theme.isDark"
        :end-date="new Date(endDate)"
        tooltip-unit="提交"
        :locale="locale"
        no-data-text="暂无提交"
        :tooltip-formatter="tooltipFormatter"
        :round="0"
    />
  </div>
</template>

<script lang="ts">
import { useThemeStore } from '@/stores/theme';
import { defineComponent, type PropType, computed } from 'vue';
import 'vue3-calendar-heatmap/dist/style.css';
import { CalendarHeatmap, type CalendarItem } from 'vue3-calendar-heatmap';

interface HeatmapValue {
  date: Date | string;
  count: number;
}

export default defineComponent({
  name: 'CalendarHeatMap',
  components: {
    CalendarHeatmap
  },
  props: {
    values: {
      type: Array as PropType<HeatmapValue[]>,
      required: true,
    },
    endDate: {
      type: String,
      required: true,
    },
  },
  setup(props) {
    const theme = useThemeStore();
    const processedValues = computed(() => {
        return props.values.map(v => ({...v, count: v.count || 0}))
    })

    const locale = {
      months: ['一月', '二月', '三月', '四月', '五月', '六月', '七月', '八月', '九月', '十月', '十一月', '十二月'],
      days: ['周日', '周一', '周二', '周三', '周四', '周五', '周六'],
      on: '在',
      less: '少',
      more: '多'
    };

    const tooltipFormatter = (data: CalendarItem): string => {
      const d = new Date(data.date);
      const y = d.getFullYear();
      const m = String(d.getMonth() + 1).padStart(2, '0');
      const day = String(d.getDate()).padStart(2, '0');
      return `${data.count}次提交：${y}-${m}-${day}`;
    };


    return {
      theme,
      locale,
      tooltipFormatter,
      processedValues,
    };
  }
});
</script>
