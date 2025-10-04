<template>
  <div class="container">
    <el-card>
      <!-- 标题和操作按钮 -->
      <template #header>
        <span style="font-size: 22px; font-weight: 600; color: #409eff">
          {{ mode ==='edit' ? '编辑题目' : '增加题目' }}
        </span>
      </template>
      <n-flex vertical :size="[12, 12]" style="width: 100%; padding: 15px">
        <!-- 基础信息 -->
        <el-form
          ref="formRef"
          :model="problem"
          :rules="rules"
          label-width="auto"
          style="width: 100%"
        >
          <el-form-item label="题目展示id" prop="problemId">
            <el-input
              v-model="problem.problemId"
              placeholder="请输入题目展示id"
            />
          </el-form-item>
          <el-form-item label="标题" prop="title">
            <el-input v-model="problem.title" placeholder="请输入题目标题" />
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
              :max="512"
              :step="16"
            />
          </el-form-item>
          <el-form-item label="来源" prop="source">
            <el-select v-model="problem.source" style="width: 200px">
              <el-option value="myy" label="本地" />
              <el-option value="luogu" label="洛谷" disabled />
              <el-option value="atcoder" label="AtCoder" disabled />
              <el-option value="codeforces" label="Codeforces" disabled />
            </el-select>
          </el-form-item>
          <el-form-item label="权限" prop="visibility">
            <el-select v-model="problem.visibility" style="width: 200px">
              <el-option :value="1" label="公开" />
              <el-option :value="2" label="私有" />
              <el-option :value="3" label="比赛专用" />
            </el-select>
          </el-form-item>
          <el-form-item label="难度" prop="difficulty">
            <el-select v-model="problem.difficulty" style="width: 200px">
              <el-option :value="0" label="未评定" />
              <el-option :value="1" label="入门" />
              <el-option :value="2" label="普及" />
              <el-option :value="3" label="提高" />
              <el-option :value="4" label="省选及以上" />
            </el-select>
          </el-form-item>
          <el-form-item label="模式" prop="mode">
            <el-select v-model="problem.mode" style="width: 200px">
              <el-option :value="0" label="ACM" />
              <el-option :value="1" label="OI" />
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
              multiple-limit="5"
              placeholder="请选择标签"
              clearable
              style="width: 450px"
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
          </el-form-item>
          <!-- 题目描述 -->
          <el-form-item
            label="题目描述"
            prop="content"
            label-position="top"
            style="width: 100%"
          >
            <MarkdownEidt v-model:content="problem.content" :key="`content-${mode}-${problemId}`" />
          </el-form-item>
          <!-- 输入描述（Markdown） -->
          <el-form-item
            label="输入描述"
            prop="input"
            label-position="top"
            style="width: 100%"
          >
            <MarkdownEidt v-model:content="problem.input" :key="`input-${mode}-${problemId}`" />
          </el-form-item>
          <!-- 输出描述（Markdown） -->
          <el-form-item
            label="输出描述"
            prop="output"
            label-position="top"
            style="width: 100%"
          >
            <MarkdownEidt v-model:content="problem.output" :key="`output-${mode}-${problemId}`" />
          </el-form-item>
          <!-- 测试用例 -->
          <el-form-item
            label="题面测试用例"
            prop="problemCaseList"
            label-position="top"
            style="width: 100%"
          >
            <el-button
              effect="dark"
              type="primary"
              @click="addCase"
              size="large"
              style="margin-bottom: 15px"
            >
              <el-icon size="15" style="margin-right: 5px"><Plus /></el-icon>
              新增用例
            </el-button>
            <el-table
              :data="problem.problemCaseList"
              border
              style="width: 100%"
            >
              <el-table-column label="输入">
                <template #default="{ row }">
                  <el-input
                    v-model="row.input"
                    type="textarea"
                    autosize
                    placeholder="输入"
                  />
                </template>
              </el-table-column>
              <el-table-column label="输出">
                <template #default="{ row }">
                  <el-input
                    v-model="row.output"
                    type="textarea"
                    autosize
                    placeholder="输出"
                  />
                </template>
              </el-table-column>
              <el-table-column label="备注">
                <template #default="{ row }">
                  <el-input v-model="row.note" placeholder="备注（可选）" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100" align="center">
                <template #default="{ $index }">
                  <el-button type="danger" text @click="removeCase($index)"
                    >删除</el-button
                  >
                </template>
              </el-table-column>
            </el-table>
          </el-form-item>
        </el-form>
      </n-flex>
      <template #footer>
        <el-button
          size="large"
          type="primary"
          :loading="loading"
          @click="handleSubmitProblem"
        >
          {{ mode ==='edit' ? '更新' : '保存' }}
        </el-button>
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
//import
import { ref, defineAsyncComponent, watch } from "vue";
import { ElMessage } from "element-plus";
import { useRoute, useRouter } from "vue-router";
import { addProblem, updateProblem, getAdminProblemById, getTagsList } from "@/api/api";
import type { Tag } from "@/type/types";
//异步导入markdown
const MarkdownEidt = defineAsyncComponent(
  () => import("@/components/common/MarkdownEidt.vue")
);

//data
const route = useRoute();
const router = useRouter();
const loading = ref(false);
const formRef = ref();
const mode = ref('add');
const problemId = ref<number | null>(null);
const tagList = ref<Tag[]>([] as Tag[]);

//验证题目展示id格式
const validateProblemId = (rule: any, value: string, callback: any) => {
  if (!value) {
    callback(new Error("题目展示id不能为空"));
  } else if (value.length > 20) {
    callback(new Error("题目展示id长度不能超过20个字符"));
  } else if (!/^[a-zA-Z0-9-]+$/.test(value)) {
    callback(new Error("题目展示id只能包含字母、数字和 -"));
  } else {
    callback();
  }
};
//验证测试用例
const validateTestCases = (rule: any, value: any[], callback: any) => {
  if (value && value.length > 5) {
    callback(new Error("最多设置5个测试样例"));
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
//验证标签列表
const validateTagList = (rule: any, value: any[], callback: any) => {
  if (value && value.length > 5) {
    callback(new Error("最多设置5个标签"));
  } else {
    callback();
  }
};
//表单验证规则
const rules = {
  problemId: [
    { required: true, message: "题目展示id不能为空", trigger: "blur" },
    { validator: validateProblemId, trigger: "blur" },
  ],
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
    { required: true, message: "请选择题目来源", trigger: "change" },
    { max: 255, message: "题目来源长度不能超过255个字符", trigger: "blur" },
  ],
  difficulty: [
    { required: true, message: "请选择题目难度", trigger: "change" },
    {
      type: "number",
      min: 0,
      max: 4,
      message: "难度参数非法",
      trigger: "change",
    },
  ],
  mode: [
    { required: true, message: "请选择题目评测模式", trigger: "change" },
    {
      type: "number",
      min: 0,
      max: 1,
      message: "评测模式参数非法",
      trigger: "change",
    },
  ],
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
      min: 128,
      max: 512,
      message: "内存限制必须在128MB到512MB之间",
      trigger: "blur",
    },
  ],
  problemCaseList: [{ validator: validateTestCases, trigger: "blur" }],
  tagList: [{ validator: validateTagList, trigger: "blur" }],
};

const problem = ref({
  id: null,
  problemId: "",
  title: "",
  content: "请输入题干",
  input: "请输入输入描述",
  output: "请输入输出描述",
  source: "myy",
  difficulty: 0,
  mode: 0,
  visibility: 1,
  openCaseResult: false,
  timeLimit: 1000,
  memoryLimit: 256,
  problemCaseList: [
    {
      input: "测试用例1输入(记得删掉)",
      output: "测试用例1输出",
      note: "测试用例1备注",
    },
  ],
  tagList: [], // 标签列表
});

//methods
//添加题面测试用例
const addCase = () => {
  if (problem.value.problemCaseList.length >= 5) {
    ElMessage.error("最多只能添加5个测试用例");
    return;
  }
  problem.value.problemCaseList.push({ input: "", output: "", note: "" });
};
//删除题面测试用例
const removeCase = (index: number) => {
  problem.value.problemCaseList.splice(index, 1);
};
//提交题目（新增或编辑）
const handleSubmitProblem = () => {
  if (!formRef.value) return;
  formRef.value.validate((valid: boolean) => {
    if (valid) {
      loading.value = true;
      
      //构造符合后端DTO的数据结构
      const submitData = {
        problemId: problem.value.problemId,
        title: problem.value.title,
        content: problem.value.content,
        input: problem.value.input,
        output: problem.value.output,
        source: problem.value.source,
        difficulty: problem.value.difficulty,
        mode: problem.value.mode,
        visibility: problem.value.visibility,
        openCaseResult: problem.value.openCaseResult,
        timeLimit: problem.value.timeLimit,
        memoryLimit: problem.value.memoryLimit,
        problemCaseList: problem.value.problemCaseList,
        tagList: problem.value.tagList || []
      };
      
      const api = mode.value ==='edit' && problem.value.id
        ? updateProblem(problem.value.id, submitData)
        : addProblem(submitData);
      
      api.then((res) => {
          ElMessage.success(res.data.msg);
          if (mode.value ==='edit') {
            router.push('/admin/problem/list');
          } else {
            //新增模式成功后重置表单
            resetForm();
          }
        }).catch((e) => {
          console.error(e);
        }).finally(() => {
          loading.value = false;
        });
    } else {
      ElMessage.warning("请检查表单输入");
    }
  });
};
//加载标签列表
const loadTagList = () => {
  getTagsList().then(res => {
    tagList.value = res.data.data;
  }).catch(e => {
    console.log(e);
  });
};
//重置表单
const resetForm = () => {
  problem.value= {
    id: null,
    problemId: "",
    title: "",
    content: "请输入题干",
    input: "请输入输入描述",
    output: "请输入输出描述",
    source: "myy",
    difficulty: 0,
    mode: 0,
    visibility: 1,
    openCaseResult: false,
    timeLimit: 1000,
    memoryLimit: 256,
    problemCaseList: [
      {
        input: "测试用例1输入(记得删掉)",
        output: "测试用例1输出",
        note: "测试用例1备注",
      },
    ],
    tagList: [],
  };
  if (formRef.value) {
    formRef.value.resetFields();
  }
};
//加载题目数据
const loadProblemData = async () => {
  if (!problemId.value) return;
  try {
    loading.value = true;
    const res = await getAdminProblemById(problemId.value);
    const data = res.data.data;
    
    //获取到的数据赋值给表单
    Object.assign(problem.value, {
      id: data.id,
      problemId: data.problemId,
      title: data.title,
      content: data.content,
      input: data.input,
      output: data.output,
      visibility: data.visibility,
      source: data.source,
      difficulty: data.difficulty,
      mode: data.mode,
      openCaseResult: data.openCaseResult,
      timeLimit: data.timeLimit,
      memoryLimit: Math.round(data.memoryLimit / 1024), // 后端KB转换为前端MB
      problemCaseList: data.problemCaseList || [],
      tagList: data.tagList || [],
    });
  } catch (e) {
    console.log(e);
  } finally {
    loading.value = false;
  }
};

//监听路由参数变化
watch(() => route.name, () => {
  if (route.name === 'AdminProblemEdit' && route.params.id) {
    mode.value = 'edit';
    problemId.value = Number(route.params.id);
    loadProblemData();
  } else {
    mode.value = 'add';
    resetForm();
  }
  loadTagList();
}, { immediate: true });
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
