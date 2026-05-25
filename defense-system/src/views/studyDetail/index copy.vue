<template>
  <div class="w-full h-full flex flex-col overflow-hidden" v-loading="loading">
    <section class="navbar p-[20px] flex items-center justify-between">
      <Button type="primary" class="w-[100px]" @click="$router.back()">
        返回
      </Button>
      <span class="text-xs text-[rgba(0,0,0,0.45)]">本报告由人工智能生成</span>
    </section>
    <section class="grid grid-cols-10 gap-x-3 content h-full overflow-hidden">
      <div class="col-span-4 grid grid-rows-2 gap-y-3 h-full overflow-hidden">
        <div
          class="tableBox text-center p-[30px] flex flex-col items-center justify-center"
        >
          <Progress
            type="dashboard"
            :percent="(info?.score / info?.totalScore) * 100 || 0"
            :strokeWidth="10"
            strokeColor="#726EF0"
            trailColor="#e8e7fe"
            :size="150"
          >
            <template #format>
              <div class="flex flex-col">
                <span class="text-lg text-[#726EF0]">当前得分</span>
                <span class="text-2xl text-[#726EF0] font-bold">
                  {{ info?.score }}
                </span>
                <span class="text-sm text-[#999]">
                  满分{{ info?.totalScore || 0 }}分
                </span>
              </div>
            </template>
          </Progress>

          <Steps
            class="w-full mt-6"
            :current="active"
            :items="list"
            label-placement="vertical"
          >
            <template #progressDot="{ index, status, prefixCls }">
              <div
                class="size-[10px] rounded-full"
                :class="`${prefixCls}-icon-dot`"
              ></div>
            </template>
          </Steps>
          <section class="text-base text-[#666666] mt-8 w-full">
            <div class="text-sm pb-1">当前得分由系统换算得出</div>
            <span>通过：{{ info?.passScore || 0 }}分以上</span>
          </section>
        </div>
        <div class="tableBox p-6">
          <div class="w-full h-full" id="chart_demo"></div>
        </div>
      </div>
      <div
        class="col-span-6 tableBox overflow-y-auto p-10 relative scrollbar space-y-5"
        @scroll="handleScroll"
        ref="contentRef"
      >
        <section v-if="info?.defense">
          <div class="flex items-center justify-between space-x-[10px]">
            <div class="text-[20px] text-[#000] font-medium flex-grow">
              答辩
            </div>
            <!-- <div
              class="size-[60px] flex-shrink-0 rounded-full bg-[#4B84FF] flex items-center justify-center text-white"
            >
              <span class="text-lg font-bold">{{
                info.defense?.score || 0
              }}</span>
              <span class="text-base">分</span>
            </div> -->
          </div>

          <div>
            <div class="flex items-center space-x-[10px] mt-[10px]">
              <div
                class="size-[10px] bg-[#4B84FF] rounded-full overflow-hidden"
              ></div>
              <span class="text-[#333333] text-xl font-medium">学生回答</span>
            </div>
            <div class="my-2 pl-4">
              <AudioPlayer
                v-if="info.defense?.defenseAnswerFile"
                :url="info.defense?.defenseAnswerFile"
              ></AudioPlayer>
            </div>
            <div class="text-base text-[#666666] pl-5 mt-[10px]">
              {{ info.defense?.defenseAnswer || "-" }}
            </div>
          </div>
          <!-- <div>
            <div class="flex items-center space-x-[10px] mt-[10px]">
              <div
                class="size-[10px] bg-[#4B84FF] rounded-full overflow-hidden"
              ></div>
              <span class="text-[#333333] text-xl font-medium">分析</span>
            </div>
            <ul class="text-base text-[#666666] !pl-8 mt-[10px] list-disc">
              <li v-for="(ite, ind) in info?.defense?.analysis" :key="ind">
                {{ ite?.name }}（{{ ite?.score }}分）：{{ ite?.description }}
              </li>
            </ul>
          </div>
          <div>
            <div class="flex items-center space-x-[10px] mt-[10px]">
              <div
                class="size-[10px] bg-[#4B84FF] rounded-full overflow-hidden"
              ></div>
              <span class="text-[#333333] text-xl font-medium">建议</span>
            </div>
            <ul class="text-base text-[#666666] !pl-8 mt-[10px] list-disc">
              <li v-for="(ite, ind) in info?.defense?.suggestions" :key="ind">
                {{ ite }}
              </li>
            </ul>
          </div>
          <div>
            <div class="flex items-center space-x-[10px] mt-[10px]">
              <div
                class="size-[10px] bg-[#4B84FF] rounded-full overflow-hidden"
              ></div>
              <span class="text-[#333333] text-xl font-medium">亮点与不足</span>
            </div>
            <div class="text-base text-[#666666] pl-5 mt-[10px]">
              <div class="flex">
                <div class="flex-shrink-0">亮点：</div>
                <div class="flex-grow overflow-hidden">
                  <template v-if="info?.defense?.strengths?.length">
                    <div
                      v-for="(ite, ind) in info?.defense?.strengths"
                      :key="ind"
                    >
                      {{ ite }}
                    </div>
                  </template>
                  <template>无</template>
                </div>
              </div>
              <div class="flex">
                <div class="flex-shrink-0">不足：</div>
                <div class="flex-grow overflow-hidden">
                  <template v-if="info?.defense?.weaknesses?.length">
                    <div
                      v-for="(ite, ind) in info?.defense?.weaknesses"
                      :key="ind"
                    >
                      {{ ite }}
                    </div>
                  </template>
                  <template>无</template>
                </div>
              </div>
            </div>
          </div> -->
          <Divider
            v-if="info?.result?.length"
            class="bg-[rgba(5,5,5,0.06)] h-[2px]"
          />
        </section>
        <section v-for="(item, index) in info?.result" :key="index">
          <div class="flex items-start justify-between space-x-[10px]">
            <div class="text-[20px] text-[#333333] font-medium flex-grow">
              {{ index + 1 }}. {{ item?.question }}
            </div>
            <!-- <div
              class="size-[60px] flex-shrink-0 rounded-full bg-[#4B84FF] flex items-center justify-center text-white"
            >
              <span class="text-lg font-bold">{{ item?.totalScore || 0 }}</span>
              <span class="text-base">分</span>
            </div> -->
          </div>
          <div>
            <div class="flex items-center space-x-[10px] mt-[10px]">
              <div
                class="size-[10px] bg-[#4B84FF] rounded-full overflow-hidden"
              ></div>
              <span class="text-[#333333] text-xl font-medium">学生回答</span>
            </div>
            <div class="my-2 pl-4">
              <AudioPlayer
                v-if="item?.answerFile"
                :url="item?.answerFile"
              ></AudioPlayer>
            </div>
            <div class="text-base text-[#666666] pl-5 mt-[10px]">
              {{ item?.answer || "-" }}
            </div>
          </div>
          <template v-if="item?.followQuestion">
            <div>
              <div class="flex items-center space-x-[10px] mt-[10px]">
                <div
                  class="size-[10px] bg-[#4B84FF] rounded-full overflow-hidden"
                ></div>
                <span class="text-[#333333] text-xl font-medium">追问</span>
              </div>
              <div class="text-base text-[#666666] pl-5 mt-[10px]">
                {{ item?.followQuestion || "-" }}
              </div>
            </div>
            <div>
              <div class="flex items-center space-x-[10px] mt-[10px]">
                <div
                  class="size-[10px] bg-[#4B84FF] rounded-full overflow-hidden"
                ></div>
                <span class="text-[#333333] text-xl font-medium">
                  学生追问回答
                </span>
              </div>
              <div class="my-2 pl-4">
                <AudioPlayer
                  v-if="item?.followAnswerFile"
                  :url="item?.followAnswerFile"
                ></AudioPlayer>
              </div>
              <div class="text-base text-[#666666] pl-5 mt-[10px]">
                {{ item?.followAnswer || "-" }}
              </div>
            </div>
          </template>
          <!-- <div>
            <div class="flex items-center space-x-[10px] mt-[10px]">
              <div
                class="size-[10px] bg-[#4B84FF] rounded-full overflow-hidden"
              ></div>
              <span class="text-[#333333] text-xl font-medium">分析</span>
            </div>
            <ul class="text-base text-[#666666] !pl-8 mt-[10px] list-disc">
              <li v-for="(ite, ind) in item?.analysis" :key="ind">
                {{ ite?.name }}（{{ ite?.score }}分）：{{ ite?.description }}
              </li>
            </ul>
          </div>
          <div>
            <div class="flex items-center space-x-[10px] mt-[10px]">
              <div
                class="size-[10px] bg-[#4B84FF] rounded-full overflow-hidden"
              ></div>
              <span class="text-[#333333] text-xl font-medium">建议</span>
            </div>
            <ul class="text-base text-[#666666] !pl-8 mt-[10px] list-disc">
              <li v-for="(ite, ind) in item?.suggestions" :key="ind">
                {{ ite }}
              </li>
            </ul>
          </div>
          <div>
            <div class="flex items-center space-x-[10px] mt-[10px]">
              <div
                class="size-[10px] bg-[#4B84FF] rounded-full overflow-hidden"
              ></div>
              <span class="text-[#333333] text-xl font-medium">亮点与不足</span>
            </div>
            <div class="text-base text-[#666666] pl-5 mt-[10px]">
              <div class="flex">
                <div class="flex-shrink-0">亮点：</div>
                <div class="flex-grow overflow-hidden">
                  <template v-if="item?.strengths?.length">
                    <div v-for="(ite, ind) in item?.strengths" :key="ind">
                      {{ ite }}
                    </div>
                  </template>
                  <template>无</template>
                </div>
              </div>
              <div class="flex">
                <div class="flex-shrink-0">不足：</div>
                <div class="flex-grow overflow-hidden">
                  <template v-if="item?.weaknesses?.length">
                    <div v-for="(ite, ind) in item?.weaknesses" :key="ind">
                      {{ ite }}
                    </div>
                  </template>
                  <template>无</template>
                </div>
              </div>
            </div>
          </div> -->
        </section>
        <template v-if="!loading">
          <Divider class="bg-[rgba(5,5,5,0.06)] h-[2px]" />
          <div v-if="info?.showResult">
            <div class="flex items-center space-x-[10px] mt-[10px]">
              <div
                class="size-[10px] bg-[#4B84FF] rounded-full overflow-hidden"
              ></div>
              <span class="text-[#333333] text-xl font-medium">分析</span>
            </div>
            <ul class="text-base text-[#666666] !pl-8 mt-[10px] list-disc">
              <li v-for="(ite, ind) in info?.analysis?.analysis" :key="ind">
                {{ ite?.name }}（{{ ite?.score }}分）：{{ ite?.description }}
              </li>
            </ul>
          </div>
          <div>
            <div class="flex items-center space-x-[10px] mt-[10px]">
              <div
                class="size-[10px] bg-[#4B84FF] rounded-full overflow-hidden"
              ></div>
              <span class="text-[#333333] text-xl font-medium">建议</span>
            </div>
            <ul class="text-base text-[#666666] !pl-8 mt-[10px] list-disc">
              <li v-for="(ite, ind) in info?.analysis?.suggestions" :key="ind">
                {{ ite }}
              </li>
            </ul>
          </div>
          <div>
            <div class="flex items-center space-x-[10px] mt-[10px]">
              <div
                class="size-[10px] bg-[#4B84FF] rounded-full overflow-hidden"
              ></div>
              <span class="text-[#333333] text-xl font-medium">亮点与不足</span>
            </div>
            <div class="text-base text-[#666666] pl-5 mt-[10px]">
              <div class="flex">
                <div class="flex-shrink-0">亮点：</div>
                <div class="flex-grow overflow-hidden">
                  <template v-if="info?.analysis?.strengths?.length">
                    <div
                      v-for="(ite, ind) in info?.analysis?.strengths"
                      :key="ind"
                    >
                      {{ ite }}
                    </div>
                  </template>
                  <template>无</template>
                </div>
              </div>
              <div class="flex">
                <div class="flex-shrink-0">不足：</div>
                <div class="flex-grow overflow-hidden">
                  <template v-if="info?.analysis?.weaknesses?.length">
                    <div
                      v-for="(ite, ind) in info?.analysis?.weaknesses"
                      :key="ind"
                    >
                      {{ ite }}
                    </div>
                  </template>
                  <template>无</template>
                </div>
              </div>
            </div>
          </div>
        </template>
        <Slider
          v-if="isShowBar"
          orientation="vertical"
          direction="ltr"
          :tooltips="false"
          v-model="thumbTop"
          :min="0"
          :max="100"
          @update="updateThumbTop"
        />
      </div>
    </section>
  </div>
</template>
<script setup lang="ts">
import { Button, Progress, Steps, Step, Divider } from "ant-design-vue";
import { onMounted, onUnmounted, ref } from "vue";
import useEchart from "./chart";
import { getStudyDetail } from "@/api/studyRecord";
import { useRoute } from "vue-router";
import Slider from "@vueform/slider";
import AudioPlayer from "./AudioPlayer.vue";
import { getStudentAssessmentDetail } from "@/api/studentAssessment";

const contentRef = ref<HTMLElement>(null as unknown as any);
// 是否显示滚动条
const isShowBar = ref(false);
// 滚动条相关状态
const thumbTop = ref(0);
// 计算滚动条高度和位置
const updateScrollbar = () => {
  if (!contentRef.value) return;
  const { scrollHeight, scrollTop, clientHeight } = contentRef.value;
  isShowBar.value = scrollHeight > clientHeight + 10 ? true : false;

  const trackHeight = 100;
  // 计算滚动条位置
  thumbTop.value = (scrollTop / (scrollHeight - clientHeight)) * trackHeight;
};

// 内容滚动事件处理
const handleScroll = () => {
  updateScrollbar();
};

const updateThumbTop = (e: any) => {
  contentRef.value.scrollTop =
    (e / 100) * (contentRef.value.scrollHeight - contentRef.value.clientHeight);
};

const loading = ref(false);
const active = ref(0);
const route = useRoute();
const info = ref<Record<string, any>>({});
const list = ref([
  {
    title: "不及格",
  },
  {
    title: "通过",
  },
]);

const analysisList = ref([
  // {
  //   name: "数学原理",
  //   value: 0,
  //   itemStyle: { color: "#5DD8D0" },
  // },
  // {
  //   name: "知识运用",
  //   value: 0,
  //   itemStyle: { color: "#FF9B91" },
  // },
  // {
  //   name: "问题分析",
  //   value: 0,
  //   itemStyle: { color: "#F493E1" },
  // },
  // {
  //   name: "工程能力",
  //   value: 0,
  //   itemStyle: { color: "#4B84FF" },
  // },
  // {
  //   name: "拓展能力",
  //   value: 0,
  //   itemStyle: { color: "#726EF0" },
  // },
]);

const onLoad = () => {
  loading.value = true;
  (route.name == "StudentStudyDetail"
    ? getStudentAssessmentDetail
    : getStudyDetail)({
    id: route.query?.id,
  })
    .then((res) => {
      info.value = res?.data || {};
      if (info.value.score >= info.value?.passScore && info.value?.passScore) {
        active.value = 1;
      } else {
        active.value = 0;
      }
      analysisList.value = info.value?.value || [];
      const { setOption } = useEchart("chart_demo");
      setOption(analysisList.value);
    })
    .finally(() => {
      loading.value = false;
    });
};

onMounted(() => {
  onLoad();
  updateScrollbar();
  window.addEventListener("resize", updateScrollbar);
});
onUnmounted(() => {
  window.removeEventListener("resize", updateScrollbar);
});
</script>
<style src="@vueform/slider/themes/default.css"></style>
<style scoped>
.navbar {
  background: #ffffff;
  border-radius: 8px;
  margin-bottom: 10px;
  flex-shrink: 0;
}
.tableBox {
  background: #ffffff;
  border-radius: 8px;
}

.content {
  height: calc(100% - 80px);
}

.scrollbar {
  /* 隐藏默认滚动条的关键代码 */
  scrollbar-width: none; /* Firefox */
  -ms-overflow-style: none; /* IE/Edge */
}
/* Chrome/Safari/Opera 隐藏滚动条 */
.scrollbar::-webkit-scrollbar {
  display: none;
}
:deep(.ant-steps-item-active .ant-steps-icon-dot) {
  background: #726ef0 !important;
}
:deep(.ant-steps-item-finish .ant-steps-icon-dot) {
  background: #726ef0 !important;
}
:deep(.ant-steps-item-finish .ant-steps-item-tail)::after {
  background: #726ef0 !important;
}
.slider-target {
  position: fixed;
  right: 36px;
  top: 50%;
  width: 15px;
  height: 254px;
  transform: translateY(-50%);
  background: rgba(217, 217, 217, 0.6);
  border-radius: 100px;
}
:deep(.slider-base) {
  height: 200px !important;
  background: initial;
}
:deep(.slider-connect) {
  display: none;
}
:deep(.slider-origin) {
  left: 5px;
  height: 200px;
}
:deep(.slider-handle) {
  width: 10px !important;
  height: 50px !important;
  background: #4b84ff;
  border-radius: 100px;
  box-shadow: initial;
  margin: 2px auto;
  &:focus {
    box-shadow: initial;
  }
}
</style>
