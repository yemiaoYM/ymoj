<template>
  <div class="auth-form-view login-form">
    <div class="form-header">
      <x-flex align="center" justify="space-between" :size="12">
        <h2>登录</h2>
        <x-flex v-if="registerEnabled" class="account-switch" align="center" :size="4">
          <span>还没有账号？</span>
          <el-button link type="primary" :disabled="loading" @click="$emit('switchTo', 'register')">注册账号</el-button>
        </x-flex>
      </x-flex>
      <p class="form-subtitle">登录喵语云，继续你的编程之旅。</p>
    </div>

    <el-form
      ref="formRef"
      class="auth-form"
      size="large"
      :model="form"
      :rules="rules"
      :hide-required-asterisk="true"
      :disabled="loading"
      @submit.prevent="handleLogin"
    >
      <el-form-item prop="username">
        <el-input
          name="username"
          aria-label="用户名"
          placeholder="请输入用户名"
          autocomplete="username"
          v-model="form.username"
          :prefix-icon="User"
        />
      </el-form-item>

      <el-form-item prop="password">
        <el-input
          name="password"
          aria-label="密码"
          placeholder="请输入密码"
          autocomplete="current-password"
          type="password"
          show-password
          v-model="form.password"
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
            @keydown.enter.prevent="handleCaptchaEnter"
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

      <x-flex justify="flex-end" :size="0">
        <el-button link type="primary" @click="$emit('switchTo', 'forget')">忘记密码？</el-button>
      </x-flex>

      <x-flex class="form-options" align="center" :size="[0, 4]">
        <el-checkbox :class="{ 'agreement-required': !agree && agreementRequired }" v-model="agree">
          我已阅读并同意
        </el-checkbox>
        <LegalAgreement />
      </x-flex>

      <el-button class="auth-button" type="primary" native-type="submit" :loading="loading">登录</el-button>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { User, Lock, Key } from '@element-plus/icons-vue';
import { ElMessage, ElNotification, type FormInstance, type FormRules } from 'element-plus';
import { useUserStore } from '@/stores/user';
import { getCaptcha } from '@/api/api';
import type { LoginDTO } from '@/type/types';
import LegalAgreement from '@/components/common/LegalAgreement.vue';

defineProps<{
  registerEnabled: boolean
}>();

//定义事件
const emit = defineEmits<{
  switchTo: [view: 'login' | 'register' | 'forget']
}>();

//数据
const userStore = useUserStore();
const formRef = ref<FormInstance>();
const loading = ref(false);
const agreementRequired = ref(false);
const captchaImage = ref('');
const form = ref<LoginDTO>({ username: '', password: '', code: '', uuid: '' });
const agree = ref(false);

const rules: FormRules<LoginDTO> = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 2, max: 20, message: '账号长度应在2到20个字符之间', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 5, max: 20, message: '密码长度应在5到20个字符之间', trigger: 'blur' },
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
  ],
};

//生命周期
onMounted(() => {
  refreshCaptcha();
});
//方法
// 输入法确认文字时不提交登录表单
const handleCaptchaEnter = (event: KeyboardEvent) => {
  if (event.isComposing || event.keyCode === 229) {
    return;
  }
  handleLogin();
};

const handleLogin = async () => {
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

    const { username, password, code, uuid } = form.value;
    await userStore.doLogin(username, password, code, uuid);

    ElNotification({
      title: '登录成功',
      message: '欢迎来到喵语云OJ',
      type: 'success',
    });
  } catch {
    refreshCaptcha();
  } finally {
    loading.value = false;
  }
};

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
