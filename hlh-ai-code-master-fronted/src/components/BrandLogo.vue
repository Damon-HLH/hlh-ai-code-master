<script setup lang="ts">
import { useId } from 'vue'

/**
 * HCoder 品牌标识（纯内联 SVG，无二进制资源）
 * 造型：渐变圆角方块 + 左右代码括号 + 字母 H，颜色跟随设计令牌 tokens.css 的品牌色。
 */
interface Props {
  size?: number
}

const props = withDefaults(defineProps<Props>(), {
  size: 40,
})

// 同一页面可能存在多个 Logo 实例（Header / Footer / 登录页），渐变 id 必须唯一
const uid = `hc-logo-${String(useId()).replace(/[^a-zA-Z0-9_-]/g, '')}`
</script>

<template>
  <svg
    class="brand-logo"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 48 48"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
  >
    <defs>
      <linearGradient :id="`${uid}-g`" x1="4" y1="2" x2="44" y2="46" gradientUnits="userSpaceOnUse">
        <stop offset="0" stop-color="#3b82f6" />
        <stop offset="1" stop-color="#4f46e5" />
      </linearGradient>
    </defs>

    <!-- 渐变圆角方块底 -->
    <rect width="48" height="48" rx="14" :fill="`url(#${uid}-g)`" />
    <!-- 左右代码括号 -->
    <path
      d="M11.6 20.2 L8.2 24 L11.6 27.8"
      stroke="#ffffff"
      stroke-opacity="0.75"
      stroke-width="2.6"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M36.4 20.2 L39.8 24 L36.4 27.8"
      stroke="#ffffff"
      stroke-opacity="0.75"
      stroke-width="2.6"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <!-- 字母 H -->
    <path
      d="M17.5 14.5 V33.5 M30.5 14.5 V33.5 M17.5 24 H30.5"
      stroke="#ffffff"
      stroke-width="4.2"
      stroke-linecap="round"
    />
  </svg>
</template>

<style scoped>
.brand-logo {
  display: block;
  flex-shrink: 0;
}
</style>
