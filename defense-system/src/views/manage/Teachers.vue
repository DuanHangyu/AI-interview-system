<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">教师管理</h1>
        <p class="page-desc">管理教师账号。教师可以创建面试、维护题库和复核学生成绩</p>
      </div>
      <Button type="primary" @click="openEdit()">
        <template #icon><PlusOutlined /></template>
        新增教师
      </Button>
    </div>

    <div class="card">
      <div class="toolbar">
        <Space wrap>
          <Input v-model:value="query.name" placeholder="姓名" allow-clear class="w-[160px]" @press-enter="search" />
          <Input v-model:value="query.account" placeholder="账号" allow-clear class="w-[160px]" @press-enter="search" />
          <Button type="primary" @click="search">查询</Button>
          <Button @click="reset">重置</Button>
        </Space>
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
        @change="onTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'phone'">{{ record.phone || "—" }}</template>
          <template v-else-if="column.key === 'action'">
            <Space :size="4">
              <Button type="link" size="small" @click="openEdit(record)">编辑</Button>
              <Popconfirm
                title="确定删除该教师账号吗？"
                ok-text="删除"
                :ok-button-props="{ danger: true }"
                @confirm="remove(record)"
              >
                <Button type="link" size="small" danger>删除</Button>
              </Popconfirm>
            </Space>
          </template>
        </template>
        <template #emptyText><Empty description="暂无教师" /></template>
      </Table>
    </div>

    <Modal
      v-model:open="editOpen"
      :title="editing.id ? '编辑教师' : '新增教师'"
      :confirm-loading="saving"
      ok-text="保存"
      destroy-on-close
      @ok="save"
    >
      <Form ref="formRef" layout="vertical" :model="editing" class="pt-2">
        <FormItem label="姓名" name="name" :rules="[{ required: true, whitespace: true, message: '请输入姓名' }]">
          <Input v-model:value="editing.name" :maxlength="30" placeholder="教师姓名" />
        </FormItem>
        <FormItem label="账号" name="account" :rules="[{ required: true, whitespace: true, message: '请输入账号' }]">
          <Input v-model:value="editing.account" :maxlength="40" placeholder="登录账号，如工号" />
        </FormItem>
        <FormItem
          label="手机号"
          name="phone"
          :rules="[
            { required: true, message: '请输入手机号' },
            { pattern: /^1\d{10}$/, message: '请输入 11 位手机号' },
          ]"
        >
          <Input v-model:value="editing.phone" :maxlength="11" placeholder="11 位手机号" />
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import {
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
  message,
} from "ant-design-vue";
import { PlusOutlined } from "@ant-design/icons-vue";
import { createTeacher, deleteTeacher, getTeacherPageList, modifyTeacher } from "@/api/teacher";

const query = reactive({ name: "", account: "" });
const page = ref(1);
const size = ref(10);
const total = ref(0);
const rows = ref<Record<string, any>[]>([]);
const loading = ref(false);

const columns = [
  { title: "姓名", dataIndex: "name", key: "name", width: 160 },
  { title: "账号", dataIndex: "account", key: "account", width: 180 },
  { title: "手机号", key: "phone", width: 160 },
  { title: "创建时间", dataIndex: "createTime", key: "createTime", width: 180 },
  { title: "操作", key: "action", width: 140 },
];

async function load() {
  loading.value = true;
  try {
    const res = await getTeacherPageList({
      name: query.name.trim() || undefined,
      account: query.account.trim() || undefined,
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
  Object.assign(query, { name: "", account: "" });
  search();
}

function onTableChange(pagination: { current?: number; pageSize?: number }) {
  page.value = pagination.current || 1;
  size.value = pagination.pageSize || 10;
  load();
}

async function remove(record: Record<string, any>) {
  await deleteTeacher({ id: record.id });
  message.success("已删除");
  if (rows.value.length === 1 && page.value > 1) page.value -= 1;
  load();
}

const editOpen = ref(false);
const saving = ref(false);
const formRef = ref();
const editing = reactive<Record<string, any>>({});

function openEdit(record?: Record<string, any>) {
  Object.assign(editing, {
    id: record?.id,
    name: record?.name || "",
    account: record?.account || "",
    phone: record?.phone || "",
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
      phone: editing.phone.trim(),
      // 编辑时留空表示不修改密码
      password: editing.password || undefined,
    };
    if (editing.id) await modifyTeacher(params);
    else await createTeacher(params);
    message.success("已保存");
    editOpen.value = false;
    load();
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.toolbar {
  padding: 16px 16px 12px;
}
</style>
