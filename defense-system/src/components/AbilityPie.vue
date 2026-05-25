<template>
  <div ref="chartRef" style="width: 100%; height: 320px"></div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import * as echarts from "echarts";

const props = defineProps<{
  abilityData: { name: string; value: number }[];
}>();

const chartRef = ref<HTMLDivElement | null>(null);
let chart: echarts.ECharts | null = null;

const renderChart = () => {
  if (!chartRef.value) return;
  chart = echarts.init(chartRef.value);

  const option = {
    title: {
      text: "",
      left: "center",
      top: 10,
      textStyle: {
        fontSize: 18,
        color: "#333",
      },
    },
    tooltip: {
      trigger: "item",
      formatter: "{b}: {c}分",
    },
    legend: {
      orient: "horizontal",
      bottom: 0,
      left: "center",
      textStyle: {
        fontSize: 12,
        color: "#333",
      },
    },
    series: [
      {
        name: "能力构成",
        type: "pie",
        radius: ["20%", "60%"], 
        center: ["50%", "40%"], // 稍微调整中心点保证位置美观
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 8,
          borderColor: "transparent",
          borderWidth: 0,
          shadowBlur: 10,
          shadowOffsetX: 2,
          shadowColor: "rgba(0, 0, 0, 0.15)",
        },
        label: {
          show: true,
          formatter: "{c}分",
          position: "outside",
          fontSize: 12,
        },
        labelLine: {
          show: true,
          length: 20,
          length2: 10,
          smooth: true,
          lineStyle: {
            color: "#aaa",
          },
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 14,
            fontWeight: "bold",
            formatter: "{c}分",
          },
        },
        data: props.abilityData,
      },
    ],
  };

  chart.setOption(option);
};

onMounted(() => {
  renderChart();
});

watch(
  () => props.abilityData,
  () => {
    if (chart) {
      renderChart();
    }
  },
  { deep: true }
);
</script>
