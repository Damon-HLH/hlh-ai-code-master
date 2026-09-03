<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { addApp, listMyAppVoByPage, listGoodAppVoByPage } from '@/api/appController'
import { getDeployUrl } from '@/config/env'
import AppCard from '@/components/AppCard.vue'
import BrandLogo from '@/components/BrandLogo.vue'
import { ArrowUpOutlined } from '@ant-design/icons-vue'

const router = useRouter()
const loginUserStore = useLoginUserStore()

// 用户提示词
const userPrompt = ref('')
const creating = ref(false)

// 我的应用数据
const myApps = ref<API.AppVO[]>([])
const myAppsPage = reactive({
  current: 1,
  pageSize: 6,
  total: 0,
})

// 精选应用数据
const featuredApps = ref<API.AppVO[]>([])
const featuredAppsPage = reactive({
  current: 1,
  pageSize: 6,
  total: 0,
})

// 设置提示词
const setPrompt = (prompt: string) => {
  userPrompt.value = prompt
}

// 优化提示词功能已移除

// 创建应用
const createApp = async () => {
  if (!userPrompt.value.trim()) {
    message.warning('请输入应用描述')
    return
  }

  if (!loginUserStore.loginUser.id) {
    message.warning('请先登录')
    await router.push('/user/login')
    return
  }

  creating.value = true
  try {
    const res = await addApp({
      initPrompt: userPrompt.value.trim(),
    })

    if (res.data.code === 0 && res.data.data) {
      message.success('应用创建成功')
      // 跳转到对话页面，确保ID是字符串类型
      const appId = String(res.data.data)
      await router.push(`/app/chat/${appId}`)
    } else {
      message.error('创建失败：' + res.data.message)
    }
  } catch (error) {
    console.error('创建应用失败：', error)
    message.error('创建失败，请重试')
  } finally {
    creating.value = false
  }
}

// 加载我的应用
const loadMyApps = async () => {
  if (!loginUserStore.loginUser.id) {
    return
  }

  try {
    const res = await listMyAppVoByPage({
      pageNum: myAppsPage.current,
      pageSize: myAppsPage.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
    })

    if (res.data.code === 0 && res.data.data) {
      myApps.value = res.data.data.records || []
      myAppsPage.total = res.data.data.totalRow || 0
    }
  } catch (error) {
    console.error('加载我的应用失败：', error)
  }
}

// 加载精选应用
const loadFeaturedApps = async () => {
  try {
    const res = await listGoodAppVoByPage({
      pageNum: featuredAppsPage.current,
      pageSize: featuredAppsPage.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
    })

    if (res.data.code === 0 && res.data.data) {
      featuredApps.value = res.data.data.records || []
      featuredAppsPage.total = res.data.data.totalRow || 0
    }
  } catch (error) {
    console.error('加载精选应用失败：', error)
  }
}

// 查看对话
const viewChat = (appId: string | number | undefined) => {
  if (appId) {
    router.push(`/app/chat/${appId}?view=1`)
  }
}

// 查看作品
const viewWork = (app: API.AppVO) => {
  if (app.deployKey) {
    const url = getDeployUrl(app.deployKey)
    window.open(url, '_blank')
  }
}

// 格式化时间函数已移除，不再需要显示创建时间

// 页面加载时获取数据
onMounted(() => {
  loadMyApps()
  loadFeaturedApps()

  // 鼠标跟随光效
  const handleMouseMove = (e: MouseEvent) => {
    const { clientX, clientY } = e
    const { innerWidth, innerHeight } = window
    const x = (clientX / innerWidth) * 100
    const y = (clientY / innerHeight) * 100

    document.documentElement.style.setProperty('--mouse-x', `${x}%`)
    document.documentElement.style.setProperty('--mouse-y', `${y}%`)
  }

  document.addEventListener('mousemove', handleMouseMove)

  // 清理事件监听器
  return () => {
    document.removeEventListener('mousemove', handleMouseMove)
  }
})
</script>

<template>
  <div id="homePage">
    <div class="container">
      <!-- 网站标题和描述 -->
      <div class="hero-section">
        <div class="hero-text">
          <h1 class="hero-title">
            <span class="hero-title-line">AI 驱动的</span>
            <span class="hero-title-line">零代码应用生成平台</span>
          </h1>
          <p class="hero-description">用AI创造无限可能，让每个想法快速变成现实</p>
        </div>
        <!-- 右侧纯装饰卡，无任何交互 -->
        <div class="hero-visual" aria-hidden="true">
          <div class="visual-glow"></div>
          <div class="visual-card">
            <div class="visual-brand">
              <BrandLogo :size="32" />
              <span class="visual-brand-name">HCoder</span>
            </div>
            <div class="visual-code">&lt; /&gt;</div>
            <svg class="visual-bolt" viewBox="0 0 24 24" width="26" height="26">
              <path
                d="M13.6 2 L5.8 13.4 H10.6 L8.9 22 L18.2 9.9 H13.1 Z"
                fill="currentColor"
              />
            </svg>
            <div class="visual-caption">一句话，生成完整可部署的 Web 应用</div>
          </div>
          <span class="visual-badge badge-1">Vue</span>
          <span class="visual-badge badge-2">HTML</span>
          <span class="visual-badge badge-3">一键部署</span>
        </div>
      </div>

      <!-- 用户提示词输入框 -->
      <div class="input-section">
        <a-textarea
          v-model:value="userPrompt"
          placeholder="描述你想要创建的应用，例如：帮我创建一个现代化的个人博客网站"
          :rows="4"
          :maxlength="1000"
          class="prompt-input"
        />
        <div class="input-actions">
          <a-button
            type="primary"
            shape="round"
            size="large"
            @click="createApp"
            :loading="creating"
          >
            开始创建
            <ArrowUpOutlined />
          </a-button>
        </div>
      </div>

      <!-- 快捷按钮 -->
      <div class="quick-actions">
        <span class="quick-label">快速开始：</span>
        <a-button
          type="default"
          @click="
            setPrompt(
              '创建一个现代化的个人博客网站，包含文章列表、详情页、分类标签、搜索功能、评论系统和个人简介页面。采用简洁的设计风格，支持响应式布局，文章支持Markdown格式，首页展示最新文章和热门推荐。',
            )
          "
          >个人博客网站</a-button
        >
        <a-button
          type="default"
          @click="
            setPrompt(
              '设计一个专业的企业官网，包含公司介绍、产品服务展示、新闻资讯、联系我们等页面。采用商务风格的设计，包含轮播图、产品展示卡片、团队介绍、客户案例展示，支持多语言切换和在线客服功能。',
            )
          "
          >企业官网</a-button
        >
        <a-button
          type="default"
          @click="
            setPrompt(
              '构建一个功能完整的在线商城，包含商品展示、购物车、用户注册登录、订单管理、支付结算等功能。设计现代化的商品卡片布局，支持商品搜索筛选、用户评价、优惠券系统和会员积分功能。',
            )
          "
          >在线商城</a-button
        >
        <a-button
          type="default"
          @click="
            setPrompt(
              '制作一个精美的作品展示网站，适合设计师、摄影师、艺术家等创作者。包含作品画廊、项目详情页、个人简历、联系方式等模块。采用瀑布流或网格布局展示作品，支持图片放大预览和作品分类筛选。',
            )
          "
          >作品展示网站</a-button
        >
      </div>

      <!-- 我的作品 -->
      <div class="section">
        <h2 class="section-title">我的作品</h2>
        <div class="app-grid">
          <AppCard
            v-for="app in myApps"
            :key="app.id"
            :app="app"
            @view-chat="viewChat"
            @view-work="viewWork"
          />
        </div>
        <div class="pagination-wrapper">
          <a-pagination
            v-model:current="myAppsPage.current"
            v-model:page-size="myAppsPage.pageSize"
            :total="myAppsPage.total"
            :show-size-changer="false"
            :show-total="(total: number) => `共 ${total} 个应用`"
            @change="loadMyApps"
          />
        </div>
      </div>

      <!-- 精选案例 -->
      <div class="section">
        <h2 class="section-title">精选案例</h2>
        <div class="featured-grid">
          <AppCard
            v-for="app in featuredApps"
            :key="app.id"
            :app="app"
            :featured="true"
            @view-chat="viewChat"
            @view-work="viewWork"
          />
        </div>
        <div class="pagination-wrapper">
          <a-pagination
            v-model:current="featuredAppsPage.current"
            v-model:page-size="featuredAppsPage.pageSize"
            :total="featuredAppsPage.total"
            :show-size-changer="false"
            :show-total="(total: number) => `共 ${total} 个案例`"
            @change="loadFeaturedApps"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
#homePage {
  width: 100%;
  margin: 0;
  padding: 0;
  min-height: 100vh;
  background:
    var(--hc-bg-page),
    radial-gradient(circle at 18% 10%, rgba(37, 99, 235, 0.1) 0%, transparent 46%),
    radial-gradient(circle at 86% 6%, rgba(79, 70, 229, 0.08) 0%, transparent 44%);
  position: relative;
  overflow: hidden;
}

/* 鼠标跟随柔光 */
#homePage::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: radial-gradient(
    620px circle at var(--mouse-x, 50%) var(--mouse-y, 30%),
    rgba(37, 99, 235, 0.07) 0%,
    rgba(79, 70, 229, 0.04) 42%,
    transparent 78%
  );
  pointer-events: none;
}

/* 斜向微光 */
#homePage::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background:
    linear-gradient(45deg, transparent 34%, rgba(37, 99, 235, 0.03) 50%, transparent 66%),
    linear-gradient(-45deg, transparent 34%, rgba(79, 70, 229, 0.03) 50%, transparent 66%);
  pointer-events: none;
  animation: lightPulse 8s ease-in-out infinite alternate;
}

@keyframes lightPulse {
  0% {
    opacity: 0.35;
  }
  100% {
    opacity: 0.7;
  }
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
  position: relative;
  z-index: 2;
  width: 100%;
  box-sizing: border-box;
}

/* 英雄区域：左对齐双栅，与下方输入卡共用同一条 1000px 居中轴，避免标题贴容器左边界 */
.hero-section {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  gap: 48px;
  align-items: center;
  text-align: left;
  padding: 72px 0 48px;
  max-width: 1000px;
  margin: 0 auto 8px;
  color: var(--hc-text-1);
  position: relative;
}

.hero-text {
  position: relative;
  z-index: 2;
}

.hero-title {
  font-size: 54px;
  font-weight: 700;
  margin: 0 0 20px;
  line-height: 1.24;
  letter-spacing: -0.5px;
  color: var(--hc-primary);
}

/* 标题拆两排：第一排「AI驱动的」，第二排「零代码应用生成平台」 */
.hero-title-line {
  display: block;
}

.hero-description {
  font-size: 18px;
  margin: 0;
  line-height: 1.7;
  color: var(--hc-text-2);
}

/* 右侧装饰卡（纯展示，无交互） */
.hero-visual {
  position: relative;
  height: 300px;
  pointer-events: none;
}

.visual-glow {
  position: absolute;
  top: 8%;
  left: 8%;
  right: 4%;
  bottom: 12%;
  border-radius: 28px;
  background: var(--hc-gradient);
  opacity: 0.16;
  filter: blur(28px);
}

.visual-card {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 290px;
  padding: 24px 26px;
  border-radius: var(--hc-radius-lg);
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid var(--hc-border-light);
  box-shadow: var(--hc-shadow-pop);
  animation: cardFloat 6s ease-in-out infinite;
}

.visual-brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.visual-brand-name {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.2px;
  color: var(--hc-text-1);
}

.visual-code {
  margin-top: 16px;
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
  font-size: 42px;
  font-weight: 700;
  line-height: 1;
  background: var(--hc-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.visual-bolt {
  position: absolute;
  right: 26px;
  top: 76px;
  color: #facc15;
  filter: drop-shadow(0 4px 10px rgba(250, 204, 21, 0.45));
}

.visual-caption {
  margin-top: 18px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--hc-text-3);
}

.visual-badge {
  position: absolute;
  padding: 5px 14px;
  border-radius: var(--hc-radius-pill);
  background: #fff;
  border: 1px solid var(--hc-border-light);
  box-shadow: var(--hc-shadow-card);
  font-size: 12px;
  font-weight: 600;
  color: var(--hc-primary);
}

.badge-1 {
  top: 14px;
  right: 6px;
  animation: badgeFloat 5s ease-in-out infinite;
}

.badge-2 {
  bottom: 44px;
  left: -8px;
  animation: badgeFloat 7s ease-in-out 0.6s infinite;
}

.badge-3 {
  bottom: 4px;
  right: 24px;
  animation: badgeFloat 6s ease-in-out 1.2s infinite;
}

@keyframes cardFloat {
  0%,
  100% {
    transform: translate(-50%, -50%);
  }
  50% {
    transform: translate(-50%, calc(-50% - 9px));
  }
}

@keyframes badgeFloat {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-8px);
  }
}

/* 输入区域 */
.input-section {
  position: relative;
  margin: 0 auto 20px;
  max-width: 1000px;
}

.prompt-input {
  border-radius: var(--hc-radius-lg);
  border: 1px solid var(--hc-border-light);
  font-size: 15px;
  line-height: 1.7;
  padding: 20px 152px 20px 20px;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(20px);
  box-shadow: var(--hc-shadow-card);
  transition:
    box-shadow 0.3s,
    transform 0.3s,
    border-color 0.3s,
    background 0.3s;
}

.prompt-input:hover {
  border-color: rgba(37, 99, 235, 0.32);
}

.prompt-input:focus {
  background: #fff;
  border-color: var(--hc-primary);
  box-shadow: var(--hc-shadow-hover);
  transform: translateY(-2px);
}

.input-actions {
  position: absolute;
  bottom: 14px;
  right: 14px;
  display: flex;
  gap: 8px;
  align-items: center;
}

.input-actions .ant-btn {
  padding-inline: 22px;
  font-weight: 600;
  box-shadow: 0 6px 18px rgba(37, 99, 235, 0.28);
}

/* 快捷按钮 */
.quick-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: center;
  margin-bottom: 64px;
  flex-wrap: wrap;
}

.quick-label {
  font-size: 14px;
  color: var(--hc-text-2);
}

.quick-actions .ant-btn {
  border-radius: var(--hc-radius-pill);
  padding: 8px 22px;
  height: auto;
  background: #fff;
  border: 1px solid rgba(37, 99, 235, 0.16);
  color: var(--hc-text-2);
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.04);
  transition: all 0.3s;
  position: relative;
  overflow: hidden;
}

.quick-actions .ant-btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(37, 99, 235, 0.1), transparent);
  transition: left 0.5s;
}

.quick-actions .ant-btn:hover::before {
  left: 100%;
}

.quick-actions .ant-btn:hover {
  background: #fff;
  border-color: rgba(37, 99, 235, 0.45);
  color: var(--hc-primary);
  transform: translateY(-2px);
  box-shadow: 0 8px 22px rgba(37, 99, 235, 0.16);
}

/* 区域标题 */
.section {
  margin-bottom: 60px;
}

.section-title {
  position: relative;
  padding-left: 14px;
  font-size: 24px;
  font-weight: 600;
  margin-bottom: 28px;
  color: var(--hc-text-1);
}

.section-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 20px;
  border-radius: var(--hc-radius-pill);
  background: var(--hc-gradient);
}

/* 我的作品网格 */
.app-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
  margin-bottom: 32px;
}

/* 精选案例网格 */
.featured-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
  margin-bottom: 32px;
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}

/* 响应式设计 */
/* 双栅布局下文本列宽 = 容器宽 - 48(gap) - 380(装饰卡)，
   在 992~1200px 区间会把第二排标题挤到临界宽度，此处先降一级字号保底 */
@media (max-width: 1200px) {
  .hero-title {
    font-size: 46px;
  }
}

@media (max-width: 992px) {
  .hero-section {
    grid-template-columns: minmax(0, 1fr);
    gap: 0;
    text-align: center;
    padding: 48px 0 32px;
  }

  .hero-visual {
    display: none;
  }
}

@media (max-width: 768px) {
  .hero-title {
    font-size: 32px;
  }

  .hero-description {
    font-size: 15px;
  }

  .prompt-input {
    padding: 16px 16px 66px;
  }

  .app-grid,
  .featured-grid {
    grid-template-columns: 1fr;
  }

  .quick-actions {
    justify-content: center;
  }
}
</style>
