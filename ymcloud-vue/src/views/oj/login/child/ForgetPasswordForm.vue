<template>
  <div class="auth-form-view">
    <div class="form-header">
      <x-flex align="center" justify="space-between" :size="12">
        <h2>找回密码</h2>
        <x-flex class="account-switch" align="center" :size="4">
          <span>想起密码了？</span>
          <el-button link type="primary" :disabled="loading || sendingCode" @click="$emit('switchTo', 'login')">返回登录</el-button>
        </x-flex>
      </x-flex>
      <p class="form-subtitle">验证绑定邮箱，为你的账号设置新密码。</p>
    </div>

    <el-form
      ref="formRef"
      class="auth-form"
      size="large"
      :model="form"
      :rules="rules"
      :hide-required-asterisk="true"
      :disabled="loading"
      @submit.prevent="onResetPassword"
    >
      <el-form-item prop="username">
        <el-input
          name="username"
          aria-label="用户名"
          placeholder="请输入注册时的用户名"
          autocomplete="username"
          v-model="form.username"
          :prefix-icon="User"
        />
      </el-form-item>

      <el-form-item prop="email">
        <el-input
          name="email"
          aria-label="邮箱"
          placeholder="请输入账号绑定的邮箱"
          autocomplete="email"
          inputmode="email"
          v-model="form.email"
          :prefix-icon="Message"
        />
      </el-form-item>

      <el-form-item prop="code">
        <x-flex class="email-code-row" align="center" :size="10" :wrap="false">
          <el-input-otp
            class="code-input"
            size="default"
            inputmode="numeric"
            aria-label="六位数字邮箱验证码"
            v-model="form.code"
            :length="6"
            :validator="handleValidateCodeDigit"
          />
          <el-button
            class="send-code-button"
            type="primary"
            plain
            :loading="sendingCode"
            :disabled="isCountingDown"
            @click="onSendCode"
          >
            <el-countdown
              v-if="isCountingDown"
              format="ss [秒后重试]"
              :value="retryAt"
              :value-style="{ fontSize: 'inherit', color: 'inherit' }"
              @finish="onCountdownFinish"
            />
            <span v-else>发送验证码</span>
          </el-button>
        </x-flex>
      </el-form-item>

      <el-form-item prop="password">
        <el-input
          name="password"
          aria-label="新密码"
          placeholder="设置 5–20 位新密码"
          autocomplete="new-password"
          type="password"
          show-password
          v-model="form.password"
          :prefix-icon="Lock"
        />
      </el-form-item>

      <el-form-item prop="rePassword">
        <el-input
          name="rePassword"
          aria-label="确认新密码"
          placeholder="再次输入新密码"
          autocomplete="new-password"
          type="password"
          show-password
          v-model="form.rePassword"
          :prefix-icon="Lock"
        />
      </el-form-item>

      <el-button
        class="auth-button"
        type="primary"
        native-type="submit"
        :loading="loading"
        :disabled="sendingCode"
      >重置密码</el-button>
    </el-form>
    <p class="form-hint">验证码将发送至绑定邮箱，请注意查收。</p>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { Lock, Message, User } from '@element-plus/icons-vue';
import { ElMessage, type FormInstance, type FormItemRule, type FormRules } from 'element-plus';
import { forgetPasswordSendCode, forgetPasswordResetPassword } from '@/api/user/auth';
import { useAsyncChange } from '@/hooks/useAsyncChange';

//定义事件
const emit = defineEmits<{
  switchTo: [view: 'login' | 'register' | 'forget']
}>();

//数据
const formRef = ref<FormInstance>();
const { pending: loading, beforeChange: resetPassword } = useAsyncChange();
const { pending: sendingCode, beforeChange: sendCode } = useAsyncChange();
const isCountingDown = ref<boolean>(false);
const retryAt = ref(0);
const form = ref({
  username: '',
  email: '',
  code: '',
  password: '',
  rePassword: '',
});
const uuid = ref<string>('');

const validatePassword: FormItemRule['validator'] = (rule, value, callback) => {
  if (value !== form.value.password) {
    callback(new Error('两次输入的密码不一致'));
  } else {
    callback();
  }
};
const rules: FormRules<typeof form.value> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '账号长度应在 2 到 20 个字符之间', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '请输入有效的邮箱地址', trigger: ['blur', 'change'] },
  ],
  code: [
    { required: true, message: '请输入6位验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '验证码必须是6位数字', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 5, max: 20, message: '密码长度在5到20之间', trigger: 'blur' }
  ],
  rePassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validatePassword, message: '两次输入的密码不一致!', trigger: 'blur' },
  ],
};

//methods
//发送验证码
const onSendCode = async () => {
  const formInstance = formRef.value;
  if (!formInstance || loading.value || isCountingDown.value) {
    return;
  }
  const sent = await sendCode(async () => {
    await formInstance.validateField(['username', 'email']);
    const res = await forgetPasswordSendCode({
      username: form.value.username,
      email: form.value.email,
    });
    uuid.value = res.data.data;
  });
  if (sent) {
    ElMessage.success('验证码已发送，请注意查收！');
    retryAt.value = Date.now() + 59000;
    isCountingDown.value = true;
  }
};
//重置密码
const onResetPassword = async () => {
  const formInstance = formRef.value;
  if (!formInstance || sendingCode.value) {
    return;
  }
  const saved = await resetPassword(async () => {
    await formInstance.validate();
    const res = await forgetPasswordResetPassword({
      username: form.value.username,
      password: form.value.password,
      rePassword: form.value.rePassword,
      code: form.value.code,
      uuid: uuid.value
    });
    ElMessage.success(res.data.msg);
  });
  if (saved) {
    emit('switchTo', 'login');
  }
};
//逐位校验；验证码始终使用字符串，保留开头的 0
const handleValidateCodeDigit = (value: string) => value === '' || /^\d$/.test(value);
//倒计时结束
const onCountdownFinish = () => {
  isCountingDown.value = false;
};
</script>

<style scoped src="@/assets/styles/auth-form.css"></style>
