<template>
  <div ref="chartRef" style="width: 400px; height: 400px;"></div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue';
import * as echarts from 'echarts';

const chartRef = ref(null);
let chartInstance = null;

const props = defineProps({
  scores: {
    type: Object,
    required: true,
    default: () => ({})
  }
});

const initChart = () => {
  if (!chartRef.value) return;
  if (!props.scores || Object.keys(props.scores).length === 0) {
    if (chartInstance) {
      chartInstance.clear();
    }
    return;
  }

  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value);
  }

  const indicators = Object.keys(props.scores).map(key => ({
    name: key,
    max: 100
  }));

  const values = Object.values(props.scores);

  const option = {
    tooltip: {},
    radar: {
      indicator: indicators,
      shape: 'circle',
      splitNumber: 5,
      axisName: {
        fontSize: 14
      }
    },
    series: [{
      name: '综合素质评分',
      type: 'radar',
      data: [
        {
          value: values,
          name: '评分',
          areaStyle: {
            color: 'rgba(0, 123, 255, 0.4)'
          },
          lineStyle: {
            color: '#007bff'
          },
          itemStyle: {
            color: '#007bff'
          }
        }
      ]
    }]
  };

  chartInstance.setOption(option);
};

onMounted(() => {
  initChart();
});

watch(() => props.scores, () => {
  initChart();
}, { deep: true });
</script>
