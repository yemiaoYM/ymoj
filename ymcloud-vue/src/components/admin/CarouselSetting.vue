<template>
  <el-card v-loading="loading">
    <template #header><span class="card-title">首页轮播图</span></template>
    <x-flex vertical :size="16">
      <el-text type="info">展示比例为 16:9，建议 1600×900 像素。最多 10 张，单张不超过 3MB；上传前可裁剪。</el-text>
      <el-alert v-if="failed" title="轮播图加载失败，请重试" type="error" show-icon :closable="false" />
      <x-flex align="flex-start" :size="16">
        <x-flex v-for="(image, index) in images" :key="image" class="carousel-item" vertical :size="8">
          <el-image
            class="carousel-image"
            fit="cover"
            preview-teleported
            :alt="`轮播图 ${index + 1}`"
            :src="image"
            :preview-src-list="images"
            :initial-index="index"
          />
          <x-flex v-if="canEdit" justify="space-between" :size="4">
            <el-button text size="small" :disabled="busy || index === 0" @click="onMove(index, -1)">前移</el-button>
            <el-button text size="small" :disabled="busy || index === images.length - 1" @click="onMove(index, 1)">后移</el-button>
            <el-button text type="danger" size="small" :disabled="busy" @click="onRemove(index)">移除</el-button>
          </x-flex>
        </x-flex>
        <ImageCropUpload
          v-if="canEdit && images.length < 10"
          :disabled="!loaded || busy"
          :aspect-ratio="[16, 9]"
          :output-width="1600"
          @cropped="onUpload"
        >
          <el-button class="carousel-add" aria-label="添加轮播图" :disabled="!loaded || busy" :loading="uploading" :icon="Plus" />
        </ImageCropUpload>
      </x-flex>
      <el-text v-if="loaded && !images.length" type="info">
        {{ dirty ? '列表为空，保存后隐藏首页轮播。' : '尚无轮播图，首页轮播区域已隐藏。' }}
      </el-text>
      <el-text v-if="dirty" type="warning">列表有未保存的更改。移除仅取消首页展示，不删除存储文件。</el-text>
      <x-flex v-if="pendingUpload" align="center">
        <el-text type="warning">图片上传失败，所选图片已保留。</el-text>
        <el-button :disabled="busy || !canEdit" @click="onUpload(pendingUpload)">重试上传</el-button>
      </x-flex>
    </x-flex>
    <template #footer>
      <x-flex>
        <el-button v-if="canEdit" type="primary" :loading="saving" :disabled="!loaded || uploading" @click="onSave">保存轮播图</el-button>
        <el-button :icon="Refresh" :disabled="loading || busy" @click="onReload">重新加载</el-button>
      </x-flex>
    </template>
  </el-card>
</template>

<script setup lang="ts">
import modal from '@/plugins/modal';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { Plus, Refresh } from '@element-plus/icons-vue';
import ImageCropUpload from '@/components/common/ImageCropUpload.vue';
import { getPublicCarousel, saveCarousel, uploadCarousel } from '@/api/system';
import { useUserStore } from '@/stores/user';
import { useAsyncChange } from '@/hooks/async/useAsyncChange';

const userStore = useUserStore();
const canEdit = computed(() => userStore.hasEveryPermission(['system:setting:edit']));
const images = ref<string[]>([]);
const loaded = ref(false);
const loading = ref(false);
const { pending: saving, beforeChange: saveImages } = useAsyncChange();
const uploading = ref(false);
const failed = ref(false);
const dirty = ref(false);
const pendingUpload = ref<File | null>(null);
const busy = computed(() => saving.value || uploading.value);
let disposed = false;

async function handleLoad() {
  loading.value = true;
  failed.value = false;
  loaded.value = false;
  try {
    const response = await getPublicCarousel();
    if (!disposed) {
      images.value = response.data.data;
      loaded.value = true;
      dirty.value = false;
      pendingUpload.value = null;
    }
  } catch {
    if (!disposed) {
      failed.value = true;
    }
  } finally {
    if (!disposed) {
      loading.value = false;
    }
  }
}

async function onReload() {
  if (loading.value || busy.value) {
    return;
  }
  if (dirty.value || pendingUpload.value) {
    try {
      await modal.confirm('重新加载将丢弃未保存的轮播列表，是否继续？', '重新加载');
    } catch {
      return; // 用户取消，不丢弃当前编辑。
    }
  }
  if (!disposed && !busy.value && !loading.value) {
    await handleLoad();
  }
}

async function onUpload(file: File) {
  if (!canEdit.value || !loaded.value || busy.value || images.value.length >= 10) {
    return;
  }
  uploading.value = true;
  try {
    const response = await uploadCarousel(file);
    if (!disposed) {
      // 上传接口把新地址追加到已发布列表，当前编辑顺序以本地草稿为准。
      const urls = response.data.data;
      const uploadedUrl = urls[urls.length - 1];
      if (!uploadedUrl) {
        ElMessage.error('未收到轮播图片地址');
        pendingUpload.value = file;
        return;
      }
      images.value.push(uploadedUrl);
      dirty.value = true;
      pendingUpload.value = null;
      ElMessage.success('图片已上传，保存轮播图后首页生效');
    }
  } catch {
    if (!disposed) {
      pendingUpload.value = file; // 保留所选图片供重试
    }
  } finally {
    if (!disposed) {
      uploading.value = false;
    }
  }
}

function onMove(index: number, offset: number) {
  if (busy.value || !canEdit.value || !loaded.value) {
    return;
  }
  const image = images.value.splice(index, 1)[0];
  if (image) {
    images.value.splice(index + offset, 0, image);
    dirty.value = true;
  }
}

function onRemove(index: number) {
  if (!busy.value && canEdit.value && loaded.value) {
    images.value.splice(index, 1);
    dirty.value = true;
  }
}

async function onSave() {
  if (!canEdit.value || !loaded.value || busy.value) {
    return;
  }
  const saved = await saveImages(async () => {
    await saveCarousel(images.value);
  });
  if (saved) {
    dirty.value = false;
    ElMessage.success('轮播图已保存，首页重新加载后生效');
  }
}

onMounted(handleLoad);
onBeforeUnmount(() => {
  disposed = true;
  pendingUpload.value = null;
});
</script>

<style scoped>
.card-title {
  color: var(--el-color-primary);
}
.carousel-item,
.carousel-add {
  width: 192px;
}
.carousel-image,
.carousel-add {
  aspect-ratio: 16 / 9;
  height: auto;
}
.carousel-image {
  width: 100%;
  border-radius: var(--el-border-radius-base);
}
.carousel-add {
  font-size: 28px;
  border-style: dashed;
}
</style>
