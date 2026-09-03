<script setup lang="ts">
/**
 * 登录 / 注册页左侧品牌展示区（纯展示，无任何交互）
 */
import { CheckCircleFilled } from '@ant-design/icons-vue'
import BrandLogo from '@/components/BrandLogo.vue'

interface Props {
  title: string
  subtitle: string
}

withDefaults(defineProps<Props>(), {
  title: '欢迎回来',
  subtitle: '',
})

// 平台能力亮点（纯文案展示）
const highlights = ['零代码应用生成', 'AI 智能助手', '快速部署上线']
</script>

<template>
  <div class="auth-brand-panel">
    <div class="panel-header">
      <div class="panel-logo-wrap">
        <BrandLogo :size="34" />
      </div>
      <div class="panel-brand-text">
        <span class="panel-brand-name">HCoder</span>
        <span class="panel-brand-slogan">AI应用生成平台</span>
      </div>
    </div>

    <h2 class="panel-title">{{ title }}</h2>
    <p class="panel-subtitle">{{ subtitle }}</p>

    <ul class="panel-highlights">
      <li v-for="item in highlights" :key="item" class="highlight-item">
        <CheckCircleFilled class="highlight-icon" />
        <span>{{ item }}</span>
      </li>
    </ul>

    <!-- 装饰圆环 -->
    <div class="panel-decor decor-1"></div>
    <div class="panel-decor decor-2"></div>
  </div>
</template>

<style scoped>
.auth-brand-panel {
  position: relative;
  overflow: hidden;
  padding: 40px 36px;
  background: linear-gradient(160deg, #2563eb 0%, #4f46e5 100%);
  color: #fff;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 36px;
}

.panel-logo-wrap {
  padding: 4px;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.18);
}

.panel-brand-text {
  display: flex;
  flex-direction: column;
}

.panel-brand-name {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: -0.3px;
}

.panel-brand-slogan {
  font-size: 12px;
  line-height: 1.4;
  color: rgba(255, 255, 255, 0.75);
}

.panel-title {
  margin: 0 0 10px;
  font-size: 28px;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: -0.4px;
}

.panel-subtitle {
  margin: 0 0 32px;
  font-size: 14px;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.82);
}

.panel-highlights {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.highlight-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.92);
}

.highlight-icon {
  font-size: 16px;
  color: #a5f3fc;
}

.panel-decor {
  position: absolute;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.18);
  pointer-events: none;
}

.decor-1 {
  width: 220px;
  height: 220px;
  right: -80px;
  bottom: -70px;
}

.decor-2 {
  width: 130px;
  height: 130px;
  right: -20px;
  top: -50px;
  background: rgba(255, 255, 255, 0.08);
  border: none;
}
</style>
