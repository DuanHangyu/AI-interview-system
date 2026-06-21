<template>
  <Modal
    v-model:open="open"
    :title="detail?.id ? '编辑' : '新增'"
    @ok="submit"
    :confirm-loading="loading"
    :after-close="
      () => {
        formRef?.resetFields();
      }
    "
    :width="720"
    :bodyStyle="{
      maxHeight: 'calc(80vh - 100px)',
      overflowY: 'auto',
      padding: '0px 10px',
    }"
  >
    <div class="h-[1px] w-full absolute bg-[#D9D9D9] left-0"></div>
    <Form layout="vertical" :model="formState" class="pt-3" ref="formRef">
      <FormItem
        label="考核主题"
        name="theme"
        :rules="[{ required: true, message: '该项为必填项' }]"
      >
        <Input v-model:value="formState.theme" placeholder="请输入" />
      </FormItem>
      <FormItem
        label="考核要求"
        name="assessmentRequirements"
        :rules="[{ required: true, message: '该项为必填项' }]"
      >
        <Textarea
          v-model:value="formState.assessmentRequirements"
          :rows="6"
          placeholder="请输入"
        />
      </FormItem>
      <FormItem
        name="assessmentCriteria"
        :rules="[{ required: true, message: '该项为必填项' }]"
      >
        <template #label>
          <div class="flex items-center">
            <span>考核标准</span>
            <Button type="link" @click="useTemplate">使用示例</Button>
          </div>
        </template>
        <Textarea
          v-model:value="formState.assessmentCriteria"
          :rows="8"
          placeholder="请输入"
        />
      </FormItem>
      <FormItem
        label="考核总分"
        name="totalScore"
        :rules="[{ required: true, message: '该项为必填项' }]"
      >
        <InputNumber
          class="w-full"
          :min="0"
          :precision="0"
          v-model:value="formState.totalScore"
          placeholder="请输入"
        />
      </FormItem>
      <FormItem
        label="及格分"
        name="passScore"
        :rules="[{ required: true, message: '该项为必填项' }]"
      >
        <InputNumber
          class="w-full"
          :min="0"
          :precision="0"
          v-model:value="formState.passScore"
          placeholder="请输入"
        />
      </FormItem>
      <FormItem
        label="是否答辩"
        name="defense"
        :rules="[{ required: true, message: '该项为必填项' }]"
      >
        <Switch
          v-model:checked="formState.defense"
          :checked-value="true"
          :un-checked-value="false"
        />
      </FormItem>
      <FormItem
        label="答辩时长（秒）"
        name="duration"
        :rules="[{ required: true, message: '该项为必填项' }]"
        v-if="formState.defense"
      >
        <InputNumber
          class="w-full"
          v-model:value="formState.duration"
          placeholder="请输入"
        />
      </FormItem>
      <FormItem
        label="是否提问"
        name="question"
        :rules="[{ required: true, message: '该项为必填项' }]"
      >
        <Switch
          v-model:checked="formState.question"
          :checked-value="true"
          :un-checked-value="false"
        />
      </FormItem>
      <FormItem
        label="提问个数"
        name="questionCount"
        :rules="[{ required: true, message: '该项为必填项' }]"
        v-if="formState.question"
      >
        <InputNumber
          class="w-full"
          v-model:value="formState.questionCount"
          placeholder="请输入"
        />
      </FormItem>
      <FormItem
        label="每个提问回答时长（秒）"
        name="answerTime"
        :rules="[{ required: true, message: '该项为必填项' }]"
        v-if="formState.question"
      >
        <InputNumber
          class="w-full"
          v-model:value="formState.answerTime"
          placeholder="请输入"
        />
      </FormItem>
      <FormItem
        label="是否追问"
        name="followUp"
        :rules="[{ required: true, message: '该项为必填项' }]"
      >
        <Switch
          v-model:checked="formState.followUp"
          :checked-value="true"
          :un-checked-value="false"
        />
      </FormItem>
      <FormItem
        name="followUpPrompt"
        :rules="[{ required: true, message: '该项为必填项' }]"
        v-if="formState.followUp"
      >
        <template #label>
          <div class="flex items-center">
            <span>追问提示词</span>
            <Button type="link" @click="useTemplate3">使用示例</Button>
          </div>
        </template>
        <Textarea
          v-model:value="formState.followUpPrompt"
          :rows="6"
          placeholder="请输入"
        />
      </FormItem>
      <!-- <FormItem
        name="followUpStandards"
        :rules="[{ required: true, message: '该项为必填项' }]"
        v-if="formState.followUp"
      >
        <template #label>
          <div class="flex items-center">
            <span>追问标准</span>
            <Button type="link" @click="useTemplate2">使用示例</Button>
          </div>
        </template>
        <Textarea
          v-model:value="formState.followUpStandards"
          :rows="6"
          placeholder="请输入"
        />
      </FormItem> -->
      <FormItem
        label="是否显示结果"
        name="showResult"
        :rules="[{ required: true, message: '该项为必填项' }]"
      >
        <Switch
          v-model:checked="formState.showResult"
          :checked-value="true"
          :un-checked-value="false"
        />
      </FormItem>
      <FormItem
        label="考核文件"
        name="assessmentFiles"
        :rules="[]"
      >
        <UploadDragger
          name="file"
          :show-upload-list="{ showRemoveIcon: false, showPreviewIcon: false }"
          accept=".pdf"
          :headers="headers"
          :action="action"
          multiple
          @change="handleChange"
          v-model:file-list="formState.assessmentFiles"
        >
          <p class="ant-upload-drag-icon">
            <inbox-outlined></inbox-outlined>
          </p>
          <p class="ant-upload-text">点击或拖拽上传</p>
          <template #itemRender="{ originNode, file, actions }">
            <div class="flex items-center space-x-3 h-12">
              <!-- <span :style="file.status === 'error' ? 'color: red' : ''">
                {{ file?.name }}
              </span> -->
              <component :is="originNode" />
              <div
                class="flex items-center space-x-2 pt-2"
                v-if="file?.status == 'done'"
              >
                <a href="javascript:;" @click="previewFn(file)">
                  <EyeOutlined class="cursor-pointer hover:text-[#1677FF]" />
                </a>
                <a href="javascript:;" @click="downFile(file)">
                  <DownloadOutlined
                    class="cursor-pointer hover:text-[#1677FF]"
                  />
                </a>
                <a href="javascript:;" @click="actions?.remove">
                  <DeleteOutlined class="hover:text-red-500" />
                </a>
              </div>
            </div>
          </template>
        </UploadDragger>
      </FormItem>

      <FormItem
        label="选择班级/学生"
        name="classAndStudent"
        :rules="[{ required: true, message: '该项为必填项' }]"
      >
        <TreeSelect
          :tree-data="schoolClassAndStudent"
          :field-names="{ children: 'students' }"
          multiple
          placeholder="请选择"
          treeCheckable
          treeCheckStrictly
          v-model:value="formState.classAndStudent"
        />
      </FormItem>
      <FormItem label="邀请教师" name="inviteTeachers">
        <Select
          v-model:value="formState.inviteTeachers"
          placeholder="请选择"
          mode="multiple"
          :options="teacherList"
          :field-names="{ label: 'name', value: 'id' }"
        />
      </FormItem>
      <FormItem
        label="是否需要预约"
        name="needAppoint"
        :rules="[{ required: true, message: '该项为必填项' }]"
      >
        <Switch
          v-model:checked="formState.needAppoint"
          :checked-value="true"
          :un-checked-value="false"
        />
      </FormItem>
      <!-- <FormItem
        label="考试参与学生"
        name="allStudent"
        :rules="[{ required: true, message: '该项为必填项' }]"
      >
        <FormItemRest>
          <RadioGroup v-model:value="formState.allStudent">
            <Radio :value="true">全部学生</Radio>
            <Radio :value="false">自定义范围</Radio>
          </RadioGroup>
          <Select
            v-if="formState.allStudent === false"
            v-model:value="formState.participatingStudents"
            class="mt-2"
            placeholder="请选择"
            mode="multiple"
            :options="studentList"
            :field-names="{ label: 'name', value: 'id' }"
          >
            <template #option="{ name, id }">
              <div class="flex items-center space-x-2">
                <Checkbox
                  :checked="formState.participatingStudents?.includes(id)"
                />
                <span>{{ name }}</span>
              </div>
            </template>
          </Select>
        </FormItemRest>
      </FormItem> -->
    </Form>
    <Preview ref="previewRef" />
  </Modal>
</template>
<script setup lang="ts">
import {
  Modal,
  Form,
  FormItem,
  Input,
  Textarea,
  Switch,
  UploadDragger,
  Select,
  InputNumber,
  message,
  UploadChangeParam,
  Button,
  Space,
  TreeSelect,
  UploadProps,
} from "ant-design-vue";
import { onMounted, ref, watch } from "vue";
import {
  InboxOutlined,
  EyeOutlined,
  DownloadOutlined,
  DeleteOutlined,
} from "@ant-design/icons-vue";
import { createAssessment, modifyAssessment } from "@/api/assessment";
import { getToken } from "@/utils/auth";
import { getSchoolClassStudent, getStudentPageList } from "@/api/student";
import { getFileSign } from "@/api/common";
import axios from "axios";
import saveAs from "file-saver";
import Preview from "@/components/Preview/index.vue";
import { getAllTeachers } from "@/api/teacher";

const action = process.env.VUE_APP_BASE_API + "/file/upload";
const headers = ref({
  Authorization: `Bearer ${getToken()}`,
});

const emits = defineEmits(["success"]);
const open = ref<boolean>(false);
const detail = ref<Record<string, any>>({});
const formState = ref<Recordable>({});
const formRef = ref();
const loading = ref(false);
const studentList = ref<Recordable[]>([]);
const teacherList = ref<Recordable[]>([]);
const schoolClassAndStudent = ref<Recordable[]>([]);

const submit = () => {
  formRef.value
    .validate()
    .then(async () => {
      loading.value = true;
      let isUploading = false;
      let isNoPdf = false;
      for (
        let index = 0;
        index < formState.value?.assessmentFiles?.length;
        index++
      ) {
        const element = formState.value?.assessmentFiles?.[index];
        if (!element?.response?.data?.url) {
          isUploading = true;
        }
        if (
          element?.response?.data?.url &&
          !element?.response?.data?.url?.includes(".pdf")
        ) {
          isNoPdf = true;
        }
      }
      if (isUploading) {
        message.error("文件正在上传，请稍等...");
        return;
      }
      if (isNoPdf) {
        message.error("存在非pdf文件");
        return;
      }
      const schoolClassList = [];
      const studentIds = [];
      if (!formState.value?.inviteTeachers?.length) {
        delete formState.value?.inviteTeachers;
      }
      for (
        let index = 0;
        index < formState.value?.classAndStudent?.length;
        index++
      ) {
        const element = formState.value?.classAndStudent?.[index];
        if (element?.value?.includes("**student**__")) {
          studentIds.push(element?.value?.split("**student**__")?.[1]);
        }
        if (element?.value?.includes("**class**__")) {
          const id = element?.value?.split("**class**__")?.[1];
          const current = schoolClassAndStudent.value?.find(
            (item) => item?.schoolClass == id
          );
          const ids = current?.students?.map((item: { id: any }) => item?.id);
          schoolClassList.push({
            schoolClass: id,
            students: ids,
          });
        }
      }
      const params: Recordable = {
        ...formState.value,
        assessmentFiles: formState.value?.assessmentFiles
          ?.filter(
            (item: { response: { data: { url: any } } }) =>
              item?.response?.data?.url
          )
          ?.map(
            (item: {
              response: { data: { url: string; fileName: string } };
              size: number;
            }) => {
              return {
                fileUrl: item?.response?.data?.url,
                fileSize: item?.size,
                fileName: item?.response?.data?.fileName,
              };
            }
          ),
        studentIds,
        schoolClassList,
      };
      if (detail.value?.id) {
        await modifyAssessment({
          id: detail.value?.id,
          ...params,
        });
      } else {
        await createAssessment(params);
      }
      message.success("保存成功");
      emits("success");
      open.value = false;
    })
    .catch((error: any) => {
      console.log("error", error);
    })
    .finally(() => {
      loading.value = false;
    });
};

defineExpose({
  openModal: (e: Record<string, any> = {}) => {
    open.value = true;
    detail.value = e;
    formState.value = {
      theme: "",
      assessmentCriteria: "",
      duration: undefined,
      questionCount: undefined,
      answerTime: undefined,
      followUp: false,
      totalScore: undefined,
      passScore: undefined,
      defense: false,
      question: false,
      showResult: false,
      assessmentRequirements: "",
      followUpPrompt: "",
      // followUpStandards: "",
      assessmentFiles: [],
      allStudent: undefined,
      participatingStudents: [],
      classAndStudent: [],
      inviteTeachers: [],
      needAppoint: true,
    };
    Object.assign(formState.value, e as any);
    if (e?.participatingStudents?.length) {
      formState.value.participatingStudents = e?.participatingStudents?.map(
        (item: { id: number }) => item?.id
      );
    }
    if (e?.assessmentFiles?.length) {
      formState.value.assessmentFiles = e?.assessmentFiles?.map(
        (item: Recordable) => {
          return {
            uid: item?.id,
            name: item?.fileName,
            size: item?.fileSize,
            status: "done",
            response: {
              data: { url: item?.fileUrl, fileName: item?.fileName },
            },
            url: item?.fileUrl,
          };
        }
      );
    }

    if (e?.id) {
      const classList = e?.schoolClassList?.map(
        (item: any) => item?.schoolClass
      );
      const studentList = e?.students?.map((item: any) => item?.id);
      for (let index = 0; index < schoolClassAndStudent.value.length; index++) {
        const element = schoolClassAndStudent.value[index];
        if (classList.includes(element?.schoolClass)) {
          formState.value.classAndStudent.push(element);
        }
        for (let index2 = 0; index2 < element.students?.length; index2++) {
          const element2 = element.students?.[index2];
          if (studentList.includes(element2?.id)) {
            formState.value.classAndStudent.push(element2);
          }
        }
      }
      formState.value.inviteTeachers = e?.inviteTeachers?.map(
        (item: { id: any }) => item?.id
      );
    }

    getStudentPageList({ page: 1, size: 100000 }).then((res) => {
      studentList.value = res?.data?.records || [];
    });
  },
});

const handleChange = (info: UploadChangeParam) => {
  if (info.file.status !== "uploading") {
    console.log(info.file, info.fileList);
  }
  if (info.file.status === "done") {
  } else if (info.file.status === "error") {
    message.error(`上传失败`);
  }
};

const useTemplate = () => {
  formState.value.assessmentCriteria = `1. 劳动价值的主要内容与核心观点
1. 阐述劳动价值论的主要内容与核心观点
2. 评分标准：5-6分：准确界定“抽象劳动创造价值”“社会必要劳动时间”等概念，阐明“人的抽象劳动是价值创造的唯一源泉”是马克思劳动价值理论的核心逻辑；
3-4分：仅复述商品二因素或劳动二重性，未触及价值创造本质；
0-2分：概念混淆（如将使用价值与价值混同）

2. 分析劳动价值理论“过时论”观点的主要依据
1. 说明人工智能对劳动形态的影响，阐明劳动价值理论“过时论”观点的主要依据
2. 评分标准：5-6分：指出AI改变具体劳动形式，劳动价值理论“过时论”聚焦于技术变革对传统劳动价值理论的冲击，认为在智能化、自动化背景下，“人的抽象劳动是价值创造的唯一源泉”的核心逻辑被颠覆，马克思的劳动价值论难以充分阐释价值创造的新模式；
3-4分：仅描述技术现象或劳动形式的改变（如机器替代流水线工人，脑力劳动替代体力劳动），缺乏马克思主义政治经济学分析；
0-2分：未能将理论与实际关联，回答没有逻辑，不能阐明劳动价值理论“过时论”观点的主要依据

3. 辩证批判“过时论”
1. 辩证论证劳动价值论的“过时论”是否成立
2. 评分标准：大致答出此点得2分：指出“过时论”混淆了“价值创造”与“价值实现”或者“类人劳动”只是“赋予价值”而非创造价值；大致答出此点得3分：强调人工智能的“类人劳动”是人类劳动的延伸，人工智能与马克思当年描述的自动化机器一样都是生产工具，属于不变资本；数据、算法、算力等非物质生产要素，本质上仍然是人类具体劳动的产物。其价值创造显然离不开人类既有的物质和精神条件，也不可能在没有人类劳动的前提下自发形成；大致答出此点得3分：引用马克思主义政治经济学基本原理，说明人工智能时代的资本主义生产方式发生变化，但本质仍然是人的活劳动创造价值，资本增殖仍是依靠对活劳动的榨取，资本主义榨取剩余价值的本质没有变

4. 方法论与结论
1.结论需体现历史唯物主义方法论，辩证分析马克思劳动价值的当代价值
2.评分标准：4-5分：得出马克思主义劳动价值论未过时的结论，并辩证看待人工智能对社会历史发展的影响；2-3分：简单肯定/否定，缺乏矛盾分析；0-1分：脱离马克思主义立场，武断认为人工智能的出现否定或证伪了马克思劳动价值理论`;
};
const useTemplate2 = () => {
  formState.value.followUpStandards = `【面试官分析框架】

1. 回答分析 (Answer Analysis): 在你内心（不要直接输出），根据以下四点对候选人的回答进行评估：

完整性 (STAR原则): 回答是否清晰地描述了情境(S)、我的任务(T)、我的行动(A)和可量化的结果(R)？缺少了哪个环节？

深度 (Depth): 回答是停留在“做了什么”(What)，还是深入解释了“为什么这么做”(Why)和“具体怎么做到的”(How)？

具体性 (Specificity): 回答是使用了具体的例子和数据来支撑，还是充满了模糊、笼统的表述？

相关性 (Relevance): 回答是否精准地回应了我的问题核心？

2. 决策 (Decision): 基于你的分析，做出以下两种决策中的一种：

[追问]: 如果回答在上述任意一点上存在明显不足（例如，缺少结果、没有解释原因、过于笼统）。

[通过]: 如果回答结构完整、有深度、有细节，可以进入下一个话题。`;
};

const useTemplate3 = () => {
  formState.value.followUpPrompt = `若用户启用“追问”功能，可在题目后附加一个追问问题；该追问必须与原题在考查主题或知识范畴上保持相似，但不得以原题的答案、结论或事实判断为前提，不得存在逻辑因果、条件依赖、推导关系或隐含预设；`;
};

const downFile = (e: Recordable) => {
  getFileSign({ url: e?.response?.data?.url }).then((res) => {
    axios.get(res?.data, { responseType: "blob" }).then((res2) => {
      saveAs(res2?.data, e?.response?.data?.fileName);
    });
  });
};
const previewRef = ref();
const previewFn = (e: Recordable) => {
  previewRef.value?.openModal(e?.response?.data?.url);
};

onMounted(() => {
  getSchoolClassStudent().then((res) => {
    schoolClassAndStudent.value = res?.data || [];
    for (let index = 0; index < schoolClassAndStudent.value.length; index++) {
      const element = schoolClassAndStudent.value?.[index];
      element.value = `**class**__${element?.schoolClass}`;
      element.label = element?.schoolClass;
      for (let index2 = 0; index2 < element?.students?.length; index2++) {
        const element2 = element?.students[index2];
        element2.value = `**student**__${element2?.id}`;
        element2.label = element2.name;
      }
    }
  });
  getAllTeachers().then((res) => {
    teacherList.value = res?.data || [];
  });
});

watch(
  () => formState.value,
  () => {
    for (let index = 0; index < schoolClassAndStudent.value.length; index++) {
      const element = schoolClassAndStudent.value?.[index];
      const status = !!element.students?.find((item: { value: any }) =>
        formState.value.classAndStudent
          ?.map((ite: { value: any }) => ite.value)
          ?.includes(item?.value)
      );
      element.disabled = status;
      for (let index2 = 0; index2 < element?.students?.length; index2++) {
        const element2 = element?.students[index2];
        const status2 = formState.value.classAndStudent
          ?.map((ite: { value: any }) => ite.value)
          ?.includes(element?.value);
        element2.disabled = status2;
      }
    }
  },
  { deep: true, immediate: true }
);

const progress: UploadProps["progress"] = {
  strokeColor: {
    "0%": "#108ee9",
    "100%": "#87d068",
  },
  strokeWidth: 3,
  format: (percent) => `${parseFloat(percent?.toFixed(2) as string)}%`,
  class: "test",
};

</script>
<style scoped>
:deep(.ant-upload-list-item) {
  margin-top: 0px !important;
}
</style>
