<template>
  <div>
    <el-config-provider :message="messageConfig" :locale="zhCn">
      <router-view />
      <div v-if="appStore.initialLoadError" class="page-state">
        <el-card class="page-state-card" shadow="never">
          <el-result
            icon="warning"
            title="页面加载失败"
            sub-title="暂时无法连接服务，请稍后重试。"
          >
            <template #extra>
              <el-button
                type="primary"
                :icon="Refresh"
                :loading="appStore.initialLoading"
                @click="handleRetry"
              >
                重新加载
              </el-button>
            </template>
          </el-result>
        </el-card>
      </div>
    </el-config-provider>
  </div>
</template>

<script setup lang="ts">
import { onMounted, nextTick, reactive, watch } from "vue";
import { LOGO, MOTTO } from "@/constants/logo";
import zhCn from "element-plus/dist/locale/zh-cn.mjs";
import { Refresh } from "@element-plus/icons-vue";
import { useRouter } from "vue-router";
import { useAppStore } from "@/stores/app";
import { useThemeStore } from "@/stores/theme";
import { useUserStore } from "@/stores/user";

const router = useRouter();
const appStore = useAppStore();
const userStore = useUserStore();
useThemeStore();

// 会话失效后由页面导航回登录页，Store 只清理身份
watch(() => userStore.sessionExpired, async expired => {
  const route = router.currentRoute.value;
  if (expired && route.meta.requireLogin) {
    const path = route.path.startsWith('/admin/') ? '/admin/login' : '/login';
    await router.replace({ path, query: { redirect: route.fullPath } });
  }
});

//消息配置
const messageConfig = reactive({
  max: 1,
  plain: true,
  placement: "top",
});

//methods
const handleRetry = async () => {
  if (appStore.initialLoading) {
    return;
  }
  try {
    const path = appStore.retryPath
      || window.location.pathname + window.location.search + window.location.hash;
    await router.replace(path);
  } catch (e) {
    console.error(e);
  }
};

const removeLoader = () => {
  document.getElementById("app-loader")?.remove();
};
//首次导航结束后移除项目原有启动动画，正常切换页面不显示额外加载画面
watch(() => appStore.initialLoading, loading => {
  if (!loading) {
    removeLoader();
  }
});
onMounted(async () => {
  await nextTick();
  if (!appStore.initialLoading) {
    removeLoader();
  }
  console.log(LOGO);
  console.log(MOTTO);
});
</script>

<style src="@/assets/styles/theme.css"></style>
<style src="@/assets/styles/content-theme.css"></style>

<style>
.page-state {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--el-fill-color-light);
}

.page-state-card {
  width: 100%;
  max-width: 520px;
}

*,
*::before,
*::after {
  box-sizing: border-box;
  margin: 0;
  font-weight: normal;
}

b {
  font-weight: bolder;
}

body {
  min-height: 100vh;
  color: var(--color-text);
  background: var(--color-background);
  transition: color 0.5s, background-color 0.5s;
  line-height: 1.6;
  font-family: sans-serif;
  font-size: 15px;
  text-rendering: optimizeLegibility;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

:where(button, input, select, textarea) {
  font-family: inherit;
}

#app {
  width: 100%;
  height: 100%;
}

/* 禁用所有原生滚动条 */
body,
#app,
html {
  scrollbar-width: none; /* Firefox */
  -ms-overflow-style: none; /* IE 10+ */
}
body::-webkit-scrollbar,
#app::-webkit-scrollbar,
html::-webkit-scrollbar {
  display: none; /* Chrome/Safari/Webkit */
}
</style>
