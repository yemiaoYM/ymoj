<template>
  <main class="not-found-page">
    <svg class="error-art" viewBox="0 0 480 280" fill="none" aria-hidden="true">
      <path d="M78 244C157 237 317 237 402 244" class="art-ground" />
      <g class="art-numbers">
        <path d="M116 87L69 164H132M119 126V210" />
        <path d="M372 87L325 164H388M375 126V210" />
      </g>
      <path d="M189 238C167 223 174 205 154 199M291 238C317 222 306 205 326 199" class="art-trail" />
      <g transform="rotate(-8 233 159)">
        <path d="M242 75C210 75 207 88 209 110L211 124C212 137 206 143 191 147C208 150 214 157 213 170L211 188C209 210 221 218 248 216" class="art-character" />
        <path d="M223 115L225 123M245 113L247 121" class="art-face" />
        <path d="M237 136L245 134" class="art-detail" />
        <path d="M211 211L202 235L187 235M239 217L249 235L263 235" class="art-detail" />
        <path d="M220 169L244 179M258 162L277 150" class="art-detail" />
        <path d="M244 154L263 160L282 152L278 193L259 201L240 194Z" class="art-paper" />
        <path d="M263 160L259 201M250 170L254 172M267 180L274 175" class="art-map" />
      </g>
      <path d="M275 64C282 57 293 61 292 69C292 75 283 76 285 84M286 94V95" class="art-question" />
      <path d="M145 61L152 69M157 51L159 62" class="art-detail art-small" />
    </svg>
    <h1>页面走丢了</h1>
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
  if (window.history.state?.back) {
    router.back();
  } else {
    handleToHome();
  }
};
</script>

<style scoped>
.not-found-page {
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

.art-question {
  stroke: var(--el-color-primary);
  stroke-width: 2.5;
}

.art-trail {
  stroke: var(--el-text-color-placeholder);
  stroke-width: 1.5;
  stroke-dasharray: 3 7;
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
