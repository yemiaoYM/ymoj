<template>
  <div v-loading="uploading" element-loading-text="正在上传正文图片" style="width: 100%;height: 100%;">
    <MdEditor 
      ref="editorRef"
      v-model="modelValue" 
      :toolbars="toolbars"
      :sanitize="sanitizeMarkdown"
      :disabled="disabled || uploading"
      previewTheme="cyanosis"
      show-toolbar-name
      no-upload-img
      @on-upload-img="onUploadImg"
      @on-drop="onDropImage"
    >
      <template #defToolbars>
        <NormalToolbar title="标记" aria-label="标记" :disabled="disabled || uploading" @on-click="onMark">
          <Highlighter class="md-editor-icon" />
          <span class="md-editor-toolbar-item-name">标记</span>
        </NormalToolbar>
        <NormalToolbar title="居左" aria-label="居左" :disabled="disabled || uploading" @on-click="onAlign('left')">
          <svg class="md-editor-icon" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M3 5h18 M3 10h12 M3 15h18 M3 20h12" fill="none" stroke="currentColor" stroke-width="1.8" />
          </svg>
          <span class="md-editor-toolbar-item-name">居左</span>
        </NormalToolbar>
        <NormalToolbar title="居中" aria-label="居中" :disabled="disabled || uploading" @on-click="onAlign('center')">
          <svg class="md-editor-icon" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M3 5h18 M6 10h12 M3 15h18 M6 20h12" fill="none" stroke="currentColor" stroke-width="1.8" />
          </svg>
          <span class="md-editor-toolbar-item-name">居中</span>
        </NormalToolbar>
        <NormalToolbar title="居右" aria-label="居右" :disabled="disabled || uploading" @on-click="onAlign('right')">
          <svg class="md-editor-icon" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M3 5h18 M9 10h12 M3 15h18 M9 20h12" fill="none" stroke="currentColor" stroke-width="1.8" />
          </svg>
          <span class="md-editor-toolbar-item-name">居右</span>
        </NormalToolbar>
        <DropdownToolbar
          title="图片"
          :visible="imageMenuVisible"
          :disabled="disabled || uploading"
          @on-change="imageMenuVisible = $event"
        >
          <ImageIcon class="md-editor-icon" />
          <span class="md-editor-toolbar-item-name">图片</span>
          <template #overlay>
            <ul class="md-editor-menu" role="menu" @click="imageMenuVisible = false">
              <li
                class="md-editor-menu-item md-editor-menu-item-image"
                role="menuitem"
                tabindex="0"
                @click="editorRef.execCommand('image')"
                @keydown.enter="editorRef.execCommand('image'); imageMenuVisible = false"
              >添加链接</li>
              <li class="md-editor-menu-item md-editor-menu-item-image" role="none">
                <ImageCropUpload
                  ref="cropUploadRef"
                  :disabled="disabled || uploading"
                  :aspect-ratio="null"
                  :output-width="1920"
                  @cropped="onPreviewImage"
                >
                  <span role="menuitem">上传图片</span>
                </ImageCropUpload>
              </li>
            </ul>
          </template>
        </DropdownToolbar>
      </template>
    </MdEditor>
  </div>
</template>

<script>
import { ref,defineComponent,watch,onBeforeUnmount,nextTick } from 'vue';
import { DropdownToolbar, MdEditor, NormalToolbar } from 'md-editor-v3';
import { Highlighter, Image as ImageIcon } from '@lucide/vue';
import ImageCropUpload from '@/components/common/ImageCropUpload.vue';
import { createMarkdownSanitizer } from '@/utils/markdown';
import { uploadMarkdownImage } from '@/api/markdown';
import { ElMessage } from 'element-plus';
import 'md-editor-v3/lib/style.css';

export default defineComponent({
  name: 'MarkdownEidt',
  components: {
    MdEditor,
    NormalToolbar,
    DropdownToolbar,
    Highlighter,
    ImageIcon,
    ImageCropUpload
  },
  props: {
    content: {
      type: String,
      default: "写点什么呢？"
    },
    disabled: {
      type: Boolean,
      default: false
    }
  },
  emits: ["update:content"],
  setup(props, { emit }) {
    //配置
    const toolbars = [
      'bold',
      'underline',
      'italic',
      0,
      '-',
      'title',
      'strikeThrough',
      'sub',
      'sup',
      'quote',
      1,
      2,
      3,
      'unorderedList',
      'orderedList',
      'task',
      '-',
      'codeRow',
      'code',
      'link',
      4,
      'table',
      'katex',
      '-',
      'revoke',
      'next',
      '=',
      'pageFullscreen',
      'fullscreen',
      'preview',
      'previewOnly',
    ];
    
    const modelValue = ref(props.content);
    const editorRef = ref();
    const cropUploadRef = ref();
    const imageMenuVisible = ref(false);
    const uploading = ref(false);
    const localImages = new Map();
    const sanitizeMarkdown = createMarkdownSanitizer(url => localImages.has(url));

    const onMark = () => {
      editorRef.value.insert((selected) => ({
        targetValue: `<mark>${selected || '标记文字'}</mark>`,
        select: true,
        deviationStart: 6,
        deviationEnd: -7,
      }));
    };

    const onAlign = (align) => {
      editorRef.value.insert((selected) => ({
        targetValue: `\n\n<div style="text-align: ${align}">\n\n${selected || '请输入内容'}\n\n</div>\n\n`,
        select: false,
      }));
    };

    const onUploadImg = (files) => {
      if (files.length !== 1) {
        ElMessage.warning('请一次选择一张图片进行裁剪');
        return;
      }
      cropUploadRef.value.open(files[0]);
    };

    const onDropImage = (event) => {
      const files = event.dataTransfer.files;
      if (files.length > 0) {
        event.preventDefault();
        onUploadImg(Array.from(files));
      }
    };

    const onPreviewImage = (blob) => {
      const url = URL.createObjectURL(blob);
      localImages.set(url, { blob, uploadedUrl: '' });
      editorRef.value.insert(() => ({ targetValue: `![](${url})\n`, select: false }));
    };

    // 保存方先完成表单校验；失败向保存边界传播，已上传图片在重试或撤销后复用
    const uploadImages = async () => {
      if (uploading.value || !editorRef.value) {
        return false;
      }
      const urls = modelValue.value.match(/blob:(?:https?:\/\/|null\/)[^\s)"'<>]+/g) || [];
      if (urls.some(url => !localImages.has(url))) {
        ElMessage.warning('本地图片已失效，请删除后重新选择图片');
        return false;
      }
      uploading.value = true;
      try {
        for (const url of new Set(urls)) {
          const image = localImages.get(url);
          if (!image.uploadedUrl) {
            const response = await uploadMarkdownImage(image.blob);
            image.uploadedUrl = response.data.data;
          }
          if (!editorRef.value) {
            return false;
          }
          modelValue.value = modelValue.value.replaceAll(url, image.uploadedUrl);
          await nextTick();
        }
        return urls.some(url => modelValue.value.includes(url)) ? false : modelValue.value;
      } finally {
        uploading.value = false;
      }
    };

    const releaseImages = () => {
      for (const url of localImages.keys()) {
        URL.revokeObjectURL(url);
      }
      localImages.clear();
    };
    onBeforeUnmount(releaseImages);

    //监听编辑器内部变化->通知父组件更新
    watch(modelValue, (val, previous) => {
      if (val === props.content) {
        return;
      }
      // 在受控值边界限长，避免编辑器读取粘贴/删除事件的空 data
      if (val.length > 65535) {
        modelValue.value = previous;
        ElMessage.warning('内容不能超过65535个字符');
        return;
      }
      emit("update:content", val);
    });

    //监听props变化->更新内部状态
    watch(() => props.content, (newContent) => {
      if (newContent !== modelValue.value) {
        if (newContent === '') {
          releaseImages();
        }
        modelValue.value = newContent;
      }
    });

    return {
      sanitizeMarkdown,
      modelValue,
      toolbars,
      editorRef,
      cropUploadRef,
      imageMenuVisible,
      uploading,
      onMark,
      onAlign,
      onUploadImg,
      onDropImage,
      onPreviewImage,
      uploadImages
    }
  }
})
</script>
