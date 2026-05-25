<template>
  <div class="size-full flex flex-col gap-y-4">
    <section class="bg-white rounded-lg p-4 flex-shrink-0">
      <Form
        layout="inline"
        ref="formRef"
        class="flex gap-2"
        :model="params"
        :label-col="{ style: { width: 100, flexShrink: 0 } }"
      >
        <FormItem label="考核主题" name="theme">
          <Input placeholder="请输入" v-model:value="params.theme" />
        </FormItem>
        <FormItem>
          <Button class="w-[100px] mr-2" type="primary" @click="searchFn">
            查询
          </Button>
          <Button class="w-[100px]" @click="resetForm">重置</Button>
        </FormItem>
      </Form>
    </section>
    <section
      class="bg-white rounded-lg p-5 flex-grow flex flex-col overflow-hidden space-y-4"
    >
      <div class="flex space-x-2 flex-shrink-0">
        <Button type="primary" class="w-[100px]" @click="addConfig">
          新增
        </Button>
      </div>
      <div
        class="w-full flex-grow overflow-y-auto space-y-4 pb-6"
        v-loading="loading"
        :class="
          dataSource.length == 0 && isResult
            ? 'flex flex-col items-center justify-center'
            : ''
        "
      >
        <template v-if="dataSource.length">
          <Card v-for="(item, index) in dataSource" :key="item?.id">
            <template #title>
              <span>{{ item?.theme }}</span>
            </template>
            <template #extra>
              <div class="space-x-2">
                <Button type="primary" @click="() => updateConfig(item)">
                  修改
                </Button>
                <Button
                  type="primary"
                  ghost
                  @click="() => openSubscribe(item)"
                  v-if="item?.needAppoint"
                >
                  预约设置
                </Button>
                <Button type="primary" danger @click="() => delFn(item)">
                  删除
                </Button>
              </div>
            </template>
            <template #default>
              <div class="grid grid-cols-5 text-black gap-4">
                <section class="descrption">
                  <div class="descrption-label">考核总分：</div>
                  <div class="descrption-content">{{ item?.totalScore }}</div>
                </section>
                <section class="descrption">
                  <div class="descrption-label">及格分：</div>
                  <div class="descrption-content">{{ item?.passScore }}</div>
                </section>
                <section class="descrption">
                  <div class="descrption-label">是否答辩：</div>
                  <div class="descrption-content">
                    {{ item?.defense ? "是" : "否" }}
                  </div>
                </section>
                <section class="descrption" v-if="item?.defense">
                  <div class="descrption-label">答辩时长（秒）：</div>
                  <div class="descrption-content">{{ item?.duration }}</div>
                </section>
                <section class="descrption">
                  <div class="descrption-label">是否提问：</div>
                  <div class="descrption-content">
                    {{ item?.question ? "是" : "否" }}
                  </div>
                </section>
                <section class="descrption" v-if="item?.question">
                  <div class="descrption-label">提问个数：</div>
                  <div class="descrption-content">
                    {{ item?.questionCount }}
                  </div>
                </section>
                <section class="descrption" v-if="item?.question">
                  <div class="descrption-label">每个提问回答时长（秒）：</div>
                  <div class="descrption-content">{{ item?.answerTime }}</div>
                </section>
                <section class="descrption" v-if="item?.question">
                  <div class="descrption-label">是否追问：</div>
                  <div class="descrption-content">
                    {{ item?.followUp ? "是" : "否" }}
                  </div>
                </section>
                <section
                  class="descrption"
                  v-if="item?.question && item?.followUp"
                >
                  <div class="descrption-label">追问次数：</div>
                  <div class="descrption-content">
                    {{ item?.followUpCount }}
                  </div>
                </section>

                <!-- <section
                  class="descrption"
                  v-if="
                    item?.followUp && item?.followUpStandards && item?.question
                  "
                >
                  <div class="descrption-label">追问标准：</div>
                  <div
                    class="descrption-content !text-[#1677FF] cursor-pointer"
                    @click="() => openView(item?.followUpStandards, '追问标准')"
                  >
                    查看全部
                  </div>
                </section> -->
                <section class="descrption">
                  <div class="descrption-label">是否显示结果：</div>
                  <div class="descrption-content">
                    {{ item?.showResult ? "是" : "否" }}
                  </div>
                </section>
                <section class="descrption" v-if="item?.assessmentRequirements">
                  <div class="descrption-label">考核要求：</div>
                  <div
                    class="descrption-content !text-[#1677FF] cursor-pointer"
                    @click="
                      () => openView(item?.assessmentRequirements, '考核要求')
                    "
                  >
                    查看全部
                  </div>
                </section>
                <section class="descrption" v-if="item?.assessmentCriteria">
                  <div class="descrption-label">考核标准：</div>
                  <div
                    class="descrption-content !text-[#1677FF] cursor-pointer"
                    @click="
                      () => openView(item?.assessmentCriteria, '考核标准')
                    "
                  >
                    查看全部
                  </div>
                </section>
                <section class="descrption">
                  <div class="descrption-label">考试参与学生：</div>
                  <div
                    class="descrption-content !text-[#1677FF] cursor-pointer"
                    @click="() => viewOpenStudent(item)"
                  >
                    查看学生
                  </div>
                </section>
                <section class="descrption" v-if="item?.needAppoint">
                  <div class="descrption-label">是否设置预约：</div>
                  <div class="descrption-content">
                    <span>是</span>
                    <span
                      class="!text-[#1677FF] cursor-pointer pl-4"
                      @click="openSubscribeDetail(item)"
                    >
                      预约详情
                    </span>
                  </div>
                </section>
              </div>
            </template>
          </Card>
        </template>
        <Empty v-if="!dataSource.length && isResult" />
      </div>
      <div class="flex-shrink-0 flex justify-end">
        <Pagination
          show-quick-jumper
          :total="total"
          :show-total="(t) => `共 ${t} 条`"
          v-model:current="params.page"
          v-model:page-size="params.size"
        />
      </div>
    </section>
    <AssessmentCriteriaModal ref="assessmentCriteria" />
    <HandleAssessment ref="handleAssessmentRef" @success="onLoad" />
    <ViewStudentModal ref="viewRef" />
    <SubscribeSettingModal ref="subscribeRef" @success="onLoad" />
    <SubscribeDetailModal ref="subscribeDetailRef" />
  </div>
</template>
<script lang="ts" setup>
import {
  Form,
  Pagination,
  FormItem,
  Input,
  Button,
  Modal,
  Card,
  message,
  Empty,
} from "ant-design-vue";
import { createVNode, onMounted, ref, watch } from "vue";
import { ExclamationCircleOutlined } from "@ant-design/icons-vue";
import AssessmentCriteriaModal from "./components/AssessmentCriteriaModal.vue";
import HandleAssessment from "./components/HandleAssessment.vue";
import ViewStudentModal from "./components/ViewStudentModal.vue";
import { getAssessmentList, removeAssessment } from "@/api/assessment";
import SubscribeSettingModal from "./components/SubscribeSettingModal.vue";
import SubscribeDetailModal from "./components/SubscribeDetailModal.vue";

defineOptions({
  name: "AssessSetting",
});
const assessmentCriteria = ref();
const viewRef = ref();
const handleAssessmentRef = ref();
const formRef = ref();
const total = ref(0);
const params = ref({ page: 1, size: 20, theme: "" });
const loading = ref(false);
const dataSource = ref<Recordable[]>([]);
const isResult = ref(false);
const subscribeRef = ref();
const subscribeDetailRef = ref();

const searchFn = () => {
  params.value.page = 1;
  onLoad();
};
const resetForm = () => {
  formRef.value.resetFields();
  params.value.page = 1;
  onLoad();
};

const addConfig = () => {
  handleAssessmentRef.value?.openModal();
};
const updateConfig = (e: Recordable) => {
  handleAssessmentRef.value?.openModal(e);
};
const delFn = (e: Recordable) => {
  Modal.confirm({
    title: "确认删除吗？",
    icon: createVNode(ExclamationCircleOutlined),
    content: "此操作将永久删除，是否继续？",
    okText: "确认删除",
    okButtonProps: {
      type: "primary",
      danger: true,
    },
    onOk: async () => {
      await removeAssessment({
        id: e?.id,
      });
      message.success("已删除");
      onLoad();
    },
  });
};

const openView = (t: string, title: string) => {
  assessmentCriteria.value?.openModal(t, title);
};
const viewOpenStudent = (item: Recordable) => {
  viewRef.value?.openModal(item?.participatingStudents || []);
};

const onLoad = () => {
  loading.value = true;
  isResult.value = false;
  getAssessmentList(params.value)
    .then((res) => {
      dataSource.value = res?.data?.records || [];
      total.value = res?.data?.total || 0;
    })
    .finally(() => {
      loading.value = false;
      isResult.value = true;
    });
};
watch(
  () => [params.value.page, params.value?.size],
  () => {
    onLoad();
  }
);

onMounted(() => {
  onLoad();
});

const openSubscribe = (record: Recordable) => {
  subscribeRef.value?.openModal(record);
};
const openSubscribeDetail = (record: Recordable) => {
  subscribeDetailRef.value?.openModal(record);
};
</script>
<style scoped>
:deep(.ant-form-item-row) {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
}
.descrption {
  display: flex;
  align-items: center;
}
.descrption-label {
  font-weight: normal;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.45);
  flex-shrink: 0;
}
.descrption-content {
  font-weight: normal;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.88);
  flex-grow: 1;
  white-space: wrap;
  overflow: hidden;
}
</style>
