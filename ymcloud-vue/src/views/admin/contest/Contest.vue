<template>
  <div class="container">
    <el-card>
      <!-- 标题和操作按钮 -->
      <template #header>
        <span style="font-size: 22px; font-weight: 600; color: #409eff">
          {{ mode === 'edit' ? '编辑比赛' : '创建比赛' }}
        </span>
      </template>
      <n-flex vertical :size="[12, 12]" style="width: 100%; padding: 15px">
        <!-- 基础信息 -->
        <el-form
          ref="formRef"
          :model="contest"
          :rules="rules"
          label-width="auto"
          style="width: 100%"
        >
          <!-- 基本信息区域 -->
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="比赛标题" prop="title">
                <el-input v-model="contest.title" placeholder="请输入比赛标题" />
              </el-form-item>
            </el-col>
          </el-row>

          <!-- 时间设置区域 -->
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="开始时间" prop="startTime">
                <el-date-picker
                  v-model="contest.startTime"
                  type="datetime"
                  placeholder="选择开始时间"
                  style="width: 100%"
                  format="YYYY-MM-DD HH:mm:ss"
                  value-format="YYYY-MM-DD HH:mm:ss"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="结束时间" prop="endTime">
                <el-date-picker
                  v-model="contest.endTime"
                  type="datetime"
                  placeholder="选择结束时间"
                  style="width: 100%"
                  format="YYYY-MM-DD HH:mm:ss"
                  value-format="YYYY-MM-DD HH:mm:ss"
                />
              </el-form-item>
            </el-col>
          </el-row>

          <!-- 比赛设置区域 -->
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="比赛类型" prop="style">
                <el-select v-model="contest.style" style="width: 100%">
                  <el-option :value="0" label="其他" />
                  <el-option :value="1" label="周赛" />
                  <el-option :value="2" label="月赛" />
                  <el-option :value="3" label="娱乐赛" />
                  <el-option :value="4" label="挑战赛" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="赛制模式" prop="mode">
                <el-select v-model="contest.mode" style="width: 100%">
                  <el-option :value="0" label="ACM" />
                  <el-option :value="1" label="OI" />
                  <el-option :value="2" label="IOI" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="访问权限" prop="visibility">
                <el-select v-model="contest.visibility" style="width: 100%">
                  <el-option :value="0" label="公开" />
                  <el-option :value="1" label="私有（需密码）" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <!-- 比赛密码（私有比赛时显示） -->
          <el-form-item v-if="contest.visibility === 1" label="比赛密码" prop="password">
            <el-input
              v-model="contest.password"
              placeholder="请输入比赛密码（私有比赛必填）"
              show-password
              style="width: 300px"
            />
          </el-form-item>

          <!-- 其他设置 -->
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="是否计入Rating" prop="rated">
                <el-radio-group v-model="contest.rated">
                  <el-radio :value="0">否</el-radio>
                  <el-radio :value="1">是</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="允许结束后提交" prop="allowEndSubmit">
                <el-switch v-model="contest.allowEndSubmit" />
              </el-form-item>
            </el-col>
          </el-row>

          <!-- 比赛描述 -->
          <el-form-item
            label="比赛说明"
            prop="description"
            label-position="top"
            style="width: 100%"
          >
            <MarkdownEidt v-model:content="contest.description" :key="`description-${mode}-${contestId}`" />
          </el-form-item>
        </el-form>
      </n-flex>
      <template #footer>
        <el-button
          v-permission="mode === 'edit' ? ['contest:contest:edit'] : ['contest:contest:add']"
          size="large"
          type="primary"
          :loading="loading"
          @click="handleSubmitContest"
        >
          {{ mode === 'edit' ? '更新' : '保存' }}
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
import { addAdminContest, updateAdminContest, getAdminContestById } from "@/api/api";

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
const contestId = ref<number | null>(null);

// 验证比赛时间
const validateContestTime = (rule: any, value: string, callback: any) => {
  if (!contest.value.startTime || !contest.value.endTime) {
    callback();
    return;
  }
  const startTime = new Date(contest.value.startTime);
  const endTime = new Date(contest.value.endTime);
  if (startTime >= endTime) {
    callback(new Error("开始时间必须早于结束时间"));
    return;
  }
  callback();
};

//表单验证规则
const rules = {
  title: [
    { required: true, message: "比赛标题不能为空", trigger: "blur" },
    { max: 100, message: "比赛标题长度不能超过100个字符", trigger: "blur" },
  ],
  description: [
    { required: true, message: "比赛说明不能为空", trigger: "blur" },
  ],
  startTime: [
    { required: true, message: "请选择开始时间", trigger: "change" },
    { validator: validateContestTime, trigger: "change" },
  ],
  endTime: [
    { required: true, message: "请选择结束时间", trigger: "change" },
    { validator: validateContestTime, trigger: "change" },
  ],
  style: [
    { required: true, message: "请选择比赛类型", trigger: "change" },
  ],
  mode: [
    { required: true, message: "请选择赛制模式", trigger: "change" },
  ],
  visibility: [
    { required: true, message: "请选择访问权限", trigger: "change" },
  ],
  password: [
    { 
      required: true, 
      message: "私有比赛必须设置密码", 
      trigger: "blur",
      validator: (rule: any, value: string, callback: any) => {
        if (contest.value.visibility === 1 && !value) {
          callback(new Error("私有比赛必须设置密码"));
        } else {
          callback();
        }
      }
    },
  ],
  rated: [
    { required: true, message: "请选择是否计入Rating", trigger: "change" },
  ],
};

const contest = ref({
  id: null,
  title: "",
  description: "请输入比赛说明",
  sourceId: 0,
  rated: 0,
  mode: 0,
  style: 0,
  visibility: 0,
  password: "",
  startTime: "",
  endTime: "",
  allowEndSubmit: false,
});

//methods
//提交比赛（新增或编辑）
const handleSubmitContest = () => {
  if (!formRef.value) return;
  formRef.value.validate((valid: boolean) => {
    if (valid) {
      loading.value = true;
      
      //构造符合后端DTO的数据结构
      const submitData = {
        title: contest.value.title,
        description: contest.value.description,
        sourceId: contest.value.sourceId,
        rated: contest.value.rated,
        mode: contest.value.mode,
        style: contest.value.style,
        visibility: contest.value.visibility,
        password: contest.value.visibility === 1 ? contest.value.password : null,
        startTime: contest.value.startTime,
        endTime: contest.value.endTime,
        allowEndSubmit: contest.value.allowEndSubmit,
      };
      
      const api = mode.value === 'edit' && contest.value.id
        ? updateAdminContest(contest.value.id, submitData)
        : addAdminContest(submitData);
      
      api.then((res) => {
          ElMessage.success(res.data.msg);
          if (mode.value === 'edit') {
            router.push('/admin/contest/list');
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

//重置表单
const resetForm = () => {
  contest.value = {
    id: null,
    title: "",
    description: "请输入比赛说明",
    sourceId: 0,
    rated: 0,
    mode: 0,
    style: 0,
    visibility: 0,
    password: "",
    startTime: "",
    endTime: "",
    allowEndSubmit: false,
  };
  if (formRef.value) {
    formRef.value.resetFields();
  }
};

//加载比赛数据
const loadContestData = async () => {
  if (!contestId.value) return;
  try {
    loading.value = true;
    const res = await getAdminContestById(contestId.value);
    const data = res.data.data;
    
    //获取到的数据赋值给表单
    Object.assign(contest.value, {
      id: data.id,
      title: data.title,
      description: data.description,
      sourceId: data.sourceId,
      rated: data.rated,
      mode: data.mode,
      style: data.style,
      visibility: data.visibility,
      password: data.password,
      startTime: data.startTime,
      endTime: data.endTime,
      allowEndSubmit: data.allowEndSubmit,
    });
  } catch (e) {
    console.log(e);
  } finally {
    loading.value = false;
  }
};

//监听路由参数变化
watch(() => route.name, () => {
  if (route.name === 'AdminContestEdit' && route.params.id) {
    mode.value = 'edit';
    contestId.value = Number(route.params.id);
    loadContestData();
  } else {
    mode.value = 'add';
    resetForm();
  }
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
