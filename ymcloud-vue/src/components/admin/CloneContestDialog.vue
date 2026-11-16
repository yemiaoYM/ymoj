<template>
  <el-dialog
    title="克隆比赛"
    width="min(560px, calc(100vw - 32px))"
    :model-value="modelValue"
    :close-on-click-modal="false"
    :close-on-press-escape="!pending"
    :show-close="!pending"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" label-position="top" :model="form" :rules="rules" :disabled="pending">
      <el-form-item label="新比赛标题" prop="title">
        <el-input v-model="form.title" maxlength="100" show-word-limit />
      </el-form-item>
      <el-form-item label="访问类型" prop="visibility">
        <el-select v-model="form.visibility">
          <el-option label="公开" :value="CONTEST_VISIBILITY.PUBLIC" />
          <el-option label="密码" :value="CONTEST_VISIBILITY.PASSWORD" />
          <el-option label="私有" :value="CONTEST_VISIBILITY.PRIVATE" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="form.visibility === CONTEST_VISIBILITY.PASSWORD" label="新比赛密码" prop="password">
        <el-input v-model="form.password" type="password" show-password autocomplete="new-password" />
      </el-form-item>
      <el-form-item label="开始时间" prop="startTime">
        <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" />
      </el-form-item>
      <el-form-item label="结束时间" prop="endTime">
        <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" />
      </el-form-item>
      <el-text type="info">共用原题，保留原题权限；不会复制报名、提交和评测结果。</el-text>
    </el-form>
    <template #footer>
      <el-button :disabled="pending" @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="pending" @click="onClone">创建克隆赛</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { CONTEST_VISIBILITY } from '@/constants/contest';
import { ref, watch, nextTick } from 'vue';
import { ElMessage } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import type { CloneContestRequest } from '@/types/contest';
import { cloneAdminContest } from '@/api/contest';
import { useAsyncChange } from '@/hooks/useAsyncChange';

const props = defineProps<{ modelValue: boolean; sourceId: number; title: string }>();
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; created: [] }>();
const formRef = ref<FormInstance>();
const { pending, beforeChange } = useAsyncChange();
const form = ref<CloneContestRequest>({
  title: '',
  visibility: CONTEST_VISIBILITY.PRIVATE,
  password: null,
  startTime: '',
  endTime: '',
});
const rules: FormRules<CloneContestRequest> = {
  title: [
    { required: true, message: '请输入新比赛标题', trigger: 'blur' },
    { max: 100, message: '标题不能超过100个字符', trigger: 'blur' },
  ],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [
    { required: true, message: '请选择结束时间', trigger: 'change' },
    {
      validator: (_rule, value, callback) => {
        if (form.value.startTime && value && new Date(value) <= new Date(form.value.startTime)) {
          callback(new Error('结束时间必须晚于开始时间'));
        } else {
          callback();
        }
      },
      trigger: 'change',
    },
  ],
  password: [
    {
      validator: (_rule, value, callback) => {
        if (form.value.visibility === CONTEST_VISIBILITY.PASSWORD
          && (typeof value !== 'string' || value.length < 5 || value.length > 20)) {
          callback(new Error('请输入5-20位新密码'));
        } else {
          callback();
        }
      },
      trigger: 'blur',
    },
  ],
};

watch(() => props.modelValue, async (open) => {
  if (open) {
    form.value = {
      title: `${props.title} (克隆)`,
      visibility: CONTEST_VISIBILITY.PRIVATE,
      password: null,
      startTime: '',
      endTime: '',
    };
    await nextTick();
    formRef.value?.clearValidate();
  } else {
    form.value.password = null;
  }
});

async function onClone() {
  const instance = formRef.value;
  if (!instance) {
    return;
  }
  const saved = await beforeChange(async () => {
    await instance.validate();
    await cloneAdminContest(props.sourceId, {
      ...form.value,
      password: form.value.visibility === CONTEST_VISIBILITY.PASSWORD ? form.value.password : null,
    });
  });
  if (saved) {
    ElMessage.success('克隆比赛已创建');
    emit('update:modelValue', false);
    emit('created');
  }
}
</script>
