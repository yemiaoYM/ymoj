<template>
  <div class="container">
    <el-card>
      <!-- 标题和操作按钮 -->
      <template #header>
        <span style="font-size: 22px; font-weight: 600; color: var(--ym-link)">
          {{ mode === 'edit' ? '编辑题目' : '新增题目' }}
        </span>
      </template>
      <x-flex vertical :size="[12, 12]" style="width: 100%; padding: 15px">
        <el-skeleton v-if="loading" animated :rows="8" />
        <el-alert v-else-if="loadFailed" title="题目加载失败，请重试" type="error" :closable="false">
          <el-button text type="primary" @click="handleLoadProblem">重新加载</el-button>
        </el-alert>
        <!-- 基础信息 -->
        <el-form
          v-else
          ref="formRef"
          style="width: 100%"
          label-width="auto"
          scroll-to-error
          :label-position="isNarrow ? 'top' : 'right'"
          :model="problem"
          :rules="rules"
          :disabled="saving || spjCompiling"
        >
          <el-form-item label="题目展示ID" prop="problemId">
            <el-input
              v-model="problem.problemId"
              placeholder="1～15位英文字母、数字或下划线，例如 P1000"
              clearable
              :maxlength="mode === 'add' ? 15 : undefined"
              :show-word-limit="mode === 'add'"
              :disabled="mode === 'edit'"
            />
          </el-form-item>
          <el-form-item label="标题" prop="title">
            <el-input v-model="problem.title" placeholder="请输入题目标题" maxlength="50" show-word-limit />
          </el-form-item>
          <el-form-item label="时间限制(ms)" prop="timeLimit">
            <el-input-number
              v-model="problem.timeLimit"
              :min="1000"
              :max="10000"
              :step="100"
            />
          </el-form-item>
          <el-form-item label="内存限制(MB)" prop="memoryLimit">
            <el-input-number
              v-model="problem.memoryLimit"
              :min="256"
              :max="1024"
              :step="16"
            />
          </el-form-item>
          <el-form-item label="权限" prop="visibility">
            <el-select v-model="problem.visibility" disabled style="width: 200px">
              <el-option :value="PROBLEM_VISIBILITY.PUBLIC" label="公开" />
              <el-option :value="PROBLEM_VISIBILITY.PRIVATE" label="私有" />
              <el-option :value="PROBLEM_VISIBILITY.CONTEST_EXCLUSIVE" label="比赛专用" />
            </el-select>
          </el-form-item>
          <el-form-item label="难度" prop="difficulty">
            <el-select v-model="problem.difficulty" style="width: 200px">
              <el-option
                v-for="item in PROBLEM_DIFFICULTIES"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="是否公开测试用例" prop="openCaseResult">
            <el-switch v-model="problem.openCaseResult" />
          </el-form-item>
          <!-- 标签管理 -->
          <el-form-item label="标签管理" prop="tagList">
            <el-select
              v-model="problem.tagList"
              multiple
              :multiple-limit="5"
              placeholder="请选择标签"
              clearable
              filterable
              style="width: 100%; max-width: 450px"
            >
              <el-option
                v-for="item in tagList"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              >
                <el-tag :color="item.color" style="color: white;">
                  {{ item.name }}
                </el-tag>
              </el-option>
            </el-select>
            <el-button v-if="tagsFailed" text type="primary" @click="handleLoadTags">标签加载失败，点击重试</el-button>
          </el-form-item>
          <!-- 题目描述 -->
          <el-form-item
            label="题目描述"
            prop="content"
            label-position="top"
            style="width: 100%"
          >
            <MarkdownEidt
              ref="contentEditorRef"
              :key="`content-${mode}-${problemId}`"
              v-model:content="problem.content"
              :disabled="saving"
            />
          </el-form-item>
          <!-- 输入描述（Markdown） -->
          <el-form-item
            label="输入描述"
            prop="input"
            label-position="top"
            style="width: 100%"
          >
            <MarkdownEidt
              ref="inputEditorRef"
              :key="`input-${mode}-${problemId}`"
              v-model:content="problem.input"
              :disabled="saving"
            />
          </el-form-item>
          <!-- 输出描述（Markdown） -->
          <el-form-item
            label="输出描述"
            prop="output"
            label-position="top"
            style="width: 100%"
          >
            <MarkdownEidt
              ref="outputEditorRef"
              :key="`output-${mode}-${problemId}`"
              v-model:content="problem.output"
              :disabled="saving"
            />
          </el-form-item>
          <!-- 测试用例 -->
          <el-form-item
            label="题面测试用例"
            prop="problemCaseList"
            label-position="top"
            style="width: 100%"
          >
            <el-button
              style="margin-bottom: 15px"
              type="primary"
              :disabled="problem.problemCaseList.length >= 3"
              @click="onAddCase"
            >
              <el-icon size="15" style="margin-right: 5px"><Plus /></el-icon>
              新增用例（最多3组）
            </el-button>
            <el-table
              :data="problem.problemCaseList"
              border
              empty-text="暂无题面样例"
              style="width: 100%"
            >
              <el-table-column label="输入" min-width="160">
                <template #default="{ row }">
                  <el-input
                    v-model="row.input"
                    type="textarea"
                    placeholder="输入"
                    maxlength="200"
                    :autosize="{ minRows: 2, maxRows: 6 }"
                  />
                </template>
              </el-table-column>
              <el-table-column label="输出" min-width="160">
                <template #default="{ row }">
                  <el-input
                    v-model="row.output"
                    type="textarea"
                    placeholder="输出"
                    maxlength="200"
                    :autosize="{ minRows: 2, maxRows: 6 }"
                  />
                </template>
              </el-table-column>
              <el-table-column label="备注" min-width="160">
                <template #default="{ row }">
                  <el-input v-model="row.note" placeholder="备注（可选）" maxlength="200" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100" align="center">
                <template #default="{ $index }">
                  <el-button type="danger" text @click="onRemoveCase($index)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-form-item>
          <el-form-item label="题目来源" prop="source">
            <el-input
              v-model="problem.source"
              placeholder="可选，例如：此题来源于洛谷 P1000"
              clearable
              maxlength="255"
              show-word-limit
            />
          </el-form-item>
          <el-form-item label="判题方式" prop="judgeMode">
            <el-radio-group v-model="problem.judgeMode">
              <el-tooltip content="按测试数据运行程序，并比对输出结果。" placement="top">
                <el-radio-button value="default">普通判题</el-radio-button>
              </el-tooltip>
              <el-tooltip content="使用专用校验程序判断答案，适合多解或自定义误差等情况。" placement="top">
                <el-radio-button value="spj">特殊判题</el-radio-button>
              </el-tooltip>
              <el-tooltip content="用户程序与交互程序交换输入输出。暂未支持。" placement="top">
                <el-radio-button value="interactive" disabled>交互判题</el-radio-button>
              </el-tooltip>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="problem.judgeMode === 'spj'" prop="spjCode">
            <SpjEditor
              :key="`${mode}-${problemId}`"
              v-model:code="problem.spjCode"
              v-model:language="problem.spjLanguage"
              :disabled="saving"
              @busy="spjCompiling = $event"
            />
          </el-form-item>
        </el-form>
      </x-flex>
      <template #footer>
        <el-button
          v-permission="mode === 'edit' ? ['problem:problem:edit'] : ['problem:problem:add']"
          size="large"
          type="primary"
          :loading="saving"
          :disabled="loading || loadFailed || spjCompiling"
          @click="onSubmitProblem"
        >
          {{ mode === 'edit' ? '更新' : '保存' }}
        </el-button>
        <el-popconfirm v-if="mode === 'add'" title="清空当前内容及本地草稿？" @confirm="handleDiscardDraft">
          <template #reference>
            <el-button size="large" type="warning" :disabled="saving">清空</el-button>
          </template>
        </el-popconfirm>
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, defineAsyncComponent, watch, nextTick, onMounted, onBeforeUnmount } from "vue";
import { ElMessage } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { useRoute, useRouter } from "vue-router";
import { addProblem, updateProblem, getAdminProblemById, getTagsList } from "@/api/api";
import type { Tag, ProblemDTO } from "@/type/types";
import { PROBLEM_DIFFICULTIES, PROBLEM_VISIBILITY } from "@/constants/problem";
import { isDraftRecord, useLocalDraft } from "@/hooks/useLocalDraft";
import { useAsyncChange } from "@/hooks/useAsyncChange";
import { useRemoteProblemStore } from "@/stores/remoteProblem";
const MarkdownEidt = defineAsyncComponent(
  () => import("@/components/common/MarkdownEidt.vue")
);
const SpjEditor = defineAsyncComponent(() => import('@/components/admin/SpjEditor.vue'));

type ProblemForm = Omit<ProblemDTO, "examples" | "source" | "timeLimit" | "memoryLimit"> & {
  source: string;
  tagList: number[];
  timeLimit: number | null | undefined;
  memoryLimit: number | null | undefined;
};

const route = useRoute();
const router = useRouter();
const remoteStore = useRemoteProblemStore();
const media = window.matchMedia('(max-width: 767px)');
const isNarrow = ref(media.matches);
const loading = ref(false);
const { pending: saving, beforeChange: saveProblem } = useAsyncChange();
const loadFailed = ref(false);
const tagsFailed = ref(false);
const spjCompiling = ref(false);
const formRef = ref<FormInstance>();
const contentEditorRef = ref<InstanceType<typeof MarkdownEidt>>();
const inputEditorRef = ref<InstanceType<typeof MarkdownEidt>>();
const outputEditorRef = ref<InstanceType<typeof MarkdownEidt>>();
const mode = ref('add');
const problemId = ref<number | null>(null);
const tagList = ref<Tag[]>([]);
const problem = ref<ProblemForm>(handleCreateForm());
let revision = 0;
const draftActive = ref(false);
type ProblemDraft = ProblemForm & { importNotice?: string };
const localDraft = useLocalDraft<ProblemDraft>(
  'problem',
  () => problem.value,
  () => draftActive.value,
  (value): value is ProblemDraft => {
    if (!isDraftRecord(value)) return false;
    return value.id === null
      && ['problemId', 'title', 'content', 'input', 'output', 'source']
        .every(key => typeof value[key] === 'string')
      && ['difficulty', 'visibility'].every(key => typeof value[key] === 'number')
      && ['timeLimit', 'memoryLimit'].every(key => value[key] == null || typeof value[key] === 'number')
      && (value.judgeMode === 'default' || value.judgeMode === 'spj')
      && typeof value.spjCode === 'string' && value.spjLanguage === 'C++'
      && typeof value.openCaseResult === 'boolean'
      && Array.isArray(value.tagList) && value.tagList.every(tag => typeof tag === 'number')
      && Array.isArray(value.problemCaseList) && value.problemCaseList.every(item =>
        isDraftRecord(item) && typeof item.input === 'string' && typeof item.output === 'string'
        && typeof item.note === 'string');
  },
);

function handleDiscardDraft() {
  problem.value = handleCreateForm();
  localDraft.clear();
  nextTick(() => formRef.value?.clearValidate());
}

//验证测试用例
const validateTestCases = (_rule: unknown, value: ProblemForm['problemCaseList'], callback: (error?: Error) => void) => {
  if (value.length > 3) {
    callback(new Error("最多设置3组题面样例"));
  } else {
    // 验证每个测试用例的内容长度
    for (let i = 0; i < value.length; i++) {
      const testCase = value[i];
      if (testCase.input && testCase.input.length > 200) {
        callback(new Error(`第${i + 1}个测试样例输入内容长度不能超过200`));
        return;
      }
      if (testCase.output && testCase.output.length > 200) {
        callback(new Error(`第${i + 1}个测试样例输出内容长度不能超过200`));
        return;
      }
      if (testCase.note && testCase.note.length > 200) {
        callback(new Error(`第${i + 1}个测试样例备注内容长度不能超过200`));
        return;
      }
    }
    callback();
  }
};
//表单验证规则
const rules = computed<FormRules<ProblemForm>>(() => ({
  problemId: mode.value === 'add' ? [
    { required: true, message: "请输入题目展示ID", trigger: "blur" },
    { pattern: /^[A-Za-z0-9_]{1,15}$/, message: "仅支持1～15位英文字母、数字或下划线", trigger: "blur" },
  ] : [],
  title: [
    { required: true, message: "题目标题不能为空", trigger: "blur" },
    { max: 50, message: "题目标题长度不能超过50个字符", trigger: "blur" },
  ],
  content: [{ required: true, message: "题干不能为空", trigger: "blur" }],
  input: [
    { required: true, message: "输入描述不能为空", trigger: "blur" },
    { max: 2000, message: "输入描述长度不能超过2000字符", trigger: "blur" },
  ],
  output: [
    { required: true, message: "输出描述不能为空", trigger: "blur" },
    { max: 2000, message: "输出描述长度不能超过2000字符", trigger: "blur" },
  ],
  source: [
    { max: 255, message: "题目来源长度不能超过255个字符", trigger: "blur" },
  ],
  difficulty: [
    { required: true, message: "请选择题目难度", trigger: "change" },
    {
      type: "number",
      min: 1,
      max: 5,
      message: "难度参数非法",
      trigger: "change",
    },
  ],
  judgeMode: [
    { required: true, message: "请选择题目判题方式", trigger: "change" },
    { type: "enum", enum: ['default', 'spj'], message: "目前仅支持普通判题和特殊判题", trigger: "change" },
  ],
  spjCode: problem.value.judgeMode === 'spj' ? [
    { required: true, whitespace: true, message: '请输入 SPJ 源码', trigger: 'blur' },
    { max: 65535, message: 'SPJ 源码不能超过65535个字符', trigger: 'blur' },
  ] : [],
  visibility: [
    { required: true, message: "请选择题目可见性", trigger: "change" },
    {
      type: "number",
      min: 1,
      max: 3,
      message: "可见性参数非法",
      trigger: "change",
    },
  ],
  openCaseResult: [
    {
      required: true,
      message: "请设置是否允许查看测试样例结果",
      trigger: "change",
    },
  ],
  timeLimit: [
    { required: true, message: "请设定时间限制", trigger: "blur" },
    {
      type: "number",
      min: 1000,
      max: 10000,
      message: "时间限制必须在1000ms到10000ms之间",
      trigger: "blur",
    },
  ],
  memoryLimit: [
    { required: true, message: "请设定内存限制", trigger: "blur" },
    {
      type: "number",
      min: 256,
      max: 1024,
      message: "内存限制必须在256MB到1024MB之间",
      trigger: "blur",
    },
  ],
  problemCaseList: [{ validator: validateTestCases, trigger: "blur" }],
  tagList: [{ type: "array", max: 5, message: "最多设置5个标签", trigger: "change" }],
}));

//添加题面测试用例
const onAddCase = () => {
  if (problem.value.problemCaseList.length >= 3) {
    ElMessage.error("最多设置3组题面样例");
    return;
  }
  problem.value.problemCaseList.push({ input: "", output: "", note: "" });
};
//删除题面测试用例
const onRemoveCase = (index: number) => {
  problem.value.problemCaseList.splice(index, 1);
};
//提交题目（新增或编辑）
const onSubmitProblem = async () => {
  const form = formRef.value;
  const contentEditor = contentEditorRef.value;
  const inputEditor = inputEditorRef.value;
  const outputEditor = outputEditorRef.value;
  if (!form || !contentEditor || !inputEditor || !outputEditor || saving.value || spjCompiling.value || loading.value || loadFailed.value) return;
  const current = revision;
  await saveProblem(async () => {
    await form.validate();
    if (current !== revision) return false;
    for (const editor of [contentEditor, inputEditor, outputEditor]) {
      if (await editor.uploadImages() === false || current !== revision) return false;
    }
    const { id, ...values } = problem.value;
    const submitData = { ...values, source: values.source.trim() || null };
    const res = mode.value === 'edit' && id
      ? await updateProblem(id, submitData)
      : await addProblem(submitData);
    if (current !== revision) return false;
    ElMessage.success(res.data.msg);
    if (mode.value === 'edit') {
      await router.push('/admin/problem/list');
    } else {
      problem.value = handleCreateForm();
      localDraft.clear();
      await nextTick();
      formRef.value?.clearValidate();
    }
  });
};

const handleLoadTags = async () => {
  tagsFailed.value = false;
  try {
    const res = await getTagsList();
    tagList.value = res.data.data;
  } catch {
    tagsFailed.value = true;
  }
};

// 每次新增使用独立的空表单，提示文字不作为实际正文或样例提交。
function handleCreateForm(): ProblemForm {
  return {
    id: null,
    problemId: "",
    title: "",
    content: "",
    input: "",
    output: "",
    source: "",
    difficulty: 1,
    judgeMode: "default",
    spjLanguage: 'C++',
    spjCode: '',
    visibility: PROBLEM_VISIBILITY.PRIVATE,
    openCaseResult: false,
    timeLimit: 1000,
    memoryLimit: 256,
    problemCaseList: [],
    tagList: [],
  };
}

const handleLoadProblem = async () => {
  const id = problemId.value;
  if (!id || !Number.isSafeInteger(id) || id < 1) {
    loadFailed.value = true;
    return;
  }
  const current = ++revision;
  loading.value = true;
  loadFailed.value = false;
  try {
    const res = await getAdminProblemById(id);
    if (current !== revision) return;
    const data = res.data.data;
    problem.value = {
      id: data.id,
      problemId: data.problemId,
      title: data.title,
      content: data.content,
      input: data.input,
      output: data.output,
      visibility: data.visibility,
      source: data.source ?? "",
      difficulty: data.difficulty,
      judgeMode: data.judgeMode,
      spjLanguage: data.spjLanguage ?? 'C++',
      spjCode: data.spjCode ?? '',
      openCaseResult: data.openCaseResult,
      timeLimit: data.timeLimit,
      memoryLimit: Math.round(data.memoryLimit / 1024), // 后端KiB转换为表单MiB
      problemCaseList: data.problemCaseList ?? [],
      tagList: data.tagList ?? [],
    };
  } catch {
    if (current === revision) loadFailed.value = true;
  } finally {
    if (current === revision) loading.value = false;
  }
};

// 切换题目或回到新增页时，使旧请求失效，避免覆盖正在编辑的表单。
watch(() => [route.name, route.params.id], () => {
  localDraft.flush();
  draftActive.value = false;
  revision++;
  mode.value = route.name === 'AdminProblemEdit' ? 'edit' : 'add';
  problemId.value = mode.value === 'edit' ? Number(route.params.id) : null;
  loading.value = false;
  loadFailed.value = false;
  problem.value = handleCreateForm();
  if (mode.value === 'edit') {
    handleLoadProblem();
  } else if (route.name === 'AdminAddProblem') {
    const cached = localDraft.restore();
    if (cached) {
      const { importNotice: _notice, ...fields } = cached;
      problem.value = fields;
    }
    draftActive.value = true;
    if (route.query.remote === '1') {
      const draft = remoteStore.draft;
      remoteStore.draft = null;
      if (draft) {
        const { notice, ...fields } = draft;
        problem.value = {
          ...problem.value,
          ...fields,
          timeLimit: draft.timeLimit === null ? 1000 : Math.min(10000, Math.max(1000, draft.timeLimit)),
          memoryLimit: draft.memoryLimit === null ? 256 : Math.min(1024, Math.max(256, draft.memoryLimit)),
        };
      }
    }
    nextTick(() => formRef.value?.clearValidate());
  }
}, { immediate: true });

const onScreenChange = (event: MediaQueryListEvent) => {
  isNarrow.value = event.matches;
};

onMounted(() => {
  handleLoadTags();
  media.addEventListener('change', onScreenChange);
});
onBeforeUnmount(() => {
  revision++;
  media.removeEventListener('change', onScreenChange);
});
</script>

<style scoped>
.container {
  width: 100%;
  height: 100%;
}
@media screen and (min-width: 1024px) {
  .container {
    padding: 15px 5%;
  }
}
</style>
