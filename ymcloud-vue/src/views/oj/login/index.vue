<template>
  <div class="login-container">
    <!-- 左侧品牌区域 -->
    <div class="left-section">
      <div class="brand-content">
        <div class="brand-header">
          <el-icon class="brand-icon" :size="32" color="#409EFF">
            <CircleCheck />
          </el-icon>
          <h1 class="brand-title">YMOJ</h1>
        </div>
        <p class="brand-subtitle">欢迎来到 YMOJ</p>
        <p class="brand-description">这里是一个在线评测系统，帮助你提升编程技能。</p>

        <!-- 插图区域 -->
        <div class="illustration">
          <div class="code-window">
            <div class="window-header">
              <div class="window-dots">
                <span class="dot red"></span>
                <span class="dot yellow"></span>
                <span class="dot green"></span>
              </div>
            </div>
            <div class="window-content">
              <div class="code-lines">
                <div class="code-line"></div>
                <div class="code-line short"></div>
                <div class="code-line"></div>
              </div>
            </div>
          </div>

          <!-- 程序员插图 -->
          <div class="programmer">
            <div class="programmer-body">
              <div class="head"></div>
              <div class="laptop">
                <div class="laptop-screen"></div>
                <div class="laptop-keyboard"></div>
              </div>
            </div>
          </div>

          <!-- 装饰元素 -->
          <div class="decoration-plants">
            <div class="plant left-plant"></div>
            <div class="plant right-plant"></div>
          </div>

          <!-- 代码标签 -->
          <div class="code-tag">
            <el-icon :size="24" color="#409EFF">
              <Edit />
            </el-icon>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧表单区域 -->
    <div class="right-section">
      <div class="form-container">
        <!-- 切换标签 -->
        <div class="form-tabs">
          <div 
            class="tab-item" 
            :class="{ active: currentView === 'login' }"
            @click="switchView('login')"
          >
            登录
          </div>
          <div 
            v-if="registerEnabled"
            class="tab-item" 
            :class="{ active: currentView === 'register' }"
            @click="switchView('register')"
          >
            注册
          </div>
          <div 
            class="tab-item" 
            :class="{ active: currentView === 'forget' }"
            @click="switchView('forget')"
          >
            找回密码
          </div>
        </div>

        <!-- 表单内容区域 -->
        <div class="form-content">
          <el-alert
            v-if="settingLoaded && !registerEnabled"
            title="系统暂未开放新用户注册"
            type="info"
            :closable="false"
            show-icon
            class="register-alert"
          />
          <el-alert
            v-else-if="!settingLoaded && !settingLoading"
            title="注册状态暂时无法获取，请稍后重试"
            type="warning"
            :closable="false"
            class="register-alert"
          >
            <el-link type="primary" @click="loadSystemSetting">重新加载</el-link>
          </el-alert>
          <transition name="form-fade" mode="out-in">
            <LoginForm 
              v-if="currentView === 'login'" 
              key="login"
              :register-enabled="registerEnabled"
              @switchTo="switchView" 
            />
            <RegisterForm 
              v-else-if="currentView === 'register' && registerEnabled"
              key="register"
              @switchTo="switchView" 
              @refreshSetting="loadSystemSetting"
            />
            <ForgetPasswordForm 
              v-else-if="currentView === 'forget'" 
              key="forget"
              @switchTo="switchView" 
            />
          </transition>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
//import
import { ref, defineAsyncComponent, onMounted } from 'vue';
import { CircleCheck, Edit } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { getPublicSystemSetting } from '@/api/api';
const LoginForm = defineAsyncComponent(() => import('./child/LoginForm.vue'));
const RegisterForm = defineAsyncComponent(() => import('./child/RegisterForm.vue'));
const ForgetPasswordForm = defineAsyncComponent(() => import('./child/ForgetPasswordForm.vue'));

//data
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
    registerEnabled.value = res.data.data.registerEnabled === true;
    settingLoaded.value = true;
  } catch (e) {
    registerEnabled.value = false;
    settingLoaded.value = false;
    console.error(e);
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
.register-alert {
  width: 100%;
  max-width: 400px;
  flex-shrink: 0;
  margin-bottom: 24px;
}

.login-container {
  display: flex;
  min-height: 100vh;
  background: linear-gradient(135deg, #299BFF 0%, #21c1c6 100%);
}

/* 左侧品牌区域 */
.left-section {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  color: white;
}

.brand-content {
  max-width: 500px;
  text-align: center;
}

.brand-header {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}

.brand-icon {
  margin-right: 12px;
  background: white;
  border-radius: 50%;
  padding: 8px;
}

.brand-title {
  font-size: 36px;
  font-weight: 600;
  margin: 0;
  color: white;
}

.brand-subtitle {
  font-size: 22px;
  margin: 8px 0;
  opacity: 0.9;
}

.brand-description {
  font-size: 16px;
  opacity: 0.8;
  margin-bottom: 40px;
}

/* 插图样式 */
.illustration {
  position: relative;
  height: 300px;
  margin: 40px 0;
}

.code-window {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 12px;
  width: 280px;
  height: 180px;
  margin: 0 auto;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  position: relative;
  z-index: 2;
}

.window-header {
  background: #f5f5f5;
  height: 32px;
  border-radius: 12px 12px 0 0;
  display: flex;
  align-items: center;
  padding: 0 12px;
}

.window-dots {
  display: flex;
  gap: 6px;
}

.dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}

.dot.red {
  background: #ff5f57;
}

.dot.yellow {
  background: #ffbd2e;
}

.dot.green {
  background: #28ca42;
}

.window-content {
  padding: 20px;
}

.code-lines {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.code-line {
  height: 8px;
  background: linear-gradient(90deg, #409EFF, #67C23A);
  border-radius: 4px;
}

.code-line.short {
  width: 60%;
}

.programmer {
  position: absolute;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 3;
}

.programmer-body {
  position: relative;
}

.head {
  width: 40px;
  height: 40px;
  background: #fdbcb4;
  border-radius: 50%;
  margin: 0 auto 8px;
  position: relative;
}

.head::before {
  content: '';
  position: absolute;
  top: 8px;
  left: 50%;
  transform: translateX(-50%);
  width: 24px;
  height: 16px;
  background: #4a5568;
  border-radius: 12px 12px 0 0;
}

.laptop {
  width: 60px;
  height: 40px;
  position: relative;
}

.laptop-screen {
  width: 60px;
  height: 32px;
  background: #2d3748;
  border-radius: 4px 4px 0 0;
  border: 2px solid #4a5568;
}

.laptop-keyboard {
  width: 64px;
  height: 8px;
  background: #e2e8f0;
  border-radius: 0 0 4px 4px;
  margin-left: -2px;
}

.decoration-plants {
  position: absolute;
  bottom: 0;
  width: 100%;
}

.plant {
  position: absolute;
  bottom: 0;
  width: 40px;
  height: 60px;
}

.left-plant {
  left: 20%;
  background: linear-gradient(to top, #48bb78, #68d391);
  border-radius: 20px 20px 0 0;
  transform: rotate(-10deg);
}

.right-plant {
  right: 20%;
  background: linear-gradient(to top, #48bb78, #68d391);
  border-radius: 20px 20px 0 0;
  transform: rotate(10deg);
}

.code-tag {
  position: absolute;
  bottom: 40px;
  left: 20px;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 50%;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

/* 右侧表单区域 */
.right-section {
  flex: 1;
  background: white;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.form-container {
  width: 100%;
  max-width: 450px;
}

/* 标签切换样式 */
.form-tabs {
  display: flex;
  background: #f5f7fa;
  border-radius: 12px;
  padding: 4px;
  margin-bottom: 32px;
  position: relative;
}

.tab-item {
  flex: 1;
  text-align: center;
  padding: 12px 16px;
  font-size: 16px;
  font-weight: 500;
  color: #666;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.3s ease;
  position: relative;
  z-index: 2;
}

.tab-item.active {
  color: #409EFF;
  background: white;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
}

.tab-item:hover:not(.active) {
  color: #409EFF;
  background: rgba(64, 158, 255, 0.05);
}

/* 表单内容区域 */
.form-content {
  min-height: 500px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
}

/* 过渡动画 */
.form-fade-enter-active,
.form-fade-leave-active {
  transition: all 0.3s ease;
}

.form-fade-enter-from {
  opacity: 0;
  transform: translateX(20px);
}

.form-fade-leave-to {
  opacity: 0;
  transform: translateX(-20px);
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

/* 响应式设计 */
@media (max-width: 768px) {
  .login-container {
    flex-direction: column;
  }

  .left-section {
    min-height: 40vh;
    padding: 20px;
  }

  .right-section {
    padding: 20px;
  }

  .illustration {
    height: 200px;
    margin: 20px 0;
  }

  .code-window {
    width: 200px;
    height: 120px;
  }
}
</style>
