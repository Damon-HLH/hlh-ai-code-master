<template>
  <a-layout-footer class="footer">
    <div class="footer-content">
      <div class="footer-brand">
        <BrandLogo :size="26" />
        <span class="footer-brand-name">HCoder</span>
      </div>
      <p class="footer-slogan">
        专业的AI应用生成平台，为你提供一键式生成部署Web方案。
      </p>
      <p class="copyright">© 2026 HCoder应用生成. 保留所有权利.</p>
    </div>
  </a-layout-footer>
</template>

<script setup lang="ts">
import BrandLogo from '@/components/BrandLogo.vue'
</script>

<style scoped>
.footer {
  background: #fff;
  text-align: center;
  padding: 28px 20px;
  margin-top: 0;
  border-top: 1px solid var(--hc-border-light);
}

.footer-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.footer-brand {
  display: flex;
  align-items: center;
  gap: 8px;
}

.footer-brand-name {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.2px;
  color: var(--hc-text-1);
}

.footer-slogan {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--hc-text-2);
}

.copyright {
  margin: 0;
  font-size: 12px;
  color: var(--hc-text-3);
}
</style>
