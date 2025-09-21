<template>
  <div class="aside-container">
    <el-menu
      active-text-color="#319cf4"
      :default-active="activeMenuName"
      router
      :collapse="appStore.isCollapse"
      :collapse-transition="false"
      :unique-opened="true"
      mode="vertical"
      :style="menuStyle"
    >
      <!-- logo -->
      <n-flex style="width: 100%;" justify="center" align="center">
        <el-image
          lazy 
          :src="LogoImage" 
          alt="Logo"
          style="height: 50px;width: 50px;margin: 15px 0px;"
          />
      </n-flex>
      <!-- 菜单 -->
      <el-menu-item index="/admin/index">
        <el-icon><Odometer /></el-icon>
        <span>仪表盘</span>
      </el-menu-item>

      <sidebar-item :item="menus"/>
    </el-menu>
  </div>
</template>

<script setup lang="ts">
//import
import { ref,computed, onMounted, onBeforeUnmount, defineAsyncComponent } from 'vue';
import { useRoute } from 'vue-router';
import { useAppStore } from '@/stores/app';
import { usePermissionStore } from '@/stores/permission';
const SidebarItem = defineAsyncComponent(() => import('./SidebarItem.vue'));
import LogoImage from '@/assets/image/logo.jpg';

//data
const permissionStore=usePermissionStore();
const appStore=useAppStore();
const route = useRoute();
const activeMenuName = computed(()=>route.path);
const isHidden=ref<boolean>(false);

const menuStyle=computed(()=>{
  return {
    width: appStore.isCollapse ? '64px' : '200px',
    display: isHidden.value ? 'none' : 'block'
  }
});
const menus = ref(permissionStore.sidebarRouters);

// menus.value=[
// {
//     "code": null,
//     "name": "题目管理",
//     "path": "problem",
//     "routeName": "problem",
//     "component": null,
//     "icon": "Tickets",
//     "children": [
//         {
//             "code": "problem:list",
//             "name": "题目列表",
//             "path": "/admin/problem/list",
//             "routeName": "problemList",
//             "component": "problem/index",
//             "icon": null,
//             "children": []
//         },
//         {
//             "code": "problem:add",
//             "name": "增加题目",
//             "path": "/admin/problem/add",
//             "routeName": "problemAdd",
//             "component": "problem/add",
//             "icon": null,
//             "children": []
//         },
//         {
//             "code": "problem:tag:list",
//             "name": "标签管理",
//             "path": "/admin/problem/tag",
//             "routeName": "problemTag",
//             "component": "problem/tag",
//             "icon": null,
//             "children": []
//         }
//     ]
// },
// {
//     "code": null,
//     "name": "比赛管理",
//     "path": "contest",
//     "routeName": "contest",
//     "component": null,
//     "icon": "Trophy",
//     "children": [
//         {
//             "code": "contest:list",
//             "name": "比赛列表",
//             "path": "/admin/contest/list",
//             "routeName": "contestList",
//             "component": "contest/index",
//             "icon": null,
//             "children": []
//         },
//         {
//             "code": "contest:add",
//             "name": "创建比赛",
//             "path": "/admin/contest/add",
//             "routeName": "contestAdd",
//             "component": "contest/add",
//             "icon": null,
//             "children": []
//         }
//     ]
// }
// ]

onMounted(() => {
  window.addEventListener('resize', handleResize);
});
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize);
});
const handleResize=()=>{
  isHidden.value=window.innerWidth < 1024;
}

</script>

<style scoped lang="css">
.aside-container{
  height: 100vh;
  width: 100%;
}
.el-menu {
  height: 100%;
  width: 100%;
}
</style>