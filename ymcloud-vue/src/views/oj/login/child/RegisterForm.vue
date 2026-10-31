<template>
  <div class="auth-form-view">
    <div class="form-header">
      <x-flex align="center" justify="space-between" :size="12">
        <h2>创建账号</h2>
        <x-flex class="account-switch" align="center" :size="4">
          <span>已有账号？</span>
          <el-button link type="primary" :disabled="loading" @click="$emit('switchTo', 'login')">去登录</el-button>
        </x-flex>
      </x-flex>
      <p class="form-subtitle">从这里出发，让每一行代码都有意义。</p>
    </div>

    <el-form
      ref="formRef"
      class="auth-form"
      size="large"
      :model="form"
      :rules="rules"
      :hide-required-asterisk="true"
      :disabled="loading"
      @submit.prevent="handleRegister"
    >
      <el-form-item prop="username">
        <el-input
          name="username"
          aria-label="用户名"
          placeholder="用户名（2–20 个字符）"
          autocomplete="username"
          v-model="form.username"
          :prefix-icon="User"
        />
      </el-form-item>

      <el-form-item prop="email">
        <el-input
          name="email"
          aria-label="邮箱"
          placeholder="用于找回密码，请填写常用邮箱"
          autocomplete="email"
          inputmode="email"
          v-model="form.email"
          :prefix-icon="Message"
        />
      </el-form-item>

      <el-form-item prop="password">
        <el-input
          name="password"
          aria-label="密码"
          placeholder="设置 5–20 位密码"
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
          aria-label="确认密码"
          placeholder="再次输入密码"
          autocomplete="new-password"
          type="password"
          show-password
          v-model="form.rePassword"
          :prefix-icon="Lock"
        />
      </el-form-item>

      <el-form-item prop="code" for="auth-captcha">
        <x-flex class="captcha-row" align="center" :size="12" :wrap="false">
          <el-input
            id="auth-captcha"
            aria-label="图形验证码"
            placeholder="请输入验证码"
            autocomplete="off"
            v-model="form.code"
            :prefix-icon="Key"
          />
          <el-button
            class="captcha-button"
            native-type="button"
            aria-label="刷新图形验证码"
            title="点击刷新验证码"
            :disabled="loading"
            @click="refreshCaptcha"
          >
            <img v-if="captchaImage" alt="图形验证码" :src="captchaImage" />
            <span v-else>点击获取</span>
          </el-button>
        </x-flex>
      </el-form-item>

      <x-flex class="form-options" align="center" :size="[0, 4]">
        <el-checkbox :class="{ 'agreement-required': !agree && agreementRequired }" v-model="agree">
          我已阅读并同意
        </el-checkbox>
        <LegalAgreement />
      </x-flex>

      <el-button class="auth-button" type="primary" native-type="submit" :loading="loading">创建账号</el-button>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { User, Lock, Key, Message } from '@element-plus/icons-vue';
import { ElMessage, ElNotification, type FormInstance, type FormItemRule, type FormRules } from 'element-plus';
import { useUserStore } from '@/stores/user';
import { getCaptcha } from '@/api/api';
import type { RegisterDTO } from '@/type/types';
import LegalAgreement from '@/components/common/LegalAgreement.vue';

//定义事件
const emit = defineEmits<{
  switchTo: [view: 'login' | 'register' | 'forget']
  refreshSetting: []
}>();

//数据
const userStore = useUserStore();
const formRef = ref<FormInstance>();
const loading = ref(false);
const agreementRequired = ref(false);
const captchaImage = ref('');
const form = ref<RegisterDTO>({ username: '', email: '', password: '', rePassword: '', code: '', uuid: '' });
const agree = ref(false);

//密码验证
const validatePassword: FormItemRule['validator'] = (rule, value, callback) => {
  if (value !== form.value.password) {
    callback(new Error('两次输入的密码不一致'));
  } else {
    callback();
  }
};
const rules: FormRules<RegisterDTO> = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 2, max: 20, message: '账号长度应在 2 到 20 个字符之间', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 5, max: 20, message: '密码长度应在 5 到 20 个字符之间', trigger: 'blur' },
  ],
  rePassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validatePassword, trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9_-]+(\.[a-zA-Z0-9_-]+)*@[a-zA-Z0-9_-]+(\.[a-zA-Z0-9_-]+)*(\.[a-zA-Z]{2,})$/,
      message: '请输入有效的邮箱地址',
      trigger: ['blur', 'change'],
    },
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
  ],
};

//生命周期
onMounted(() => {
  refreshCaptcha();
});
//注册
const handleRegister = async () => {
  if (!formRef.value || loading.value) {
    return;
  }
  loading.value = true;
  try {
    let valid = false;
    await formRef.value.validate((passed) => {
      valid = passed;
    });
    if (!valid) {
      return;
    }
    if (!agree.value) {
      agreementRequired.value = true;
      ElMessage.warning('请阅读并同意用户协议和隐私政策');
      return;
    }

    await userStore.doRegister(
      form.value.username,
      form.value.password,
      form.value.rePassword,
      form.value.email,
      form.value.code,
      form.value.uuid
    );

    ElNotification({
      title: '注册成功',
      message: '欢迎加入YMOJ！',
      type: 'success',
    });

    // 注册成功后切换到登录页面
    emit('switchTo', 'login');
  } catch {
    emit('refreshSetting');
    refreshCaptcha();
  } finally {
    loading.value = false;
  }
};
//刷新验证码
const refreshCaptcha = () => {
  getCaptcha().then(res => {
    form.value.uuid = res.data.data.uuid;
    captchaImage.value = res.data.data.image;
  }).catch(() => {
    captchaImage.value = '';
  });
};
</script>

<style scoped src="../auth-form.css"></style>
