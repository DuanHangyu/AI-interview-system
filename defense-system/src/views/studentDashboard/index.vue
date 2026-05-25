<template>
  <main class="size-full relative flex flex-col">
    <img
      src="@/assets/studentUI_home/bg.png"
      class="size-full absolute top-0 left-0"
      alt=""
    />
    <div class="px-[24px] relative pt-6 flex-shrink-0">
      <StudentHeader class="!w-full" />
    </div>
    <div
      class="mx-auto relative w-full h-full px-[24px] flex flex-col items-center justify-around flex-grow pb-[100px]"
    >
      <section>
        <div class="flex items-end justify-center space-x-[60px] h-[60px]">
          <div
            class="dyy"
            :class="tab == 1 ? 'dyy_selected' : 'dyy_no'"
            @click="changeTab(1)"
          ></div>
          <div
            class="dwc"
            :class="tab == 2 ? 'dwc_selected' : 'dwc_no'"
            @click="changeTab(2)"
          ></div>
          <div
            class="pgz"
            :class="tab == 3 ? 'pgz_selected' : 'pgz_no'"
            @click="changeTab(3)"
          ></div>
          <div
            class="ywc"
            :class="tab == 4 ? 'ywc_selected' : 'ywc_no'"
            @click="changeTab(4)"
          ></div>
        </div>
        <img
          src="@/assets/studentUI_home/qxfgx.png"
          class="w-[1000px] h-[57px] ml-12 block"
          alt=""
        />
      </section>
      <section class="grid grid-cols-6 ml-[160px] h-[480px] z-10">
        <template v-if="list.length != 6 && isResult">
          <div
            v-for="item in 6 - list.length"
            :key="item"
            class="relative w-[340px] h-[480px] rounded-xl overflow-hidden ml-[-160px] card flex flex-col justify-between"
            :style="{ transform: `rotate(${getRandomInteger(-8, 8)}deg)` }"
          >
            <img
              :src="bgArr[getRandomInteger(0, 9)]"
              class="absolute w-full h-full"
              alt=""
            />
            <section class="flex justify-end relative flex-shrink-0">
              <img
                src="@/assets/studentUI_home/empty_star_top.png"
                class="w-[160px] h-[140px]"
                alt=""
              />
            </section>
            <section
              class="relative flex flex-col justify-center items-center flex-grow mb-5"
            >
              <img
                src="@/assets/studentUI_home/empty.png"
                class="w-[180px] h-[150px]"
                alt=""
              />
              <span class="text-[28px] text-[rgba(54,63,80,0.5)] font-normal">
                暂无考试内容
              </span>
            </section>
            <section
              class="flex justify-start relative pl-5 pb-5 flex-shrink-0"
            >
              <img
                src="@/assets/studentUI_home/empty_star_bottom.png"
                class="w-[110px] h-[100px]"
                alt=""
              />
            </section>
          </div>
        </template>
        <template v-if="list.length > 0">
          <div
            class="relative w-[340px] h-[480px] rounded-xl overflow-hidden ml-[-160px] card flex flex-col justify-between"
            v-for="item in list"
            :key="item?.id"
            :style="{ transform: `rotate(${getRandomInteger(-8, 8)}deg)` }"
          >
            <img
              :src="bgArr[getRandomInteger(0, 9)]"
              class="absolute w-full h-full"
              alt=""
            />
            <section class="relative">
              <div
                class="p-5 pb-0 line-clamp-5 text-[25px] text-[#363F50] font-bold min-h-[80px]"
              >
                {{ item?.theme }}
              </div>
              <div class="mt-7 ml-5 actionBtn hover:scale-105" v-if="tab == 1">
                <div
                  class="actionBtn-in flex items-center justify-center cursor-pointer"
                  @click="toAppoint(item)"
                >
                  <img
                    src="@/assets/studentUI_home/ljyy.png"
                    class="w-[80px] h-[18px]"
                    alt=""
                  />
                </div>
              </div>
              <div
                class="mt-7 ml-5 actionBtn"
                :class="
                  serverTime?.isBefore(dayjs(item?.timePeriod))
                    ? ''
                    : 'hover:scale-105'
                "
                v-if="tab == 2"
              >
                <div
                  class="flex items-center justify-center cursor-pointer"
                  @click="startFn(item)"
                  :class="
                    serverTime?.isBefore(dayjs(item?.timePeriod))
                      ? 'disabled-actionBtn'
                      : 'actionBtn-in'
                  "
                >
                  <img
                    src="@/assets/studentUI_home/ljks.png"
                    class="w-[80px] h-[18px]"
                    alt=""
                  />
                </div>
              </div>
              <div
                class="mt-7 ml-5 actionBtn hover:scale-105"
                v-if="tab == 4 && item.state == 2"
              >
                <div
                  class="flex items-center justify-center cursor-pointer actionBtn-in"
                  @click="toDetail(item)"
                >
                  <img
                    src="@/assets/studentUI_home/ckjg.png"
                    class="w-[80px] h-[18px]"
                    alt=""
                  />
                </div>
              </div>
              <div
                class="mt-2 ml-5 actionBtn"
                :class="
                  item.state == 3 || item.state == 5 ? '' : 'hover:scale-105'
                "
                v-if="
                  tab == 4 &&
                  (item.state == 3 ||
                    item.state == 4 ||
                    item.state == 5 ||
                    (item?.appointRecordFlag &&
                      item.state != 3 &&
                      item.state != 5 &&
                      typeof item?.defenseScore == 'number' &&
                      item?.defenseScore < item?.passScore))
                "
              >
                <Tooltip>
                  <template #title>{{
                    item?.assessmentFailPunish
                      ? `请于${
                          item?.rescheduleAppointTime
                            ? dayjs(item?.rescheduleAppointTime).format(
                                "YYYY-MM-DD HH:mm"
                              )
                            : "-"
                        }后重新预约`
                      : "暂无可预约时间"
                  }}</template>
                  <div
                    class="flex items-center justify-center cursor-pointer disabled-actionBtn"
                    @click="toDetail(item)"
                    v-if="item.state == 3 || item.state == 5"
                  >
                    <img
                      src="@/assets/studentUI_home/cxyy.png"
                      class="w-[80px] h-[18px]"
                      alt=""
                    />
                  </div>
                </Tooltip>
                <div
                  class="flex items-center justify-center cursor-pointer actionBtn-in"
                  v-if="
                    item.state == 4 ||
                    (item?.appointRecordFlag &&
                      item.state != 3 &&
                      item.state != 5 &&
                      typeof item?.defenseScore == 'number' &&
                      item?.defenseScore < item?.passScore)
                  "
                  @click="toAppoint(item)"
                >
                  <img
                    src="@/assets/studentUI_home/cxyy.png"
                    class="w-[80px] h-[18px]"
                    alt=""
                  />
                  <img
                </div>
              </div>
            </section>
            <section class="p-4 relative">
              <div
                class="flex items-center space-x-4 h-12 mb-4"
                v-if="
                  tab == 1 ||
                  (tab == 2 && serverTime?.isBefore(dayjs(item?.timePeriod))) ||
                  (tab == 4 && item.state != 2)
                "
              >
                <div class="yyBtn flex items-center justify-center">
                  <img
                    src="@/assets/studentUI_home/yy.png"
                    class="size-[27px]"
                    alt=""
                  />
                </div>
                <span
                  class="text-[#1677FF] text-base font-normal cursor-pointer"
                  v-if="
                    tab == 2 &&
                    serverTime?.isBefore(dayjs(item?.timePeriod)) &&
                    item?.timePeriod
                  "
                  @click="cancelSubscribe(item)"
                >
                  解除预约
                </span>
              </div>
              <div class="content space-y-2">
                <!-- <div class="relative mb-3" v-if="tab == 4 && item.state == 2">
                  <span class="text-[36px] font-bold text-[#363F50]">
                    {{ item?.defenseScore || 0 }}
                  </span>
                  <span class="text-sm text-[#363F50] font-normal">分</span>
                  <img
                    src="@/assets/studentUI_home/sorce_line.png"
                    class="absolute w-[40px] h-[10px] left-0 bottom-[-3px]"
                    alt=""
                  />
                </div> -->
                <div class="mb-3" v-if="tab == 4">
                  <img
                    src="@/assets/studentUI_text/pass.png"
                    v-if="
                      item?.defenseScore &&
                      item?.defenseScore >= item?.passScore
                    "
                    class="w-[56px] h-[26px]"
                    alt=""
                  />
                  <img
                    src="@/assets/studentUI_text/noPass.png"
                    v-else
                    class="w-[84px] h-[26px]"
                    alt=""
                  />
                </div>
                <div
                  v-if="item?.stateTag"
                  class="mb-2 bg-[#FFF1F0] border-[1px] border-solid border-[#FFA39E] rounded-md px-3 py-1 text-base text-[#F5222D] font-normal w-fit"
                >
                  {{ item?.stateTag }}
                </div>
                <div class="columnContent" v-if="tab == 2 && item?.timePeriod">
                  <span class="label">预约考试时间</span>
                  <span class="text">{{
                    dayjs(item?.timePeriod).format("YYYY/MM/DD HH:mm")
                  }}</span>
                </div>
                <div class="columnContent" v-if="item?.location">
                  <span class="label">考试地点</span>
                  <span class="text">{{ item?.location }}</span>
                </div>
                <div
                  class="columnContent"
                  v-if="tab == 4 && item?.timePeriod && item?.state != 2"
                >
                  <span class="label">原预约考试时间</span>
                  <span class="text">{{
                    dayjs(item?.timePeriod).format("YYYY/MM/DD HH:mm")
                  }}</span>
                </div>
                <div class="columnContent">
                  <span class="label">考试总分</span>
                  <span class="text">{{ item?.score || 0 }}</span>
                </div>
                <div class="columnContent" v-if="item?.defense">
                  <span class="label">答辩时长（秒）</span>
                  <span class="text"> {{ item?.duration || 0 }}</span>
                </div>
                <div class="columnContent" v-if="item?.question">
                  <span class="label">提问个数</span>
                  <span class="text"> {{ item?.questionCount || 0 }}</span>
                </div>
                <div class="columnContent" v-if="item?.question">
                  <span class="label">每个提问回答时长（秒）</span>
                  <span class="text"> {{ item?.answerTime || 0 }}</span>
                </div>
                <div class="columnContent" v-if="tab == 4 && item.state == 2">
                  <span class="label">完成时间</span>
                  <span class="text">{{
                    item?.assessmentEndTime
                      ? dayjs(item?.assessmentEndTime).format(
                          "YYYY/MM/DD HH:mm"
                        )
                      : "-"
                  }}</span>
                </div>
              </div>
            </section>
          </div>
        </template>
      </section>
      <section
        class="flex items-center space-x-6 z-30"
        :class="total > 6 ? ` opacity-100` : 'opacity-0'"
      >
        <img
          src="@/assets/studentUI_home/pre.png"
          class="w-[52px] h-[52px] cursor-pointer scale-[2] hover:scale-[2.1]"
          alt=""
          @click="preFn"
          v-if="page > 1"
        />
        <img
          src="@/assets/studentUI_home/pre_disabled.png"
          class="w-[52px] h-[52px] scale-[2]"
          :style="{ cursor: 'not-allowed' }"
          alt=""
          v-else
        />
        <img
          src="@/assets/studentUI_home/next_disabled.png"
          class="w-[52px] h-[52px] scale-[2]"
          :style="{ cursor: 'not-allowed' }"
          alt=""
          v-if="page * 6 > total"
        />
        <img
          src="@/assets/studentUI_home/next.png"
          class="w-[52px] h-[52px] cursor-pointer scale-[2] hover:scale-[2.1]"
          alt=""
          @click="nextFn"
          v-else
        />
      </section>
    </div>
    <img
      src="@/assets/studentUI_home/bottom_text.png"
      class="absolute bottom-0 translate-y-[56px] left-1/2 -translate-x-1/2 w-[1300px] h-[160px]"
      alt=""
    />
    <AppointModal
      ref="appointRef"
      @success="
        () => {
          onList();
        }
      "
    />
  </main>
</template>
<script setup lang="ts">
import {
  getAssessmentTodo,
  getAssessmentAnalysis,
  getAssessmentDone,
  getAppointmentList,
  getMachineTime,
  cancelAppointment,
} from "@/api/studentAssessment";
import StudentHeader from "@/components/Layout/componets/StudentHeader.vue";
import dayjs from "dayjs";
import { createVNode, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import AppointModal from "./AppointModal.vue";
import { message, Modal, Tooltip } from "ant-design-vue";
import { ExclamationCircleOutlined } from "@ant-design/icons-vue";
const BG0 = require("@/assets/studentUI_home/BG0@2x.png");
const BG1 = require("@/assets/studentUI_home/BG1@2x.png");
const BG2 = require("@/assets/studentUI_home/BG2@2x.png");
const BG3 = require("@/assets/studentUI_home/BG3@2x.png");
const BG4 = require("@/assets/studentUI_home/BG4@2x.png");
const BG5 = require("@/assets/studentUI_home/BG5@2x.png");
const BG6 = require("@/assets/studentUI_home/BG6@2x.png");
const BG7 = require("@/assets/studentUI_home/BG7@2x.png");
const BG8 = require("@/assets/studentUI_home/BG8@2x.png");
const BG9 = require("@/assets/studentUI_home/BG9@2x.png");
const BG11 = require("@/assets/studentUI_home/BG11@2x.png");

const bgArr = [BG0, BG1, BG2, BG3, BG4, BG5, BG6, BG7, BG8, BG9, BG11];

const tab = ref(1);
const changeTab = (e: number) => {
  tab.value = e;
  page.value = 1;
  list.value = [];
  onList();
};

function getRandomInteger(min: number, max: number) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

const appointRef = ref();
const loading = ref(false);
const isResult = ref(false);
const list = ref<Recordable[]>([]);
const total = ref(0);
const page = ref(1);
const serverTime = ref(dayjs());

const onList = () => {
  loading.value = true;
  isResult.value = false;
  if (tab.value == 1) {
    getAppointmentList({
      page: page.value,
      size: 6,
    })
      .then((res) => {
        list.value = res?.data?.records || [];
        total.value = res?.data?.total || 0;
      })
      .finally(() => {
        loading.value = false;
        isResult.value = true;
      });
  } else if (tab.value == 2) {
    getAssessmentTodo({
      page: page.value,
      size: 6,
    })
      .then((res) => {
        list.value = res?.data?.records || [];
        total.value = res?.data?.total || 0;
      })
      .finally(() => {
        loading.value = false;
        isResult.value = true;
      });
  } else if (tab.value == 3) {
    getAssessmentAnalysis({
      page: page.value,
      size: 6,
    })
      .then((res) => {
        list.value = res?.data?.records || [];
        total.value = res?.data?.total || 0;
      })
      .finally(() => {
        loading.value = false;
        isResult.value = true;
      });
  } else {
    getAssessmentDone({
      page: page.value,
      size: 6,
    })
      .then((res) => {
        list.value = res?.data?.records || [];
        total.value = res?.data?.total || 0;
      })
      .finally(() => {
        loading.value = false;
        isResult.value = true;
      });
  }
  getMachineTime().then((res) => {
    if (res?.data) {
      serverTime.value = dayjs(res.data, "YYYY-MM-DD HH:mm:ss");
    }
  });
};

const router = useRouter();

const startFn = (e: Recordable) => {
  if (serverTime.value?.isBefore(dayjs(e?.timePeriod))) return;
  if (e?.defense && e?.question) {
    if (e?.finishDefense) {
      router.push(`/question?id=${e?.id}`);
    } else {
      router.push(`/dashboard?id=${e?.id}`);
    }
  } else if (e?.defense) {
    if (!e?.finishDefense) {
      router.push(`/dashboard?id=${e?.id}`);
    } else {
      router.push({
        name: "ResultOverPage",
      });
    }
  } else {
    router.push(`/question?id=${e?.id}`);
  }
};

const toAppoint = (e: Record<string, any>) => {
  appointRef.value?.openModal(e);
};

const toDetail = (e: Record<string, any>) => {
  if (e?.state == 2) {
    router.push(`/studyDetail?id=${e?.id}`);
  }
  if (e?.state == 3 || e?.state == 5) {
    return;
  }
  if (e?.state == 4) {
    toAppoint(e);
  }
};

onMounted(() => {
  onList();
});

const preFn = () => {
  if (page.value <= 1) return;
  page.value -= 1;
  onList();
};
const nextFn = () => {
  if (page.value * 6 >= total.value) return;
  page.value += 1;
  onList();
};

const cancelSubscribe = (e: Recordable) => {
  Modal.confirm({
    title: "确认解除预约吗？",
    icon: createVNode(ExclamationCircleOutlined, { class: "!text-[#1677FF]" }),
    okText: "解除预约",
    onOk: async () => {
      await cancelAppointment(e?.id);
      message.success("已解除预约");
      onList();
    },
  });
};
</script>
<style scoped>
.dyy {
  background-size: 100% 100%;
  background-repeat: no-repeat;
  cursor: pointer;
}
.dyy_no {
  background-image: url(@/assets/studentUI_text/dyy.png);
  width: 72px;
  height: 26px;
}
.dyy_no:hover {
  background-image: url(@/assets/studentUI_text/dyy_selected.png);
}
.dyy_selected {
  background-image: url(@/assets/studentUI_text/dyy_selected.png);
  width: 120px;
  height: 45px;
}

.dwc {
  background-size: 100% 100%;
  background-repeat: no-repeat;
  cursor: pointer;
  margin-bottom: 8px;
}
.dwc_no {
  background-image: url(@/assets/studentUI_text/dwc.png);
  width: 72px;
  height: 26px;
}
.dwc_no:hover {
  background-image: url(@/assets/studentUI_text/dwc_selected.png);
}
.dwc_selected {
  background-image: url(@/assets/studentUI_text/dwc_selected.png);
  width: 120px;
  height: 45px;
}

.pgz {
  background-size: 100% 100%;
  background-repeat: no-repeat;
  cursor: pointer;
  margin-bottom: 8px;
}
.pgz_no {
  background-image: url(@/assets/studentUI_text/pgz.png);
  width: 72px;
  height: 26px;
}
.pgz_no:hover {
  background-image: url(@/assets/studentUI_text/pgz_selected.png);
}
.pgz_selected {
  background-image: url(@/assets/studentUI_text/pgz_selected.png);
  width: 120px;
  height: 45px;
}

.ywc {
  background-size: 100% 100%;
  background-repeat: no-repeat;
  cursor: pointer;
}
.ywc_no {
  background-image: url(@/assets/studentUI_text/yjs.png);
  width: 72px;
  height: 26px;
}
.ywc_no:hover {
  background-image: url(@/assets/studentUI_text/yjs_selected.png);
}
.ywc_selected {
  background-image: url(@/assets/studentUI_text/yjs_selected.png);
  width: 120px;
  height: 45px;
}

.actionBtn {
  width: 138px;
  height: 52px;
  background: rgba(255, 255, 255, 0.7);
  box-shadow: 0px 18px 30px 0px rgba(0, 0, 0, 0.05);
  border-radius: 507px 507px 507px 507px;
  padding: 4px;
}
.actionBtn > .disabled-actionBtn {
  background: linear-gradient(90deg, #cbcfd6 7%, #b7babe 100%);
  box-shadow: 0px 12px 24px 0px rgba(0, 0, 0, 0.1),
    inset 0px 2px 1px 0px rgba(255, 255, 255, 0.4),
    inset 0px -2px 1px 0px rgba(0, 0, 0, 0.2);
  border-radius: 701px 701px 701px 701px;
  width: 100%;
  height: 100%;
}
.actionBtn > .actionBtn-in {
  background: linear-gradient(90deg, #92bfff 1%, #0068fa 100%);
  box-shadow: 0px 12px 24px 0px rgba(0, 0, 0, 0.1),
    inset 0px 2px 1px 0px rgba(255, 255, 255, 0.4),
    inset 0px -2px 1px 0px rgba(0, 0, 0, 0.2);
  border-radius: 701px 701px 701px 701px;
  width: 100%;
  height: 100%;
}

.card {
  cursor: pointer;
  transition: transform 0.6s ease; /* 设置动画时间 */
  border: 1px solid rgba(255, 255, 255, 0.6);
  box-shadow: 0px 20px 60px 0px rgba(55, 57, 65, 0.15);
}
.card:hover {
  transform: rotate(0deg) translateY(-18px) !important;
  z-index: 100;
  box-shadow: 0px 20px 60px 0px rgba(55, 57, 65, 0.3);
}

.content {
  width: 100%;
  background: linear-gradient(
    135deg,
    rgba(255, 255, 255, 0.4) 3%,
    rgba(255, 255, 255, 0.6) 100%
  );
  box-shadow: 0px 8px 16px 0px rgba(0, 0, 0, 0.04);
  border-radius: 16px 16px 16px 16px;
  border: 1px solid rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  padding: 16px 24px;
}
.yyBtn {
  width: 48px;
  height: 48px;
  background: linear-gradient(
    135deg,
    rgba(255, 255, 255, 0.4) 3%,
    rgba(255, 255, 255, 0.7) 100%
  );
  box-shadow: 0px 8px 16px 0px rgba(0, 0, 0, 0.04);
  border-radius: 16px 16px 16px 16px;
  border: 1px solid rgba(255, 255, 255, 0.6);
  cursor: pointer;
}
.columnContent {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.columnContent > .label {
  font-weight: 400;
  font-size: 16px;
  color: #7a8298;
}
.columnContent > .text {
  font-weight: 500;
  font-size: 16px;
  color: #363f50;
}
</style>
