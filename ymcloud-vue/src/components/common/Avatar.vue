<template>
  <el-avatar
    :size="size" 
    :src="src"
    shape="circle"
    @error="errorHandler"
    :style="avatarStyle"
  >
    <span :style="textStyle">{{ displayText }}</span>
  </el-avatar>
</template>

<script lang="ts">
import { computed, defineComponent } from 'vue';

export default defineComponent({
  name: 'Avatar',
  props: {
    src: {
      type: [String,null],
      default: null
    },
    text: {
      type: [String,null],
      default: null
    },
    size: {
      type: [String, Number],
      default: 'default'
    }
  },
  setup(props) {
    // 预定义的颜色数组
    const colors = [
      '#f56a00', '#7265e6', '#ffbf00', '#00a2ae', '#13c2c2',
      '#ff7875', '#ff9c6e', '#ffc069', '#d3f261', '#95de64',
      '#5cdbd3', '#69c0ff', '#85a5ff', '#b37feb', '#ff85c0',
      '#f759ab', '#fa541c', '#fa8c16', '#faad14', '#a0d911',
      '#52c41a', '#13c2c2', '#1890ff', '#2f54eb', '#722ed1',
      '#eb2f96'
    ];

    // 获取显示的文本（首字符）
    const displayText = computed(() => {
      if (!props.text) return 'U';
      return props.text.charAt(0).toUpperCase();
    });

    // 根据首字符生成一致的颜色
    const backgroundColor = computed(() => {
      const char = displayText.value;
      const charCode = char.charCodeAt(0);
      const colorIndex = charCode % colors.length;
      return colors[colorIndex];
    });

    // 根据大小计算字体大小
    const fontSize = computed(() => {
      const sizeValue = props.size;
      
      // 如果是数字，直接计算
      if (typeof sizeValue === 'number') {
        return Math.max(12, Math.floor(sizeValue * 0.4)) + 'px';
      }
      
      // 如果是字符串，根据预设值计算
      switch (sizeValue) {
        case 'large':
          return '18px';
        case 'default':
          return '14px';
        case 'small':
          return '12px';
        default:
          // 尝试解析数字
          const numSize = parseInt(sizeValue as string);
          if (!isNaN(numSize)) {
            return Math.max(12, Math.floor(numSize * 0.4)) + 'px';
          }
          return '14px';
      }
    });

    // Avatar 样式
    const avatarStyle = computed(() => ({
      backgroundColor: backgroundColor.value,
      color: '#ffffff'
    }));

    // 文本样式
    const textStyle = computed(() => ({
      fontWeight: 'bold',
      fontSize: fontSize.value,
      lineHeight: '1'
    }));

    // 错误处理函数
    const errorHandler = () => {
      // 当图片加载失败时，显示文本
      return true;
    };

    return {
      displayText,
      avatarStyle,
      textStyle,
      errorHandler
    };
  }
});
</script>
