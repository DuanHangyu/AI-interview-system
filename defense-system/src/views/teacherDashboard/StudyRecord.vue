<template>
  <div class="size-full flex flex-col gap-y-4">
    <section class="bg-white rounded-lg p-4 flex-shrink-0">
      <Form
        layout="inline"
        ref="formRef"
        class="grid grid-cols-4 gap-2"
        :model="params"
        :label-col="{ style: { width: 100, flexShrink: 0 } }"
      >
        <FormItem label="学生姓名" name="studentName">
          <Input placeholder="请输入" v-model:value="params.studentName" />
        </FormItem>
        <FormItem label="考核名称" name="theme">
          <Input placeholder="请输入" v-model:value="params.theme" />
        </FormItem>
        <!-- <FormItem label="考核模式" name="username">
          <Select
            v-model:value="params.projectType"
            placeholder="请选择"
            :options="projectTypes"
            :fieldNames="{ label: 'projectType', value: 'id' }"
          />
        </FormItem> -->
        <FormItem>
          <Button class="w-[100px] mr-2" type="primary" @click="searchFn">
            查询
          </Button>
          <Button class="w-[100px]" @click="resetForm">重置</Button>
        </FormItem>
      </Form>
    </section>
    <section class="bg-white rounded-lg p-5 flex-grow flex flex-col">
      <Button type="primary" class="mb-4 flex-shrink-0 w-fit" @click="exportFn">
        导出学生报告
      </Button>
      <div class="w-full flex-grow" ref="tableContainer">
        <Table
          :columns="columns"
          :row-selection="{
            selectedRowKeys: selectedRowKeys,
            onChange: onSelectChange,
          }"
          :data-source="dataSource"
          class="size-full"
          :pagination="{
            total: total,
            current: params.page,
            pageSize: params.size,
            pageSizeOptions: [20, 50, 100],
            showQuickJumper: true,
            showTotal: (t) => `共 ${t} 条`,
          }"
          :scroll="{
            y: tableHeight,
          }"
          row-key="id"
          @change="changeFn"
          :loading="loading"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'score'">
              <div
                class="size-11 rounded-full bg-[#1677FF] flex items-center justify-center text-white"
              >
                <span class="font-semibold text-lg">
                  {{ record?.score || 0 }}
                </span>
                <span class="font-semibold text-[11px] pt-[5px]">分</span>
              </div>
            </template>
            <template v-if="column.key === 'checkScore'">
              <div class="flex items-center space-x-[10px]">
                <span>{{ record?.checkScore || record?.score || 0 }}分</span>
                <EditOutlined
                  class="cursor-pointer"
                  @click="() => updateScore(record)"
                />
              </div>
            </template>
            <template v-if="column.key === 'action'">
              <div class="flex items-center space-x-2">
                <Button type="primary" @click="goDetail(record)">
                  查看详情
                </Button>
                <Button
                  type="primary"
                  danger
                  @click="retakeFn(record)"
                  v-if="record?.state != 3"
                >
                  重考
                </Button>
              </div>
            </template>
          </template>
        </Table>
      </div>
    </section>
    <UpdateTeacherScoreModal ref="updateTeacherScore" @success="onLoad" />
  </div>
</template>
<script lang="ts" setup>
import {
  Form,
  Table,
  FormItem,
  Input,
  Button,
  Select,
  TablePaginationConfig,
  message,
  Modal,
} from "ant-design-vue";
import { createVNode, nextTick, onMounted, onUnmounted, ref } from "vue";
import { EditOutlined, ExclamationCircleOutlined } from "@ant-design/icons-vue";
import UpdateTeacherScoreModal from "./components/UpdateTeacherScoreModal.vue";
import { useRouter } from "vue-router";
import {
  exportStudentRecord,
  getStudyRecordList,
  retakeApi,
} from "@/api/studyRecord";
import dayjs from "dayjs";
import { getProjectTypeList } from "@/api/projectType";
import saveAs from "file-saver";
defineOptions({
  name: "StudyRecord",
});

const loading = ref(false);
const router = useRouter();
const formRef = ref();
const updateTeacherScore = ref();
const params = ref({
  page: 1,
  size: 20,
  studentName: "",
  theme: "",
  projectType: undefined,
});
const total = ref(0);
const projectTypes = ref<Recordable[]>([]);

const columns = [
  {
    title: "序号",
    dataIndex: "index",
    key: "index",
    customRender: (e: { index: any }) => {
      return (params.value.page - 1) * params.value.size + e.index + 1;
    },
    width: 80,
  },
  {
    title: "学生姓名",
    dataIndex: "studentName",
    key: "studentName",
  },
  {
    title: "考核名称",
    dataIndex: "theme",
    key: "theme",
  },
  {
    title: "考核总分",
    dataIndex: "totalScore",
    key: "totalScore",
  },
  {
    title: "考核得分",
    dataIndex: "score",
    key: "score",
    customRender: (e: { value: any }) => {
      return e?.value == -1 ? "--" : e?.value;
    },
  },
  {
    title: "教师核分",
    key: "checkScore",
    dataIndex: "checkScore",
  },
  {
    title: "考核时间",
    key: "assessmentTime",
    dataIndex: "assessmentTime",
    customRender: (e: { record: Recordable }) => {
      return dayjs(e?.record?.assessmentTime)?.format("YYYY/MM/DD HH:mm");
    },
  },
  {
    title: "操作",
    key: "action",
    width: 200,
  },
];

const dataSource = ref([]);
const tableContainer = ref();
const tableHeight = ref(0);
const listenResize = () => {
  tableHeight.value = tableContainer.value?.offsetHeight - 55 - 64;
};

const onLoad = () => {
  loading.value = true;
  getStudyRecordList(params.value)
    .then((res) => {
      dataSource.value = res?.data?.records || [];
      total.value = res?.data?.total || 0;
    })
    .finally(() => {
      loading.value = false;
    });
};

const searchFn = () => {
  params.value.page = 1;
  onLoad();
};
const resetForm = () => {
  formRef.value.resetFields();
  params.value.page = 1;
  onLoad();
};

const changeFn = (e: TablePaginationConfig) => {
  params.value.page = e?.current || 1;
  params.value.size = e?.pageSize || 20;
  onLoad();
};

onMounted(() => {
  onLoad();
  getProjectTypeList().then((res) => {
    projectTypes.value = res?.data || [];
  });
  nextTick(() => {
    listenResize();
  });
  window.addEventListener("resize", listenResize);
});

onUnmounted(() => {
  window.removeEventListener("resize", listenResize);
});

const updateScore = (e: Recordable) => {
  updateTeacherScore.value?.openModal(e || {});
};

const goDetail = (e: Record<string, any>) => {
  router.push(`/studyDetail?id=${e?.id}`);
};

const selectedRowKeys = ref<Array<string | number>>([]);
const onSelectChange = (keys: Array<string | number>) => {
  selectedRowKeys.value = keys;
};

const exportFn = () => {
  if (!selectedRowKeys.value?.length) {
    message.warning("请先选择数据");
    return;
  }
  exportStudentRecord({
    recordIds: selectedRowKeys.value,
  }).then((response) => {
    const { data, headers } = response;
    const contentDisposition = headers["content-disposition"];
    let organFileName = "";
    try {
      if (contentDisposition) {
        const fileNameMatch = contentDisposition.match(
          /filename\*=UTF-8''([^;]+)/i
        );
        if (fileNameMatch && fileNameMatch.length > 1) {
          organFileName = decodeURIComponent(fileNameMatch[1]);
        }
      }
    } catch (error) {}
    const blob = new Blob([data]);
    saveAs(blob, organFileName || "考核记录报告.xlsx");
    selectedRowKeys.value = [];
  });
};

const retakeFn = (e: Recordable) => {
  Modal.confirm({
    title: "确认重考吗？",
    icon: createVNode(ExclamationCircleOutlined),
    okText: "重考",
    okButtonProps: {
      type: "primary",
    },
    onOk: async () => {
      await retakeApi({
        recordId: e?.id,
      });
      message.success("已修改");
      onLoad();
    },
  });
};
</script>
<style scoped>
:deep(.ant-form-item-row) {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
}
</style>
