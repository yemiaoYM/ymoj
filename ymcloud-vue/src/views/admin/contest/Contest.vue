<template>
  <div class="container">
    <el-card>
      <!-- 标题和操作按钮 -->
      <template #header>
        <span style="font-size: 22px; font-weight: 600; color: #409eff">
          {{ mode === 'edit' ? '编辑比赛' : '创建比赛' }}
        </span>
      </template>
      <x-flex vertical :size="[12, 12]" style="width: 100%; padding: 15px">
        <el-skeleton v-if="loading" animated :rows="8" />
        <el-alert v-else-if="loadFailed" title="比赛加载失败，请重试" type="error" :closable="false">
          <el-button text type="primary" @click="loadContestData">重新加载</el-button>
        </el-alert>
        <!-- 基础信息 -->
        <el-form
          v-else
          :disabled="saving || uploading"
          ref="formRef"
          :model="contest"
          :rules="rules"
          label-width="auto"
          label-position="top"
          style="width: 100%"
        >
          <el-form-item label="比赛标题" prop="title">
            <el-input v-model="contest.title" placeholder="请输入比赛标题" maxlength="100" show-word-limit />
          </el-form-item>
          <el-form-item label="比赛封面" prop="image">
            <x-flex align="center" :size="20" style="width: 100%">
              <el-image
                class="contest-cover"
                fit="cover"
                alt="比赛封面"
                :src="contest.image || defaultContest"
              />
              <x-flex vertical :size="8">
                <x-flex v-if="canEdit" align="center">
                  <ImageCropUpload
                    :key="`cover-${mode}-${contestId}`"
                    :disabled="saving || uploading"
                    :aspect-ratio="[1, 1]"
                    :output-width="1024"
                    @cropped="onUploadImage"
                  >
                    <el-button type="primary" plain :loading="uploading" :disabled="saving || uploading">上传封面</el-button>
                  </ImageCropUpload>
                  <el-button :disabled="saving || uploading || !contest.image" @click="onResetImage">恢复默认</el-button>
                </x-flex>
                <el-text type="info" size="small">支持 JPEG、PNG、WebP，最大 5MB，上传前可裁剪。</el-text>
                <x-flex v-if="pendingUpload" align="center">
                  <el-text type="danger">封面上传失败</el-text>
                  <el-button text :disabled="saving || uploading" @click="onUploadImage(pendingUpload)">重试上传</el-button>
                </x-flex>
              </x-flex>
            </x-flex>
          </el-form-item>
          <x-flex :size="[20, 0]">
            <el-form-item class="contest-field" label="开始时间" prop="startTime">
              <el-date-picker
                v-model="contest.startTime"
                type="datetime"
                placeholder="选择开始时间"
                style="width: 100%"
                format="YYYY-MM-DD HH:mm:ss"
                value-format="YYYY-MM-DD HH:mm:ss"
              />
            </el-form-item>
            <el-form-item class="contest-field" label="结束时间" prop="endTime">
              <el-date-picker
                v-model="contest.endTime"
                type="datetime"
                placeholder="选择结束时间"
                style="width: 100%"
                format="YYYY-MM-DD HH:mm:ss"
                value-format="YYYY-MM-DD HH:mm:ss"
              />
            </el-form-item>
          </x-flex>
          <x-flex :size="[20, 0]">
            <el-form-item class="contest-field" label="比赛类型" prop="style">
              <el-select v-model="contest.style" style="width: 100%">
                <el-option :value="0" label="其他" />
                <el-option :value="1" label="周赛" />
                <el-option :value="2" label="月赛" />
                <el-option :value="3" label="娱乐赛" />
                <el-option :value="4" label="挑战赛" />
              </el-select>
            </el-form-item>
            <el-form-item class="contest-field" label="赛制模式" prop="mode">
              <el-select v-model="contest.mode" style="width: 100%">
                <el-option :value="0" label="ACM" />
                <el-option :value="1" label="OI" />
                <el-option :value="2" label="IOI" />
              </el-select>
            </el-form-item>
            <el-form-item class="contest-field" label="访问权限" prop="visibility">
              <el-select v-model="contest.visibility" style="width: 100%">
                <el-option :value="0" label="公开" />
                <el-option :value="1" label="私有（需密码）" />
              </el-select>
            </el-form-item>
          </x-flex>
          <el-form-item v-if="contest.visibility === 1" label="比赛密码" prop="password">
            <el-input
              v-model="contest.password"
              placeholder="请输入比赛密码（私有比赛必填）"
              show-password
              style="width: 100%; max-width: 400px"
            />
          </el-form-item>
          <x-flex :size="[20, 0]">
            <el-form-item class="contest-field" label="是否计入Rating" prop="rated">
              <el-radio-group v-model="contest.rated">
                <el-radio :value="0">否</el-radio>
                <el-radio :value="1">是</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item class="contest-field" label="允许结束后提交" prop="allowEndSubmit">
              <el-switch v-model="contest.allowEndSubmit" />
            </el-form-item>
          </x-flex>

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
      </x-flex>
      <template #footer>
        <el-button
          v-permission="mode === 'edit' ? ['contest:contest:edit'] : ['contest:contest:add']"
          size="large"
          type="primary"
          :loading="saving"
          :disabled="loading || loadFailed || uploading"
          @click="handleSubmitContest"
        >
          {{ mode === 'edit' ? '更新' : '保存' }}
        </el-button>
        <el-popconfirm v-if="mode === 'add'" title="清空当前内容及本地草稿？" @confirm="handleDiscardDraft">
          <template #reference>
            <el-button size="large" type="warning" :disabled="saving || uploading">清空</el-button>
          </template>
        </el-popconfirm>
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
//import
import { computed, ref, defineAsyncComponent, watch, nextTick, onBeforeUnmount } from "vue";
import { ElMessage } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { isDraftRecord, useLocalDraft } from "@/composables/useLocalDraft";
import { useAsyncChange } from "@/composables/useAsyncChange";
import { useRoute, useRouter } from "vue-router";
import ImageCropUpload from '@/components/common/ImageCropUpload.vue';
import defaultContest from '@/assets/image/defaultContest.jpg';
import { uploadContestImage } from '@/api/contest';
import { useUserStore } from '@/stores/user';
import { addAdminContest, updateAdminContest, getAdminContestById } from "@/api/api";

//异步导入markdown
const MarkdownEidt = defineAsyncComponent(
  () => import("@/components/common/MarkdownEidt.vue")
);

//data
const route = useRoute();
const router = useRouter();
const loading = ref(false);
const { pending: saving, beforeChange: saveContest } = useAsyncChange();
const uploading = ref(false);
const pendingUpload = ref<Blob | null>(null);
const userStore = useUserStore();
const canEdit = computed(() => userStore.hasEveryPermission([
  mode.value === 'edit' ? 'contest:contest:edit' : 'contest:contest:add',
]));
const loadFailed = ref(false);
const formRef = ref<FormInstance>();
const mode = ref('add');
const contestId = ref<number | null>(null);

// 验证比赛时间
const validateContestTime = (_rule: unknown, _value: unknown, callback: (error?: Error) => void) => {
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
const rules: FormRules<ContestForm> = {
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
      validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
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

type ContestForm = {
  id: number | null;
  title: string;
  image: string | null;
  description: string;
  sourceId: number;
  rated: number;
  mode: number;
  style: number;
  visibility: number;
  password: string;
  startTime: string | null;
  endTime: string | null;
  allowEndSubmit: boolean;
};
type ContestDraft = Omit<ContestForm, 'password'>;

function createForm(): ContestForm {
  return {
    id: null,
    title: '',
    image: null,
    description: '',
    sourceId: 0,
    rated: 0,
    mode: 0,
    style: 0,
    visibility: 0,
    password: '',
    startTime: '',
    endTime: '',
    allowEndSubmit: false,
  };
}

const contest = ref<ContestForm>(createForm());
const draftActive = ref(false);
let revision = 0;
const localDraft = useLocalDraft<ContestDraft>(
  'contest',
  () => {
    const { password, ...fields } = contest.value;
    return fields;
  },
  () => draftActive.value,
  (value): value is ContestDraft => {
    if (!isDraftRecord(value)) return false;
    return value.id === null && !('password' in value)
      && (value.image === null || typeof value.image === 'string')
      && ['title', 'description'].every(key => typeof value[key] === 'string')
      && ['startTime', 'endTime'].every(key => value[key] === null || typeof value[key] === 'string')
      && ['sourceId', 'rated', 'mode', 'style', 'visibility'].every(key => typeof value[key] === 'number')
      && typeof value.allowEndSubmit === 'boolean';
  },
);

function handleDiscardDraft() {
  pendingUpload.value = null;
  contest.value = createForm();
  localDraft.clear();
  nextTick(() => formRef.value?.clearValidate());
}

async function onUploadImage(blob: Blob) {
  if (!canEdit.value || saving.value || uploading.value) return;
  const current = revision;
  uploading.value = true;
  try {
    const response = await uploadContestImage(blob);
    if (current !== revision) return;
    contest.value.image = response.data.data;
    pendingUpload.value = null;
  } catch {
    if (current === revision) pendingUpload.value = blob;
  } finally {
    if (current === revision) uploading.value = false;
  }
}

function onResetImage() {
  contest.value.image = null;
  pendingUpload.value = null;
}

const handleSubmitContest = async () => {
  const form = formRef.value;
  if (!form || saving.value || uploading.value || loading.value || loadFailed.value) return;
  const current = revision;
  await saveContest(async () => {
    await form.validate();
    if (current !== revision) return false;
    const { id, ...fields } = contest.value;
    const submitData = { ...fields, password: fields.visibility === 1 ? fields.password : null };
    const res = mode.value === 'edit' && id
      ? await updateAdminContest(id, submitData)
      : await addAdminContest(submitData);
    if (current !== revision) return false;
    ElMessage.success(res.data.msg);
    if (mode.value === 'edit') {
      await router.push('/admin/contest/list');
    } else {
      handleDiscardDraft();
    }
  });
};

const loadContestData = async () => {
  const id = contestId.value;
  if (!id || !Number.isSafeInteger(id) || id < 1) {
    loadFailed.value = true;
    return;
  }
  const current = ++revision;
  loading.value = true;
  loadFailed.value = false;
  try {
    const res = await getAdminContestById(id);
    if (current !== revision) return;
    const data = res.data.data;
    contest.value = {
      id: data.id,
      title: data.title,
      image: data.image ?? null,
      description: data.description,
      sourceId: data.sourceId,
      rated: data.rated,
      mode: data.mode,
      style: data.style,
      visibility: data.visibility,
      password: data.password ?? '',
      startTime: data.startTime,
      endTime: data.endTime,
      allowEndSubmit: data.allowEndSubmit,
    };
  } catch {
    if (current === revision) loadFailed.value = true;
  } finally {
    if (current === revision) loading.value = false;
  }
};

watch(() => [route.name, route.params.id], () => {
  localDraft.flush();
  draftActive.value = false;
  revision++;
  mode.value = route.name === 'AdminContestEdit' ? 'edit' : 'add';
  contestId.value = mode.value === 'edit' ? Number(route.params.id) : null;
  loading.value = false;
  loadFailed.value = false;
  uploading.value = false;
  pendingUpload.value = null;
  contest.value = createForm();
  if (mode.value === 'edit') {
    loadContestData();
  } else if (route.name === 'AdminContestAdd') {
    const cached = localDraft.restore();
    if (cached) contest.value = { ...cached, password: '' };
    draftActive.value = true;
    nextTick(() => formRef.value?.clearValidate());
  }
}, { immediate: true });

onBeforeUnmount(() => { revision++; });
</script>

<style scoped>
.contest-field {
  flex: 1 1 240px;
  min-width: 0;
}
.contest-cover {
  width: 160px;
  max-width: 100%;
  aspect-ratio: 1;
  border-radius: var(--el-border-radius-base);
}
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
