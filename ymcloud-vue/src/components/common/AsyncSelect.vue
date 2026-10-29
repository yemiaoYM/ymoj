<template>
  <el-select
    v-bind="$attrs"
    :model-value="modelValue"
    :disabled="disabled || pending"
    :loading="pending"
    @change="onChange"
  >
    <slot />
  </el-select>
</template>

<script setup lang="ts" generic="T extends string | number | boolean | null">
import { useAsyncChange, type ChangeResult } from '@/composables/useAsyncChange';

defineOptions({ inheritAttrs: false });
const props = defineProps<{
  modelValue: T;
  disabled?: boolean;
  beforeChange: (value: T) => ChangeResult | Promise<ChangeResult>;
}>();
const emit = defineEmits<{ 'update:modelValue': [value: T] }>();
const { pending, beforeChange } = useAsyncChange();
const onChange = async (value: T) => {
  if (value === props.modelValue) return;
  if (await beforeChange(() => props.beforeChange(value))) {
    emit('update:modelValue', value);
  }
};
</script>
