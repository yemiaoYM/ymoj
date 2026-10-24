<template>
  <div class="system-setting-management">
    <x-flex vertical :size="16">
      <el-card v-loading="loading">
        <template #header>
          <span class="card-title">网站设置</span>
        </template>

        <el-alert
          v-if="!loaded && !loading"
          class="setting-alert"
          title="系统配置加载失败，请重试"
          type="error"
          show-icon
          :closable="false"
        />

        <el-form
          ref="formRef"
          label-width="120px"
          :model="form"
          :rules="rules"
          :disabled="!loaded || saving || !canEdit"
        >
          <el-form-item label="是否允许注册" prop="registerEnabled">
            <div>
              <el-switch aria-label="是否允许注册" v-model="form.registerEnabled" />
              <div class="setting-tip">关闭后，新用户将无法注册，已有用户仍可正常登录。</div>
            </div>
          </el-form-item>
          <el-divider content-position="left">首页备案栏</el-divider>
          <el-form-item label="显示备案栏" prop="filingEnabled">
            <div>
              <el-switch aria-label="显示备案栏" v-model="form.filingEnabled" />
              <div class="setting-tip">关闭后首页隐藏整个备案栏，已填写的信息仍会保留。</div>
            </div>
          </el-form-item>
          <el-form-item label="ICP备案号" prop="icpFilingNumber">
            <el-input
              placeholder="填写完整备案号，未备案可留空"
              maxlength="100"
              clearable
              show-word-limit
              v-model.trim="form.icpFilingNumber"
            />
          </el-form-item>
          <el-form-item label="公安备案号" prop="policeFilingNumber">
            <el-input
              placeholder="填写完整公安备案号，包含连续的数字编号"
              maxlength="100"
              clearable
              show-word-limit
              v-model.trim="form.policeFilingNumber"
            />
            <div class="setting-tip">开启时至少填写一项。链接自动指向官方备案查询网站，无需填写网址。</div>
          </el-form-item>
          <el-alert
            v-if="saveFailed"
            class="setting-alert"
            title="保存失败，输入已保留，请重试"
            type="error"
            show-icon
            :closable="false"
          />
        </el-form>
        <x-flex>
          <el-button
            v-if="canEdit"
            type="primary"
            :loading="saving"
            :disabled="!loaded"
            @click="onSaveSetting"
          >保存</el-button>
          <el-button :icon="Refresh" :disabled="saving || loading" @click="onReloadSetting">
            {{ loaded ? '刷新' : '重新加载' }}
          </el-button>
        </x-flex>
      </el-card>
      <CarouselSetting />
    </x-flex>
  </div>
</template>

<script setup lang="ts">
//import
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { Refresh } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { getAdminSystemSetting, updateSystemSetting } from "@/api/api";
import { useUserStore } from "@/stores/user";
import type { SystemSettingDTO } from "@/type/types";
import CarouselSetting from "@/components/admin/CarouselSetting.vue";

//data
const userStore = useUserStore();
const loading = ref(false);
const saving = ref(false);
const loaded = ref(false);
const saveFailed = ref(false);
const formRef = ref<FormInstance>();
const form = ref<SystemSettingDTO>({
  registerEnabled: true,
  filingEnabled: false,
  icpFilingNumber: "",
  policeFilingNumber: "",
});
const canEdit = computed(() => userStore.hasEveryPermission(["system:setting:edit"]));
let disposed = false;
const rules: FormRules<SystemSettingDTO> = {
  filingEnabled: [{
    validator: (_rule, _value, callback) => {
      const missing = form.value.filingEnabled
        && !form.value.icpFilingNumber.trim()
        && !form.value.policeFilingNumber.trim();
      callback(missing ? new Error("开启备案栏时请至少填写一项备案号") : undefined);
    },
  }],
  icpFilingNumber: [{ max: 100, message: "ICP备案号不能超过100个字符", trigger: "blur" }],
  policeFilingNumber: [
    { max: 100, message: "公安备案号不能超过100个字符", trigger: "blur" },
    { pattern: /^(?:\s*|[^0-9]*[0-9]+[^0-9]*)$/, message: "公安备案号应包含一段连续的数字编号", trigger: "blur" },
  ],
};

//methods
const handleLoadSystemSetting = async () => {
  if (loading.value || saving.value) {
    return;
  }
  loading.value = true;
  loaded.value = false;
  saveFailed.value = false;
  try {
    const res = await getAdminSystemSetting();
    if (disposed) {
      return;
    }
    form.value = res.data.data;
    loaded.value = true;
    formRef.value?.clearValidate();
  } catch {
    if (!disposed) {
      loaded.value = false; // 请求层提示错误，本页提供重新加载入口。
    }
  } finally {
    if (!disposed) {
      loading.value = false;
    }
  }
};

const onReloadSetting = () => handleLoadSystemSetting();

const onSaveSetting = async () => {
  const formInstance = formRef.value;
  if (!formInstance || !loaded.value || saving.value || !canEdit.value) {
    return;
  }
  saving.value = true;
  saveFailed.value = false;
  try {
    let valid = false;
    await formInstance.validate((passed) => {
      valid = passed;
    });
    if (!valid || disposed) {
      return;
    }
    await updateSystemSetting({ ...form.value });
    if (!disposed) {
      ElMessage.success("保存成功，首页重新加载后生效");
    }
  } catch {
    if (!disposed) {
      saveFailed.value = true;
    }
  } finally {
    if (!disposed) {
      saving.value = false;
    }
  }
};

onMounted(() => {
  handleLoadSystemSetting();
});
onBeforeUnmount(() => {
  disposed = true;
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
