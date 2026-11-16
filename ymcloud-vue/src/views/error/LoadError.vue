<template>
  <main class="load-error-page">
    <svg class="error-art" viewBox="0 0 480 280" fill="none" aria-hidden="true">
      <path d="M78 244C157 237 317 237 402 244" class="art-ground" />
      <path d="M178 88C151 87 147 99 149 119L150 133C151 145 145 151 132 154C147 158 153 164 152 177L150 194C148 214 160 222 185 219" class="art-character" />
      <path d="M304 87C331 87 335 99 333 119L332 133C331 145 337 151 350 154C335 158 329 164 330 177L332 194C334 214 322 222 297 219" class="art-character art-partner" />
      <path d="M162 124L163 131M181 123L182 130M300 124L299 131M319 123L318 130" class="art-face" />
      <path d="M174 145L182 146M300 145L308 144" class="art-detail" />
      <path d="M160 218L151 238L137 238M182 220L190 238L203 238M301 220L291 238L278 238M323 218L333 238L347 238" class="art-detail" />
      <path d="M173 171C196 171 200 199 218 196M308 171C292 168 298 212 271 209C249 207 278 183 261 180" class="art-cable" />
      <g transform="rotate(-18 224 193)">
        <path d="M215 184H227V201H215Z" class="art-plug" />
        <path d="M228 188H235M228 196H235" class="art-detail" />
      </g>
      <g transform="rotate(18 255 177)">
        <path d="M250 169H262V186H250Z" class="art-plug" />
        <path d="M250 174H246M250 181H246" class="art-detail" />
      </g>
      <path d="M232 154L227 144M245 151L248 139M258 158L267 151" class="art-question" />
      <path d="M221 62C229 57 234 68 241 63C248 58 253 66 260 61" class="art-trail" />
    </svg>
    <h1>页面加载失败</h1>
    <x-flex class="error-actions" justify="center" :size="12">
      <el-button
        type="primary"
        size="large"
        :icon="Refresh"
        :loading="appStore.initialLoading"
        @click="handleRetry"
      >
        重新加载
      </el-button>
    </x-flex>
  </main>
</template>

<script setup lang="ts">
import { Refresh } from '@element-plus/icons-vue';
import { useRouter } from 'vue-router';
import { useAppStore } from '@/stores/app';

const router = useRouter();
const appStore = useAppStore();

/**
 * 重试首次加载失败的目标路由
 */
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
</script>

<style scoped>
.load-error-page {
  position: fixed;
  inset: 0;
  z-index: 2000;
  overflow-y: auto;
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

.art-character {
  stroke: var(--el-color-primary);
  stroke-width: 15;
}

.art-partner {
  stroke: var(--el-text-color-secondary);
}

.art-face {
  stroke: var(--el-text-color-primary);
  stroke-width: 3;
}

.art-detail,
.art-cable {
  stroke: var(--el-text-color-primary);
  stroke-width: 2.5;
}

.art-plug {
  fill: var(--el-bg-color);
  stroke: var(--el-text-color-primary);
  stroke-width: 2;
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
