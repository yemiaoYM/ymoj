import { defineStore } from 'pinia';
import { ref } from 'vue';

/**
 * 应用状态
 */
export const useAppStore = defineStore('app', () => { 
    const isCollapse = ref<boolean>(false);
    //仅用于首次打开或刷新页面，普通导航不显示全屏状态
    const initialLoading = ref(true);
    const initialLoadError = ref(false);
    const retryPath = ref('');
    return { isCollapse, initialLoading, initialLoadError, retryPath };
});
