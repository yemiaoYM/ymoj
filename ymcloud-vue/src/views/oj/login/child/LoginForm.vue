<template>
  <div class="login-form-container">
    <div class="form-header">
      <h2>登录你的账户</h2>
      <p class="form-subtitle">欢迎回来！请输入您的详细信息</p>
    </div>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      :hide-required-asterisk="true"
      class="auth-form"
      size="large"
      @submit.prevent="handleLogin"
    >
      <el-form-item prop="username">
        <el-input
          v-model="form.username"
          name="username"
          placeholder="请输入用户名"
          :prefix-icon="User"
          autocomplete="username"
        />
      </el-form-item>

      <el-form-item prop="password" class="password-item">
        <el-input
          v-model="form.password"
          name="password"
          type="password"
          placeholder="请输入密码"
          :prefix-icon="Lock"
          show-password
          :autocomplete="rememberPassword ? 'current-password' : 'off'"
        />
      </el-form-item>

      <div class="password-options">
        <el-tooltip content="使用浏览器密码管理器记住密码，请勿在公共设备上启用" placement="top">
          <el-checkbox v-model="rememberPassword" :disabled="loading" @change="handleRememberChange">
            记住密码
          </el-checkbox>
        </el-tooltip>
        <el-link type="primary" :underline="false" @click="$emit('switchTo', 'forget')">
          忘记密码？
        </el-link>
      </div>

      <el-form-item prop="code" class="captcha-item">
        <el-input
          v-model="form.code"
          placeholder="请输入验证码"
          :prefix-icon="Key"
          style="flex: 1; margin-right: 12px;"
          @keydown.enter.prevent="handleCaptchaEnter"
        />
        <el-image :src="captchaImage" @click="refreshCaptcha" class="captcha-image" alt="验证码" />
      </el-form-item>

      <div class="form-options">
        <el-checkbox v-model="agree" :class="{ shake: !agree && shakeCheckbox }">
          我已阅读并同意
        </el-checkbox>
        <LegalAgreement />
      </div>

      <div class="form-buttons">
        <el-button type="primary" native-type="submit" class="auth-button" :loading="loading">
          登录
        </el-button>
        <el-button class="auth-button secondary" :disabled="loading" @click="resetForm">
          重置
        </el-button>
      </div>
    </el-form>

    <div v-if="registerEnabled" class="form-footer">
      <p>还没有账户？</p>
      <el-link type="primary" @click="$emit('switchTo', 'register')">
        立即注册
      </el-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { User, Lock, Key } from '@element-plus/icons-vue';
import { ElMessage, ElNotification } from 'element-plus';
import { useUserStore } from '@/stores/user';
import { getCaptcha } from '@/api/api';
import type { LoginDTO } from '@/type/types';
import LegalAgreement from '@/components/common/LegalAgreement.vue';
import {
  clearRememberedLogin,
  getRememberedPassword,
  getRememberedUsername,
  rememberLogin
} from '@/utils/rememberLogin';

defineProps<{
  registerEnabled: boolean
}>();

//定义事件
const emit = defineEmits<{
  switchTo: [view: 'login' | 'register' | 'forget']
}>();

//数据
const userStore = useUserStore();
const formRef = ref();
const loading = ref(false);
const shakeCheckbox = ref(false);
const captchaImage = ref('');
const rememberedUsername = getRememberedUsername();
const form = ref({ username: rememberedUsername } as LoginDTO);
const agree = ref(false);
const rememberPassword = ref(Boolean(rememberedUsername));

const rules = {
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
  loadCaptcha();
  restoreRememberedPassword();
});
//方法
const resetForm = () => {
  form.value = { uuid: form.value.uuid } as LoginDTO;
  agree.value = false;
  shakeCheckbox.value = false;
};

const handleRememberChange = () => {
  if (!rememberPassword.value) {
    clearRememberedLogin();
  }
};

const restoreRememberedPassword = async () => {
  if (!rememberPassword.value) {
    return;
  }
  const password = await getRememberedPassword(rememberedUsername);
  if (rememberPassword.value && form.value.username === rememberedUsername && !form.value.password) {
    form.value.password = password;
  }
};

// 输入法确认文字时不提交登录表单。
const handleCaptchaEnter = (event: KeyboardEvent) => {
  if (event.isComposing || event.keyCode === 229) {
    return;
  }
  handleLogin();
};

const handleLogin = async () => {
  if (loading.value) {
    return;
  }
  loading.value = true;
  try {
    const valid = await formRef.value.validate();
    if (!valid) {
      ElMessage.warning('请填写正确信息');
      return;
    }
    if (!agree.value) {
      shakeCheckbox.value = true;
      setTimeout(() => (shakeCheckbox.value = false), 500);
      ElMessage.warning('请阅读并同意用户协议和隐私政策');
      return;
    }

    const { username, password, code, uuid } = form.value;
    await userStore.doLogin(username, password, code, uuid);
    if (rememberPassword.value) {
      void rememberLogin(username.trim(), password);
    } else {
      clearRememberedLogin();
    }

    ElNotification({
      title: '登录成功',
      message: '欢迎来到喵语云OJ',
      type: 'success',
    });
  } catch (e) {
    refreshCaptcha();
    console.log(e);
  } finally {
    loading.value = false;
  }
};

const loadCaptcha = () => {
  getCaptcha().then(res => {
    form.value.uuid = res.data.data.uuid;
    captchaImage.value = res.data.data.image;
  }).catch(e => {
    console.log(e);
  });
};

const refreshCaptcha = () => {
  loadCaptcha();
};
</script>

<style scoped>
.login-form-container {
  width: 100%;
  max-width: 400px;
}

.form-header {
  text-align: center;
  margin-bottom: 32px;
}

.form-header h2 {
  font-size: 28px;
  font-weight: 600;
  color: #1a202c;
  margin: 0 0 8px 0;
}

.form-subtitle {
  color: #718096;
  font-size: 16px;
  margin: 0;
}

.auth-form {
  margin-bottom: 24px;
}

.auth-form .el-form-item {
  margin-bottom: 24px;
}

.auth-form .password-item {
  margin-bottom: 8px;
}

.password-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}

.password-options .el-checkbox {
  margin-right: 0;
}

.auth-form :deep(.el-input__inner) {
  font-size: 16px;
}

.auth-form :deep(.el-checkbox__label) {
  font-size: 15px;
}

.auth-form :deep(.el-input__wrapper) {
  border-radius: 10px;
}

.captcha-item :deep(.el-form-item__content) {
  display: flex;
  align-items: center;
}

.captcha-image {
  width: 100px;
  height: 40px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s;
  object-fit: cover;
}

.captcha-image:hover {
  border-color: #409EFF;
}

.form-options {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  margin-bottom: 24px;
}

.form-options .el-checkbox {
  margin-right: 0;
}

.form-buttons {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
}

.auth-button {
  flex: 1;
  height: 40px;
  font-size: 16px;
  border-radius: 10px;
}

.auth-button.secondary {
  background: #f5f5f5;
  border-color: #d9d9d9;
  color: #666;
}

.form-footer {
  text-align: center;
  margin-top: 16px;
}

.form-footer p {
  color: #718096;
  font-size: 14px;
  margin: 0 0 8px 0;
}

@keyframes shake {
  0% {
    transform: translateX(0);
  }

  25% {
    transform: translateX(-5px);
  }

  50% {
    transform: translateX(5px);
  }

  75% {
    transform: translateX(-5px);
  }

  100% {
    transform: translateX(0);
  }
}

.shake {
  animation: shake 0.5s ease;
}
</style>
