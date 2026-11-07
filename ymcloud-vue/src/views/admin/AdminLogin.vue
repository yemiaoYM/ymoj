<template>
  <div class="login-container">
    <x-flex justify="center" align="center" style="height: 100%">
      <el-card>
        <template #header>
          <x-flex vertical align="center" :size="8">
            <BrandLogo class="login-logo" />
            <h2 class="login-title">管理后台登录</h2>
            <ThemeSelect />
          </x-flex>
        </template>
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          :hide-required-asterisk="true"
          style="width: 100%;height: 100%;"
          @submit.prevent="handleLogin"
        >
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              placeholder="请输入用户名"
              :prefix-icon="User"
              autocomplete="on"
              size="large"
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              type="password"
              v-model="form.password"
              placeholder="请输入密码"
              show-password
              autocomplete="on"
              :prefix-icon="Lock"
              size="large"
            />
          </el-form-item>

          <el-form-item prop="code">
            <x-flex :wrap="false" style="width: 100%;">
              <el-input
                v-model="form.code"
                placeholder="请输入验证码"
                :prefix-icon="Key"
                size="large"
                style="flex: 1"
                @keydown.enter.prevent="handleCaptchaEnter"
              />
              <el-image
                lazy
                :src="captchaImage"
                @click="refreshCaptcha"
                style="width: 100px; cursor: pointer; margin-left: 10px"
              />
            </x-flex>
          </el-form-item>

          <el-form-item>
            <el-button
              style="width: 100%;"
              size="large"
              type="primary"
              native-type="submit"
              :loading="loading"
            >
              登录
            </el-button>
          </el-form-item>
        </el-form>
        <div class="agreement-links">
          <LegalAgreement />
        </div>
      </el-card>
    </x-flex>
  </div>
</template>

<script setup lang="ts">
import ThemeSelect from '@/components/common/ThemeSelect.vue';
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { User, Lock, Key } from '@element-plus/icons-vue';
import type { LoginDTO } from '@/type/types';
import { getCaptcha } from '@/api/api';
import type { FormInstance, FormRules } from 'element-plus';
import { useUserStore } from '@/stores/user';
import { resolveLoginRedirect } from '@/router/loginRedirect';
import LegalAgreement from '@/components/common/LegalAgreement.vue';
import BrandLogo from '@/components/common/BrandLogo.vue';

const userStore = useUserStore();
const router = useRouter();
const route = useRoute();
const formRef = ref<FormInstance>();
const loading = ref<boolean>(false);
const form = ref<LoginDTO>({ username: '', password: '', code: '', uuid: '' });
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
const captchaImage = ref<string>('');

onMounted(() => {
  loadCaptcha();
});

// 输入法确认文字时不提交登录表单。
const handleCaptchaEnter = (event: KeyboardEvent) => {
  if (event.isComposing || event.keyCode === 229) {
    return;
  }
  handleLogin();
};

//登录
const handleLogin = async () => {
  if (!formRef.value || loading.value) {
    return;
  }
  loading.value = true;
  try {
    await formRef.value.validate();
    //登录
    await userStore.doLogin(
      form.value.username,
      form.value.password,
      form.value.code,
      form.value.uuid
    );
    if (userStore.isLogin) {
      await router.push(resolveLoginRedirect(router, route));
    }
  } catch {
    //登录失败
    refreshCaptcha();
  } finally {
    loading.value = false;
  }
};
//获取验证码
const loadCaptcha = () => {
  getCaptcha().then(res => {
    form.value.uuid = res.data.data.uuid;
    captchaImage.value = res.data.data.image;
  }).catch(() => {
    captchaImage.value = '';
  });
};
//刷新验证码
const refreshCaptcha = () => {
  loadCaptcha();
};
</script>

<style scoped lang="css">
.login-logo {
  width: 200px;
}
.login-title {
  margin: 0;
  font-size: 20px;
  color: var(--el-text-color-primary);
}
.login-container {
  height: 100vh;
  background-image: url('https://images.unsplash.com/photo-1497366811353-6870744d04b2?q=80&w=2069');
  background-size: cover;
  background-position: center;
}

.agreement-links {
  text-align: center;
}

.login-form {
  border-radius: 6px;
  background: var(--ym-surface);
  width: 400px;
  padding: 25px 25px 5px 25px;
}
.title {
  margin: 0 auto 30px auto;
  text-align: center;
  color: var(--ym-login-heading);
}
.captcha-img {
  width: 33%;
  height: 40px;
}
.captcha-img img {
  width: 100%;
  height: 100%;
  cursor: pointer;
}
.footer {
  height: 40px;
  line-height: 40px;
  position: fixed;
  bottom: 0;
  width: 100%;
  text-align: center;
  color: #fff;
  font-family: Arial, serif;
  font-size: 12px;
  letter-spacing: 1px;
}
html.dark .login-container {
  background: var(--ym-surface-page);
}
</style>
