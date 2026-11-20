<template>
  <div class="tag-management">
    <el-card shadow="never">
      <x-flex justify="space-between" align="center" :size="16">
        <div>
          <h2 class="page-title">标签管理</h2>
          <el-text type="info">共 {{ tagCount }} 个标签 · {{ classifications.length }} 个分类，点击标签可编辑</el-text>
        </div>
        <x-flex class="tag-actions" align="center" :size="8">
          <el-button :icon="Refresh" :loading="loading" :disabled="buttonLoading" @click="getList">刷新</el-button>
          <el-button v-permission="['problem:tag:add']" :icon="FolderAdd" :disabled="buttonLoading" @click="onOpenClassification()">添加分类</el-button>
          <el-button v-permission="['problem:tag:add']" type="primary" :icon="Plus" :disabled="buttonLoading" @click="onOpenTag()">添加标签</el-button>
        </x-flex>
      </x-flex>
      <el-input
        class="tag-search"
        placeholder="搜索标签或分类"
        clearable
        aria-label="搜索标签或分类"
        v-model="search"
        :prefix-icon="Search"
      />
    </el-card>

    <el-alert v-if="loadFailed" title="标签列表加载失败，已显示的数据可能不是最新内容，请刷新重试。" type="error" show-icon :closable="false" />
    <div class="tag-groups" v-loading="loading">
      <el-empty v-if="loaded && !loadFailed && !filteredGroups.length" :description="search ? '没有匹配的标签或分类' : '暂无标签，点击「添加标签」开始整理题库'" />
      <el-card
        v-for="group in filteredGroups"
        :key="group.classification?.id ?? 'unclassified'"
        shadow="never"
      >
        <template #header>
          <x-flex justify="space-between" align="center" :size="12">
            <x-flex align="center" :size="8">
              <el-icon><Folder /></el-icon>
              <strong>{{ group.classification?.name || '未分类' }}</strong>
              <el-tag type="info" size="small" round>{{ group.tagList.length }} 个标签</el-tag>
              <el-text v-if="group.classification" type="info" size="small">排序 {{ group.classification.priority }}</el-text>
            </x-flex>
            <x-flex v-if="group.classification" class="tag-actions" align="center" :size="4">
              <el-button v-permission="['problem:tag:edit']" text size="small" :icon="Edit" :disabled="buttonLoading" @click="onOpenClassification(group.classification)">编辑分类</el-button>
              <el-button v-permission="['problem:tag:remove']" text type="danger" size="small" :icon="Delete" :disabled="buttonLoading" @click="onDeleteClassification(group.classification)">删除分类</el-button>
            </x-flex>
          </x-flex>
        </template>
        <x-flex align="center" :size="8">
          <el-tag
            v-for="item in group.tagList"
            :key="item.id"
            class="editable-tag"
            effect="dark"
            size="default"
            :color="item.color"
            :closable="canRemove && !buttonLoading"
            :role="canEdit ? 'button' : undefined"
            :tabindex="canEdit && !buttonLoading ? 0 : undefined"
            :aria-label="canEdit ? '编辑标签：' + item.name : undefined"
            @click="onOpenTag(item)"
            @keydown.enter.prevent="onOpenTag(item)"
            @keydown.space.prevent="onOpenTag(item)"
            @close.stop="onDeleteTag(item)"
          >{{ item.name }}</el-tag>
          <el-button
            v-permission="['problem:tag:add']"
            size="small"
            type="primary"
            plain
            :icon="Plus"
            :disabled="buttonLoading"
            @click="onOpenTag(null, group.classification)"
          >添加标签</el-button>
          <el-text v-if="!group.tagList.length" type="info" size="small">此分类暂无标签</el-text>
        </x-flex>
      </el-card>
    </div>

    <el-dialog
      width="min(440px, calc(100vw - 32px))"
      v-model="tagDialogOpen"
      :title="tag.id === null ? '添加标签' : '编辑标签'"
      :close-on-click-modal="false"
      :close-on-press-escape="!buttonLoading"
      :show-close="!buttonLoading"
    >
      <el-form ref="tagFormRef" label-width="88px" :model="tag" :rules="tagRules" :disabled="buttonLoading">
        <el-form-item label="标签名称" prop="name">
          <el-input placeholder="1～20 个字符" maxlength="20" show-word-limit v-model="tag.name" />
        </el-form-item>
        <el-form-item label="标签颜色" prop="color">
          <x-flex align="center" :size="12">
            <el-color-picker v-model="tag.color" />
            <el-tag effect="dark" :color="tag.color">{{ tag.name || '标签预览' }}</el-tag>
          </x-flex>
        </el-form-item>
        <el-form-item label="所属分类">
          <el-select placeholder="未分类" clearable v-model="tag.parentId" :value-on-clear="null">
            <el-option v-for="item in classifications" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="buttonLoading" @click="onCloseTag">取消</el-button>
        <el-button type="primary" :loading="buttonLoading" @click="onSaveTag">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      width="min(440px, calc(100vw - 32px))"
      v-model="classificationDialogOpen"
      :title="classification.id === null ? '添加分类' : '编辑分类'"
      :close-on-click-modal="false"
      :close-on-press-escape="!buttonLoading"
      :show-close="!buttonLoading"
    >
      <el-form ref="classificationFormRef" label-width="88px" :model="classification" :rules="classificationRules" :disabled="buttonLoading">
        <el-form-item label="分类名称" prop="name">
          <el-input placeholder="1～30 个字符" maxlength="30" show-word-limit v-model="classification.name" />
        </el-form-item>
        <el-form-item label="排序权重" prop="priority">
          <el-input-number controls-position="right" v-model="classification.priority" :min="1" :max="999" :precision="0" />
        </el-form-item>
        <el-text type="info" size="small">数值越小越靠前。删除分类后，标签会移至「未分类」。</el-text>
      </el-form>
      <template #footer>
        <el-button :disabled="buttonLoading" @click="onCloseClassification">取消</el-button>
        <el-button type="primary" :loading="buttonLoading" @click="onSaveClassification">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { useLoading } from '@/hooks/async/useLoading';
import modal from '@/plugins/modal';
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { Plus, Edit, Delete, Folder, FolderAdd, Refresh, Search } from '@element-plus/icons-vue';
import { useUserStore } from '@/stores/user';
import { useAsyncChange } from '@/hooks/async/useAsyncChange';
import type {
  Tag,
  TagDTO,
  TagClassification,
  TagClassificationDTO,
  TagsWithClassification,
} from '@/types/problem/tag';
import {
  getAdminTagsAndClassifications,
  addAdminTag,
  updateAdminTag,
  deleteAdminTag,
  addAdminTagClassification,
  updateAdminTagClassification,
  deleteAdminTagClassification,
} from '@/api/problem/tag';

const userStore = useUserStore();
const canEdit = computed(() => userStore.hasEveryPermission(['problem:tag:edit']));
const canRemove = computed(() => userStore.hasEveryPermission(['problem:tag:remove']));
const groups = ref<TagsWithClassification[]>([]);
const classifications = computed(() => groups.value.flatMap(group => group.classification ? [group.classification] : []));
const search = ref('');
const { loading, withLoading } = useLoading();
const loaded = ref(false);
const loadFailed = ref(false);
const { pending: buttonLoading, beforeChange: runAction } = useAsyncChange();
const tagDialogOpen = ref(false);
const classificationDialogOpen = ref(false);
const tagFormRef = ref<FormInstance>();
const classificationFormRef = ref<FormInstance>();
const tag = reactive<TagDTO & { id: number | null }>({ id: null, name: '', color: '#409eff', parentId: null });
const classification = reactive<TagClassificationDTO & { id: number | null }>({ id: null, name: '', priority: 1 });
const tagRules: FormRules<TagDTO> = {
  name: [{ required: true, whitespace: true, message: '请输入标签名称', trigger: 'blur' }, { max: 20, message: '名称最多20个字符', trigger: 'blur' }],
  color: [{ required: true, pattern: /^#[0-9a-fA-F]{6}$/, message: '请选择有效的标签颜色', trigger: 'change' }]
};
const classificationRules: FormRules<TagClassificationDTO> = {
  name: [{ required: true, whitespace: true, message: '请输入分类名称', trigger: 'blur' }, { max: 30, message: '名称最多30个字符', trigger: 'blur' }],
  priority: [{ required: true, type: 'integer', min: 1, max: 999, message: '请输入1～999的整数', trigger: 'change' }]
};
const tagCount = computed(() => groups.value.reduce((count, group) => count + group.tagList.length, 0));
const filteredGroups = computed(() => {
  const keyword = search.value.trim().toLocaleLowerCase();
  if (!keyword) {
    return groups.value;
  }
  return groups.value.map(group => ({
    ...group,
    tagList: (group.classification?.name || '未分类').toLocaleLowerCase().includes(keyword)
      ? group.tagList
      : group.tagList.filter(item => item.name.toLocaleLowerCase().includes(keyword))
  })).filter(group => group.tagList.length || (group.classification?.name || '未分类').toLocaleLowerCase().includes(keyword));
});
let disposed = false;

const getList = () => withLoading(async () => {
  loadFailed.value = false;
  try {
    const tagsResponse = await getAdminTagsAndClassifications();
    groups.value = tagsResponse.data.data;
    loaded.value = true;
  } catch {
    loadFailed.value = true;
  }
});

async function onOpenTag(value: Tag | null = null, parent: TagClassification | null = null) {
  if (buttonLoading.value || !userStore.hasEveryPermission([value ? 'problem:tag:edit' : 'problem:tag:add'])) {
    return;
  }
  Object.assign(tag, value
    ? { id: value.id, name: value.name, color: value.color, parentId: value.parentId }
    : { id: null, name: '', color: '#409eff', parentId: parent?.id ?? null });
  tagDialogOpen.value = true;
  await nextTick();
  tagFormRef.value?.clearValidate();
}

async function onOpenClassification(value: TagClassification | null = null) {
  if (buttonLoading.value || !userStore.hasEveryPermission([value ? 'problem:tag:edit' : 'problem:tag:add'])) {
    return;
  }
  Object.assign(classification, value ?? { id: null, name: '', priority: 1 });
  classificationDialogOpen.value = true;
  await nextTick();
  classificationFormRef.value?.clearValidate();
}

function onCloseTag() {
  tagDialogOpen.value = false;
}

function onCloseClassification() {
  classificationDialogOpen.value = false;
}

async function onSaveTag() {
  const form = tagFormRef.value;
  if (buttonLoading.value || !form || !userStore.hasEveryPermission([tag.id === null ? 'problem:tag:add' : 'problem:tag:edit'])) {
    return;
  }
  await runAction(async () => {
    await form.validate();
    if (disposed) {
      return false;
    }
    const data: TagDTO = { name: tag.name.trim(), color: tag.color, parentId: tag.parentId ?? null };
    const response = tag.id === null ? await addAdminTag(data) : await updateAdminTag(tag.id, data);
    if (!disposed) {
      ElMessage.success(response.data.msg);
      tagDialogOpen.value = false;
      await getList();
    }
  });
}

async function onSaveClassification() {
  const form = classificationFormRef.value;
  if (buttonLoading.value || !form || !userStore.hasEveryPermission([classification.id === null ? 'problem:tag:add' : 'problem:tag:edit'])) {
    return;
  }
  await runAction(async () => {
    await form.validate();
    if (disposed) {
      return false;
    }
    const data: TagClassificationDTO = { name: classification.name.trim(), priority: classification.priority };
    const response = classification.id === null
      ? await addAdminTagClassification(data)
      : await updateAdminTagClassification(classification.id, data);
    if (!disposed) {
      ElMessage.success(response.data.msg);
      classificationDialogOpen.value = false;
      await getList();
    }
  });
}

async function handleDelete(kind: 'tag' | 'classification', value: Tag | TagClassification) {
  if (buttonLoading.value || !canRemove.value) {
    return;
  }
  await runAction(async () => {
    await modal.confirm(
      kind === 'tag'
        ? '确认删除标签「' + value.name + '」？关联题目将取消此标签。'
        : '确认删除分类「' + value.name + '」？标签将保留并移至未分类。',
      '删除确认', '删除',
    );
    if (disposed) {
      return false;
    }
    const response = kind === 'tag' ? await deleteAdminTag(value.id) : await deleteAdminTagClassification(value.id);
    if (!disposed) {
      ElMessage.success(response.data.msg);
      await getList();
    }
  });
}

function onDeleteTag(value: Tag) {
  return handleDelete('tag', value);
}

function onDeleteClassification(value: TagClassification) {
  return handleDelete('classification', value);
}

onMounted(getList);
onBeforeUnmount(() => {
  disposed = true;
});
</script>

<style scoped>
.tag-management,
.tag-groups {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.tag-management {
  padding: 16px;
}
.tag-groups {
  min-height: 120px;
}
.page-title {
  margin: 0 0 8px;
  font-size: 18px;
  color: var(--el-text-color-primary);
}
.tag-search {
  max-width: 360px;
  margin-top: 16px;
}
.tag-actions :deep(.el-button + .el-button) {
  /* x-flex 已提供间距，避免按钮默认外边距造成换行后左侧错位。 */
  margin-left: 0;
}
.editable-tag {
  margin: 0;
}
.editable-tag[role='button'] {
  cursor: pointer;
}
.editable-tag:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}
@media (max-width: 767px) {
  .tag-management {
    padding: 12px;
  }
  .tag-search {
    max-width: none;
  }
}
</style>
