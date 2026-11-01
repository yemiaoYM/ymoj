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
            :disabled="announcementIds.length !== 1"
            @click="handleUpdate"
            >修改</el-button
          >
          <el-button
            v-permission="['announcement:announcement:remove']"
            plain
            type="danger"
            :icon="Delete"
            :disabled="announcementIds.length === 0"
            @click="handleDelete"
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
      v-model="dialogVisible"
      destroy-on-close
      :title="dialogTitle"
      width="1300px"
      :before-close="handleClose"
      style="padding: 20px"
    >
      <el-form
        ref="announcementFormRef"
        :disabled="submitLoading"
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
              ref="contentEditorRef"
              :key="`content-${currentAnnouncementId}`"
              v-model:content="announcementForm.content"
              :disabled="submitLoading"
            />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button :disabled="submitLoading" @click="handleClose">取消</el-button>
          <el-button
            v-permission="currentAnnouncementId === null ? ['announcement:announcement:add'] : ['announcement:announcement:edit']"
            type="primary"
            @click="submitForm"
            :loading="submitLoading"
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
import AsyncSwitch from '@/components/common/AsyncSwitch.vue';
import { useAsyncChange } from '@/composables/useAsyncChange';
import { useUserStore } from "@/stores/user";
import { ref, onMounted, reactive, nextTick, defineAsyncComponent } from "vue";
import { ElMessage, ElMessageBox, type FormInstance } from "element-plus";
import { Search, Refresh, Plus, Edit, Delete } from "@element-plus/icons-vue";
import type { Announcement, AnnouncementDTO } from "@/type/types";
import {
  getAdminAnnouncementList,
  getAdminAnnouncementById,
  addAnnouncement,
  updateAnnouncement,
  deleteAnnouncementByIds,
  updateAnnouncementStatus,
  updateAnnouncementPinned,
} from "@/api/api";
//异步导入markdown
const MarkdownEidt = defineAsyncComponent(
  () => import("@/components/common/MarkdownEidt.vue")
);

//data
const userStore = useUserStore();
const announcementList = ref<Announcement[]>([]);
const announcementIds = ref<number[]>([]);
const total = ref<number>(0);
const loading = ref<boolean>(false);
const { pending: submitLoading, beforeChange: saveAnnouncement } = useAsyncChange();
const dialogVisible = ref<boolean>(false);
const dialogTitle = ref<string>("");
const queryFormRef = ref();
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
  handleQuery();
});

//methods
//查询公告列表
const handleQuery = () => {
  loading.value = true;
  getAdminAnnouncementList(queryParams.value)
    .then((res) => {
      announcementList.value = res.data.data.records;
      total.value = res.data.data.total;
    })
    .catch((e) => {
      console.log(e);
    })
    .finally(() => {
      loading.value = false;
    });
};
//重置查询
const resetQuery = () => {
  queryParams.value = {
    title: null,
    pinned: null,
    status: null,
    pageNo: 1,
    pageSize: 15,
  };
  handleQuery();
};
//改变页大小
const handleSizeChange = (val: number) => {
  queryParams.value.pageSize = val;
  handleQuery();
};
//改变当前页
const handleCurrentChange = (val: number) => {
  queryParams.value.pageNo = val;
  handleQuery();
};
//选择公告
const handleSelectionChange = (selection: Announcement[]) => {
  announcementIds.value = selection.map((item) => item.id);
};
//公告的即时状态在确认和请求成功后才交给开关更新
const handleAnnouncementChange = async (row: Announcement, field: 'status' | 'pinned') => {
  const nextValue = !row[field];
  const action = field === 'status' ? (nextValue ? '显示' : '隐藏') : (nextValue ? '置顶' : '取消置顶');
  await ElMessageBox.confirm(`确定要${action}公告 "${row.title}" 吗？`, '状态修改确认', {
    confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning',
  });
  const response = field === 'status'
    ? await updateAnnouncementStatus(row.id, nextValue)
    : await updateAnnouncementPinned(row.id, nextValue);
  ElMessage.success(response.data.msg);
};
//新增公告
const handleAdd = () => {
  reset();
  dialogTitle.value = "新增公告";
  dialogVisible.value = true;
};
//编辑公告
const handleUpdate = (row: Announcement) => {
  const id = row.id || announcementIds.value[0];
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
      dialogTitle.value = "编辑公告";
      dialogVisible.value = true;
    })
    .catch((err) => {
      console.log(err);
    });
};
//删除公告
const handleDelete = (row: Announcement) => {
  const ids = row.id || announcementIds.value;
  if (!ids) {
    ElMessage.warning("请选择要删除的公告");
    return;
  }
  ElMessageBox.confirm(`确定要删除公告 "${ids}" 吗？`, "删除确认", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  })
    .then(() => {
      deleteAnnouncementByIds(ids)
        .then((res) => {
          ElMessage.success(res.data.msg);
          handleQuery();
        })
        .catch((err) => {
          console.log(err);
        });
    })
    .catch(() => {});
};
//校验和保存期间保持弹窗，只有成功后才关闭
const submitForm = async () => {
  const form = announcementFormRef.value;
  const editor = contentEditorRef.value;
  if (submitLoading.value || !form || !editor) return;
  await saveAnnouncement(async () => {
    await form.validate();
    if (await editor.uploadImages() === false) return false;
    const data = { ...announcementForm };
    const response = currentAnnouncementId.value === null
      ? await addAnnouncement(data)
      : await updateAnnouncement(currentAnnouncementId.value, data);
    ElMessage.success(response.data.msg);
    dialogVisible.value = false;
    handleQuery();
  });
};
//重置表单
const reset = () => {
  Object.assign(announcementForm, {
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
  if (submitLoading.value) return;
  reset();
  dialogVisible.value = false;
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
  background-color: #f8f8f9;
  color: #515a6e;
  height: 40px;
  font-size: 13px;
}
</style>
