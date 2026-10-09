import { defineStore } from 'pinia';
import { ref, markRaw } from 'vue';
import { getMenus } from '@/api/api';
import Layout from '@/components/admin/AdminLayout.vue';
import router from '@/router';
import { dynamicRoutes } from '@/router/modules/admin';
import { useUserStore } from '@/stores/user';

//导入views目录admin下的所有vue文件
const modules = import.meta.glob('../views/admin/**/*.vue');

export const usePermissionStore = defineStore('permission', () => {
  const userStore = useUserStore();
  const sidebarRouters = ref<any[]>([]);
  const isRoutesLoaded = ref<boolean>(false);
  const routeSignature = ref('');
  let routeRemovers: (() => void)[] = [];

  //移除上次加载的动态路由
  const resetRoutes = () => {
    routeRemovers.forEach(remove => remove());
    routeRemovers = [];
    sidebarRouters.value = [];
    routeSignature.value = '';
    isRoutesLoaded.value = false;
  };

  const setRoutesLoaded = (value: boolean) => {
    isRoutesLoaded.value = value;
  };

  //生成侧边栏路由
  const generateSidebarRouters = async () => {
    await getMenus().then(res => {
      const menus = res.data.data;
      const signature = JSON.stringify([menus, userStore.user.permissions, userStore.user.roles]);
      if (isRoutesLoaded.value && routeSignature.value === signature) {
        return;
      }
      resetRoutes();
      sidebarRouters.value = filterAsyncRouter(menus);
      sidebarRouters.value.forEach(route => routeRemovers.push(router.addRoute(route)));
      //动态路由动态添加
      const asyncRoutes = filterDynamicRoutes(dynamicRoutes);
      asyncRoutes.forEach(route => {
        routeRemovers.push(router.addRoute(route));
      });
      routeSignature.value = signature;
      setRoutesLoaded(true);
    });
    return sidebarRouters.value;
  };

  //动态路由遍历，验证是否具备权限
  const filterDynamicRoutes = (routes: any[]) => {
    const res: any[] = [];
    routes.forEach(route => {
      if (route.permissions) {
        if (userStore.hasEveryPermission(route.permissions)) {
          res.push(route);
        }
      } else if (route.roles) {
        if (userStore.hasAnyRole(route.roles)) {
          res.push(route);
        }
      }
    });
    return res;
  };

  //转换后台返回的菜单路由
  const filterAsyncRouter = (asyncRouterMap: any[]) => {
    return asyncRouterMap.filter(route => {
      if (route.component) {
        //组件处理
        if (route.component === 'Layout') {
          route.component = markRaw(Layout);
        } else {
          route.component = loadView(route.component);
          if (!route.component) {
            return false;
          }
        }
      }
      if (route.children != null && route.children && route.children.length) {
        route.children = filterAsyncRouter(route.children);
      } else {
        delete route['children'];
      }
      return true;
    });
  };

  //按组件路径加载页面
  const loadView = (view: string) => {
    let res: any;
    for (const path in modules) {
      const dir = path.split('views/')[1].split('.vue')[0];
      if (dir === view) {
        res = () => modules[path]();
      }
    }
    return res;
  };

  return {
    sidebarRouters,
    generateSidebarRouters,
    isRoutesLoaded,
    setRoutesLoaded,
    resetRoutes,
    routeSignature,
  };
});
