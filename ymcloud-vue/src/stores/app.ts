import { defineStore } from 'pinia';
import { ref } from 'vue';

/**
 * 应用状态
 */
export const useAppStore = defineStore('app', () => { 
    const isCollapse = ref<boolean>(false);
    return { isCollapse };
});
