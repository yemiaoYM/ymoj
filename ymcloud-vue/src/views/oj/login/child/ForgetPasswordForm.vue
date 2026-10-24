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
        <x-flex style="width: 100%;">
          <el-input-otp
            class="code-input"
            size="default"
            inputmode="numeric"
            aria-label="六位数字验证码"
            v-model="form.code"
            :length="6"
            :validator="handleValidateCodeDigit"
          />
          <el-button
            style="flex: 2;"
            type="primary"
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
        <el-input v-model="form.password" type="password" placeholder="请输入新密码" :prefix-icon="Lock" show-password
          autocomplete="new-password" />
      </el-form-item>

      <el-form-item prop="rePassword">
        <el-input v-model="form.rePassword" type="password" placeholder="请确认新密码" :prefix-icon="Lock" show-password
          autocomplete="new-password" />
      </el-form-item>

      <div class="form-buttons">
        <el-button class="auth-button" type="primary" :loading="loading" @click="onResetPassword">
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
import { ElMessage, type FormInstance, type FormItemRule, type FormRules } from 'element-plus';
import { forgetPasswordSendCode, forgetPasswordResetPassword } from '@/api/api';

//定义事件
const emit = defineEmits<{
  switchTo: [view: 'login' | 'register' | 'forget']
}>();

//数据
const formRef = ref<FormInstance>();
const loading = ref<boolean>(false);
const sendingCode = ref(false);
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
const rules: FormRules = {
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
  if (!formRef.value || sendingCode.value || isCountingDown.value) return;
  sendingCode.value = true;
  try {
    await formRef.value.validateField(['username', 'email']);
    const res = await forgetPasswordSendCode({
      username: form.value.username,
      email: form.value.email,
    });
    uuid.value = res.data.data;
    ElMessage.success('验证码已发送，请注意查收！');
    retryAt.value = Date.now() + 59000;
    isCountingDown.value = true;
  } catch {
    // 字段错误由表单显示；请求失败由统一请求层提示，保留重试入口。
  } finally {
    sendingCode.value = false;
  }
};
//重置密码
const onResetPassword = async () => {
  if (!formRef.value || loading.value) return;
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
      code: form.value.code,
      uuid: uuid.value
    });
    ElMessage.success(res.data.msg);
  }catch(e){
    console.log(e);
  }finally{
    loading.value = false;
  }
};
//逐位校验；验证码始终使用字符串，保留开头的 0。
const handleValidateCodeDigit = (value: string) => value === '' || /^\d$/.test(value);
//倒计时结束
const onCountdownFinish = () => {
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

.code-input {
  flex: 8;
  min-width: 0;
  align-self: center;
  --el-input-otp-size: 34px;
  --el-input-otp-field-width: calc((100% - 40px) / 6);
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
