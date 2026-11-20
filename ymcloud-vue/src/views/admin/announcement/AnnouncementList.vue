<template>
  <div class="announcement-management">
    <x-flex vertical :size="16">
      <el-card>
        <!-- 搜索区域 -->
        <el-form :model="queryParams" ref="queryFormRef" label-width="auto">
          <x-flex :size="10">
            <el-form-item label="公告标题" prop="title">
              <el-input
                v-model="queryParams.title"
                placeholder="请输入公告标题"
                clearable
              />
            </el-form-item>
            <el-form-item label="置顶状态" prop="pinned">
              <el-select
                v-model="queryParams.pinned"
                placeholder="请选择置顶状态"
                clearable
                style="width: 120px"
              >
                <el-option label="已置顶" :value="true" />
                <el-option label="未置顶" :value="false" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态" prop="status">
              <el-select
                v-model="queryParams.status"
                placeholder="请选择状态"
                clearable
                style="width: 120px"
              >
                <el-option label="可见" :value="true" />
                <el-option label="隐藏" :value="false" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="handleQuery"
                >搜索</el-button
              >
              <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </x-flex>
        </el-form>

        <!-- 操作区域 -->
        <x-flex :size="10" align="center" style="padding-bottom: 15px">
          <el-button
            v-permission="['announcement:announcement:add']"
            plain
            type="primary"
            :icon="Plus"
            @click="handleAdd"
          >
            新增
          </el-button>
          <el-button
            v-permission="['announcement:announcement:query', 'announcement:announcement:edit']"
            plain
            type="success"
            :icon="Edit"
            :disabled="single"
            @click="handleUpdate()"
            >修改</el-button
          >
          <el-button
            v-permission="['announcement:announcement:remove']"
            plain
            type="danger"
            :icon="Delete"
            :disabled="multiple"
            @click="handleDelete()"
            >删除</el-button
          >
        </x-flex>

        <!-- 公告列表 -->
        <el-table
          :data="announcementList"
          v-loading="loading"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="55" align="center" />
          <el-table-column label="ID" prop="id" width="80" align="center" />
          <el-table-column
            label="标题"
            prop="title"
            min-width="150"
            show-overflow-tooltip
          />
          <el-table-column
            label="权重"
            prop="priority"
            width="80"
            align="center"
          />
          <el-table-column label="置顶" width="80" align="center">
            <template #default="scope">
              <AsyncSwitch
                :disabled="!userStore.hasEveryPermission(['announcement:announcement:edit'])"
                v-model="scope.row.pinned"
                :before-change="() => handleAnnouncementChange(scope.row, 'pinned')"
              />
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80" align="center">
            <template #default="scope">
              <AsyncSwitch
                :disabled="!userStore.hasEveryPermission(['announcement:announcement:edit'])"
                v-model="scope.row.status"
                :before-change="() => handleAnnouncementChange(scope.row, 'status')"
              />
            </template>
          </el-table-column>
          <el-table-column
            label="发布人"
            prop="authorUsername"
            width="120"
            align="center"
          />
          <el-table-column
            label="创建时间"
            prop="createdTime"
            width="160"
            align="center"
          >
            <template #default="scope">
              {{ scope.row.createdTime }}
            </template>
          </el-table-column>
          <el-table-column
            label="更新时间"
            prop="updatedTime"
            width="160"
            align="center"
          >
            <template #default="scope">
              {{ scope.row.updatedTime }}
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="150" align="center">
            <template #default="scope">
              <x-flex :size="0" justify="center">
                <el-button
                  v-permission="['announcement:announcement:query', 'announcement:announcement:edit']"
                  class="operation-button"
                  type="primary"
                  size="small"
                  text
                  :icon="Edit"
                  @click="handleUpdate(scope.row)"
                >
                  编辑
                </el-button>
                <el-button
                  v-permission="['announcement:announcement:remove']"
                  class="operation-button"
                  type="danger"
                  size="small"
                  text
                  :icon="Delete"
                  @click="handleDelete(scope.row)"
                >
                  删除
                </el-button>
              </x-flex>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <x-flex justify="center" style="margin-top: 20px">
          <el-pagination
            :page-size="queryParams.pageSize"
            :current-page="queryParams.pageNo"
            :total="total"
            :page-sizes="[10, 20, 30]"
            background
            layout="total, sizes, prev, pager, next"
            @update:current-page="handleCurrentChange"
            @update:page-size="handleSizeChange"
          />
        </x-flex>
      </el-card>
    </x-flex>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialog.visible"
      destroy-on-close
      :title="dialog.title"
      width="1300px"
      :before-close="handleClose"
      style="padding: 20px"
    >
      <el-form
        ref="announcementFormRef"
        :disabled="buttonLoading"
        :model="announcementForm"
        :rules="announcementRules"
        label-width="100px"
        label-position="top"
      >
        <el-form-item label="公告标题" prop="title">
          <el-input
            v-model="announcementForm.title"
            placeholder="请输入公告标题"
          />
        </el-form-item>
        <el-form-item label="公告权重" prop="priority">
          <el-input-number
            v-model="announcementForm.priority"
            :min="0"
            :max="999"
            placeholder="权重值越大越靠前"
          />
        </el-form-item>
        <el-form-item label="置顶状态" prop="pinned">
          <el-switch
            :disabled="!userStore.hasEveryPermission([currentAnnouncementId === null ? 'announcement:announcement:add' : 'announcement:announcement:edit'])"
            v-model="announcementForm.pinned"
            active-text="置顶"
            inactive-text="不置顶"
          />
        </el-form-item>
        <el-form-item label="显示状态" prop="status">
          <el-switch
            :disabled="!userStore.hasEveryPermission([currentAnnouncementId === null ? 'announcement:announcement:add' : 'announcement:announcement:edit'])"
            v-model="announcementForm.status"
            active-text="显示"
            inactive-text="隐藏"
          />
        </el-form-item>
        <el-form-item label="公告内容" prop="content">
          <div style="width: 100%">
            <MarkdownEidt
              @image-uploaded="onImageUploaded"
              image-purpose="announcement"
              :biz-id="currentAnnouncementId || undefined"
              ref="contentEditorRef"
              :key="`content-${currentAnnouncementId}`"
              v-model:content="announcementForm.content"
              :disabled="buttonLoading"
            />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button :disabled="buttonLoading" @click="handleClose">取消</el-button>
          <el-button
            v-permission="currentAnnouncementId === null ? ['announcement:announcement:add'] : ['announcement:announcement:edit']"
            type="primary"
            @click="submitForm"
            :loading="buttonLoading"
          >
            确定
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
//import
import modal from '@/plugins/modal';
import { useDialogState } from '@/hooks/dialog/useDialogState';
import { useSearchReset } from '@/hooks/form/useSearchReset';
import { useTableSelection } from '@/hooks/table/useTableSelection';
import { useLoading } from '@/hooks/async/useLoading';
import { referencedImageIds } from '@/hooks/useFileImages';
import AsyncSwitch from '@/components/common/AsyncSwitch.vue';
import { useAsyncChange } from '@/hooks/async/useAsyncChange';
import { useUserStore } from "@/stores/user";
import { ref, onMounted, reactive, nextTick, defineAsyncComponent } from "vue";
import { ElMessage, type FormInstance } from "element-plus";
import { Search, Refresh, Plus, Edit, Delete } from "@element-plus/icons-vue";
import type { Announcement, AnnouncementDTO } from '@/types/announcement';
import {
  getAdminAnnouncementList,
  getAdminAnnouncementById,
  addAnnouncement,
  updateAnnouncement,
  deleteAnnouncementByIds,
  updateAnnouncementStatus,
  updateAnnouncementPinned,
} from '@/api/announcement';

//异步导入markdown
const MarkdownEidt = defineAsyncComponent(
  () => import("@/components/common/markdown/MarkdownEidt.vue")
);

//data
const userStore = useUserStore();
const announcementList = ref<Announcement[]>([]);
const { ids: announcementIds, single, multiple, handleSelectionChange } = useTableSelection((row: Announcement) => row.id);
const total = ref<number>(0);
const { loading, withLoading } = useLoading();
const { pending: buttonLoading, beforeChange: runAction } = useAsyncChange();
const { dialog, openDialog, closeDialog } = useDialogState();
const queryFormRef = ref<FormInstance>();
const announcementFormRef = ref<FormInstance>();
const contentEditorRef = ref<InstanceType<typeof MarkdownEidt>>();
const currentAnnouncementId = ref<number | null>(null);
//查询参数
const queryParams = ref({
  title: null as string | null,
  pinned: null as boolean | null,
  status: null as boolean | null,
  pageNo: 1,
  pageSize: 10,
});
//表单数据
const announcementForm = reactive<AnnouncementDTO>({
  title: "",
  content: "",
  priority: 0,
  pinned: false,
  status: true,
});
function onImageUploaded(id: string) {
  (announcementForm.fileIds ??= []).push(id);
}

//表单验证规则
const announcementRules = {
  title: [
    { required: true, message: "请输入公告标题", trigger: "blur" },
    { min: 1, max: 100, message: "标题长度在1到100个字符", trigger: "blur" },
  ],
  content: [
    { required: true, message: "请输入公告内容", trigger: "blur" },
    { max: 10000, message: "内容长度不能超过10000个字符", trigger: "blur" },
  ],
  priority: [
    { required: true, message: "请输入权重", trigger: "blur" },
    {
      type: "number",
      min: 0,
      max: 999,
      message: "权重范围在0-999",
      trigger: "blur",
    },
  ],
};

onMounted(() => {
  getList();
});

//methods
//查询公告列表
const getList = () => withLoading(async () => {
  try {
    const { data } = await getAdminAnnouncementList(queryParams.value);
    announcementList.value = data.data.records;
    total.value = data.data.total;
  } catch {
    // 请求层已提示查询失败，保留当前列表
    return;
  }
});
//重置查询
const handleQuery = () => {
  queryParams.value.pageNo = 1;
  return getList();
};

const { resetQuery } = useSearchReset(queryFormRef, queryParams, 15, getList);
//改变页大小
const handleSizeChange = (val: number) => {
  queryParams.value.pageSize = val;
  getList();
};
//改变当前页
const handleCurrentChange = (val: number) => {
  queryParams.value.pageNo = val;
  getList();
};
//公告的即时状态在确认和请求成功后才交给开关更新
const handleAnnouncementChange = async (row: Announcement, field: 'status' | 'pinned') => {
  const nextValue = !row[field];
  const action = field === 'status' ? (nextValue ? '显示' : '隐藏') : (nextValue ? '置顶' : '取消置顶');
  await modal.confirm(`确定要${action}公告 "${row.title}" 吗？`, '状态修改确认');
  const response = field === 'status'
    ? await updateAnnouncementStatus(row.id, nextValue)
    : await updateAnnouncementPinned(row.id, nextValue);
  ElMessage.success(response.data.msg);
};
//新增公告
const handleAdd = () => {
  reset();
  openDialog("新增公告");
};
//编辑公告
const handleUpdate = (row?: Announcement) => {
  const id = row?.id ?? announcementIds.value[0];
  if (!id) {
    ElMessage.warning("请选择要编辑的公告");
    return;
  }
  reset();
  getAdminAnnouncementById(id)
    .then((res) => {
      const data = res.data.data;
      Object.assign(announcementForm, {
        title: data.title,
        content: data.content,
        priority: data.priority,
        pinned: data.pinned,
        status: data.status,
      });
      currentAnnouncementId.value = id;
      openDialog("编辑公告");
    })
    .catch((err) => {
      console.log(err);
    });
};
//删除公告
const handleDelete = (row?: Announcement) => runAction(async () => {
  const ids = row ? [row.id] : announcementIds.value;
  if (!ids.length) return false;
  await modal.confirm(`是否确认删除公告编号为 "${ids.join(', ')}" 的数据项？`, '删除确认');
  const response = await deleteAnnouncementByIds(ids);
  ElMessage.success(response.data.msg);
  await getList();
});
//校验和保存期间保持弹窗，只有成功后才关闭
const submitForm = async () => {
  const form = announcementFormRef.value;
  const editor = contentEditorRef.value;
  if (buttonLoading.value || !form || !editor) return;
  await runAction(async () => {
    await form.validate();
    if (!editor.canSaveContent()) return false;
    const data = {
      ...announcementForm,
      fileIds: currentAnnouncementId.value === null
        ? referencedImageIds(announcementForm.fileIds, announcementForm.content)
        : undefined,
    };
    const response = currentAnnouncementId.value === null
      ? await addAnnouncement(data)
      : await updateAnnouncement(currentAnnouncementId.value, data);
    ElMessage.success(response.data.msg);
    closeDialog();
    getList();
  });
};
//重置表单
const reset = () => {
  Object.assign(announcementForm, {
    fileIds: [],
    title: "",
    content: "",
    priority: 0,
    pinned: false,
    status: true,
  });
  currentAnnouncementId.value = null;
  nextTick(() => {
    if (announcementFormRef.value) {
      announcementFormRef.value.resetFields();
    }
  });
};
//关闭对话框
const handleClose = () => {
  if (buttonLoading.value) return;
  reset();
  closeDialog();
};
</script>

<style scoped>
.announcement-management {
  padding: 20px;
}

.dialog-footer {
  text-align: right;
}

.operation-button {
  padding: 0px;
}

:deep(.el-table__header-wrapper th) {
  word-break: break-word;
  background-color: var(--ym-table-header);
  color: var(--ym-table-heading);
  height: 40px;
  font-size: 13px;
}
</style>
