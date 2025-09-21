<template>
  <template v-for="child in item" :key="child.name">
    <!-- 叶子节点 -->
    <el-menu-item
      v-if="!child.children || child.children.length === 0"
      :index="resolvePath(basePath, child.path)"
    >
      <el-icon v-if="child.meta.icon">
        <component :is="child.meta.icon" />
      </el-icon>
      <span>{{ child.meta.title }}</span>
    </el-menu-item>

    <!-- 子菜单 -->
    <el-sub-menu v-else :index="resolvePath(basePath, child.path)">
      <template #title>
        <el-icon v-if="child.meta.icon">
          <component :is="child.meta.icon" />
        </el-icon>
        <span>{{ child.meta.title }}</span>
      </template>
      <!-- 递归 -->
      <SidebarItem :item="child.children" :basePath="resolvePath(basePath, child.path)" />
    </el-sub-menu>
  </template>
</template>

<script>
import { defineComponent } from 'vue';

export default defineComponent({
  props: {
    item: {
      type: Array,
      default: () => [],
    },
    basePath: {
      type: String,
      default: '',
    },
  },
  setup(props){

    //解析路径
    const resolvePath = (basePath = '', routePath = '') => {
      if (routePath.startsWith('/')) {
        return routePath;
      }
      if (basePath.endsWith('/')) {
        return basePath + routePath;
      }
      return basePath + '/' + routePath;
    }

    return {
      resolvePath
    }
  }
});
</script>
