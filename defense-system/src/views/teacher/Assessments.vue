<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">面试管理</h1>
        <p class="page-desc">配置面试流程、评分标准与题库；在“考场”中安排考试场次、查看考试进度</p>
      </div>
      <Button type="primary" @click="router.push('/assessments/new')">
        <template #icon><PlusOutlined /></template>
        新建面试
      </Button>
    </div>

    <div class="card">
      <div class="toolbar">
        <InputSearch
          v-model:value="theme"
          placeholder="搜索面试名称"
          allow-clear
          class="w-[280px]"
          @search="search"
        />
      </div>
      <Table
        size="middle"
        row-key="id"
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :pagination="{
          current: page,
          pageSize: size,
          total,
          showSizeChanger: true,
          showTotal: (t: number) => `共 ${t} 条`,
        }"
        :scroll="{ x: 1080 }"
        @change="onTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'theme'">
            <a class="font-medium" @click="router.push(`/assessments/${record.id}/edit`)">{{ record.theme }}</a>
          </template>
          <template v-else-if="column.key === 'flow'">
            <Tag v-if="record.defense" :bordered="false" color="blue">答辩 {{ minutes(record.duration) }}</Tag>
            <Tag v-if="record.question" :bordered="false" color="cyan">问答 {{ record.questionCount || 0 }} 题</Tag>
            <Tag v-if="record.question && record.followUp" :bordered="false">含追问</Tag>
          </template>
          <template v-else-if="column.key === 'bank'">
            <template v-if="record.question">
              <span class="num">{{ record.questions?.length || 0 }}</span> 题
              <span class="muted">，每人抽 {{ record.questionCount || 0 }}</span>
            </template>
            <span v-else class="muted">—</span>
          </template>
          <template v-else-if="column.key === 'students'">
            <span class="num">{{ record.participatingStudents?.length || 0 }}</span> 人
          </template>
          <template v-else-if="column.key === 'appoint'">
            <Badge v-if="record.needAppoint" status="processing" :text="`${slotCount(record)} 个场次`" />
            <Badge v-else status="default" text="不分场次" />
          </template>
          <template v-else-if="column.key === 'score'">
            <span class="num">{{ record.totalScore ?? "—" }}</span>
            <span class="muted"> / 及格 {{ record.passScore ?? "—" }}</span>
          </template>
          <template v-else-if="column.key === 'result'">
            <span :class="record.showResult ? '' : 'muted'">{{ record.showResult ? "可查看" : "不可查看" }}</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <Space :size="4">
              <Button type="link" size="small" @click="router.push(`/assessments/${record.id}/exam`)">考场</Button>
              <Button type="link" size="small" @click="router.push(`/assessments/${record.id}/edit`)">编辑</Button>
              <Popconfirm
                title="删除后，该面试的题库和所有学生作答记录都会被删除，确定删除吗？"
                ok-text="删除"
                :ok-button-props="{ danger: true }"
                @confirm="remove(record)"
              >
                <Button type="link" size="small" danger>删除</Button>
              </Popconfirm>
            </Space>
          </template>
        </template>
        <template #emptyText>
          <Empty description="暂无面试，点击右上角新建" />
        </template>
      </Table>
    </div>

  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { Badge, Button, Empty, InputSearch, Popconfirm, Space, Table, Tag, message } from "ant-design-vue";
import { PlusOutlined } from "@ant-design/icons-vue";
import { getAssessmentList, removeAssessment } from "@/api/assessment";

const router = useRouter();
const theme = ref("");
const page = ref(1);
const size = ref(10);
const total = ref(0);
const rows = ref<Record<string, any>[]>([]);
const loading = ref(false);

const columns = [
  { title: "面试名称", key: "theme", ellipsis: true, width: 220 },
  { title: "流程", key: "flow", width: 190 },
  { title: "题库", key: "bank", width: 130 },
  { title: "参与学生", key: "students", width: 90 },
  { title: "场次", key: "appoint", width: 100 },
  { title: "满分 / 及格", key: "score", width: 110 },
  { title: "学生报告", key: "result", width: 90 },
  { title: "操作", key: "action", width: 200, fixed: "right" as const },
];

function minutes(seconds?: number) {
  if (!seconds) return "";
  return seconds % 60 === 0 ? `${seconds / 60} 分钟` : `${seconds} 秒`;
}

function slotCount(record: Record<string, any>) {
  return record.appointmentSetting?.timePeriods?.length || 0;
}

async function load() {
  loading.value = true;
  try {
    const res = await getAssessmentList({ theme: theme.value || undefined, page: page.value, size: size.value });
    rows.value = res?.data?.records || [];
    total.value = res?.data?.total || 0;
  } finally {
    loading.value = false;
  }
}

function search() {
  page.value = 1;
  load();
}

function onTableChange(pagination: { current?: number; pageSize?: number }) {
  page.value = pagination.current || 1;
  size.value = pagination.pageSize || 10;
  load();
}

async function remove(record: Record<string, any>) {
  await removeAssessment({ id: record.id });
  message.success("已删除");
  if (rows.value.length === 1 && page.value > 1) page.value -= 1;
  load();
}

onMounted(load);
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 16px 12px;
}
</style>
