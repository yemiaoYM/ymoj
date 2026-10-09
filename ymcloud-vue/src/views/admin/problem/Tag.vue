<template>
  <div class="tag-management">
    <!-- 页面标题和操作区 -->
    <el-card>
      <template #header>
        <n-flex justify="space-between" align="center">
          <span style="font-size: 18px; font-weight: 600; color: #409eff;">标签管理</span>
          <n-flex :size="12" align="center">
            <el-button
              v-permission="['problem:tag:add']"
              type="primary"
              @click="openTagDialog('add', null)"
              :icon="Plus"
            >
              添加标签
            </el-button>
            <el-button
              v-permission="['problem:tag:add']"
              type="warning"
              @click="openTagClassificationDialog('add', null)"
              :icon="FolderAdd"
            >
              添加分类
            </el-button>
          </n-flex>
        </n-flex>
      </template>
      
      <el-alert
        title="标签管理提示"
        description="默认所有标签都是【未分类】，请先【添加标签分类】，再修改【标签分类】进行归类。"
        type="info"
        :closable="false"
        show-icon
      />
    </el-card>

    <!-- 标签列表区域 -->
    <div v-loading="getTagListLoading" style="margin-top: 16px;">
      <n-flex vertical :size="16">
        <el-card
          v-for="(tagsAndClassification, index) in tagsAndClassificationList"
          :key="index"
          shadow="hover"
        >
          <!-- 分类标题区 -->
          <template #header>
            <n-flex justify="space-between" align="center">
              <n-flex align="center" :size="12">
                <el-icon size="16" color="#409eff">
                  <Folder v-if="tagsAndClassification.classification" />
                  <Document v-else />
                </el-icon>
                <span style="font-size: 16px; font-weight: 500;">
                  {{ tagsAndClassification.classification?.name || '未分类' }}
                </span>
                <el-tag v-if="tagsAndClassification.classification" size="small" type="info">
                  排序: {{ tagsAndClassification.classification.priority }}
                </el-tag>
              </n-flex>
              
              <n-flex :size="8" v-if="tagsAndClassification.classification">
                <el-button
                  v-permission="['problem:tag:edit']"
                  type="primary"
                  size="small"
                  :icon="Edit"
                  circle
                  @click="openTagClassificationDialog('update', tagsAndClassification.classification)"
                />
                <el-button
                  v-permission="['problem:tag:remove']"
                  type="danger"
                  size="small"
                  :icon="Delete"
                  circle
                  @click="deleteTagClassification(tagsAndClassification.classification)"
                />
              </n-flex>
            </n-flex>
          </template>

          <!-- 标签内容区 -->
          <n-flex :size="8" style="min-height: 40px;" align="flex-start">
            <el-button
              v-permission="['problem:tag:add']"
              size="small"
              type="primary"
              plain
              :icon="Plus"
              @click="openTagDialog('add', null, tagsAndClassification.classification)"
            >
              添加标签
            </el-button>
            
            <el-tag
              v-for="(tag, tagIndex) in tagsAndClassification.tagList"
              :key="tagIndex"
              :color="tag.color"
              effect="dark"
              :closable="userStore.hasEveryPermission(['problem:tag:remove'])"
              size="default"
              style="cursor: pointer; margin: 2px;"
              @close="deleteTag(tag)"
              @click="userStore.hasEveryPermission(['problem:tag:edit']) && openTagDialog('update', tag)"
            >
              {{ tag.name }}
            </el-tag>
          </n-flex>
        </el-card>
      </n-flex>
    </div>

    <!-- 标签对话框 -->
    <el-dialog
      v-model="addTagDialogVisible"
      :title="upsertTagTitle"
      width="400px"
      :close-on-click-modal="false"
    >
      <el-form :model="tag" label-width="100px" label-position="left">
        <el-form-item label="标签名称" required>
          <el-input v-model="tag.name" placeholder="请输入标签名称" />
        </el-form-item>
        
        <el-form-item label="标签颜色" required>
          <n-flex align="center" :size="12">
            <el-color-picker v-model="tag.color" />
            <el-tag :color="tag.color" effect="dark">{{ tag.name || '预览' }}</el-tag>
          </n-flex>
        </el-form-item>

        <el-form-item label="标签分类">
          <el-select v-model="tag.parentId" placeholder="选择分类" style="width: 100%;">
            <el-option label="未分类" :value="null" />
            <el-option
              v-for="(classification, index) in chooseTagClassificationList"
              :key="index"
              :label="classification.name"
              :value="classification.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <n-flex justify="end" :size="12">
          <el-button @click="addTagDialogVisible = false">取消</el-button>
          <el-button
            v-permission="tag.id ? ['problem:tag:edit'] : ['problem:tag:add']"
            type="primary"
            @click="upsertTag"
            :loading="upsertTagLoading"
          >
            {{ upsertTagBtn }}
          </el-button>
        </n-flex>
      </template>
    </el-dialog>

    <!-- 标签分类对话框 -->
    <el-dialog
      v-model="addTagClassificationDialogVisible"
      :title="upsertTagClassificationTitle"
      width="400px"
      :close-on-click-modal="false"
    >
      <el-form :model="tagClassification" label-width="100px" label-position="left">
        <el-form-item label="分类名称" required>
          <el-input v-model="tagClassification.name" placeholder="请输入分类名称" />
        </el-form-item>

        <el-form-item label="排序权重" required>
          <el-input-number
            v-model="tagClassification.priority"
            :min="1"
            :max="999"
            controls-position="right"
            style="width: 100%;"
            placeholder="数值越小排序越靠前"
          />
          <div style="font-size: 12px; color: #909399; margin-top: 4px;">
            数值越小排序越靠前，范围：1-999
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <n-flex justify="end" :size="12">
          <el-button @click="addTagClassificationDialogVisible = false">取消</el-button>
          <el-button
            v-permission="tagClassification.id ? ['problem:tag:edit'] : ['problem:tag:add']"
            type="primary"
            @click="upsertTagClassification"
            :loading="upsertTagClassificationLoading"
          >
            {{ tagClassification.id ? '更新' : '添加' }}
          </el-button>
        </n-flex>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
//import
import { useUserStore } from '@/stores/user';
import { ref, reactive, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus, Edit, Delete, Folder, Document, FolderAdd } from '@element-plus/icons-vue';
import { 
  getAdminTagsAndClassifications, 
  addAdminTag, 
  updateAdminTag, 
  deleteAdminTag,
  getAdminTagClassifications,
  addAdminTagClassification,
  updateAdminTagClassification,
  deleteAdminTagClassification
} from '@/api/api';

//data
const userStore = useUserStore();
const getTagListLoading = ref<boolean>(false);
const upsertTagLoading = ref<boolean>(false);
const upsertTagClassificationLoading = ref<boolean>(false);
//对话框显示状态
const addTagDialogVisible = ref<boolean>(false);
const addTagClassificationDialogVisible = ref<boolean>(false);
//对话框标题
const upsertTagTitle = ref<string>('');
const upsertTagClassificationTitle = ref<string>('');
const upsertTagBtn = ref<string>('');
//标签和分类列表
const tagsAndClassificationList = ref<any[]>([]);
//可选择的标签分类列表
const chooseTagClassificationList = ref<any[]>([]);


const tag = reactive({
  id: null as number | null,
  name: '',
  color: '#409eff',
  parentId: null as number | null,
});
const tagClassification = reactive({
  id: null as number | null,
  name: '',
  priority: 1,
});


//methods
//初始化数据
const init = () => {
  getTagListLoading.value = true;
  //获取标签和分类列表
  getAdminTagsAndClassifications().then(res => {
    tagsAndClassificationList.value = res.data.data || [];
  }).catch(e => {
    console.log(e);
  });
  getAdminTagClassifications().then(res => {
    chooseTagClassificationList.value = res.data.data || [];
  }).catch(e => {
    console.log(e);
  });
  getTagListLoading.value = false;
};
//打开标签对话框
const openTagDialog = (type: string, tagData: any, classification?: any) => {
  if (type === 'add') {
    upsertTagTitle.value = '添加标签';
    upsertTagBtn.value = '添加';
    //重置表单
    tag.id = null;
    tag.name = '';
    tag.color = '#409eff';
    tag.parentId = classification ? classification.id : null;
  } else {
    upsertTagTitle.value = '编辑标签';
    upsertTagBtn.value = '更新';
    //填充表单
    tag.id = tagData.id;
    tag.name = tagData.name;
    tag.color = tagData.color;
    tag.parentId = tagData.parentId;
  }
  addTagDialogVisible.value = true;
};
//打开标签分类对话框
const openTagClassificationDialog = (type: string, classificationData: any) => {
  if (type === 'add') {
    upsertTagClassificationTitle.value = '添加标签分类';
    //重置表单
    tagClassification.id = null;
    tagClassification.name = '';
    tagClassification.priority = 1;
  } else {
    upsertTagClassificationTitle.value = '编辑标签分类';
    //填充表单
    tagClassification.id = classificationData.id;
    tagClassification.name = classificationData.name;
    tagClassification.priority = classificationData.priority;
  }
  addTagClassificationDialogVisible.value = true;
};
//新增/更新标签
const upsertTag = async () => {
  if (!tag.name.trim()) {
    ElMessage.warning('请输入标签名称');
    return;
  }
  if (!tag.color.trim()) {
    ElMessage.warning('请选择标签颜色');
    return;
  }
  upsertTagLoading.value = true;
  try {
    const tagData = {
      name: tag.name.trim(),
      color: tag.color,
      parentId: tag.parentId
    };
    
    let res;
    if (tag.id) {
      //更新标签
      res = await updateAdminTag(tag.id, tagData);
    } else {
      //添加标签
      res = await addAdminTag(tagData);
    }
    ElMessage.success(res.data.msg);
    addTagDialogVisible.value = false;
    init(); //重新加载数据
  } catch (e) {
    console.log(e);
  } finally {
    upsertTagLoading.value = false;
  }
};
//新增/更新标签分类
const upsertTagClassification = async () => {
  if (!tagClassification.name.trim()) {
    ElMessage.warning('请输入标签分类名称');
    return;
  }
  if (tagClassification.priority < 1 || tagClassification.priority > 999) {
    ElMessage.warning('排序权重必须在1-999之间');
    return;
  }
  upsertTagClassificationLoading.value = true;
  try {
    const classificationData = {
      name: tagClassification.name.trim(),
      priority: tagClassification.priority
    };
    let res;
    if (tagClassification.id) {
      //更新标签分类
      res = await updateAdminTagClassification(tagClassification.id, classificationData);
    } else {
      //添加标签分类
      res = await addAdminTagClassification(classificationData);
    }
    ElMessage.success(res.data.msg);
    addTagClassificationDialogVisible.value = false;
    init(); // 重新加载数据
  } catch (e) {
    console.log(e);
  } finally {
    upsertTagClassificationLoading.value = false;
  }
};
//删除标签
const deleteTag = (tagData: any) => {
  ElMessageBox.confirm(
    `确认删除标签 "${tagData.name}" 吗？删除后与该标签关联的题目将取消关联。`,
    '删除确认',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(async () => {
    try {
      const res = await deleteAdminTag(tagData.id);
      ElMessage.success(res.data.msg);
      init(); //重新加载数据
    } catch (e) {
      console.log(e);
    } finally {
      upsertTagClassificationLoading.value = false;
    }
  }).catch(() => {});
};
//删除标签分类
const deleteTagClassification = (classificationData: any) => {
  ElMessageBox.confirm(
    `确认删除标签分类 "${classificationData.name}" 吗？删除后该分类下的所有标签将移至未分类。`,
    '删除确认',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(async () => {
    try {
      const res = await deleteAdminTagClassification(classificationData.id);
      ElMessage.success(res.data.msg);
      init(); //重新加载数据
    } catch (e) {
      console.log(e);
    } finally {
      upsertTagClassificationLoading.value = false;
    }
  }).catch(() => {});
};

//生命周期
onMounted(() => {
  init();
});
</script>

<style scoped>
.tag-management {
  padding: 16px;
}

@media (max-width: 768px) {
  .tag-management {
    padding: 12px;
  }
}
</style>
