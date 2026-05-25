<template>
  <main class="size-full relative flex flex-col studentDetail px-6">
    <div class="min-w-[1450px] w-full mt-6 flex-shrink-0 px-[30px]">
      <StudentHeader class="w-full" />
    </div>

    <div class="content flex flex-col items-center relative">
      <div
        class="flex space-x-4 flex-grow overflow-hidden pt-4 pb-[30px] px-[30px] w-full"
      >
        <div
          class="w-8 h-8 flex-shrink-0 rounded-full cursor-pointer overflow-hidden"
          @click="$router.back()"
        >
          <img src="@/assets/studentDetail/back.png" class="scale-[5]" alt="" />
        </div>
        <div
          class="w-[547px] flex-shrink-0 space-y-4 grid grid-cols-1 grid-rows-2"
        >
          <section class="card flex flex-col">
            <img
              src="@/assets/studentDetail/dqdf.png"
              class="w-20 h-5 flex-shrink-0"
              alt=""
            />

            <div class="flex-grow flex flex-col justify-center">
              <div
                class="text-[rgba(0,0,0,0.45)] text-[20px] font-normal space-y-4 flex flex-col items-center justify-center"
                v-if="!showResult"
              >
                <img
                  src="@/assets/studentDetail/empty.png"
                  class="w-[152px] h-[114px] scale-150"
                  alt=""
                />
                <div>教师设置不允许查看</div>
              </div>
              <template v-else>
                <DqdfChart
                  :class="showResult ? '' : 'h-[350px]'"
                  :info="info"
                  v-if="info?.id"
                />
              </template>
            </div>
            <div
              class="flex-shrink-0 text-center text-sm text-[#4D4E58] font-normal"
              v-if="showResult"
            >
              当前得分由系统换算得出
            </div>
          </section>
          <section class="card flex flex-col">
            <img
              src="@/assets/studentDetail/dfmx.png"
              class="w-20 h-5 flex-shrink-0"
              alt=""
            />
            <div class="flex-grow">
              <div
                class="text-[rgba(0,0,0,0.45)] h-full text-[20px] font-normal space-y-4 flex flex-col items-center justify-center"
                v-if="!showResult"
              >
                <img
                  src="@/assets/studentDetail/empty.png"
                  class="w-[152px] h-[114px] scale-150"
                  alt=""
                />
                <div>教师设置不允许查看</div>
              </div>
              <template v-else>
                <DfmxChart :info="info" v-if="info?.id" />
              </template>
            </div>
          </section>
        </div>
        <div class="flex-grow card flex flex-col">
          <div class="flex-shrink-0 flex items-center justify-between">
            <img
              src="@/assets/studentDetail/dbxq.png"
              class="w-20 h-5"
              alt=""
              v-if="showResult"
            />
            <img
              src="@/assets/studentDetail/jyldbz.png"
              class="w-[164px] h-5"
              v-else
              alt=""
            />
            <div class="space-x-2 flex items-center">
              <img
                src="@/assets/studentDetail/prev.png"
                class="w-10 h-10 cursor-pointer hover:scale-110"
                alt=""
                @click="prev"
              />
              <img
                src="@/assets/studentDetail/next.png"
                class="w-10 h-10 cursor-pointer hover:scale-110"
                alt=""
                @click="next"
              />
            </div>
          </div>
          <div class="flex-grow mt-4 relative">
            <template v-for="(item, index) in list">
              <div
                class="absolute w-full"
                v-if="index < 3"
                :class="`index${index}`"
                :key="index"
              >
                <AnalysisCard
                  :info="item"
                  :summary="info?.summary"
                  v-if="item.cardTag == 'analysis'"
                />
                <SuggestionCard
                  :info="item"
                  v-else-if="item.cardTag == 'suggestions'"
                />
                <LdYBzCard
                  :info="item"
                  v-else-if="item.cardTag == 'strengths'"
                />
                <DefenseCard
                  :info="item"
                  v-else-if="item.cardTag == 'defense'"
                />
                <QuestionCard :info="item" :key="index" v-else />
              </div>
            </template>
          </div>
        </div>
      </div>
      <div
        class="h-[30px] flex items-center justify-center text-xs text-[#FFFFFF] font-normal absolute bottom-0 left-0 w-full"
      >
        本报告由人工智能生成
      </div>
    </div>
  </main>
</template>
<script setup lang="ts">
import StudentHeader from "@/components/Layout/componets/StudentHeader.vue";
import DefenseCard from "./components/DefenseCard.vue";
import QuestionCard from "./components/QuestionCard.vue";
import SuggestionCard from "./components/SuggestionCard.vue";
import LdYBzCard from "./components/LdYBzCard.vue";
import AnalysisCard from "./components/AnalysisCard.vue";
import DfmxChart from "./components/DfmxChart.vue";
import DqdfChart from "./components/DqdfChart.vue";
import { useRoute } from "vue-router";
import { computed, onMounted, ref } from "vue";
import { getStudentAssessmentDetail } from "@/api/studentAssessment";
import { getStudyDetail } from "@/api/studyRecord";

const colorClasses = [
  "cardColor1",
  "cardColor2",
  "cardColor3",
  "cardColor4",
  "cardColor5",
  "cardColor6",
];
const loading = ref(false);
const route = useRoute();
const info = ref<Recordable>({});
const list = ref<Recordable[]>([]);
const showResult = computed(() => {
  if (route.name == "StudentStudyDetail") {
    return !!info.value?.showResult;
  } else {
    return true;
  }
});
const onLoad = () => {
  loading.value = true;
  (route.name == "StudentStudyDetail"
    ? getStudentAssessmentDetail
    : getStudyDetail)({
    id: route.query?.id,
  })
    .then((res) => {
      info.value = res?.data || {};
      if (route.name == "StudentStudyDetail") {
        if (info.value?.showResult) {
          if (info.value?.defense) {
            list.value?.push({
              cardTag: "defense",
              ...(info.value?.defense || {}),
            });
          }
          list.value?.push(
            ...(res?.data?.result?.map(
              (
                item: { cardTag: string; questionIndex: number },
                index: number
              ) => {
                item.cardTag = "question";
                item.questionIndex = index + 1;
                return item;
              }
            ) || [])
          );
          list.value?.push({
            cardTag: "analysis",
            analysis: res?.data?.analysis?.analysis,
          });
        }
      } else {
        if (info.value?.defense) {
          list.value?.push({
            cardTag: "defense",
            ...(info.value?.defense || {}),
          });
        }
        list.value?.push(
          ...(res?.data?.result?.map(
            (
              item: { cardTag: string; questionIndex: number },
              index: number
            ) => {
              item.cardTag = "question";
              item.questionIndex = index + 1;
              return item;
            }
          ) || [])
        );
        list.value?.push({
          cardTag: "analysis",
          analysis: res?.data?.analysis?.analysis,
        });
      }
      list.value?.push({
        cardTag: "suggestions",
        suggestions: res?.data?.analysis?.suggestions,
      });
      list.value?.push({
        cardTag: "strengths",
        strengths: res?.data?.analysis?.strengths,
        weaknesses: res?.data?.analysis?.weaknesses,
      });
      for (let index = 0; index < list.value.length; index++) {
        const element = list.value[index];
        element.colorClass = colorClasses[index % 6];
      }
    })
    .finally(() => {
      loading.value = false;
    });
};

onMounted(() => {
  onLoad();
});

const prev = () => {
  const data = list.value?.pop();
  list.value?.unshift(data as any);
  setTimeout(() => {
    document.querySelector(".index0")?.classList?.add("fade-enter");
  }, 100);
  setTimeout(() => {
    document.querySelector(".index0")?.classList?.remove("fade-enter");
  }, 800);
};
const next = () => {
  const data = list.value?.shift();
  list.value?.push(data as any);
  setTimeout(() => {
    document.querySelector(".index0")?.classList?.add("fade-enter");
  }, 100);
  setTimeout(() => {
    document.querySelector(".index0")?.classList?.remove("fade-enter");
  }, 800);
};
</script>
<style scoped>
.studentDetail {
  background: linear-gradient(180deg, #f8dfe1 0%, #f9dee1 46%, #88aacf 100%);
  .content {
    width: calc(1390px + 60px);
    height: 100%;
    margin: 0 auto;
  }
  .card {
    background: linear-gradient(
      180deg,
      rgba(255, 243, 236, 0.8) 21%,
      rgba(228, 234, 255, 0.56) 100%
    );
    box-shadow: 0px 20px 40px 0px rgba(9, 18, 43, 0.16);
    border-radius: 16px;
    padding: 24px;
  }
}
.index0 {
  width: 100%;
  height: calc(100% - 32px);
  top: 32px;
  left: 0;
  z-index: 3;
}
@keyframes scaleEnter {
  0% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.03);
  }
  100% {
    transform: scale(1);
  }
}
.fade-enter {
  animation: scaleEnter 0.9s cubic-bezier(0.34, 1.56, 0.64, 1);
}
@keyframes scaleLevel {
  0% {
    transform: scale(1);
  }
  50% {
    transform: scale(0.9);
  }
  100% {
    transform: scale(1);
  }
}
.fade-leave {
  animation: scaleLevel 0.9s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.index1 {
  width: calc(100% - 32px);
  height: calc(100% - 16px);
  top: 16px;
  left: 16px;
  z-index: 2;
}
.index2 {
  width: calc(100% - 64px);
  height: 100%;
  top: 0;
  left: 32px;
  z-index: 1;
}
</style>
<style>
.cardColor1 {
  background: linear-gradient(180deg, #c4c8e2 0%, rgba(246, 246, 246, 0.7) 96%);
  box-shadow: 0px 20px 40px 0px rgba(9, 18, 43, 0.08);
  border-radius: 13px 13px 13px 13px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(40px);
}
.cardColor2 {
  background: linear-gradient(180deg, #f7bbac 0%, rgba(246, 246, 246, 0.7) 96%);
  box-shadow: 0px 20px 40px 0px rgba(9, 18, 43, 0.08);
  border-radius: 13px 13px 13px 13px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(40px);
}
.cardColor3 {
  background: linear-gradient(180deg, #eeddb7 0%, rgba(246, 246, 246, 0.7) 96%);
  box-shadow: 0px 20px 40px 0px rgba(9, 18, 43, 0.08);
  border-radius: 13px 13px 13px 13px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(40px);
}
.cardColor4 {
  background: linear-gradient(
    180deg,
    #b7eaee 0%,
    rgba(246, 246, 246, 0.7) 100%
  );
  box-shadow: 0px 20px 40px 0px rgba(9, 18, 43, 0.08);
  border-radius: 13px 13px 13px 13px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(40px);
}
.cardColor5 {
  background: linear-gradient(
    180deg,
    #c0e7c6 0%,
    rgba(246, 246, 246, 0.7) 100%
  );
  box-shadow: 0px 20px 40px 0px rgba(9, 18, 43, 0.08);
  border-radius: 13px 13px 13px 13px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(40px);
}
.cardColor6 {
  background: linear-gradient(
    180deg,
    #e2cfeb 0%,
    rgba(246, 246, 246, 0.7) 100%
  );
  box-shadow: 0px 20px 40px 0px rgba(9, 18, 43, 0.08);
  border-radius: 13px 13px 13px 13px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(40px);
}

/* Webkit浏览器滚动条 */
.custom-scrollbar::-webkit-scrollbar {
  width: 6px;
}

.custom-scrollbar::-webkit-scrollbar-track {
  background: rgba(255, 255, 255, 0.3);
  border-radius: 3px;
}

.custom-scrollbar::-webkit-scrollbar-thumb {
  background: #c0c0c0;
  border-radius: 3px;
}

.custom-scrollbar::-webkit-scrollbar-thumb:hover {
  background: #a0a0a0;
}

/* Firefox浏览器支持 */
@supports not selector(::-webkit-scrollbar) {
  .custom-scrollbar {
    scrollbar-width: thin;
    scrollbar-color: #c0c0c0 rgba(255, 255, 255, 0.3);
  }
}
</style>
