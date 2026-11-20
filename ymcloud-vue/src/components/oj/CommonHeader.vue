<template>
  <header class="common-header">
    <x-flex style="width: 100%;height: 100%;" justify="space-between">
      <x-flex class="l-content" style="height: 100%;" align="center">
        <BrandLogo :compact="isMobile" />
      </x-flex>
      <!-- 保持挂载，切到桌面时让抽屉完成关闭动画。 -->
      <x-flex v-show="isMobile" align="center">
        <el-button
          aria-label="打开导航菜单"
          size="large"
          circle
          :icon="Menu"
          :aria-expanded="showMobileMenu"
          @click="showMobileMenu = true"
        />
        <!-- 页面与菜单均由受限的 el-scrollbar 滚动，无需锁定 body。 -->
        <el-drawer
          title="导航菜单"
          body-class="oj-menu-drawer-body"
          direction="ltr"
          size="min(280px, 85vw)"
          append-to-body
          destroy-on-close
          v-model="showMobileMenu"
          :lock-scroll="false"
        >
          <el-scrollbar height="100%">
            <x-flex class="mobile-theme" align="center" justify="space-between">
              <span>外观主题</span>
              <ThemeSelect />
            </x-flex>
            <el-menu
              mode="vertical"
              background-color="transparent"
              active-text-color="#2196f3"
              text-color="var(--ym-menu-text)"
              router
              :default-active="activeMenu"
              @select="onCloseMobileMenu"
            >
              <el-menu-item index="/">
                <el-icon><House /></el-icon>
                首页
              </el-menu-item>
              <el-menu-item index="/problem">
                <el-icon><Document /></el-icon>
                题库
              </el-menu-item>
              <el-menu-item index="/status">
                <el-icon><Check /></el-icon>
                状态
              </el-menu-item>
              <el-menu-item index="/contest">
                <el-icon><Trophy /></el-icon>
                比赛
              </el-menu-item>
              <el-menu-item index="/about">
                <el-icon><InfoFilled /></el-icon>
                关于
              </el-menu-item>
            </el-menu>
          </el-scrollbar>
        </el-drawer>
      </x-flex>
      <x-flex v-if="!isMobile" class="menu-content" style="height: 100%;">
        <el-menu
          :default-active="activeMenu"
          mode="horizontal"
          router
          active-text-color="#2196f3"
          text-color="var(--ym-menu-text)"
        >
          <el-menu-item index="/">
            <el-icon><HomeFilled /></el-icon>
            首页
          </el-menu-item>
          <el-menu-item index="/problem">
            <el-icon><Grid /></el-icon>
            题目
          </el-menu-item>
          <el-menu-item index="/status">
            <el-icon><TrendCharts /></el-icon>
            状态
          </el-menu-item>
          <el-menu-item index="/contest">
            <el-icon><Trophy /></el-icon>
            比赛
          </el-menu-item>
          <el-menu-item index="/about">
            <el-icon><InfoFilled /></el-icon>
            关于
          </el-menu-item>
        </el-menu>
      </x-flex>
      <x-flex class="r-content" align="center" style="margin-right: 25px;">
        <ThemeSelect v-if="!isMobile" />
        <el-button v-if="!isLogin" type="primary" plain round size="large" @click="Login">登录 | 注册</el-button>
        <x-flex v-else-if="user">
          <Avatar
            :src="user.avatar" 
            :text="user.username" 
            :size="40"
          />
          <el-dropdown trigger="click" placement="bottom">
            <x-flex align="center" style="cursor: pointer;">
              <span style="font-size: 17px;font-weight: bold;margin: 0 4px;">{{ user.username }}</span>
              <el-icon class="el-icon--right">
                <arrow-down />
              </el-icon>
            </x-flex>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="toUserHome">我的首页</el-dropdown-item>
                <el-dropdown-item @click="toSetting">我的设置</el-dropdown-item>
                <el-dropdown-item v-if="userStore.hasEveryPermission(['admin:access'])" @click="toAdmin">后端管理</el-dropdown-item>
                <el-dropdown-item divided :disabled="loggingOut" @click="LoginOut">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </x-flex>
      </x-flex>
    </x-flex>
  </header>
</template>

<script setup lang="ts">
import ThemeSelect from '@/components/common/ThemeSelect.vue';
import { ref, computed, watch, onMounted, onBeforeUnmount, defineAsyncComponent } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useUserStore } from '@/stores/user';
import { useAsyncChange } from '@/hooks/async/useAsyncChange';
import { storeToRefs } from 'pinia';
import { ElMessage } from 'element-plus';
import { Menu } from '@element-plus/icons-vue';
import BrandLogo from '@/components/common/BrandLogo.vue';
const Avatar=defineAsyncComponent(()=>import('@/components/common/Avatar.vue'));

//data
const userStore=useUserStore();
const router=useRouter();
const route=useRoute();
const { user, isLogin }=storeToRefs(userStore);
const { pending: loggingOut, beforeChange: logout } = useAsyncChange();

const isMobile = ref<boolean>(window.innerWidth <= 1024);
const showMobileMenu = ref<boolean>(false);

//methods
const onCloseMobileMenu = () => {
  showMobileMenu.value = false;
};
const onViewportChange = () => {
  isMobile.value = window.innerWidth <= 1024;
  if (!isMobile.value) {
    onCloseMobileMenu();
  }
};
watch(() => route.fullPath, onCloseMobileMenu);
onMounted(() => {
  window.addEventListener('resize', onViewportChange);
});
onBeforeUnmount(() => {
  window.removeEventListener('resize', onViewportChange);
});
//动态获取当前路由
const activeMenu=computed(()=>{
  const path = route.path;
  if(path.trim() === "/" || path.trim() === ""){
    return "/";
  }
  return "/"+path.split('/')[1];
});
//登录
const Login = () => {
  router.push('/login');
};
//退出登录
const LoginOut = async () => {
  await logout(async () => {
    try {
      await userStore.doLogout();
      ElMessage.success("退出登录成功");
    } finally {
      await router.push('/login');
    }
  });
};
//跳转到用户主页
const toUserHome=()=>{
  if (user.value) {
    router.push({ name: 'UserHome', query: { username: user.value.username } });
  }
}
//跳转到设置
const toSetting=()=>{
  router.push({ name: 'Setting' });
}
//跳转到后端管理
const toAdmin=()=>{
  router.push("/admin");
}
</script>

<style scoped>
.common-header {
  width: 100%;
  height: 100%;
  display: flex;
  overflow: hidden;
  background-color: var(--ym-surface);
  box-shadow: 0 1px 5px 0 var(--ym-header-shadow);
  border-bottom: 1px solid var(--ym-divider-soft);
  box-sizing: border-box;
}
.l-content {
  flex-shrink: 0;
  margin-left: 20px;
}
.el-menu{
  border: none;
  background: transparent;
}
.el-menu--horizontal {
  min-width: 600px;
}

/* 抽屉传送到 body；只由内部 el-scrollbar 负责滚动。 */
:global(.oj-menu-drawer-body) {
  min-height: 0;
  padding: 0;
  overflow: hidden;
}
.mobile-theme {
  padding: 12px 20px;
}
</style>
