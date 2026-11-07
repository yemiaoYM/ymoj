import { createRouter, createWebHistory, START_LOCATION } from 'vue-router';
import { useUserStore } from '@/stores/user';
import { usePermissionStore } from '@/stores/permission';
import { useAppStore } from '@/stores/app';
import { isPathMatch } from '@/utils/utils';
import { ElMessage } from 'element-plus';
//模块化路由
import ojRoutes from '@/router/modules/oj';
import adminRoutes from '@/router/modules/admin';
import errorRoutes from '@/router/modules/error';

const whiteList = ['/login', '/admin/register'];

const isWhiteList = (path: string) => {
  return whiteList.some(pattern => isPathMatch(pattern, path))
}

const routes = [
  ...ojRoutes,
  ...adminRoutes,
  ...errorRoutes,
]

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach(async (to, from) => {
  const appStore = useAppStore();
  const initialNavigation = from === START_LOCATION;
  if (initialNavigation) {
    appStore.initialLoading = true;
    appStore.retryPath = to.fullPath;
  }
  if (to.meta.title) {
    document.title = to.meta.title + ' - YMOJ';
  }
  const userStore = useUserStore();
  const permissionStore = usePermissionStore();
  const isAdmin = to.path === '/admin' || to.path.startsWith('/admin/');
  const loginPath = isAdmin ? '/admin/login' : '/login';
  const homePath = isAdmin ? '/admin' : '/';
  const requireLogin = to.meta.requireLogin && !isWhiteList(to.path);
  const loginTarget = { path: loginPath, query: { redirect: to.fullPath } };

  try {
    if (userStore.token) {
      await userStore.getUserInfo();
    }
    if (!userStore.isLogin) {
      permissionStore.resetRoutes();
      return requireLogin ? loginTarget : true;
    }
    if (to.path === loginPath) {
      return { path: homePath };
    }
    if (isWhiteList(to.path)) {
      return true;
    }
    if (isAdmin) {
      if (!userStore.hasPermission('admin:access')) {
        permissionStore.resetRoutes();
        return { path: '/' };
      }
      const routesLoaded = permissionStore.isRoutesLoaded;
      await permissionStore.generateSidebarRouters();
      if (!userStore.isLogin) {
        return loginTarget;
      }
      if (!routesLoaded && permissionStore.isRoutesLoaded) {
        return { path: to.path, query: to.query, hash: to.hash, replace: true };
      }
    }
    return true;
  } catch {
    // 请求层仅在明确 401 时清理会话，网络及服务错误保留令牌供重试
    if (!userStore.token) {
      return requireLogin ? loginTarget : true;
    }
    if (initialNavigation) {
      appStore.initialLoadError = true;
    }
    return false;
  }
});

router.afterEach((to, from, failure) => {
  if (from === START_LOCATION) {
    const appStore = useAppStore();
    appStore.initialLoading = false;
    if (!failure) {
      appStore.initialLoadError = false;
    }
  }
});

router.onError((error, to, from) => {
  if (from === START_LOCATION) {
    const appStore = useAppStore();
    appStore.initialLoading = false;
    appStore.initialLoadError = true;
    appStore.retryPath = to.fullPath;
  } else {
    ElMessage.error('页面加载失败，请稍后重试');
  }
});

export default router;
