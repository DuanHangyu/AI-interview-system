<template>
  <div class="page editor">
    <div class="page-head">
      <div>
        <Breadcrumb class="mb-1">
          <BreadcrumbItem><router-link to="/assessments">面试管理</router-link></BreadcrumbItem>
          <BreadcrumbItem>{{ isEdit ? "编辑面试" : "新建面试" }}</BreadcrumbItem>
        </Breadcrumb>
        <h1 class="page-title">{{ isEdit ? form.theme || "编辑面试" : "新建面试" }}</h1>
      </div>
    </div>

    <Spin :spinning="loading">
      <Result v-if="notFound" status="404" title="面试不存在" sub-title="该面试可能已被删除，或你没有访问权限。">
        <template #extra><Button type="primary" @click="router.replace('/assessments')">返回列表</Button></template>
      </Result>

      <Form v-else ref="formRef" layout="vertical" :model="form" class="sections" :required-mark="true">
        <!-- 基本信息 -->
        <section class="card card-pad">
          <h2 class="sec-title">基本信息</h2>
          <FormItem label="面试名称" name="theme" :rules="[{ required: true, whitespace: true, message: '请输入面试名称' }]">
            <Input v-model:value="form.theme" :maxlength="100" placeholder="如：软件工程课程设计答辩" />
          </FormItem>
          <FormItem
            label="面试要求"
            name="assessmentRequirements"
            :rules="[{ required: true, whitespace: true, message: '请输入面试要求' }]"
            extra="向学生说明需要介绍的内容，也会作为 AI 出题和评分的背景"
          >
            <Textarea
              v-model:value="form.assessmentRequirements"
              :auto-size="{ minRows: 3, maxRows: 8 }"
              placeholder="如：介绍项目背景、系统设计、个人负责的模块和测试情况"
            />
          </FormItem>
          <FormItem label="考核材料" extra="可选，PDF 格式。AI 生成题目和评分时会参考材料内容" class="mb-0">
            <Upload
              v-model:file-list="files"
              name="file"
              accept=".pdf"
              :action="uploadAction"
              :headers="uploadHeaders"
              :before-upload="beforeUpload"
              @change="onFileChange"
            >
              <Button>
                <template #icon><UploadOutlined /></template>
                上传 PDF
              </Button>
            </Upload>
          </FormItem>
        </section>

        <!-- 面试流程 -->
        <section class="card card-pad">
          <h2 class="sec-title">面试流程</h2>
          <FormItem name="flow" :rules="[{ validator: validateFlow }]" class="mb-2">
            <FormItemRest>
            <div class="flow">
              <div class="flow-item" :class="{ 'is-on': form.defense }">
                <div class="flow-head">
                  <div>
                    <div class="flow-name">项目答辩</div>
                    <div class="muted text-xs mt-1">学生先完整介绍自己的项目</div>
                  </div>
                  <Switch v-model:checked="form.defense" />
                </div>
                <div v-if="form.defense" class="flow-body">
                  <span class="muted">答辩时长</span>
                  <InputNumber
                    v-model:value="form.durationMinutes"
                    :min="0.5"
                    :max="120"
                    :step="0.5"
                    :precision="1"
                    addon-after="分钟"
                    class="w-[160px]"
                  />
                </div>
              </div>
              <div class="flow-item" :class="{ 'is-on': form.question }">
                <div class="flow-head">
                  <div>
                    <div class="flow-name">随机问答</div>
                    <div class="muted text-xs mt-1">从题库中按维度均衡随机抽题</div>
                  </div>
                  <Switch v-model:checked="form.question" />
                </div>
                <div v-if="form.question" class="flow-body flex-wrap">
                  <span class="muted">每人抽题</span>
                  <InputNumber
                    v-model:value="form.questionCount"
                    :min="1"
                    :max="50"
                    :precision="0"
                    addon-after="题"
                    class="w-[120px]"
                  />
                  <span class="muted ml-3">每题作答</span>
                  <InputNumber
                    v-model:value="form.answerTime"
                    :min="10"
                    :max="1800"
                    :precision="0"
                    addon-after="秒"
                    class="w-[130px]"
                  />
                  <span class="ml-3 inline-flex items-center gap-2">
                    <Switch v-model:checked="form.followUp" size="small" />
                    <span>启用追问</span>
                    <Tooltip title="题库中填写了追问的题目，学生回答后会继续回答追问">
                      <QuestionCircleOutlined class="muted" />
                    </Tooltip>
                  </span>
                </div>
              </div>
            </div>
          </FormItemRest>
          </FormItem>
        </section>

        <!-- 题库 -->
        <section v-if="form.question" class="card card-pad">
          <div class="sec-head">
            <div>
              <h2 class="sec-title mb-0">题库</h2>
              <div class="muted text-[13px] mt-1">
                共 <span class="num">{{ questions.length }}</span> 题，每位学生随机抽取
                <span class="num">{{ form.questionCount || 0 }}</span> 题。题目语音会在保存后自动生成。
              </div>
            </div>
            <Space>
              <Button :loading="draftLoading" @click="openDraft">
                <template #icon><ThunderboltOutlined /></template>
                AI 生成草稿
              </Button>
              <Button @click="openBulk">批量粘贴</Button>
              <Button type="primary" ghost @click="openQuestion()">
                <template #icon><PlusOutlined /></template>
                添加题目
              </Button>
            </Space>
          </div>
          <FormItem name="questions" :rules="[{ validator: validateQuestions }]" class="mb-0 mt-4">
            <Table
              size="middle"
              row-key="__key"
              :columns="questionColumns"
              :data-source="questions"
              :pagination="false"
              :scroll="{ x: 960 }"
            >
              <template #bodyCell="{ column, record, index }">
                <template v-if="column.key === 'index'">
                  <span class="num muted">{{ index + 1 }}</span>
                </template>
                <template v-else-if="column.key === 'dimension'">
                  <Tag v-if="record.dimension" :bordered="false" color="red">{{ record.dimension }}</Tag>
                  <span v-else class="muted">未设置</span>
                </template>
                <template v-else-if="column.key === 'content'">
                  <div class="q-content">{{ record.content }}</div>
                  <div v-if="record.referenceAnswer" class="q-sub">参考答案：{{ record.referenceAnswer }}</div>
                  <div v-if="form.followUp && record.followUpQuestion" class="q-sub">追问：{{ record.followUpQuestion }}</div>
                </template>
                <template v-else-if="column.key === 'audio'">
                  <Tag v-if="audioState(record) === 'ready'" :bordered="false" color="success">已生成</Tag>
                  <Tag v-else-if="audioState(record) === 'pending'" :bordered="false" color="processing">生成中</Tag>
                  <Tag v-else :bordered="false">保存后生成</Tag>
                </template>
                <template v-else-if="column.key === 'action'">
                  <Space :size="0">
                    <Button type="link" size="small" @click="openQuestion(index)">编辑</Button>
                    <Button type="text" size="small" :disabled="index === 0" @click="move(index, -1)">
                      <template #icon><ArrowUpOutlined /></template>
                    </Button>
                    <Button type="text" size="small" :disabled="index === questions.length - 1" @click="move(index, 1)">
                      <template #icon><ArrowDownOutlined /></template>
                    </Button>
                    <Popconfirm title="确定删除这道题吗？" ok-text="删除" @confirm="questions.splice(index, 1)">
                      <Button type="link" size="small" danger>删除</Button>
                    </Popconfirm>
                  </Space>
                </template>
              </template>
              <template #emptyText>
                <Empty description="题库为空，可手动添加、批量粘贴或用 AI 生成草稿" />
              </template>
            </Table>
          </FormItem>
        </section>

        <!-- 评分设置 -->
        <section class="card card-pad">
          <h2 class="sec-title">评分设置</h2>
          <div class="grid grid-cols-2 gap-x-6 max-w-[520px]">
            <FormItem label="满分" name="totalScore" :rules="[{ required: true, message: '请输入满分' }]">
              <InputNumber v-model:value="form.totalScore" :min="1" :max="1000" :precision="0" class="w-full" />
            </FormItem>
            <FormItem label="及格分" name="passScore" :rules="[{ required: true, validator: validatePass }]">
              <InputNumber v-model:value="form.passScore" :min="0" :max="1000" :precision="0" class="w-full" />
            </FormItem>
          </div>
          <FormItem
            label="评分标准"
            name="assessmentCriteria"
            :rules="[{ required: true, whitespace: true, message: '请输入评分标准' }]"
          >
            <Textarea
              v-model:value="form.assessmentCriteria"
              :auto-size="{ minRows: 5, maxRows: 14 }"
              placeholder="按维度写出评分标准，例如：&#10;技术实现（40分）：方案是否合理，是否有深度&#10;工程质量（30分）：测试是否充分，系统是否稳定&#10;表达能力（30分）：表达是否清晰，逻辑是否完整"
            />
            <template #extra>
              AI 按这里的维度逐项打分。维度写成“名称（分值）”的形式，报告中会显示每个维度的满分。题库中题目的“维度”建议与这里一致。
            </template>
          </FormItem>
          <FormItem class="mb-0">
            <div class="flex items-center gap-3">
              <Switch v-model:checked="form.showResult" />
              <span>学生可以查看面试报告</span>
              <span class="muted text-[13px]">关闭后，报告只有教师可见</span>
            </div>
          </FormItem>
        </section>

        <!-- 参与人员 -->
        <section class="card card-pad">
          <h2 class="sec-title">参与人员</h2>
          <FormItem
            label="参与学生"
            name="picked"
            :rules="[{ required: true, type: 'array', min: 1, message: '请选择参与学生' }]"
            extra="勾选整个班级时按班级分配，之后加入该班级的学生也会自动参加"
          >
            <TreeSelect
              v-model:value="form.picked"
              :tree-data="studentTree"
              tree-checkable
              :show-checked-strategy="SHOW_PARENT"
              tree-node-filter-prop="title"
              :max-tag-count="12"
              placeholder="选择班级或学生"
              allow-clear
              show-search
              class="w-full"
              :list-height="360"
            />
          </FormItem>
          <FormItem label="协作教师" name="inviteTeachers" extra="协作教师可以查看和管理这场面试" class="mb-0">
            <Select
              v-model:value="form.inviteTeachers"
              mode="multiple"
              :options="teacherOptions"
              option-filter-prop="label"
              placeholder="可选"
              allow-clear
              class="w-full"
            />
          </FormItem>
        </section>

        <!-- 预约 -->
        <section class="card card-pad">
          <h2 class="sec-title">预约</h2>
          <div class="flex items-center gap-3">
            <Switch v-model:checked="form.needAppoint" />
            <span>学生需要预约面试时段</span>
          </div>
          <div class="muted text-[13px] mt-2">
            {{
              form.needAppoint
                ? "保存后，在面试列表中点击“预约设置”添加时段。学生预约后在对应时段参加面试。"
                : "学生可以随时进入面试。"
            }}
          </div>
          <Alert
            v-if="isEdit && original.needAppoint && !form.needAppoint"
            class="mt-3"
            type="warning"
            show-icon
            message="关闭预约后，已设置的时段和学生的预约记录将被删除。"
          />
        </section>
      </Form>
    </Spin>

    <div v-if="!notFound" class="footer-bar">
      <div class="footer-inner">
        <Button @click="router.push('/assessments')">取消</Button>
        <Button type="primary" :loading="saving" :disabled="loading" @click="save">
          {{ isEdit ? "保存修改" : "创建面试" }}
        </Button>
      </div>
    </div>

    <!-- 题目编辑 -->
    <Modal
      v-model:open="questionOpen"
      :title="editingIndex === null ? '添加题目' : `编辑第 ${editingIndex + 1} 题`"
      :width="640"
      ok-text="确定"
      @ok="saveQuestion"
    >
      <Form ref="questionFormRef" layout="vertical" :model="editing" class="pt-2">
        <FormItem label="考核维度" name="dimension" extra="用于按维度均衡抽题，建议与评分标准中的维度一致">
          <AutoComplete
            v-model:value="editing.dimension"
            :options="dimensionOptions"
            placeholder="如：技术实现"
            :filter-option="filterDimension"
          />
        </FormItem>
        <FormItem label="题目" name="content" :rules="[{ required: true, whitespace: true, message: '请输入题目' }]">
          <Textarea v-model:value="editing.content" :auto-size="{ minRows: 3, maxRows: 8 }" placeholder="题目内容" />
        </FormItem>
        <FormItem label="参考答案" name="referenceAnswer" extra="可选。AI 评分时会对照参考答案的要点">
          <Textarea v-model:value="editing.referenceAnswer" :auto-size="{ minRows: 2, maxRows: 8 }" placeholder="答题要点" />
        </FormItem>
        <FormItem
          label="追问"
          name="followUpQuestion"
          class="mb-0"
          :extra="form.followUp ? '可选。学生回答本题后继续回答这个追问' : '当前未启用追问，需在面试流程中开启'"
        >
          <Input v-model:value="editing.followUpQuestion" :disabled="!form.followUp" placeholder="追问内容" />
        </FormItem>
      </Form>
    </Modal>

    <!-- 批量粘贴 -->
    <Modal v-model:open="bulkOpen" title="批量粘贴题目" :width="600" ok-text="添加" @ok="applyBulk">
      <div class="muted text-[13px] mb-2">每行一道题，空行会被忽略。添加后可再逐题补充维度和参考答案。</div>
      <Textarea v-model:value="bulkText" :rows="12" placeholder="每行一道题" />
      <div class="muted text-xs mt-2">将添加 {{ bulkLines.length }} 道题</div>
    </Modal>

    <!-- AI 草稿 -->
    <Modal
      v-model:open="draftOpen"
      title="AI 生成题库草稿"
      :width="760"
      :ok-text="draftItems.length ? `添加选中的 ${draftSelected.length} 题` : '生成'"
      :ok-button-props="{ disabled: draftItems.length > 0 && !draftSelected.length }"
      :confirm-loading="draftLoading"
      @ok="draftItems.length ? applyDraft() : generateDraft()"
    >
      <template v-if="!draftItems.length">
        <div class="muted text-[13px]">
          AI 会根据面试名称、面试要求、评分标准和考核材料生成题目、维度与参考答案。生成结果需要你审核后再添加。
        </div>
        <div class="flex items-center gap-3 mt-4">
          <span>生成数量</span>
          <InputNumber v-model:value="draftCount" :min="1" :max="30" :precision="0" addon-after="题" class="w-[140px]" />
        </div>
        <Spin v-if="draftLoading" class="block mt-6" tip="正在生成，通常需要 10～30 秒" />
      </template>
      <template v-else>
        <div class="flex items-center justify-between mb-3">
          <Checkbox
            :checked="draftSelected.length === draftItems.length"
            :indeterminate="draftSelected.length > 0 && draftSelected.length < draftItems.length"
            @change="toggleAllDraft"
            >全选</Checkbox
          >
          <Button type="link" size="small" :loading="draftLoading" @click="generateDraft">重新生成</Button>
        </div>
        <div class="draft-list">
          <label v-for="(item, i) in draftItems" :key="i" class="draft-item">
            <Checkbox :checked="draftSelected.includes(i)" @change="toggleDraft(i)" />
            <div class="min-w-0">
              <div>
                <Tag v-if="item.dimension" :bordered="false" color="red">{{ item.dimension }}</Tag>
                <span class="font-medium">{{ item.content }}</span>
              </div>
              <div v-if="item.referenceAnswer" class="q-sub">参考答案：{{ item.referenceAnswer }}</div>
              <div v-if="item.followUpQuestion" class="q-sub">追问：{{ item.followUpQuestion }}</div>
            </div>
          </label>
        </div>
      </template>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  Alert,
  AutoComplete,
  Breadcrumb,
  BreadcrumbItem,
  Button,
  Checkbox,
  Empty,
  Form,
  FormItem,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Result,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Tag,
  Textarea,
  Tooltip,
  TreeSelect,
  Upload,
  message, FormItemRest } from "ant-design-vue";
import type { UploadChangeParam, UploadFile } from "ant-design-vue";
import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  PlusOutlined,
  QuestionCircleOutlined,
  ThunderboltOutlined,
  UploadOutlined,
} from "@ant-design/icons-vue";
import { createAssessment, generateQuestionDraft, getAssessmentList, modifyAssessment } from "@/api/assessment";
import { getSchoolClassStudent } from "@/api/student";
import { getAllTeachers } from "@/api/teacher";
import { getToken } from "@/utils/auth";

interface Question {
  __key: string;
  id?: number;
  dimension: string;
  content: string;
  referenceAnswer: string;
  followUpQuestion: string;
  audioReady?: boolean;
  // 已保存题目的原始内容，用于判断语音是否需要重新生成
  savedContent?: string;
}

const SHOW_PARENT = TreeSelect.SHOW_PARENT;
const CLASS_PREFIX = "class:";
const STUDENT_PREFIX = "student:";

const route = useRoute();
const router = useRouter();
const editId = computed(() => (route.params.id ? Number(route.params.id) : undefined));
const isEdit = computed(() => !!editId.value);

const formRef = ref();
const loading = ref(false);
const saving = ref(false);
const notFound = ref(false);
const original = ref<Record<string, any>>({});

const form = reactive({
  theme: "",
  assessmentRequirements: "",
  defense: true,
  durationMinutes: 5 as number | undefined,
  question: true,
  questionCount: 3 as number | undefined,
  answerTime: 120 as number | undefined,
  followUp: true,
  totalScore: 100 as number | undefined,
  passScore: 60 as number | undefined,
  assessmentCriteria: "",
  showResult: true,
  needAppoint: false,
  picked: [] as string[],
  inviteTeachers: [] as number[],
});
const questions = ref<Question[]>([]);

// ---------------- 考核材料 ----------------
const uploadAction = process.env.VUE_APP_BASE_API + "/file/upload";
const uploadHeaders = { Authorization: `Bearer ${getToken()}` };
const files = ref<UploadFile[]>([]);

function beforeUpload(file: File) {
  const ok = file.type === "application/pdf" || file.name.toLowerCase().endsWith(".pdf");
  if (!ok) message.error("只支持 PDF 文件");
  return ok || Upload.LIST_IGNORE;
}

function onFileChange(info: UploadChangeParam) {
  if (info.file.status === "error") message.error(`${info.file.name} 上传失败`);
  if (info.file.status === "done" && info.file.response?.code !== 200) {
    message.error(info.file.response?.message || `${info.file.name} 上传失败`);
    files.value = files.value.filter((f) => f.uid !== info.file.uid);
  }
}

function buildFiles() {
  return files.value
    .filter((f) => f.status === "done" && f.response?.data?.url)
    .map((f) => {
      // 已有文件原样提交，保留后端解析过的材料内容
      const raw = (f as any).raw as Record<string, any> | undefined;
      if (raw) return raw;
      return { fileUrl: f.response.data.url, fileName: f.response.data.fileName || f.name, fileSize: f.size };
    });
}

// ---------------- 参与人员 ----------------
const classData = ref<{ schoolClass: string; students: Record<string, any>[] }[]>([]);
const teacherOptions = ref<{ label: string; value: number }[]>([]);
const studentTree = computed(() =>
  classData.value.map((c) => ({
    title: `${c.schoolClass || "未分班"}（${c.students?.length || 0}人）`,
    value: CLASS_PREFIX + c.schoolClass,
    key: CLASS_PREFIX + c.schoolClass,
    children: (c.students || []).map((s) => ({
      title: s.account ? `${s.name}（${s.account}）` : s.name,
      value: STUDENT_PREFIX + s.id,
      key: STUDENT_PREFIX + s.id,
    })),
  }))
);

function buildParticipants() {
  const schoolClassList: { schoolClass: string; students: number[] }[] = [];
  const studentIds: number[] = [];
  for (const value of form.picked) {
    if (value.startsWith(CLASS_PREFIX)) {
      const name = value.slice(CLASS_PREFIX.length);
      const cls = classData.value.find((c) => c.schoolClass === name);
      schoolClassList.push({ schoolClass: name, students: (cls?.students || []).map((s) => s.id) });
    } else if (value.startsWith(STUDENT_PREFIX)) {
      studentIds.push(Number(value.slice(STUDENT_PREFIX.length)));
    }
  }
  return { schoolClassList, studentIds };
}

// ---------------- 题库 ----------------
let keySeq = 0;
function makeQuestion(item: Record<string, any> = {}, saved = false): Question {
  keySeq += 1;
  return {
    __key: `q${keySeq}`,
    id: saved ? item.id : undefined,
    dimension: item.dimension || "",
    content: item.content || "",
    referenceAnswer: item.referenceAnswer || "",
    followUpQuestion: item.followUpQuestion || "",
    audioReady: saved ? item.audioReady : undefined,
    savedContent: saved ? item.content : undefined,
  };
}

const questionColumns = [
  { title: "#", key: "index", width: 48 },
  { title: "维度", key: "dimension", width: 140 },
  { title: "题目", key: "content" },
  { title: "语音", key: "audio", width: 100 },
  { title: "操作", key: "action", width: 200, fixed: "right" as const },
];

function audioState(q: Record<string, any>) {
  if (!q.id || q.content !== q.savedContent) return "unsaved";
  return q.audioReady ? "ready" : "pending";
}

const dimensionOptions = computed(() => {
  const names = new Set<string>();
  questions.value.forEach((q) => q.dimension && names.add(q.dimension));
  // 从评分标准中提取“名称（分值）”形式的维度
  const re = /([^\s：:（(，,；;。、\d]{2,20})\s*[（(]\s*\d+\s*分\s*[)）]/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(form.assessmentCriteria || ""))) names.add(m[1]);
  return Array.from(names).map((value) => ({ value }));
});

function filterDimension(input: string, option: { value: string }) {
  return option.value.includes(input);
}

function move(index: number, offset: number) {
  const list = questions.value;
  const target = index + offset;
  if (target < 0 || target >= list.length) return;
  [list[index], list[target]] = [list[target], list[index]];
}

const questionOpen = ref(false);
const questionFormRef = ref();
const editingIndex = ref<number | null>(null);
const editing = reactive({ dimension: "", content: "", referenceAnswer: "", followUpQuestion: "" });

function openQuestion(index?: number) {
  editingIndex.value = index ?? null;
  const q = index === undefined ? undefined : questions.value[index];
  Object.assign(editing, {
    dimension: q?.dimension || "",
    content: q?.content || "",
    referenceAnswer: q?.referenceAnswer || "",
    followUpQuestion: q?.followUpQuestion || "",
  });
  questionFormRef.value?.clearValidate();
  questionOpen.value = true;
}

async function saveQuestion() {
  await questionFormRef.value.validate();
  const values = {
    dimension: editing.dimension.trim(),
    content: editing.content.trim(),
    referenceAnswer: editing.referenceAnswer.trim(),
    followUpQuestion: editing.followUpQuestion.trim(),
  };
  if (editingIndex.value === null) {
    questions.value.push(makeQuestion(values));
  } else {
    Object.assign(questions.value[editingIndex.value], values);
  }
  questionOpen.value = false;
  formRef.value?.validateFields(["questions"]).catch(() => undefined);
}

const bulkOpen = ref(false);
const bulkText = ref("");
const bulkLines = computed(() =>
  bulkText.value
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter(Boolean)
);

function openBulk() {
  bulkText.value = "";
  bulkOpen.value = true;
}

function applyBulk() {
  if (!bulkLines.value.length) {
    message.warning("请至少输入一道题");
    return;
  }
  questions.value.push(...bulkLines.value.map((content) => makeQuestion({ content })));
  message.success(`已添加 ${bulkLines.value.length} 道题`);
  bulkOpen.value = false;
  formRef.value?.validateFields(["questions"]).catch(() => undefined);
}

const draftOpen = ref(false);
const draftLoading = ref(false);
const draftCount = ref(6);
const draftItems = ref<Record<string, any>[]>([]);
const draftSelected = ref<number[]>([]);

function openDraft() {
  if (!form.theme.trim() && !form.assessmentCriteria.trim()) {
    message.warning("请先填写面试名称或评分标准，AI 将据此出题");
    return;
  }
  draftItems.value = [];
  draftSelected.value = [];
  draftCount.value = Math.max((form.questionCount || 3) * 2, 5);
  draftOpen.value = true;
}

async function generateDraft() {
  draftLoading.value = true;
  try {
    const res = await generateQuestionDraft({
      theme: form.theme,
      assessmentRequirements: form.assessmentRequirements,
      assessmentCriteria: form.assessmentCriteria,
      count: draftCount.value,
      assessmentFiles: buildFiles(),
    });
    draftItems.value = res?.data || [];
    draftSelected.value = draftItems.value.map((_, i) => i);
    if (!draftItems.value.length) message.warning("AI 没有生成题目，请补充面试要求或评分标准后重试");
  } finally {
    draftLoading.value = false;
  }
}

function toggleDraft(i: number) {
  const idx = draftSelected.value.indexOf(i);
  if (idx >= 0) draftSelected.value.splice(idx, 1);
  else draftSelected.value.push(i);
}

function toggleAllDraft() {
  draftSelected.value =
    draftSelected.value.length === draftItems.value.length ? [] : draftItems.value.map((_, i) => i);
}

function applyDraft() {
  const picked = [...draftSelected.value].sort((a, b) => a - b).map((i) => draftItems.value[i]);
  questions.value.push(...picked.map((item) => makeQuestion(item)));
  message.success(`已添加 ${picked.length} 道题，请检查后保存`);
  draftOpen.value = false;
  formRef.value?.validateFields(["questions"]).catch(() => undefined);
}

// ---------------- 校验 ----------------
function validateFlow() {
  if (!form.defense && !form.question) return Promise.reject("项目答辩和随机问答至少开启一项");
  if (form.defense && !form.durationMinutes) return Promise.reject("请填写答辩时长");
  if (form.question && (!form.questionCount || !form.answerTime)) return Promise.reject("请填写抽题数和每题作答时长");
  return Promise.resolve();
}

function validatePass() {
  if (form.passScore === undefined || form.passScore === null) return Promise.reject("请输入及格分");
  if (form.totalScore !== undefined && form.passScore > form.totalScore) return Promise.reject("及格分不能高于满分");
  return Promise.resolve();
}

function validateQuestions() {
  if (!form.question) return Promise.resolve();
  if (!questions.value.length) return Promise.reject("请至少添加一道题");
  const empty = questions.value.findIndex((q) => !q.content.trim());
  if (empty >= 0) return Promise.reject(`第 ${empty + 1} 题内容为空`);
  const count = form.questionCount || 0;
  if (questions.value.length < count)
    return Promise.reject(`题库共 ${questions.value.length} 题，少于每人抽题数 ${count} 题`);
  return Promise.resolve();
}

// ---------------- 加载 / 保存 ----------------
function fill(item: Record<string, any>) {
  original.value = item;
  Object.assign(form, {
    theme: item.theme || "",
    assessmentRequirements: item.assessmentRequirements || "",
    defense: !!item.defense,
    durationMinutes: item.duration ? Math.round((item.duration / 60) * 10) / 10 : 5,
    question: !!item.question,
    questionCount: item.questionCount || 3,
    answerTime: item.answerTime || 120,
    followUp: !!item.followUp,
    totalScore: item.totalScore,
    passScore: item.passScore,
    assessmentCriteria: item.assessmentCriteria || "",
    showResult: !!item.showResult,
    needAppoint: !!item.needAppoint,
    picked: [
      ...(item.schoolClassList || []).map((c: Record<string, any>) => CLASS_PREFIX + c.schoolClass),
      ...(item.students || []).map((s: Record<string, any>) => STUDENT_PREFIX + s.id),
    ],
    inviteTeachers: (item.inviteTeachers || []).map((t: Record<string, any>) => t.id),
  });
  questions.value = (item.questions || []).map((q: Record<string, any>) => makeQuestion(q, true));
  files.value = (item.assessmentFiles || []).map((f: Record<string, any>, i: number) => ({
    uid: `saved-${i}`,
    name: f.fileName,
    size: Number(f.fileSize) || undefined,
    status: "done",
    response: { code: 200, data: { url: f.fileUrl, fileName: f.fileName } },
    raw: f,
  })) as unknown as UploadFile[];
}

async function load() {
  loading.value = true;
  try {
    const [classRes, teacherRes] = await Promise.all([getSchoolClassStudent(), getAllTeachers()]);
    classData.value = classRes?.data || [];
    teacherOptions.value = (teacherRes?.data || []).map((t: Record<string, any>) => ({
      label: t.account ? `${t.name}（${t.account}）` : t.name,
      value: t.id,
    }));
    if (isEdit.value) {
      // 没有按 id 查询的接口，从列表中查找
      const res = await getAssessmentList({ page: 1, size: 1000 });
      const item = (res?.data?.records || []).find((r: Record<string, any>) => r.id === editId.value);
      if (!item) {
        notFound.value = true;
        return;
      }
      fill(item);
    }
  } finally {
    loading.value = false;
  }
}

async function save() {
  try {
    await formRef.value.validate();
  } catch (e) {
    message.error("请检查表单中标红的内容");
    return;
  }
  if (files.value.some((f) => f.status === "uploading")) {
    message.warning("考核材料正在上传，请稍候");
    return;
  }
  const { schoolClassList, studentIds } = buildParticipants();
  const params: Record<string, any> = {
    theme: form.theme.trim(),
    assessmentRequirements: form.assessmentRequirements.trim(),
    assessmentCriteria: form.assessmentCriteria.trim(),
    defense: form.defense,
    // 后端要求答辩时长、作答时长始终有值
    duration: Math.round((form.durationMinutes || 5) * 60),
    question: form.question,
    questionCount: form.question ? form.questionCount : undefined,
    answerTime: form.answerTime || 120,
    followUp: form.question && form.followUp,
    totalScore: form.totalScore,
    passScore: form.passScore,
    showResult: form.showResult,
    needAppoint: form.needAppoint,
    assessmentFiles: buildFiles(),
    inviteTeachers: form.inviteTeachers,
    schoolClassList,
    studentIds,
    questions: form.question
      ? questions.value.map((q) => ({
          id: q.id,
          dimension: q.dimension.trim() || undefined,
          content: q.content.trim(),
          referenceAnswer: q.referenceAnswer.trim() || undefined,
          followUpQuestion: form.followUp ? q.followUpQuestion.trim() || undefined : undefined,
        }))
      : [],
  };
  saving.value = true;
  try {
    if (isEdit.value) {
      await modifyAssessment({ id: editId.value, ...params });
      message.success("已保存");
    } else {
      await createAssessment(params);
      message.success(form.needAppoint ? "面试已创建，请在列表中设置预约时段" : "面试已创建");
    }
    router.push("/assessments");
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.editor {
  max-width: 1080px;
  padding-bottom: 96px;
}

.sections {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sec-title {
  margin: 0 0 16px;
  font-size: 16px;
  font-weight: 600;
}

.sec-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.flow {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.flow-item {
  padding: 16px;
  border: 1px solid var(--c-border);
  border-radius: 8px;
  transition: border-color 0.2s;
}

.flow-item.is-on {
  border-color: #ecb8bc;
  background: #fffafa;
}

.flow-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.flow-name {
  font-weight: 600;
}

.flow-body {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  row-gap: 12px;
}

.q-content {
  white-space: pre-wrap;
  word-break: break-word;
}

.q-sub {
  margin-top: 4px;
  font-size: 12px;
  line-height: 20px;
  color: var(--c-text-3);
  white-space: pre-wrap;
  word-break: break-word;
}

.draft-list {
  max-height: 460px;
  overflow-y: auto;
  border: 1px solid var(--c-border);
  border-radius: 8px;
}

.draft-item {
  display: flex;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--c-border-2);
  cursor: pointer;
}

.draft-item:last-child {
  border-bottom: 0;
}

.draft-item:hover {
  background: #fafbfc;
}

.footer-bar {
  position: fixed;
  right: 0;
  bottom: 0;
  left: var(--sider-w);
  z-index: 10;
  background: var(--c-surface);
  border-top: 1px solid var(--c-border);
}

.footer-inner {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  max-width: 1080px;
  margin: 0 auto;
  padding: 12px 24px;
}

@media (max-width: 900px) {
  .footer-bar {
    left: 0;
  }
  .flow {
    grid-template-columns: 1fr;
  }
}
</style>
