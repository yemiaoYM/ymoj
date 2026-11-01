import { createRouter, createWebHistory, START_LOCATION } from 'vue-router';
import { useUserStore } from '@/stores/user';
import { usePermissionStore } from '@/stores/permission';
import { useAppStore } from '@/stores/app';
import { isPathMatch } from '@/utils/utils';
import { isAxiosError, type AxiosResponse } from 'axios';
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

router.beforeEach(async (to, from, next) => {
  const appStore = useAppStore();
  const initialNavigation = from === START_LOCATION;
  //首次进入还没有可显示的页面，普通导航保留当前页面
  if (initialNavigation) {
    appStore.initialLoading = true;
    appStore.retryPath = to.fullPath;
  }
  //设置标题
  to.meta.title && (document.title = to.meta.title + " - YMOJ");
  //获取用户信息
  const userStore = useUserStore();
  const permissionStore = usePermissionStore();

  const requireLogin = to.meta.requireLogin || false;
  const isAdmin = to.path.startsWith('/admin');
  const loginPath = isAdmin ? '/admin/login' : '/login';
  const homePath = isAdmin ? '/admin' : '/';

  //有token
  if (userStore.isLogin) {
    if (to.path === loginPath) {
      return next({ path: homePath });
    } else if(isWhiteList(to.path)){
      return next();
    } else{
      try {
        await userStore.getUserInfo(true);
        if (isAdmin) {
          if (!userStore.hasEveryPermission(['admin:access'])) {
            permissionStore.resetRoutes();
            return next({ path: '/' });
          }
          const oldSignature = permissionStore.routeSignature;
          await permissionStore.generateSidebarRouters();
          if (oldSignature !== permissionStore.routeSignature) {
            return next({ ...to, replace: true });
          }
        }
        return next();
      } catch (e) {
        const response = isAxiosError(e) ? e.response : e as AxiosResponse<{ code?: number }> | undefined;
        //仅明确未授权时退出登录，网络或服务异常保留令牌并取消本次导航
        if (response?.data?.code === 401 || response?.status === 401) {
          await userStore.doLogout();
          return next({ path: loginPath });
        }
        if (initialNavigation) {
          appStore.initialLoadError = true;
        }
        return next(false);
      }
    }
  } else {
    //没有token
    permissionStore.resetRoutes();
    if (!requireLogin || isWhiteList(to.path)) {
      //在免登录白名单或者不需要登录，直接进入
      return next();
    } else {
      //否则全部重定向到登录页
      return next(`${loginPath}?redirect=${to.fullPath}`);
    }
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
