<template>
  <div class="system-setting-management">
    <n-flex vertical :size="16">
      <el-card v-loading="loading">
        <template #header>
          <span class="card-title">网站设置</span>
        </template>

        <el-alert
          v-if="!loaded && !loading"
          title="系统配置加载失败，请重试"
          type="error"
          :closable="false"
          show-icon
          class="setting-alert"
        />

        <el-form :model="form" label-width="120px" :disabled="!loaded || saving">
          <el-form-item label="是否允许注册" prop="registerEnabled">
            <div>
              <el-switch v-model="form.registerEnabled" :disabled="!canEdit" aria-label="是否允许注册" />
              <div class="setting-tip">关闭后，新用户将无法注册，已有用户仍可正常登录。</div>
            </div>
          </el-form-item>
          <el-form-item>
            <el-button v-if="canEdit" type="primary" :loading="saving" @click="handleSave">保存</el-button>
            <el-button :icon="Refresh" :disabled="saving" @click="loadSystemSetting">刷新</el-button>
          </el-form-item>
        </el-form>
        <el-button v-if="!loaded && !loading" :icon="Refresh" @click="loadSystemSetting">重新加载</el-button>
      </el-card>
    </n-flex>
  </div>
</template>

<script setup lang="ts">
//import
import { computed, onMounted, ref } from "vue";
import { Refresh } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { getAdminSystemSetting, updateSystemSetting } from "@/api/api";
import { useUserStore } from "@/stores/user";
import type { SystemSettingDTO } from "@/type/types";

//data
const userStore = useUserStore();
const loading = ref(false);
const saving = ref(false);
const loaded = ref(false);
const form = ref<SystemSettingDTO>({ registerEnabled: true });
const canEdit = computed(() => userStore.hasEveryPermission(["system:setting:edit"]));

//methods
const loadSystemSetting = async () => {
  loading.value = true;
  loaded.value = false;
  try {
    const res = await getAdminSystemSetting();
    form.value = res.data.data;
    loaded.value = true;
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
};

const handleSave = async () => {
  if (!loaded.value || saving.value || !canEdit.value) {
    return;
  }
  saving.value = true;
  try {
    await updateSystemSetting({ ...form.value });
    ElMessage.success("保存成功");
  } catch (e) {
    console.error(e);
  } finally {
    saving.value = false;
  }
};

onMounted(() => {
  loadSystemSetting();
});
</script>

<style scoped>
.system-setting-management {
  padding: 16px;
}

.card-title {
  color: var(--el-color-primary);
}

.setting-tip {
  color: var(--el-text-color-secondary);
  font-size: 14px;
  line-height: 22px;
  margin-top: 8px;
}

.setting-alert {
  margin-bottom: 20px;
}

@media (max-width: 768px) {
  .system-setting-management {
    padding: 12px;
  }
}
</style>
