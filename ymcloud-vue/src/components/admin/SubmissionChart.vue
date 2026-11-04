<template>
  <div>
    <div id="chart" style="width: 100%; height: 300px"></div>
  </div>
</template>

<script lang="ts">
import { defineComponent, onMounted, onBeforeUnmount, watch, type PropType } from "vue";
import { useThemeStore } from "@/stores/theme";
import * as echarts from "echarts/core";
import {
  TitleComponent,
  type TitleComponentOption,
  ToolboxComponent,
  type ToolboxComponentOption,
  TooltipComponent,
  type TooltipComponentOption,
  GridComponent,
  type GridComponentOption,
  LegendComponent,
  type LegendComponentOption,
} from "echarts/components";
import { LineChart, type LineSeriesOption } from "echarts/charts";
import { UniversalTransition } from "echarts/features";
import { CanvasRenderer } from "echarts/renderers";

echarts.use([
  TitleComponent,
  ToolboxComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  LineChart,
  CanvasRenderer,
  UniversalTransition,
]);

type EChartsOption = echarts.ComposeOption<
  | TitleComponentOption
  | ToolboxComponentOption
  | TooltipComponentOption
  | GridComponentOption
  | LegendComponentOption
  | LineSeriesOption
>;

export default defineComponent({
  name: "SubmissionChart",
  props: {
    dates: {
      type: Array as PropType<string[]>,
      required: true,
      default: () => [],
    },
    totalSubmissions: {
      type: Array as PropType<number[]>,
      required: true,
      default: () => [],
    },
    acceptedSubmissions: {
      type: Array as PropType<number[]>,
      required: true,
      default: () => [],
    },
  },
  setup(props) {
    const theme = useThemeStore();
    var myChart: echarts.ECharts | null = null;

    const initChart = () => {
      var chartDom = document.getElementById('chart')!;
      if (myChart) {
        myChart.dispose();
        myChart = null;
      }
      myChart = echarts.init(chartDom, theme.isDark ? 'dark' : undefined);
      const option: EChartsOption = {
        tooltip: {
          trigger: 'axis'
        },
        legend: {
          data: ['总数', '通过']
        },
        toolbox: {
          feature: {
            saveAsImage: {}
          }
        },
        xAxis: [
          {
            type: 'category',
            boundaryGap: false,
            data: props.dates
          }
        ],
        yAxis: [
          {
            type: 'value'
          }
        ],
        series: [
          {
            name: '通过',
            type: 'line',
            stack: 'Total',
            areaStyle: {},
            data: props.acceptedSubmissions
          },
          {
            name: '总数',
            type: 'line',
            stack: 'Total',
            areaStyle: {},
            label: {
              show: true,
              position: 'top',
              formatter: function (params: any) {
                return params.value > 0 ? params.value : '';
              }
            },
            data: props.totalSubmissions
          },
        ]
      };
      option && myChart.setOption(option);
    };

    watch(()=>[props.dates, props.totalSubmissions, props.acceptedSubmissions, theme.resolvedTheme], () => {
      initChart();
    }, { flush: "post" });

    onMounted(() => {
      initChart();
    });

    //组件卸载时销毁图表实例
    onBeforeUnmount(() => {
      myChart?.dispose();
      myChart = null;
    });

    return {};
  },
});
</script>
