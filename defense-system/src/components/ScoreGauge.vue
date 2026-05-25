<template>
  <div style="width: 100%; height: 300px" ref="chartRef"></div>
</template>

<script setup>
import * as echarts from "echarts";
import { ref, onMounted, watch } from "vue";

const props = defineProps({
  score: {
    type: Number,
    default: 0,
  },
});

const chartRef = ref(null);
let chart;

const renderChart = (value) => {
  if (!chart) chart = echarts.init(chartRef.value);

  chart.setOption({
    series: [
      {
        type: "gauge",
        startAngle: 225,
        endAngle: -45,
        center: ["50%", "65%"],
        radius: "100%",
        min: 0,
        max: 100,
        splitNumber: 0,
        axisLine: {
          lineStyle: {
            width: 10,
            roundCap: true,
            color: [
              [value / 100, "#409EFF"], // 当前得分部分
              [1, "#E0E0E0"], // 剩余灰色背景
            ],
          },
        },
        pointer: { show: false },
        axisTick: { show: false },
        splitLine: { show: false },
        axisLabel: { show: false },
        detail: {
          show: true,
          offsetCenter: ["0", "5%"],
          fontSize: 30,
          fontWeight: "bold",
          color: "#409EFF",
          formatter: `${value}`,
        },
        data: [{ value }],
      },
    ],
    graphic: [
      {
        type: "text",
        left: "43%",
        top: "40%",
        style: {
          text: "当前得分",
          fill: "#409EFF",
          fontSize: 14,
          fontWeight: "normal",
          textAlign: "center",
        },
      },
      {
        type: "text",
        left: "43%",
        top: "80%",
        style: {
          text: "满分 100分",
          fill: "#666",
          fontSize: 10,
          fontWeight: "normal",
          textAlign: "center",
        },
      },
    ],
  });
};

onMounted(() => {
  renderChart(props.score);
});

watch(
  () => props.score,
  (val) => {
    renderChart(val);
  }
);
</script>
