import { defineStore } from 'pinia';
import { ref, markRaw } from 'vue';
import { getMenus } from '@/api/api';
import Layout from '@/components/admin/AdminLayout.vue';

//导入views目录admin下的所有vue文件
const modules = import.meta.glob('../views/admin/**/*.vue')

export const usePermissionStore = defineStore('permission', () => {

  const sidebarRouters = ref<any[]>([]);

  const isRoutesLoaded = ref<boolean>(false);

  const setRoutesLoaded = (value:boolean)=>{
    isRoutesLoaded.value = value;
  }

  //生成侧边栏路由
  const generateSidebarRouters = async () => {
    await getMenus().then(res=>{
      const menus = res.data.data;
      sidebarRouters.value = filterAsyncRouter(menus);
      setRoutesLoaded(true);
    })
    return sidebarRouters.value;
  }

  const filterAsyncRouter = (asyncRouterMap:any[]) => {
    return asyncRouterMap.filter(route => {
      if (route.component) {
        //组件处理
        if (route.component === 'Layout') {
          route.component = markRaw(Layout);
        } else {
          route.component = loadView(route.component);
        }
      }
      if (route.children != null && route.children && route.children.length) {
        route.children = filterAsyncRouter(route.children);
      } else {
        delete route['children'];
      }
      return true;
    })
  }

  const loadView = (view:string) => {
    let res:any;
    for (const path in modules) {
      const dir = path.split('views/')[1].split('.vue')[0]
      if (dir === view) {
        res = () => modules[path]();
      }
    }
    return res;
  }

  return {
    sidebarRouters,
    generateSidebarRouters,
    isRoutesLoaded,
    setRoutesLoaded
  }
});
