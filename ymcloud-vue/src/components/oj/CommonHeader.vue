<template>
  <header class="common-header">
    <n-flex style="width: 100%;height: 100%;" justify="space-between">
      <n-flex class="l-content" style="height: 100%;" align="center">
        <el-image 
          lazy 
          :src="LogoImage" 
          alt="Logo" 
          style="height: 50px;width: 50px;margin-left: 20px;border-radius: 30%;"
          />
        <template v-if="!isMobile">
          <n-flex vertical :size="0">
            <span style="font-size: 20px;font-weight: bold;color: black;margin-left: 5px;">YMOJ</span>
            <span style="font-size: 12px;margin-left: 5px;">Yemiao Online Judge</span>
          </n-flex>
        </template>
      </n-flex>
      <!-- 移动端菜单 -->
      <n-flex v-if="isMobile" align="center">
        <el-button size="large" :icon="Menu" circle @click="showMobileMenu = !showMobileMenu" />
        <el-drawer v-model="showMobileMenu" direction="ltr" size="70%">
          <el-menu
            mode="vertical"
            :default-active="activeMenu"
            background-color="transparent"
            active-text-color="#2196f3"
            text-color="#495060"
            router
            @select="showMobileMenu=false"
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
        </el-drawer>
      </n-flex>
      <n-flex v-else class="menu-content" style="height: 100%;">
        <el-menu
          :default-active="activeMenu"
          mode="horizontal"
          router
          active-text-color="#2196f3"
          text-color="#495060"
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
      </n-flex>
      <n-flex class="r-content" align="center" style="margin-right: 25px;">
        <el-button v-if="!isLogin" type="primary" plain round size="large" @click="Login">登录 | 注册</el-button>
        <n-flex v-else>
          <Avatar
            :src="user.avatar" 
            :text="user.username" 
            :size="40"
          />
          <el-dropdown trigger="click" placement="bottom">
            <n-flex align="center" style="cursor: pointer;">
              <span style="font-size: 17px;font-weight: bold;margin: 0 4px;">{{ user.username }}</span>
              <el-icon class="el-icon--right">
                <arrow-down />
              </el-icon>
            </n-flex>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="toUserHome">我的首页</el-dropdown-item>
                <el-dropdown-item @click="toSetting">我的设置</el-dropdown-item>
                <el-dropdown-item v-if="userStore.hasAnyRole(['ADMIN','PROBLEM_SETTER'])" @click="toAdmin">后端管理</el-dropdown-item>
                <el-dropdown-item divided @click="LoginOut">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </n-flex>
      </n-flex>
    </n-flex>
  </header>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount,defineAsyncComponent } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useUserStore } from '@/stores/user';
import { storeToRefs } from 'pinia';
import { ElMessage } from 'element-plus';
import { Menu } from '@element-plus/icons-vue';
import LogoImage from '@/assets/image/logo.jpg';
const Avatar=defineAsyncComponent(()=>import('@/components/common/Avatar.vue'));

//data
const userStore=useUserStore();
const router=useRouter();
const route=useRoute();
const { user, isLogin }=storeToRefs(userStore);

const isMobile = ref<boolean>(false);
const showMobileMenu = ref<boolean>(false);

//methods
const checkMobile = () => {
  isMobile.value = window.innerWidth <= 1024
}
onMounted(()=>{
  checkMobile();
  window.addEventListener('resize', checkMobile);
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', checkMobile)
})
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
  try{
    await userStore.doLogout();
    ElMessage.success("退出登录成功");
  }catch(error){
    console.error("退出登录错误：",error);
  }
};
//跳转到用户主页
const toUserHome=()=>{
  router.push({ name: 'UserHome', query: { username: user.value.username } });
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
  background-color: #fff;
  box-shadow: 0 1px 5px 0 rgba(0, 0, 0, 0.1);
  border-bottom: 1px solid #f0f0f0;
  box-sizing: border-box;
}
.el-menu{
  border-bottom: none;
  background: transparent;
  min-width: 600px;
}
</style>
