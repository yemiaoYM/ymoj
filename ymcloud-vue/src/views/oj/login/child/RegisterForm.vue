<template>
  <div class="register-form-container">
    <div class="form-header">
      <h2>创建新账户</h2>
      <p class="form-subtitle">加入我们，开始您的编程之旅</p>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" :hide-required-asterisk="true" class="auth-form" size="large">
      <el-form-item prop="username">
        <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" />
      </el-form-item>

      <el-form-item prop="email">
        <el-input v-model="form.email" placeholder="请输入邮箱地址" :prefix-icon="Message" />
      </el-form-item>

      <el-form-item prop="password">
        <el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password
          autocomplete="new-password" />
      </el-form-item>

      <el-form-item prop="rePassword">
        <el-input v-model="form.rePassword" type="password" placeholder="请确认密码" :prefix-icon="Lock" show-password
          autocomplete="new-password" />
      </el-form-item>

      <el-form-item prop="code" class="captcha-item">
        <el-input v-model="form.code" placeholder="请输入验证码" :prefix-icon="Key" style="flex: 1; margin-right: 12px;" />
        <el-image :src="captchaImage" @click="refreshCaptcha" class="captcha-image" alt="验证码" />
      </el-form-item>

      <div class="form-options">
        <el-checkbox v-model="agree" :class="{ shake: !agree && shakeCheckbox }">
          我已阅读并同意用户协议和隐私政策
        </el-checkbox>
      </div>

      <div class="form-buttons">
        <el-button type="primary" class="auth-button" :loading="loading" @click="handleRegister">
          注册
        </el-button>
        <el-button class="auth-button secondary" @click="resetForm">
          重置
        </el-button>
      </div>
    </el-form>

    <div class="form-footer">
      <p>已有账户？</p>
      <el-link type="primary" @click="$emit('switchTo', 'login')">
        立即登录
      </el-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { User, Lock, Key, Message } from '@element-plus/icons-vue';
import { ElMessage, ElNotification } from 'element-plus';
import { useUserStore } from '@/stores/user';
import { getCaptcha } from '@/api/api';
import type { RegisterDTO } from '@/type/types';

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
const form = ref({} as RegisterDTO);
const agree = ref(false);

//密码验证
const validatePassword = (rule: any, value: string, callback: Function) => {
  if (value !== form.value.password) {
    callback(new Error('两次输入的密码不一致'));
  } else {
    callback();
  }
};
const rules = {
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
  loadCaptcha();
});
//重置
const resetForm = () => {
  form.value = {} as RegisterDTO;
  agree.value = false;
  shakeCheckbox.value = false;
  loading.value = false;
};
//注册
const handleRegister = async () => {
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
      ElMessage.warning('请同意用户协议');
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
  } catch (e) {
    refreshCaptcha();
    console.log(e);
  } finally {
    loading.value = false;
  }
};
//验证码
const loadCaptcha = () => {
  getCaptcha().then(res => {
    form.value.uuid = res.data.data.uuid;
    captchaImage.value = res.data.data.image;
  }).catch(e => {
    console.log(e);
  });
};
//刷新验证码
const refreshCaptcha = () => {
  loadCaptcha();
};
</script>

<style scoped>
.register-form-container {
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
  margin-bottom: 20px;
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
  margin-bottom: 24px;
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
