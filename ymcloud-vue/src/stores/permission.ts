import { defineStore } from 'pinia';
import { ref, markRaw, watch, type Component } from 'vue';
import type { RouteRecordRaw } from 'vue-router';
import { getMenus } from '@/api/system/rbac';
import type { MenuRoute } from '@/types/system/rbac';
import Layout from '@/components/admin/AdminLayout.vue';
import router from '@/router';
import { dynamicRoutes } from '@/router/modules/admin';
import { useUserStore } from '@/stores/user';

const modules = import.meta.glob<{ default: Component }>('../views/admin/**/*.vue');
type AuthorizedRoute = RouteRecordRaw & { permissions?: string[]; roles?: string[] };

export const usePermissionStore = defineStore('permission', () => {
  const userStore = useUserStore();
  const sidebarRouters = ref<RouteRecordRaw[]>([]);
  const isRoutesLoaded = ref(false);
  let routeRemovers: (() => void)[] = [];
  let routesRequest: Promise<void> | undefined;

  /**
   * 移除当前身份对应的菜单及动态路由
   */
  const resetRoutes = () => {
    routesRequest = undefined;
    routeRemovers.forEach(remove => remove());
    routeRemovers = [];
    sidebarRouters.value = [];
    isRoutesLoaded.value = false;
  };

  watch(() => userStore.user, resetRoutes, { flush: 'sync' });

  /**
   * 同一身份共享菜单加载，退出后不再注册迟到响应中的路由
   */
  const generateSidebarRouters = (): Promise<void> => {
    if (routesRequest) {
      return routesRequest;
    }
    if (!userStore.isLogin || isRoutesLoaded.value) {
      return Promise.resolve();
    }
    const request = getMenus().then(response => {
      if (routesRequest !== request) {
        return;
      }
      sidebarRouters.value = filterAsyncRouter(response.data.data);
      sidebarRouters.value.forEach(route => routeRemovers.push(router.addRoute(route)));
      filterDynamicRoutes(dynamicRoutes).forEach(route => routeRemovers.push(router.addRoute(route)));
      isRoutesLoaded.value = true;
    }).finally(() => {
      if (routesRequest === request) {
        routesRequest = undefined;
      }
    });
    routesRequest = request;
    return request;
  };

  /**
   * 动态页面统一使用用户 Store 的权限与角色规则
   */
  const filterDynamicRoutes = (routes: AuthorizedRoute[]) => {
    return routes.filter(route => {
      if (route.permissions) {
        return userStore.hasEveryPermission(route.permissions);
      }
      return !!route.roles && userStore.hasAnyRole(route.roles);
    });
  };

  /**
   * 将菜单中的组件路径解析为本地页面
   */
  const filterAsyncRouter = (menus: MenuRoute[]): RouteRecordRaw[] => {
    const routes: RouteRecordRaw[] = [];
    for (const menu of menus) {
      const component = menu.component === 'Layout'
        ? markRaw(Layout)
        : modules[`../views/${menu.component}.vue`];
      if (menu.component && !component) {
        continue;
      }
      routes.push({
        path: menu.path,
        name: menu.name,
        component,
        meta: menu.meta,
        children: menu.children ? filterAsyncRouter(menu.children) : [],
      });
    }
    return routes;
  };

  return {
    sidebarRouters,
    generateSidebarRouters,
    isRoutesLoaded,
    resetRoutes,
  };
});
