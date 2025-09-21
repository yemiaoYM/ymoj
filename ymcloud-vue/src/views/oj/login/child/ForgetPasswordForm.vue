<template>
  <div class="forget-form-container">
    <div class="form-header">
      <h2>重置密码</h2>
      <p class="form-subtitle">请输入您的邮箱地址，我们将发送重置链接</p>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" :hide-required-asterisk="true" class="auth-form" size="large">
      <el-form-item prop="username">
        <el-input :prefix-icon="User" v-model="form.username" placeholder="请输入您的用户名" />
      </el-form-item>

      <el-form-item prop="email">
        <el-input v-model="form.email" placeholder="请输入邮箱地址" :prefix-icon="Message" />
      </el-form-item>

      <el-form-item label="验证码" prop="code">
        <n-flex style="width: 100%;">
          <n-input-otp block v-model:value="form.code" style="flex: 8;"/>
          <el-button style="flex: 2;" effect="dark" type="primary" @click="sendCode" :disabled="isCountingDown">
            <n-countdown
              v-if="isCountingDown"
              :duration="59000"
              :active="isCountingDown"
              :on-finish="finishCountdown"
              :render="renderCountdown"
            />
            <span v-else>发送验证码</span>
          </el-button>
        </n-flex>
      </el-form-item>

      <el-form-item prop="password">
        <el-input v-model="form.password" type="password" placeholder="请输入新密码" :prefix-icon="Lock" show-password
          autocomplete="new-password" />
      </el-form-item>

      <el-form-item prop="rePassword">
        <el-input v-model="form.rePassword" type="password" placeholder="请确认新密码" :prefix-icon="Lock" show-password
          autocomplete="new-password" />
      </el-form-item>

      <div class="form-buttons">
        <el-button type="primary" class="auth-button" :loading="loading" @click="resetPassword">
          确认重置
        </el-button>
      </div>
    </el-form>

    <div class="form-footer">
      <p>想起密码了？</p>
      <el-link type="primary" @click="$emit('switchTo', 'login')">
        返回登录
      </el-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { Lock, Message, User } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { forgetPasswordSendCode, forgetPasswordResetPassword } from '@/api/api';

//定义事件
const emit = defineEmits<{
  switchTo: [view: 'login' | 'register' | 'forget']
}>();

//数据
const formRef = ref();
const loading = ref<boolean>(false);
const isCountingDown = ref<boolean>(false);
const form = ref({
  username: '',
  email: '',
  code: ''.split(''),
  password: '',
  rePassword: '',
});
const uuid = ref<string>('');

const validatePassword = (rule: any, value: string, callback: Function) => {
  if (value !== form.value.password) {
    callback(new Error('两次输入的密码不一致'));
  } else {
    callback();
  }
};
const rules = ref({
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '账号长度应在 2 到 20 个字符之间', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '请输入有效的邮箱地址', trigger: ['blur', 'change'] },
  ],
  code: [{ required: true, message: '请输入6位验证码', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 5, max: 20, message: '密码长度在5到20之间', trigger: 'blur' }
  ],
  rePassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validatePassword, message: '两次输入的密码不一致!', trigger: 'blur' },
  ],
});

//methods
//发送验证码
const sendCode = () => {
  forgetPasswordSendCode({
    username: form.value.username,
    email: form.value.email
  }).then(res => {
    uuid.value = res.data.data;
    ElMessage.success('验证码已发送，请注意查收！');
    isCountingDown.value = true;
  }).catch(e => {
    console.log(e);
  });
};
//重置密码
const resetPassword = async () => {
  if(!formRef.value)return;
  loading.value = true;
  try{
    const valid = await formRef.value.validate();
    if(!valid){
      ElMessage.warning('请填写正确信息');
      return;
    }
    const res = await forgetPasswordResetPassword({
      username: form.value.username,
      password: form.value.password,
      rePassword: form.value.rePassword,
      code: form.value.code.join(''),
      uuid: uuid.value
    });
    ElMessage.success(res.data.msg);
  }catch(e){
    console.log(e);
  }finally{
    loading.value = false;
  }
};
//倒计时发送验证码
const renderCountdown = ({ seconds }: { seconds: number }) => {
  return `${String(seconds).padStart(2, '0')} 秒后重试`;
};
//倒计时结束
const finishCountdown = () => {
  isCountingDown.value = false;
};
</script>

<style scoped>
.forget-form-container {
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

.auth-form :deep(.el-input__wrapper) {
  border-radius: 10px;
}

.captcha-item :deep(.el-form-item__content) {
  display: flex;
  align-items: center;
}

.email-code-item {
  display: flex;
  align-items: center;
  width: 100%;
}

.send-code-btn {
  height: 40px;
  border-radius: 10px;
  white-space: nowrap;
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
</style>
