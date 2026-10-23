<template>
  <img
    class="brand-logo"
    alt="YMOJ"
    :src="compact ? '/ymoj-icon.png' : logoImage"
    :width="compact ? 48 : 180"
    :height="compact ? 48 : 60"
  />
</template>

<script setup lang="ts">
import logoImage from '@/assets/image/ymoj-logo.png';

withDefaults(defineProps<{ compact?: boolean }>(), { compact: false });
</script>

<style scoped>
.brand-logo {
  display: block;
  max-width: 100%;
  height: auto;
  object-fit: contain;
}
</style>
