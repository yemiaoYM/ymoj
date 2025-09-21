<template>
  <div class="tag-edit-container">
    <el-card>
      <template #header>
        <span style="font-size: 22px; font-weight: 600; color: #409eff;">标签管理</span>
      </template>
      <el-collapse v-model="activeNames">
        <n-flex :size="20" wrap>
          <div v-for="(category, index) in tagCategories" :key="category.name" class="tag-category-card">
            <el-collapse-item :name="category.name">
              <template #title>
                <n-flex justify="space-between" align="center" style="width: 100%; padding-right: 15px;">
                  <span style="font-weight: bold; font-size: 16px;">{{ category.name }}</span>
                  <n-flex :size="5">
                    <el-button type="primary" circle :icon="Edit" size="small" />
                    <el-button type="danger" circle :icon="Delete" size="small" />
                  </n-flex>
                </n-flex>
              </template>
              <n-flex wrap :size="10">
                <el-tag
                  v-for="tag in category.tags"
                  :key="tag"
                  closable
                  @close="handleTagClose(category, tag)"
                >
                  {{ tag }}
                </el-tag>
                <el-input
                  v-if="category.inputVisible"
                  :ref="(el:any) => (inputRefs[index] = el)"
                  v-model="category.inputValue"
                  size="small"
                  @keyup.enter="handleInputConfirm(category)"
                  @blur="handleInputConfirm(category)"
                  style="width: 90px;"
                />
                <el-button v-else size="small" @click="showInput(category, index)">
                  + 添加标签
                </el-button>
              </n-flex>
            </el-collapse-item>
          </div>
        </n-flex>
      </el-collapse>
    </el-card>
  </div>
</template>

<script lang="ts" setup>
import { ref, nextTick } from 'vue';
import { Edit, Delete } from '@element-plus/icons-vue';
import type { ElInput } from 'element-plus';

const activeNames = ref(['程序设计入门', '未分类']);
const inputRefs = ref<InstanceType<typeof ElInput>[]>([]);

const tagCategories = ref([
  { name: '程序设计入门', tags: ['加法', '数组', '乘法', '选择', '数学', '入门', '逆推'], inputVisible: false, inputValue: '' },
  { name: '基础算法', tags: [], inputVisible: false, inputValue: '' },
  { name: '搜索算法', tags: [], inputVisible: false, inputValue: '' },
  { name: '基础数据结构', tags: [], inputVisible: false, inputValue: '' },
  { name: '高级数据结构', tags: [], inputVisible: false, inputValue: '' },
  { name: '字符串', tags: [], inputVisible: false, inputValue: '' },
  { name: '图论', tags: [], inputVisible: false, inputValue: '' },
  { name: '树上算法', tags: [], inputVisible: false, inputValue: '' },
  { name: '数论', tags: [], inputVisible: false, inputValue: '' },
  { name: '蓝桥杯', tags: [], inputVisible: false, inputValue: '' },
  { name: '动态规划', tags: [], inputVisible: false, inputValue: '' },
  { name: '动态规划优化', tags: [], inputVisible: false, inputValue: '' },
  { name: '未分类', tags: ['新生赛', 'SPJ', '交互题', 'greedy', 'data structures', 'shortest paths', 'SHCPC', '一本通-基础篇', 'Special Judge', '堆', 'HDU', '早于 2010', '二分', 'USACO Contest', '三分', '剪枝优化', '拓扑排序', '数论', 'USACO Training'], inputVisible: false, inputValue: '' },
]);

const handleTagClose = (category: any, tag: string) => {
  category.tags.splice(category.tags.indexOf(tag), 1);
};

const showInput = (category: any, index: number) => {
  category.inputVisible = true;
  nextTick(() => {
    inputRefs.value[index]?.input!.focus();
  });
};

const handleInputConfirm = (category: any) => {
  if (category.inputValue) {
    category.tags.push(category.inputValue);
  }
  category.inputVisible = false;
  category.inputValue = '';
};
</script>

<style scoped lang="css">
.tag-edit-container {
  padding: 20px;
}
.tag-category-card {
  /* width: calc(33.333% - 14px); */
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background-color: #f5f7fa;
}
:deep(.el-collapse-item__header) {
  background-color: #f5f7fa;
  border-radius: 4px;
  padding: 0 10px;
}
:deep(.el-collapse-item__wrap) {
  background-color: white;
}
:deep(.el-collapse-item__content) {
  padding: 15px;
}
:deep(.el-collapse) {
  border: none;
}
</style>
