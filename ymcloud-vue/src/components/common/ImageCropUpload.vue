<template>
  <el-upload
    ref="uploadRef"
    accept="image/jpeg,image/png,image/webp"
    :auto-upload="false"
    :show-file-list="false"
    :disabled="disabled || reading || visible"
    :on-change="onSelectImage"
  >
    <slot><el-button :disabled="disabled || reading || visible">选择图片</el-button></slot>
  </el-upload>

  <el-dialog
    title="裁剪图片"
    width="min(760px, calc(100vw - 32px))"
    align-center
    destroy-on-close
    :model-value="visible"
    :close-on-click-modal="false"
    :close-on-press-escape="!exporting"
    :show-close="!exporting"
    @update:model-value="onClose"
    @opened="handleInitCropper"
    @closed="handleRelease"
  >
    <el-scrollbar max-height="calc(100dvh - 160px)" view-style="padding: 0 12px 4px 0">
      <x-flex :wrap="false" :size="16" class="crop-layout" style="flex-direction: var(--crop-layout-direction, row)">
        <x-flex vertical :size="12" class="crop-editor">
          <div class="crop-stage" tabindex="0" aria-label="图片裁剪区域，可用方向键移动图片" @keydown="onMoveImage">
            <img ref="imageRef" alt="待裁剪原图" :src="imageUrl" />
          </div>
          <x-flex justify="center" :size="8" class="crop-tools">
            <el-button aria-label="向左旋转90度" title="向左旋转90度" :icon="RefreshLeft" :disabled="!ready || exporting" @click="onRotate(-90)" />
            <el-button aria-label="向右旋转90度" title="向右旋转90度" :icon="RefreshRight" :disabled="!ready || exporting" @click="onRotate(90)" />
            <el-button aria-label="缩小图片" title="缩小图片" :icon="ZoomOut" :disabled="!ready || exporting" @click="onZoom(-0.1)" />
            <el-button aria-label="放大图片" title="放大图片" :icon="ZoomIn" :disabled="!ready || exporting" @click="onZoom(0.1)" />
            <el-button aria-label="重置裁剪" title="重置裁剪" :icon="Refresh" :disabled="!ready || exporting" @click="onReset" />
          </x-flex>
        </x-flex>
        <x-flex :wrap="false" align="center" :size="12" class="crop-preview" style="flex-direction: var(--crop-preview-direction, column)">
          <div ref="previewRef" class="crop-result" aria-label="裁剪结果预览" :style="{ aspectRatio: ratio }" />
          <x-flex vertical :size="4">
            <el-text>预览</el-text>
            <el-text type="info" size="small">{{ aspectRatio.join(':') }}</el-text>
          </x-flex>
        </x-flex>
      </x-flex>
    </el-scrollbar>
    <template #footer>
      <el-button :disabled="exporting" @click="onClose">取消</el-button>
      <el-button type="primary" :icon="Check" :disabled="!ready || disabled" :loading="exporting" @click="onConfirm">确认裁剪</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { Check, Refresh, RefreshLeft, RefreshRight, ZoomIn, ZoomOut } from '@element-plus/icons-vue';
import type { UploadFile, UploadInstance } from 'element-plus';
import Cropper from 'cropperjs';
import 'cropperjs/dist/cropper.css';

// 只处理本地选图和裁剪，emit PNG Blob；调用方负责上传、重试和业务保存。
const props = withDefaults(defineProps<{
  disabled?: boolean;
  aspectRatio?: [number, number];
  outputWidth?: number;
  maxSizeMb?: number;
}>(), {
  disabled: false,
  aspectRatio: () => [16, 9],
  outputWidth: 1600,
  maxSizeMb: 5,
});
const emit = defineEmits<{ cropped: [file: Blob] }>();
const uploadRef = ref<UploadInstance>();
const imageRef = ref<HTMLImageElement>();
const previewRef = ref<HTMLDivElement>();
const visible = ref(false);
const reading = ref(false);
const ready = ref(false);
const exporting = ref(false);
const imageUrl = ref('');
const ratio = computed(() => props.aspectRatio[0] / props.aspectRatio[1]);
const outputHeight = computed(() => Math.round(props.outputWidth / ratio.value));
let cropper: Cropper | null = null;
let generation = 0;

async function onSelectImage(file: UploadFile) {
  uploadRef.value?.clearFiles();
  const raw = file.raw;
  if (!raw || props.disabled || reading.value || visible.value) {
    return;
  }
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(raw.type) || raw.size > props.maxSizeMb * 1024 * 1024) {
    ElMessage.warning(`请选择不超过 ${props.maxSizeMb}MB 的 JPEG、PNG 或 WebP 图片`);
    return;
  }
  const current = ++generation;
  reading.value = true;
  try {
    const bitmap = await createImageBitmap(raw);
    const pixels = bitmap.width * bitmap.height;
    bitmap.close();
    if (current !== generation) {
      return;
    }
    if (pixels > 32_000_000) {
      ElMessage.warning('图片分辨率过大，请选择不超过3200万像素的图片');
      return;
    }
    handleRelease();
    imageUrl.value = URL.createObjectURL(raw);
    visible.value = true;
  } catch {
    if (current === generation) {
      ElMessage.error('图片无法读取，请重新选择');
    }
  } finally {
    if (current === generation) {
      reading.value = false;
    }
  }
}

function handleInitCropper() {
  if (!imageRef.value || !previewRef.value) {
    return;
  }
  cropper = new Cropper(imageRef.value, {
    aspectRatio: ratio.value,
    viewMode: 1,
    dragMode: 'move',
    autoCropArea: 0.8,
    preview: previewRef.value,
    toggleDragModeOnDblclick: false,
    minCropBoxWidth: 40,
    minCropBoxHeight: 40 / ratio.value,
    ready() { ready.value = true; },
    error() {
      ready.value = false;
      ElMessage.error('图片裁剪器加载失败，请重新选择');
    },
  });
}

function onRotate(degrees: number) {
  cropper?.rotate(degrees);
}

function onZoom(amount: number) {
  cropper?.zoom(amount);
}

function onReset() {
  cropper?.reset();
}

function onMoveImage(event: KeyboardEvent) {
  if (!ready.value || exporting.value) {
    return;
  }
  const offsets: Record<string, [number, number]> = {
    ArrowLeft: [-5, 0], ArrowRight: [5, 0], ArrowUp: [0, -5], ArrowDown: [0, 5],
  };
  const offset = offsets[event.key];
  if (offset) {
    event.preventDefault();
    cropper?.move(...offset);
  }
}

function onClose() {
  if (!exporting.value) {
    visible.value = false;
  }
}

function handleRelease() {
  cropper?.destroy();
  cropper = null;
  ready.value = false;
  if (imageUrl.value) {
    URL.revokeObjectURL(imageUrl.value);
    imageUrl.value = '';
  }
}

function onConfirm() {
  if (!cropper || !ready.value || exporting.value || props.disabled) {
    return;
  }
  exporting.value = true;
  cropper.disable();
  const current = generation;
  try {
    const canvas = cropper.getCroppedCanvas({
      width: props.outputWidth,
      height: outputHeight.value,
      imageSmoothingQuality: 'high',
    });
    canvas.toBlob((blob) => {
      if (current !== generation) {
        return;
      }
      exporting.value = false;
      cropper?.enable();
      if (!blob || blob.size > props.maxSizeMb * 1024 * 1024) {
        ElMessage.error('裁剪结果过大或无法导出，请更换图片');
        return;
      }
      emit('cropped', blob);
      visible.value = false;
    }, 'image/png');
  } catch {
    exporting.value = false;
    cropper.enable();
    ElMessage.error('裁剪导出失败，请重试');
  }
}

onBeforeUnmount(() => {
  generation++;
  handleRelease();
});
</script>

<style scoped>
.crop-editor {
  flex: 1;
  min-width: 0;
}
.crop-stage {
  height: clamp(200px, calc(100dvh - 260px), 420px);
  background: var(--el-fill-color-light);
}
.crop-tools :deep(.el-button + .el-button) {
  margin-left: 0;
}
.crop-preview {
  width: 160px;
  flex-shrink: 0;
}
.crop-stage > img {
  display: block;
  max-width: 100%;
  max-height: 100%;
}
.crop-stage :deep(.cropper-view-box) {
  outline-color: var(--el-color-primary);
}
.crop-stage :deep(.cropper-point) {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: var(--el-color-primary);
  opacity: 1;
}
.crop-result {
  width: 112px;
  flex-shrink: 0;
  overflow: hidden;
  background: var(--el-fill-color-light);
}
@media (max-width: 640px) {
  .crop-layout {
    --crop-layout-direction: column;
  }
  .crop-stage {
    height: clamp(180px, calc(100dvh - 380px), 360px);
  }
  .crop-preview {
    width: auto;
    --crop-preview-direction: row;
  }
}
</style>
