<template>
  <div class="score-progress-wrapper">
    <!-- 顶部阶段标签隐藏 -->
    <div class="progress-labels" style="display:none;">
      <div class="label-item label-start">
        <span class="label-text">不合格</span>
      </div>
      <div class="label-item" :style="{ left: '33%' }">
        <span class="label-text">中等</span>
      </div>
      <div class="label-item" :style="{ left: '66%' }">
        <span class="label-text">良好</span>
      </div>
      <div class="label-item label-end" :style="{ left: '100%' }">
        <span class="label-text">优秀</span>
      </div>
    </div>

    <div class="progress-bar-container">
      <div class="progress-bar-background">
        <div
          class="progress-bar-fill"
          :style="{ width: getFillWidth(score), backgroundColor: getFillColor(score) }"
        ></div>
      </div>
    </div>

    <div class="progress-dots-overlay">
      <div class="dot-item dot-start" :class="{ 'dot-active': score >= 0 }" :style="{ left: '0%' }">
        <div class="dot"></div>
      </div>
      <div
        class="dot-item"
        :class="{ 'dot-active': score >= 50 }"
        :style="{ left: '33%' }"
      >
        <div class="dot"></div>
      </div>
      <div
        class="dot-item"
        :class="{ 'dot-active': score >= 70 }"
        :style="{ left: '66%' }"
      >
        <div class="dot"></div>
      </div>
      <div
        class="dot-item dot-end"
        :class="{ 'dot-active': score >= 80 }"
        :style="{ left: '100%' }"
      >
        <div class="dot"></div>
      </div>
    </div>

    <!-- 进度条下方的新标签 -->
    <div class="progress-labels-bottom">
      <div class="label-item label-start">不合格</div>
      <div class="label-item" style="left: 33%;">中等</div>
      <div class="label-item" style="left: 66%;">良好</div>
      <div class="label-item label-end" style="left: 100%;">优秀</div>
    </div>

    <div class="score-ranges">
      <div class="score-range-item">
        <span class="range-label">不合格:</span>
        <span class="range-value">0-50分</span>
      </div>
      <div class="score-range-item">
        <span class="range-label">中等:</span>
        <span class="range-value">50-70分</span>
      </div>
      <div class="score-range-item">
        <span class="range-label">良好:</span>
        <span class="range-value">70-80分</span>
      </div>
      <div class="score-range-item">
        <span class="range-label">优秀:</span>
        <span class="range-value">80分以上</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { defineProps } from 'vue';

const props = defineProps({
  score: {
    type: Number,
    required: true,
    default: 0,
    validator: value => value >= 0 // 分数可以超过100
  },
});

// 根据分数获取填充颜色
const getFillColor = (score) => {
  if (score < 50) {
    return '#f56c6c'; // 不合格 (红色)
  } else if (score < 70) {
    return '#e6a23c'; // 中等 (橙色)
  } else if (score < 80) { // 良好 (90-100)
    return '#67c23a'; // 绿色
  } else { // 优秀 (100+)
    return '#67c23a'; // 绿色 (与良好相同颜色)
  }
};

// 根据分数获取进度条填充宽度
const getFillWidth = (score) => {
  if (score < 50) {
    return '0%'; // 或者你可以设置为一个很小的值，比如 '5%'，表示刚开始
  } else if (score < 70) {
    return '33%'; // 中等区间，进度条填充到33%
  } else if (score < 80) {
    return '66%'; // 良好区间，进度条填充到66%
  } else {
    return '100%'; // 优秀区间，进度条填充到100%
  }
};
</script>

<style scoped>
.score-progress-wrapper {
  width: 100%;
  position: relative;
  padding-top: 30px; /* 为顶部标签留空间 */
  padding-bottom: 50px; /* 为底部范围留空间，留出足够给下方标签 */
  box-sizing: border-box;
}

/* 进度条背景和填充 */
.progress-bar-container {
  height: 4px; /* 进度条的粗细 */
  border-radius: 2px;
  position: relative;
  overflow: hidden;
  margin: 10px 0; /* 调整与标签和圆点之间的垂直间距 */
  z-index: 1; /* 确保进度条在点和线下面 */
}

.progress-bar-background {
  width: 100%;
  height: 100%;
  background-color: #ebeef5;
  border-radius: 2px;
}

.progress-bar-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.6s ease-in-out, background-color 0.6s ease-in-out;
}

/* 圆点指示器和连接线的新容器 */
.progress-dots-overlay {
  position: absolute;
  top: calc(30px + 10px);
  left: 0;
  width: 100%;
  height: 4px; /* 与进度条高度相同 */
  pointer-events: none;
  z-index: 2; /* 确保圆点在进度条之上 */
}

.dot-item {
  position: absolute;
  top: 50%; /* 垂直居中 */
  transform: translate(-50%, -50%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.dot-item.dot-start {
  transform: translateY(-50%);
}

.dot-item.dot-end {
  transform: translate(-100%, -50%);
}

.dot-item .dot {
  width: 14px;
  height: 14px;
  background-color: #c0c4cc;
  border-radius: 50%;
  border: 4px solid #ffffff;
  box-sizing: border-box;
  transition: background-color 0.3s ease-in-out;
}

.dot-item.dot-active .dot {
  background-color: #67c23a;
}

/* 顶部的阶段标签（隐藏） */
.progress-labels {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 20px;
  pointer-events: none;
  z-index: 2;
}

/* 进度条下方的阶段标签 */
.progress-labels-bottom {
  position: relative;
  width: 100%;
  margin-top: 8px; /* 进度条和标签之间间距 */
  height: 20px;
  user-select: none;
  pointer-events: none;
}

.progress-labels-bottom .label-item {
  position: absolute;
  transform: translateX(-50%);
  font-size: 12px;
  color: #606266;
  white-space: nowrap;
  top: 0;
}

.progress-labels-bottom .label-start {
  transform: translateX(0);
  left: 0;
}

.progress-labels-bottom .label-end {
  transform: translateX(-100%);
  left: 100%;
}

/* 底部的分数范围提示 */
.score-ranges {
  position: absolute;
  bottom: 0;
  left: 50%;           /* 先把左边界移到父元素宽度的 50% */
  width: 80%;          /* 设置宽度为 80% */
  transform: translateX(-50%); /* 再向左移动自身宽度的 50% 实现居中 */
  display: flex;
  justify-content: space-between;
  padding-top: 20px;
  font-size: 12px;
  color: #909399;
  box-sizing: border-box;
}


.score-range-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
  padding: 0 5px;
  text-align: center;
}

.range-label {
  font-weight: bold;
  color: #606266;
}

.range-value {
  margin-top: 2px;
  font-size: 11px;
}
</style>
