<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">学生管理</h1>
        <p class="page-desc">
          {{ isAdmin ? "管理全部学生账号" : "管理参加你的面试的学生账号" }}，支持 Excel 批量导入和导出
        </p>
      </div>
      <Space>
        <Button @click="importOpen = true">
          <template #icon><ImportOutlined /></template>
          批量导入
        </Button>
        <Button type="primary" @click="openEdit()">
          <template #icon><PlusOutlined /></template>
          新增学生
        </Button>
      </Space>
    </div>

    <div class="card">
      <div class="toolbar">
        <Space wrap>
          <Input v-model:value="query.name" placeholder="姓名" allow-clear class="w-[160px]" @press-enter="search" />
          <Input v-model:value="query.account" placeholder="学号" allow-clear class="w-[160px]" @press-enter="search" />
          <Input v-model:value="query.schoolClass" placeholder="班级" allow-clear class="w-[160px]" @press-enter="search" />
          <Button type="primary" @click="search">查询</Button>
          <Button @click="reset">重置</Button>
        </Space>
        <Space>
          <span v-if="selected.length" class="muted text-[13px]">已选 {{ selected.length }} 项</span>
          <Button :loading="exporting" @click="exportRows">
            <template #icon><ExportOutlined /></template>
            {{ selected.length ? "导出选中" : "导出全部" }}
          </Button>
        </Space>
      </div>
      <Table
        size="middle"
        row-key="id"
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :row-selection="{ selectedRowKeys: selected, onChange: (keys: any[]) => (selected = keys) }"
        :pagination="{
          current: page,
          pageSize: size,
          total,
          showSizeChanger: true,
          showTotal: (t: number) => `共 ${t} 条`,
        }"
        @change="onTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'schoolClass'">
            {{ record.schoolClass || "—" }}
          </template>
          <template v-else-if="column.key === 'action'">
            <Space :size="4">
              <Button type="link" size="small" @click="openEdit(record)">编辑</Button>
              <Popconfirm
                title="删除学生会同时删除其所有面试记录，确定删除吗？"
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
          <Empty :description="isAdmin ? '暂无学生' : '暂无学生。学生被加入你的面试后会显示在这里'" />
        </template>
      </Table>
    </div>

    <!-- 新增 / 编辑 -->
    <Modal
      v-model:open="editOpen"
      :title="editing.id ? '编辑学生' : '新增学生'"
      :confirm-loading="saving"
      ok-text="保存"
      destroy-on-close
      @ok="save"
    >
      <Form ref="formRef" layout="vertical" :model="editing" class="pt-2">
        <FormItem label="姓名" name="name" :rules="[{ required: true, whitespace: true, message: '请输入姓名' }]">
          <Input v-model:value="editing.name" :maxlength="30" placeholder="学生姓名" />
        </FormItem>
        <FormItem
          label="学号"
          name="account"
          :rules="[{ required: true, whitespace: true, message: '请输入学号' }]"
          extra="学号即登录账号"
        >
          <Input v-model:value="editing.account" :maxlength="40" placeholder="学号" />
        </FormItem>
        <FormItem label="班级" name="schoolClass" :rules="[{ required: true, whitespace: true, message: '请输入班级' }]">
          <AutoComplete
            v-model:value="editing.schoolClass"
            :options="classOptions"
            placeholder="如：软件工程 2201"
            :filter-option="(input: string, option: any) => option.value.includes(input)"
          />
        </FormItem>
        <FormItem
          label="登录密码"
          name="password"
          :rules="editing.id ? [{ min: 6, message: '密码至少 6 位' }] : [{ required: true, message: '请输入登录密码' }, { min: 6, message: '密码至少 6 位' }]"
          :extra="editing.id ? '留空则不修改密码' : ''"
        >
          <InputPassword v-model:value="editing.password" :placeholder="editing.id ? '留空则不修改' : '至少 6 位'" autocomplete="new-password" />
        </FormItem>
      </Form>
    </Modal>

    <!-- 批量导入 -->
    <Modal
      v-model:open="importOpen"
      title="批量导入学生"
      :confirm-loading="importing"
      ok-text="导入"
      :ok-button-props="{ disabled: !importFile }"
      destroy-on-close
      @ok="doImport"
      @cancel="importFile = null"
    >
      <ol class="steps">
        <li>
          下载导入模板，按模板填写姓名、学号、密码、班级。
          <Button type="link" size="small" class="px-1" @click="downloadTpl">下载模板</Button>
        </li>
        <li>上传填写好的 Excel 文件。学号已存在时整批导入失败，请先检查重复。</li>
      </ol>
      <UploadDragger
        :file-list="importFile ? [importFile] : []"
        accept=".xlsx,.xls"
        :max-count="1"
        :before-upload="pickImport"
        @remove="clearImport"
      >
        <p class="text-2xl muted mb-2"><InboxOutlined /></p>
        <p>点击或拖拽 Excel 文件到此处</p>
        <p class="muted text-xs mt-1">支持 .xlsx、.xls</p>
      </UploadDragger>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import {
  AutoComplete,
  Button,
  Empty,
  Form,
  FormItem,
  Input,
  InputPassword,
  Modal,
  Popconfirm,
  Space,
  Table,
  UploadDragger,
  message,
} from "ant-design-vue";
import type { UploadFile } from "ant-design-vue";
import { ExportOutlined, ImportOutlined, InboxOutlined, PlusOutlined } from "@ant-design/icons-vue";
import { saveAs } from "file-saver";
import {
  createStudent,
  deleteStudent,
  downloadTemplate,
  exportStudent,
  getStudentPageList,
  importStudent,
  modifyStudent,
} from "@/api/student";
import { useAuthStore } from "@/stores/auth";

const auth = useAuthStore();
const isAdmin = computed(() => auth.role === "ADMIN");

const query = reactive({ name: "", account: "", schoolClass: "" });
const page = ref(1);
const size = ref(10);
const total = ref(0);
const rows = ref<Record<string, any>[]>([]);
const loading = ref(false);
const selected = ref<number[]>([]);

const columns = [
  { title: "姓名", dataIndex: "name", key: "name", width: 160 },
  { title: "学号", dataIndex: "account", key: "account", width: 180 },
  { title: "班级", key: "schoolClass", width: 200 },
  { title: "创建时间", dataIndex: "createTime", key: "createTime", width: 180 },
  { title: "操作", key: "action", width: 140 },
];

const classOptions = computed(() =>
  Array.from(new Set(rows.value.map((r) => r.schoolClass).filter(Boolean))).map((value) => ({ value }))
);

async function load() {
  loading.value = true;
  try {
    const res = await getStudentPageList({
      name: query.name.trim() || undefined,
      account: query.account.trim() || undefined,
      schoolClass: query.schoolClass.trim() || undefined,
      page: page.value,
      size: size.value,
    });
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

function reset() {
  Object.assign(query, { name: "", account: "", schoolClass: "" });
  search();
}

function onTableChange(pagination: { current?: number; pageSize?: number }) {
  page.value = pagination.current || 1;
  size.value = pagination.pageSize || 10;
  load();
}

async function remove(record: Record<string, any>) {
  await deleteStudent({ id: record.id });
  message.success("已删除");
  selected.value = selected.value.filter((id) => id !== record.id);
  if (rows.value.length === 1 && page.value > 1) page.value -= 1;
  load();
}

// ---------------- 新增 / 编辑 ----------------
const editOpen = ref(false);
const saving = ref(false);
const formRef = ref();
const editing = reactive<Record<string, any>>({});

function openEdit(record?: Record<string, any>) {
  for (const key of Object.keys(editing)) delete editing[key];
  Object.assign(editing, {
    id: record?.id,
    name: record?.name || "",
    account: record?.account || "",
    schoolClass: record?.schoolClass || "",
    password: "",
  });
  editOpen.value = true;
}

async function save() {
  await formRef.value.validate();
  saving.value = true;
  try {
    const params = {
      id: editing.id,
      name: editing.name.trim(),
      account: editing.account.trim(),
      schoolClass: editing.schoolClass.trim(),
      // 编辑时留空表示不修改密码
      password: editing.password || undefined,
    };
    if (editing.id) await modifyStudent(params);
    else await createStudent(params);
    message.success("已保存");
    editOpen.value = false;
    load();
  } finally {
    saving.value = false;
  }
}

// ---------------- 导入 / 导出 ----------------
function fileNameOf(headers: Record<string, any>, fallback: string) {
  const disposition: string = headers?.["content-disposition"] || "";
  const star = disposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (star) return decodeURIComponent(star[1]);
  const plain = disposition.match(/filename="?([^";]+)"?/i);
  return plain ? decodeURIComponent(plain[1]) : fallback;
}

async function downloadTpl() {
  const res: any = await downloadTemplate();
  saveAs(res.data, fileNameOf(res.headers, "学生导入模板.xlsx"));
}

const importOpen = ref(false);
const importing = ref(false);
const importFile = ref<UploadFile | null>(null);

function clearImport() {
  importFile.value = null;
  return true;
}

function pickImport(file: File) {
  importFile.value = { uid: String(Date.now()), name: file.name, originFileObj: file } as UploadFile;
  return false;
}

async function doImport() {
  if (!importFile.value?.originFileObj) return;
  importing.value = true;
  try {
    const data = new FormData();
    data.append("file", importFile.value.originFileObj as File);
    await importStudent(data as unknown as Record<string, any>);
    message.success("导入成功");
    importOpen.value = false;
    importFile.value = null;
    search();
  } finally {
    importing.value = false;
  }
}

const exporting = ref(false);
async function exportRows() {
  exporting.value = true;
  try {
    const res: any = await exportStudent(selected.value.length ? { ids: selected.value } : { all: true });
    saveAs(res.data, fileNameOf(res.headers, "学生信息.xlsx"));
  } finally {
    exporting.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding: 16px 16px 12px;
}

.steps {
  margin: 0 0 16px;
  padding-left: 20px;
  line-height: 28px;
  color: var(--c-text-2);
}
</style>
