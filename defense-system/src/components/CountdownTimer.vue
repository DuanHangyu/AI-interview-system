<template>
  <div class="container">
    <div
      class="progress-bar"
      role="progressbar"
      :aria-valuenow="Math.floor(progress)"
      aria-valuemin="0"
      aria-valuemax="100"
    >
      <div class="progress-fill" :style="{ width: progress + '%' }"></div>
      <div class="slider-ball" :style="{ left: sliderLeftPosition }"></div>
    </div>
    <div class="time-text">{{ Math.floor(remainingTime) }} 秒</div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, computed } from 'vue';

const props = defineProps({
  totalTime: {
    type: Number,
    default: 300, // 总时间（秒）
  },
});

const progress = ref(0); // 百分比进度（0~100）
const remainingTime = ref(props.totalTime);
let timer = null;

// 定义滑块的宽度，用于计算精确位置。这里与CSS中的width同步
const SLIDER_BALL_WIDTH = 20; // px
const PROGRESS_BAR_HEIGHT = 12; // px (用于垂直居中，虽然已经有了 transform)

// 计算滑块的精确 left 位置
const sliderLeftPosition = computed(() => {
  // progress 是 0-100
  // 当 progress 为 0 时，我们希望球的左边缘在 0px
  // 当 progress 为 100 时，我们希望球的右边缘在 100% 宽度处
  // 所以，实际的 left 应该是 (progress / 100) * (进度条宽度)
  // 然后再减去一半的球宽度，以便 transform: translateX(-50%) 能够将其中心对齐
  // 但是，如果希望左右边缘对齐，我们需要更直接的计算
  
  // 方案一：让球的中心对齐进度百分比 (你的原始CSS意图)
  // return `${progress.value}%`;

  // 方案二：让球的左边缘从0开始，右边缘到100%
  // 假设进度条的宽度是 600px (max-width)
  // 如果进度是 P%，那么球的左侧应该位于 (P/100) * 进度条宽度 - 球宽度/2
  // 或者更简单，考虑进度条的有效滑动范围
  const effectiveProgressBarWidth = 100 - (SLIDER_BALL_WIDTH / 6); // 100% - (球宽度 / 进度条最大宽度(600px)) * 100
                                                                   // 6 是 600 / 100
  // 假设进度条实际宽度是 progress-bar max-width 600px
  // 那么1%代表6px
  // 0% -> 0px
  // 100% -> 600px
  // 滑块中心在 0% 意味着左边超出 10px。滑块中心在 100% 意味着右边超出 10px。
  // 为了让球完全在进度条内，当 progress 为 0 时，left 为 0px; 当 progress 为 100 时，left 为 (100% - SLIDER_BALL_WIDTH)
  // 但是 transform: translateX(-50%) 使得 left 指向的是球的中心
  // 所以当 progress 为 0% 时，球的中心在 0% + 10px (SLIDER_BALL_WIDTH / 2)
  // 当 progress 为 100% 时，球的中心在 100% - 10px
  // 那么 progress 的值就应该是 (current_progress / total_progress) * (100 - SLIDER_BALL_WIDTH_PERCENT)
  // 实际的 left 百分比应该是 (progress.value / 100) * (100 - (SLIDER_BALL_WIDTH / 最大进度条宽度))
  // 例如，如果最大宽度是 600px，球是 20px，那么滑动范围是 580px。
  // (progress.value / 100) * (580px)
  // let actualPosition = (progress.value / 100) * (100 - (SLIDER_BALL_WIDTH * 100 / 600)); // 600px 是 max-width

  // 为了简化且符合常见的进度条滑块行为，让滑块的中心始终对齐 `progress` 百分比。
  // 如果你希望左右不超出，那就需要让 `left` 百分比计算为 `(progress / 100) * (100% - ball_width_in_percent)`
  // 并移除 transform: translateX(-50%)
  // 这里我们假设你希望球的中心对齐进度，并接受边缘稍微超出。
  // 或者，另一种更精确且不超出边缘的办法，是使用 calc()
  // let calculatedLeft = progress.value; // 这是 0-100 的百分比
  // return `calc(${calculatedLeft}% - ${SLIDER_BALL_WIDTH / 2}px)`; // 移除 transform: translateX(-50%)
  // 最终决定：为了使滑块在0%时左边缘对齐，在100%时右边缘对齐，且始终保持中心点在进度百分比处：
  // 这里的 left 仍然是球的中心点位置，但我们需要在CSS中调整 transform.
  // 调整CSS的transform为 translateX(-50%) 保持不变
  // 那么这里的 left 应该就直接是 progress.value + '%'
  // 如果要在两端都完美对齐，通常会稍微复杂一点，涉及到 JS 动态计算或者更巧妙的 CSS。
  // 但最常见和视觉上通常可以接受的是：让球的中心对齐进度百分比。
  // 那么你最初的 `left: progress + '%'` 就是正确的。
  // 考虑到 `transform: translate(-50%, -50%)` 已经将球向左偏移了自身宽度的一半
  // 当 `progress` 是 `0` 时，球的中心在 `0%` 处，它的左半部分在进度条外部。
  // 当 `progress` 是 `100` 时，球的中心在 `100%` 处，它的右半部分在进度条外部。
  // 我将调整为让球的中心对齐进度条的有效滑动范围。
  // (进度百分比 / 100) * (100% - 球宽度/进度条宽度%)
  const maxProgressBarWidth = 600; // 与 CSS .progress-bar 的 max-width 对应
  const ballWidthPercent = (SLIDER_BALL_WIDTH / maxProgressBarWidth) * 100; // 球宽度占最大进度条的百分比
  
  // 计算球中心点在0%到100%之间的有效滑动范围
  const effectiveRangePercent = 100 - ballWidthPercent;
  
  // 球的中心点应该落在 effectiveRangePercent 对应的位置
  // 如果 progress.value = 0, targetLeft = ballWidthPercent / 2
  // 如果 progress.value = 100, targetLeft = 100 - ballWidthPercent / 2
  // 这是一个线性插值
  // targetLeft = (progress.value / 100) * effectiveRangePercent + (ballWidthPercent / 2)
  let targetLeft = (progress.value / 100) * effectiveRangePercent + (ballWidthPercent / 2);

  // 确保在边界情况时不会超出
  if (progress.value === 0) {
    targetLeft = ballWidthPercent / 2; // 球中心在最左端
  } else if (progress.value === 100) {
    targetLeft = 100 - ballWidthPercent / 2; // 球中心在最右端
  }

  return `${targetLeft}%`;
});


function startCountdown() {
  const interval = 1000;
  let elapsed = 0;

  timer = setInterval(() => {
    elapsed++;
    // 确保剩余时间不会低于 0
    remainingTime.value = Math.max(0, props.totalTime - elapsed);
    
    // 计算进度，并确保不会超过 100%
    let currentProgress = (elapsed / props.totalTime) * 100;
    progress.value = Math.min(100, currentProgress);

    if (elapsed >= props.totalTime) {
      clearInterval(timer);
      timer = null; // 清除 timer 引用
      // 确保最终状态是 0 秒和 100% 进度
      remainingTime.value = 0;
      progress.value = 100;
    }
  }, interval);
}

// 停止倒计时
function stopCountdown() {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
}

// 重置倒计时到初始状态
function resetCountdown() {
  stopCountdown(); // 先停止确保没有旧的计时器在运行
  remainingTime.value = props.totalTime;
  progress.value = 0;
  // 如果需要重置后立即重新开始，可以在这里调用 startCountdown()
  // startCountdown();
}

onMounted(() => {
  startCountdown();
});

onBeforeUnmount(() => {
  // 组件卸载前清除计时器，避免内存泄漏
  stopCountdown();
});

// 暴露方法给父组件
defineExpose({
  startCountdown,
  stopCountdown,
  resetCountdown,
});
</script>

<style scoped>
.container {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%; /* 让容器也占据一定宽度 */
  padding: 20px; /* 一点内边距 */
  box-sizing: border-box;
}

.progress-bar {
  position: relative;
  width: 100%;
  max-width: 600px; /* 限制最大宽度 */
  height: 12px;
  background-color: #e5e7eb;
  border-radius: 6px;
  margin-bottom: 10px;
  overflow: hidden; /* 确保进度条填充不会溢出圆角 */
}

.progress-fill {
  position: absolute;
  height: 100%;
  background-color: #3b82f6;
  /* transition: width 1s linear; */ /* 建议移除，或调整为更短的时间 */
  /* 因为 setInterval 是 1s，所以 1s transition 可能会导致视觉上的跳跃 */
  /* 移除 transition 或者设置一个极小的 transition-delay 可以平滑最后一帧的跳动 */
  /* 或者，如果希望视觉上平滑，可以使用 CSS transform 而不是 width */
  transition: width 0.9s linear; /* 略小于 setInterval 间隔，避免跳帧感 */
  /* 或者： transition: width 0s linear; 如果不希望有动画，直接跳变 */
}

.slider-ball {
  position: absolute;
  top: 50%;
  /* transform: translate(-50%, -50%); */
  /* 保持 translateY(-50%) 用于垂直居中，但移除 translateX(-50%)
     因为 sliderLeftPosition 已经计算了中心位置 */
  transform: translateY(-50%) translateX(-50%); /* 保持这个，因为它配合计算的 left 更好 */
  
  /* 根据JS中的 SLIDER_BALL_WIDTH 变量设置宽度，保持同步 */
  width: 20px; 
  height: 20px;
  background-color: white;
  border: 3px solid #3b82f6;
  border-radius: 50%;
  pointer-events: none; /* 防止鼠标事件干扰 */
  transition: left 0.9s linear; /* 同 progress-fill 的 transition 时间 */
}

.time-text {
  font-size: 16px;
  color: #333;
  font-weight: bold;
}
</style>