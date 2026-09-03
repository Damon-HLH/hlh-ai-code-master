<template>
  <div id="userLoginPage">
    <div class="auth-card">
      <AuthBrandPanel title="欢迎回来" subtitle="登录您的账户，开始创建智能应用" />
      <div class="auth-form">
        <h2 class="form-title">用户登录</h2>
        <div class="form-subtitle">请输入您的账户信息</div>
        <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
          <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
            <a-input v-model:value="formState.userAccount" size="large" placeholder="请输入账号">
              <template #prefix>
                <UserOutlined class="field-icon" />
              </template>
            </a-input>
          </a-form-item>
          <a-form-item
            name="userPassword"
            :rules="[
              { required: true, message: '请输入密码' },
              { min: 8, message: '密码长度不能小于 8 位' },
            ]"
          >
            <a-input-password
              v-model:value="formState.userPassword"
              size="large"
              placeholder="请输入密码"
            >
              <template #prefix>
                <LockOutlined class="field-icon" />
              </template>
            </a-input-password>
          </a-form-item>
          <div class="tips">
            还没有账号？
            <RouterLink to="/user/register">立即注册</RouterLink>
          </div>
          <a-form-item>
            <a-button type="primary" html-type="submit" size="large" block>登录</a-button>
          </a-form-item>
        </a-form>
      </div>
    </div>
  </div>
</template>
<script lang="ts" setup>
import { reactive } from 'vue'
import { userLogin } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue'
import AuthBrandPanel from '@/components/AuthBrandPanel.vue'

const formState = reactive<API.UserLoginRequest>({
  userAccount: '',
  userPassword: '',
})

const router = useRouter()
const loginUserStore = useLoginUserStore()

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async (values: any) => {
  const res = await userLogin(values)
  // 登录成功，把登录态保存到全局状态中
  if (res.data.code === 0 && res.data.data) {
    await loginUserStore.fetchLoginUser()
    message.success('登录成功')
    router.push({
      path: '/',
      replace: true,
    })
  } else {
    message.error('登录失败，' + res.data.message)
  }
}
</script>

<style scoped>
#userLoginPage {
  min-height: calc(100vh - 200px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
}

.auth-card {
  display: grid;
  grid-template-columns: 1fr 1fr;
  width: 100%;
  max-width: 960px;
  border-radius: 20px;
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--hc-border-light);
  box-shadow: var(--hc-shadow-pop);
}

.auth-form {
  padding: 48px 40px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.form-title {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.3px;
  color: var(--hc-text-1);
}

.form-subtitle {
  margin-bottom: 28px;
  font-size: 13px;
  color: var(--hc-text-3);
}

.field-icon {
  color: var(--hc-text-3);
}

.auth-form :deep(.ant-input-affix-wrapper) {
  border-radius: var(--hc-radius-sm);
}

.auth-form :deep(.ant-btn-primary) {
  height: 44px;
  font-weight: 600;
  box-shadow: 0 8px 20px rgba(37, 99, 235, 0.26);
}

.tips {
  text-align: right;
  color: var(--hc-text-3);
  font-size: 13px;
  margin-bottom: 16px;
}

.tips a {
  color: var(--hc-primary);
  font-weight: 600;
}

@media (max-width: 768px) {
  .auth-card {
    grid-template-columns: 1fr;
  }

  .auth-brand-panel {
    display: none;
  }

  .auth-form {
    padding: 32px 24px;
  }
}
</style>
