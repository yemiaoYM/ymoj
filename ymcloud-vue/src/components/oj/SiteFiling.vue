<template>
  <footer
    v-if="settings?.filingEnabled && (settings.icpFilingNumber || settings.policeFilingNumber)"
    class="site-filing"
  >
    <n-flex justify="center" align="center" :size="16">
      <a
        v-if="settings.policeFilingNumber"
        class="filing-link"
        rel="noopener noreferrer"
        target="_blank"
        :href="policeFilingUrl"
      >
        <img
          class="police-icon"
          src="https://beian.mps.gov.cn/img/logo01.dd7ff50e.png"
          alt="公安备案"
        />
        {{ settings.policeFilingNumber }}
      </a>
      <a
        v-if="settings.icpFilingNumber"
        class="filing-link"
        href="https://beian.miit.gov.cn"
        rel="noopener noreferrer"
        target="_blank"
      >{{ settings.icpFilingNumber }}</a>
    </n-flex>
  </footer>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { getPublicSystemSetting } from '@/api/api';
import type { SystemSettingVO } from '@/type/types';

const settings = ref<SystemSettingVO | null>(null);
let disposed = false;
const policeFilingUrl = computed(() => {
  const code = settings.value?.policeFilingNumber.match(/[0-9]+/)?.[0] ?? '';
  return `https://beian.mps.gov.cn/#/query/webSearch?code=${encodeURIComponent(code)}`;
});

onMounted(async () => {
  try {
    const response = await getPublicSystemSetting();
    if (!disposed) {
      settings.value = response.data.data;
    }
  } catch {
    if (!disposed) {
      settings.value = null; // 请求层提示失败；不展示未经确认或硬编码的备案信息。
    }
  }
});

onBeforeUnmount(() => {
  disposed = true;
});
</script>

<style scoped>
.site-filing {
  margin-top: 2rem;
  padding: 1.5rem;
  background-color: var(--el-bg-color);
  text-align: center;
}

.filing-link {
  color: var(--el-text-color-regular);
  text-decoration: none;
  overflow-wrap: anywhere;
}

.filing-link:hover {
  color: var(--el-color-primary);
}

.police-icon {
  width: 20px;
  vertical-align: middle;
}
</style>
