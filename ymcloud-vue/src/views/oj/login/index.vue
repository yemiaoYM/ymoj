<template>
  <el-scrollbar height="100dvh">
    <x-flex class="login-page" vertical align="center" justify="center" :size="20">
      <x-flex class="login-card" :size="0" :wrap="false">
        <x-flex class="brand-panel" vertical :size="28">
          <router-link to="/" aria-label="YMOJ 首页">
            <BrandLogo class="login-logo" dark />
          </router-link>
          <h1>喵语云在线评测平台</h1>
          <div>
            <h2>从一道题开始，积累每一步成长。</h2>
            <p>练习算法，参与竞赛，探索编程的乐趣。<br />在这里，让每一次思考都有收获。</p>
          </div>
          <el-image class="brand-art" fit="contain" alt="" :src="authIllustration" />
          <router-link class="admin-entry" to="/admin/login">
            <x-flex align="center" justify="space-between">
              <span>后台管理 <small>管理员登录入口</small></span>
              <el-icon><ArrowRight /></el-icon>
            </x-flex>
          </router-link>
        </x-flex>

        <x-flex class="form-panel" vertical :size="0">
          <el-alert
            v-if="settingLoaded && !registerEnabled"
            class="register-alert"
            title="系统暂未开放新用户注册"
            type="info"
            show-icon
            :closable="false"
          />
          <el-alert
            v-else-if="!settingLoaded && !settingLoading"
            class="register-alert"
            title="注册状态暂时无法获取，请稍后重试"
            type="warning"
            :closable="false"
          >
            <el-link type="primary" @click="loadSystemSetting">重新加载</el-link>
          </el-alert>
          <LoginForm
            v-if="currentView === 'login'"
            :register-enabled="registerEnabled"
            @switchTo="switchView"
          />
          <RegisterForm
            v-else-if="currentView === 'register' && registerEnabled"
            @switchTo="switchView"
            @refreshSetting="loadSystemSetting"
          />
          <ForgetPasswordForm v-else-if="currentView === 'forget'" @switchTo="switchView" />
          <x-flex class="explore-links" justify="center" align="center" :size="32">
            <el-link type="info" underline="never" @click="router.push('/problem')">
              <x-flex inline align="center" :size="8" :wrap="false">
                <el-icon :size="18"><Reading /></el-icon>
                <span>探索题库</span>
              </x-flex>
            </el-link>
            <el-link type="info" underline="never" @click="router.push('/contest')">
              <x-flex inline align="center" :size="8" :wrap="false">
                <el-icon :size="18"><Trophy /></el-icon>
                <span>发现比赛</span>
              </x-flex>
            </el-link>
          </x-flex>
        </x-flex>
      </x-flex>
      <x-flex class="login-footer" justify="center" align="center" :size="20">
        <el-link type="info" underline="never" @click="router.push('/')">
          <x-flex inline align="center" :size="6" :wrap="false">
            <el-icon :size="14"><Back /></el-icon>
            <span>返回首页</span>
          </x-flex>
        </el-link>
        <span>YMOJ 喵语云 · 在线评测平台</span>
      </x-flex>
    </x-flex>
  </el-scrollbar>
</template>

<script lang="ts" setup>
//import
import { ref, defineAsyncComponent, onMounted } from 'vue';
import { ArrowRight, Back, Reading, Trophy } from '@element-plus/icons-vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { getPublicSystemSetting } from '@/api/api';
import BrandLogo from '@/components/common/BrandLogo.vue';
import authIllustration from '@/assets/image/auth-programming.png';
const LoginForm = defineAsyncComponent(() => import('./child/LoginForm.vue'));
const RegisterForm = defineAsyncComponent(() => import('./child/RegisterForm.vue'));
const ForgetPasswordForm = defineAsyncComponent(() => import('./child/ForgetPasswordForm.vue'));

//data
const router = useRouter();
type ViewType = 'login' | 'register' | 'forget';
const currentView = ref<ViewType>('login');
const registerEnabled = ref(false);
const settingLoaded = ref(false);
const settingLoading = ref(true);

//methods
const loadSystemSetting = async () => {
  settingLoading.value = true;
  try {
    const res = await getPublicSystemSetting();
    registerEnabled.value = res.data.data.registerEnabled;
    settingLoaded.value = true;
  } catch {
    registerEnabled.value = false;
    settingLoaded.value = false;
  } finally {
    settingLoading.value = false;
    if (!registerEnabled.value && currentView.value === 'register') {
      currentView.value = 'login';
    }
  }
};

const switchView = async (view: ViewType) => {
  if (view === 'register') {
    await loadSystemSetting();
    if (!registerEnabled.value) {
      if (settingLoaded.value) {
        ElMessage.warning('系统暂未开放新用户注册');
      }
      return;
    }
  }
  currentView.value = view;
};

onMounted(() => {
  loadSystemSetting();
});
</script>

<style scoped>
.login-page {
  min-height: 100dvh;
  padding: 20px 24px;
  background: #f8f9fb url('@/assets/image/auth-background.png') center / cover no-repeat;
}

.login-card {
  width: 1000px;
  max-width: 100%;
  min-height: 632px;
  background: #fff;
  box-shadow: 0 12px 45px rgba(35, 52, 74, 0.12);
}

.brand-panel {
  width: 46%;
  padding: 32px 44px 24px;
  color: #fff;
  background: #50647c;
}

.login-logo {
  width: 208px;
  /* 原图自带透明留白，抵消留白以对齐正文的可见左边缘 */
  margin: -12px 0 0 -10px;
}

.brand-panel h1 {
  font-size: 26px;
  font-weight: 500;
}

.brand-panel h2 {
  margin: 16px 0;
  font-size: 18px;
}

.brand-panel p {
  color: #e1e7ef;
  font-size: 16px;
  line-height: 1.9;
}

.brand-art {
  width: 100%;
  height: 180px;
}

.admin-entry {
  margin-top: auto;
  padding-top: 20px;
  border-top: 1px solid #ffffff38;
  min-height: 48px;
  color: #fff;
  font-size: 16px;
  text-decoration: none;
}

.admin-entry small {
  margin-left: 12px;
  color: #d4dee9;
  font-size: 14px;
}

.admin-entry:hover {
  color: #c6e4ff;
}

.form-panel {
  flex: 1;
  min-width: 0;
  padding: 32px 44px 24px;
}

.register-alert {
  margin-bottom: 16px;
}

.explore-links {
  margin-top: auto;
  padding-top: 20px;
  border-top: 1px solid var(--el-border-color-lighter);
  min-height: 48px;
}

.explore-links .el-link {
  --el-link-text-color: var(--el-text-color-regular);
}

.login-footer {
  color: var(--el-text-color-regular);
  font-size: 14px;
}

.login-footer .el-link {
  font-size: 14px;
}

@media (max-width: 760px) {
  .login-page {
    padding: 20px 16px;
  }

  .login-card {
    /* XFlex 使用行内方向，窄屏覆盖为纵向 */
    flex-direction: column !important;
    width: 100%;
    max-width: 460px;
    min-height: 0;
  }

  .brand-panel {
    width: 100%;
    padding: 24px;
  }

  .brand-panel > h1,
  .brand-panel > div {
    display: none;
  }

  .admin-entry {
    padding-top: 16px;
  }

  .form-panel {
    padding: 20px 24px;
  }

  .explore-links {
    margin-top: 24px;
  }
}
</style>
