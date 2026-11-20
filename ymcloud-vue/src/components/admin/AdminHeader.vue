<template>
  <div class="header-container">
    <div class="header-navigation">
      <el-button
        class="menu-toggle"
        text
        :icon="mobile || appStore.isCollapse ? Expand : Fold"
        :aria-label="mobile ? (menuOpen ? '关闭导航菜单' : '打开导航菜单') : (appStore.isCollapse ? '展开导航菜单' : '折叠导航菜单')"
        :aria-expanded="mobile ? menuOpen : !appStore.isCollapse"
        @click="emit('toggle-menu')"
      />
      <el-breadcrumb class="breadcrumb">
        <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">
          <!-- matched 中的父级是布局/目录，不是可访问页面。 -->
          <span>{{ item.meta.title }}</span>
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>
    <ThemeSelect />
    <el-dropdown v-if="user" trigger="click" placement="bottom-end">
      <el-button class="user-menu" text aria-label="账户菜单">
        <Avatar :size="32" :src="user.avatar" :text="user.username" />
        <span class="username">{{ user.username }}</span>
        <el-icon><ArrowDown /></el-icon>
      </el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item @click="onClientHome">返回客户端</el-dropdown-item>
          <el-dropdown-item :disabled="loggingOut" @click="onLogout">退出登录</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup lang="ts">
import ThemeSelect from '@/components/common/ThemeSelect.vue';
import { computed, defineAsyncComponent } from 'vue';
import { ArrowDown, Expand, Fold } from '@element-plus/icons-vue';
import { useRoute, useRouter } from 'vue-router';
import { storeToRefs } from 'pinia';
import { useUserStore } from '@/stores/user';
import { useAppStore } from '@/stores/app';
import { useAsyncChange } from '@/hooks/async/useAsyncChange';

defineProps<{ mobile: boolean; menuOpen: boolean }>();
const emit = defineEmits<{ 'toggle-menu': [] }>();
const Avatar = defineAsyncComponent(() => import('@/components/common/Avatar.vue'));
const appStore = useAppStore();
const userStore = useUserStore();
const { user } = storeToRefs(userStore);
const { pending: loggingOut, beforeChange: logout } = useAsyncChange();
const route = useRoute();
const router = useRouter();
const breadcrumbs = computed(() => route.matched.filter(item => item.meta.title));

async function onLogout() {
  await logout(async () => {
    try {
      await userStore.doLogout();
    } finally {
      await router.push('/admin/login');
    }
  });
}

function onClientHome() {
  router.push('/');
}
</script>

<style scoped>
.header-container,
.header-navigation {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.header-container {
  height: 100%;
  justify-content: space-between;
}
.header-navigation {
  flex: 1;
}
.menu-toggle {
  flex-shrink: 0;
  height: 40px;
  padding: 8px;
  font-size: 22px;
}
.breadcrumb {
  min-width: 0;
  line-height: 1.6;
  overflow: hidden;
}
.user-menu {
  flex-shrink: 0;
  height: 44px;
  padding: 4px 8px;
}
.username {
  max-width: 140px;
  margin: 0 8px;
  overflow: hidden;
  text-overflow: ellipsis;
}
@media (max-width: 767px) {
  .username {
    display: none;
  }
  .header-container,
  .header-navigation {
    gap: 8px;
  }
}
</style>
