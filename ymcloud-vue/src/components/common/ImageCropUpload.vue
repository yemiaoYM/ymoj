<template>
  <el-upload
    ref="uploadRef"
    accept="image/jpeg,image/png,image/webp"
    :auto-upload="false"
    :drag="drag"
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
    append-to-body
    destroy-on-close
    :model-value="visible"
    :close-on-click-modal="false"
    :close-on-press-escape="!exporting"
    :show-close="!exporting"
    @update:model-value="onClose"
    @opened="handleInitCropper"
    @closed="handleRelease"
  >
    <el-scrollbar max-height="calc(100dvh - 240px)" view-style="padding: 0 12px 4px 0">
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
            <el-text type="info" size="small">{{ aspectRatio?aspectRatio.join(':'):'自由比例' }}</el-text>
          </x-flex>
        </x-flex>
      </x-flex>
    </el-scrollbar>
    <template #footer>
      <x-flex align="center" justify="space-between" :size="[16, 12]">
        <el-text tag="p" class="crop-note" style="font-family: sans-serif">
          请选择不超过 <el-text tag="b" type="danger">{{ maxSizeMb }}MB</el-text> 的
          <el-text tag="b" type="danger">PNG/JPG/JPEG/WebP</el-text> 图片
        </el-text>
        <x-flex :wrap="false" :size="12" class="crop-actions">
          <el-button :disabled="exporting" @click="onClose">取消</el-button>
          <el-button :disabled="!original || disabled || exporting" @click="useOriginal">使用原图</el-button>
          <el-button
            type="primary"
            :icon="Check"
            :disabled="!ready || disabled"
            :loading="exporting"
            @click="onConfirm"
          >确认裁剪</el-button>
        </x-flex>
      </x-flex>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import { ElNotification } from 'element-plus';
import { Check, Refresh, RefreshLeft, RefreshRight, ZoomIn, ZoomOut } from '@element-plus/icons-vue';
import type { UploadFile, UploadInstance } from 'element-plus';
import Cropper from 'cropperjs';
import 'cropperjs/dist/cropper.css';

// 调用方上传所选原图或用户确认的裁剪结果
const props = withDefaults(defineProps<{
  disabled?: boolean;
  drag?: boolean;
  aspectRatio?: [number, number] | null;
  outputWidth?: number;
  maxSizeMb?: number;
}>(), {
  disabled: false,
  drag: false,
  aspectRatio: () => [16, 9],
  outputWidth: 1600,
  maxSizeMb: 3,
});
const emit = defineEmits<{ cropped: [file: File] }>();
const uploadRef = ref<UploadInstance>();
const imageRef = ref<HTMLImageElement>();
const previewRef = ref<HTMLDivElement>();
const visible = ref(false);
const reading = ref(false);
const ready = ref(false);
const exporting = ref(false);
const imageUrl = ref('');
const original = ref<File>();
const ratio = computed(() => props.aspectRatio ? props.aspectRatio[0] / props.aspectRatio[1] : undefined);
let cropper: Cropper | null = null;
let generation = 0;

async function onSelectImage(file: UploadFile) {
  uploadRef.value?.clearFiles();
  if (file.raw) {
    await open(file.raw);
  }
}

/** 选图、粘贴和拖入统一经过本地校验及裁剪确认。 */
async function open(raw: File) {
  if (props.disabled || reading.value || visible.value) {
    return;
  }
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(raw.type)) {
    ElNotification({
      title: '图片格式不支持',
      message: `${raw.name} 无法使用，请选择 PNG、JPG、JPEG 或 WebP 格式的图片。`,
      type: 'warning',
    });
    return;
  }
  if (raw.size > props.maxSizeMb * 1024 * 1024) {
    ElNotification({
      title: '超过文件大小限制',
      message: `${raw.name} 超出大小限制，请选择不超过 ${props.maxSizeMb} MB 的图片。`,
      type: 'warning',
    });
    return;
  }
  const current = ++generation;
  reading.value = true;
  try {
    const bitmap = await createImageBitmap(raw);
    bitmap.close();
    if (current !== generation) {
      return;
    }
    handleRelease();
    original.value = raw;
    imageUrl.value = URL.createObjectURL(raw);
    visible.value = true;
  } catch {
    if (current === generation) {
      ElNotification({
        title: '图片无法读取',
        message: `${raw.name} 无法解码，请检查文件或重新选择图片。`,
        type: 'error',
      });
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
    autoCropArea: props.aspectRatio ? 0.8 : 1,
    preview: previewRef.value,
    toggleDragModeOnDblclick: false,
    minCropBoxWidth: 40,
    minCropBoxHeight: ratio.value ? 40 / ratio.value : 40,
    ready() { ready.value = true; },
    error() {
      ready.value = false;
      ElNotification({
        title: '裁剪器加载失败',
        message: '请关闭裁剪窗口后重新选择图片。',
        type: 'error',
      });
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
  original.value = undefined;
  if (imageUrl.value) {
    URL.revokeObjectURL(imageUrl.value);
    imageUrl.value = '';
  }
}

/**
 * 使用用户选择的原始文件上传
 */
function useOriginal() {
  if (original.value) {
    emit('cropped', original.value);
    visible.value = false;
  }
}

function onConfirm() {
  const source = original.value;
  if (!source || !cropper || !ready.value || exporting.value || props.disabled) {
    return;
  }
  exporting.value = true;
  cropper.disable();
  const current = generation;
  try {
    const canvas = cropper.getCroppedCanvas({
      ...(ratio.value
        ? { width: props.outputWidth, height: Math.round(props.outputWidth / ratio.value) }
        : { maxWidth: props.outputWidth, maxHeight: props.outputWidth }),
      imageSmoothingQuality: 'high',
      fillColor: '#fff',
    });
    canvas.toBlob((blob) => {
      if (current !== generation) {
        return;
      }
      exporting.value = false;
      cropper?.enable();
      if (!blob || blob.type !== source.type) {
        ElNotification({
          title: '裁剪导出失败',
          message: '无法按原格式导出，请使用原图或更换图片。',
          type: 'error',
        });
        return;
      }
      if (blob.size > props.maxSizeMb * 1024 * 1024) {
        ElNotification({
          title: '裁剪结果无法使用',
          message: `请缩小裁剪范围或更换图片，导出文件不能超过 ${props.maxSizeMb} MB。`,
          type: 'error',
        });
        return;
      }
      emit('cropped', new File([blob], source.name, { type: blob.type }));
      visible.value = false;
    }, source.type);
  } catch {
    exporting.value = false;
    cropper.enable();
    ElNotification({
      title: '裁剪导出失败',
      message: '请重试或重新选择图片。',
      type: 'error',
    });
  }
}

onBeforeUnmount(() => {
  generation++;
  handleRelease();
});

defineExpose({ open });
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
  background-color: var(--ym-surface);
}
.crop-stage :deep(.cropper-point) {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: var(--el-color-primary);
  opacity: 1;
}
.crop-result {
  width: 160px;
  aspect-ratio: 1;
  flex-shrink: 0;
  overflow: hidden;
  background: var(--ym-surface);
  outline: 1px solid var(--el-border-color);
  outline-offset: -1px;
}

.crop-note {
  flex: 1 1 340px;
  margin: 0;
  text-align: left;
  line-height: 1.8;
}

.crop-actions {
  flex-shrink: 0;
  margin-left: auto;
}

.crop-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
@media (max-width: 640px) {
  .crop-layout {
    --crop-layout-direction: column;
  }
  .crop-stage {
    height: clamp(180px, calc(100dvh - 440px), 280px);
  }
  .crop-preview {
    width: auto;
    --crop-preview-direction: row;
  }
  .crop-result {
    width: 96px;
  }
}
</style>
