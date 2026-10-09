import { createRouter, createWebHistory } from 'vue-router';
import { useUserStore } from '@/stores/user';
import { usePermissionStore } from '@/stores/permission';
import { isPathMatch } from '@/utils/utils';
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
  //测试
  {
    path: '/test',
    name: 'Test',
    component: () => import('@/views/test/test.vue'),
    meta: { title: 'Test' }
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach(async (to, from, next) => {
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
        await userStore.doLogout();
        return next({ path: loginPath });
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

export default router;
