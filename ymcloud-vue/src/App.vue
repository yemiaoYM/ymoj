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

const router = useRouter();
const appStore = useAppStore();

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

/* color palette from <https://github.com/vuejs/theme> */
:root {
  --vt-c-white: #ffffff;
  --vt-c-white-soft: #f8f8f8;
  --vt-c-white-mute: #f2f2f2;

  --vt-c-black: #181818;
  --vt-c-black-soft: #222222;
  --vt-c-black-mute: #282828;

  --vt-c-indigo: #2c3e50;

  --vt-c-divider-light-1: rgba(60, 60, 60, 0.29);
  --vt-c-divider-light-2: rgba(60, 60, 60, 0.12);
  --vt-c-divider-dark-1: rgba(84, 84, 84, 0.65);
  --vt-c-divider-dark-2: rgba(84, 84, 84, 0.48);

  --vt-c-text-light-1: var(--vt-c-indigo);
  --vt-c-text-light-2: rgba(60, 60, 60, 0.66);
  --vt-c-text-dark-1: var(--vt-c-white);
  --vt-c-text-dark-2: rgba(235, 235, 235, 0.64);
}

/* semantic color variables for this project */
:root {
  --color-background: var(--vt-c-white);
  --color-background-soft: var(--vt-c-white-soft);
  --color-background-mute: var(--vt-c-white-mute);

  --color-border: var(--vt-c-divider-light-2);
  --color-border-hover: var(--vt-c-divider-light-1);

  --color-heading: var(--vt-c-text-light-1);
  --color-text: var(--vt-c-text-light-1);

  --section-gap: 160px;
}

@media (prefers-color-scheme: dark) {
  :root {
    --color-background: var(--vt-c-black);
    --color-background-soft: var(--vt-c-black-soft);
    --color-background-mute: var(--vt-c-black-mute);

    --color-border: var(--vt-c-divider-dark-2);
    --color-border-hover: var(--vt-c-divider-dark-1);

    --color-heading: var(--vt-c-text-dark-1);
    --color-text: var(--vt-c-text-dark-2);
  }
}

*,
*::before,
*::after {
  box-sizing: border-box;
  margin: 0;
  font-weight: normal;
}

body {
  min-height: 100vh;
  color: var(--color-text);
  background: var(--color-background);
  transition: color 0.5s, background-color 0.5s;
  line-height: 1.6;
  font-family: Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto,
    Oxygen, Ubuntu, Cantarell, "Fira Sans", "Droid Sans", "Helvetica Neue",
    sans-serif;
  font-size: 15px;
  text-rendering: optimizeLegibility;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
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
