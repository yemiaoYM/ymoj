import { defineStore } from 'pinia';
import { ref } from 'vue';
import type { RemoteProblemDraft } from '@/type/remoteProblem';

// 列表页向新增页交接一次草稿，不把题面放进 URL 或持久化存储。
export const useRemoteProblemStore = defineStore('remoteProblem', () => {
  const draft = ref<RemoteProblemDraft | null>(null);
  return { draft };
});
