<template>
  <main class="forbidden-page">
    <svg class="error-art" viewBox="0 0 480 280" fill="none" aria-hidden="true">
      <path d="M78 244C157 237 317 237 402 244" class="art-ground" />
      <g class="art-numbers">
        <path d="M116 87L69 164H132M119 126V210" />
        <path d="M339 91C385 75 405 113 369 144C410 148 406 200 344 204" />
      </g>
      <path d="M197 79C168 79 165 93 167 114L169 130C170 142 164 149 151 152C166 156 172 162 171 175L169 192C167 213 179 221 204 218" class="art-character" />
      <path d="M180 117L181 125M200 116L201 124" class="art-face" />
      <path d="M190 141L198 141M177 216L167 237L153 237M200 219L211 237L224 237" class="art-detail" />
      <path d="M201 170L225 159M291 205V239M244 205V239" class="art-detail" />
      <rect x="226" y="145" width="84" height="61" rx="8" class="art-paper" />
      <path d="M247 145V126C247 99 289 99 289 126V145" class="art-lock" />
      <circle cx="268" cy="172" r="5" class="art-keyhole" />
      <path d="M268 177V187" class="art-map" />
      <path d="M234 81L228 72M251 77V66" class="art-detail art-small" />
    </svg>
    <h1>暂无访问权限</h1>
    <x-flex class="error-actions" justify="center" :size="12">
      <el-button type="primary" size="large" :icon="HomeFilled" @click="handleToHome">回到首页</el-button>
      <el-button size="large" :icon="Back" @click="handleToBack">返回上一页</el-button>
    </x-flex>
  </main>
</template>

<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router';
import { HomeFilled, Back } from '@element-plus/icons-vue';

const router = useRouter();
const route = useRoute();

/**
 * 返回当前端对应的首页
 */
const handleToHome = () => {
  router.push(route.path.startsWith('/admin') ? '/admin' : '/');
};

/**
 * 直接打开无历史记录时回到首页
 */
const handleToBack = () => {
  if (window.history.state.position > 0) {
    router.back();
  } else {
    handleToHome();
  }
};
</script>

<style scoped>
.forbidden-page {
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: safe center;
  padding: 48px 24px;
  text-align: center;
  background: var(--el-bg-color-page);
}

.error-art {
  display: block;
  width: 100%;
  max-width: 480px;
  margin: 0 auto 28px;
  flex-shrink: 0;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.art-ground {
  stroke: var(--el-border-color);
  stroke-width: 1.5;
}

.art-numbers {
  stroke: var(--el-color-primary-light-7);
  stroke-width: 17;
}

.art-character {
  stroke: var(--el-color-primary);
  stroke-width: 15;
}

.art-face {
  stroke: var(--el-text-color-primary);
  stroke-width: 3;
}

.art-detail {
  stroke: var(--el-text-color-primary);
  stroke-width: 2.5;
}

.art-paper {
  fill: var(--el-bg-color);
  stroke: var(--el-text-color-primary);
  stroke-width: 2;
}

.art-map {
  stroke: var(--el-color-primary);
  stroke-width: 1.5;
}

.art-lock {
  stroke: var(--el-color-primary);
  stroke-width: 7;
}

.art-keyhole {
  fill: var(--el-color-primary);
}

.art-small {
  opacity: 0.35;
}

h1 {
  color: var(--el-text-color-primary);
  font-size: 22px;
  font-weight: 500;
  line-height: 1.5;
}

.error-actions {
  margin-top: 28px;
}

.error-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

@media (max-width: 480px) {
  .error-art {
    margin-bottom: 20px;
  }

  h1 {
    font-size: 20px;
  }
}
</style>
