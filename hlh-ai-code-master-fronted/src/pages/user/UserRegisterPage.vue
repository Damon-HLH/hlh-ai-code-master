<template>
  <div id="userRegisterPage">
    <div class="auth-card">
      <AuthBrandPanel title="创建账号" subtitle="注册后立即开始你的零代码创作" />
      <div class="auth-form">
        <h2 class="form-title">用户注册</h2>
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
              { min: 8, message: '密码不能小于 8 位' },
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
          <a-form-item
            name="checkPassword"
            :rules="[
              { required: true, message: '请确认密码' },
              { min: 8, message: '密码不能小于 8 位' },
              { validator: validateCheckPassword },
            ]"
          >
            <a-input-password
              v-model:value="formState.checkPassword"
              size="large"
              placeholder="请确认密码"
            >
              <template #prefix>
                <LockOutlined class="field-icon" />
              </template>
            </a-input-password>
          </a-form-item>
          <div class="tips">
            已有账号？
            <RouterLink to="/user/login">去登录</RouterLink>
          </div>
          <a-form-item>
            <a-button type="primary" html-type="submit" size="large" block>注册</a-button>
          </a-form-item>
        </a-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { userRegister } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import { reactive } from 'vue'
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue'
import AuthBrandPanel from '@/components/AuthBrandPanel.vue'

const router = useRouter()

const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

/**
 * 验证确认密码
 * @param rule
 * @param value
 * @param callback
 */
const validateCheckPassword = (rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value && value !== formState.userPassword) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async (values: API.UserRegisterRequest) => {
  const res = await userRegister(values)
  // 注册成功，跳转到登录页面
  if (res.data.code === 0) {
    message.success('注册成功')
    router.push({
      path: '/user/login',
      replace: true,
    })
  } else {
    message.error('注册失败，' + res.data.message)
  }
}
</script>

<style scoped>
#userRegisterPage {
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
  padding: 44px 40px;
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
  margin-bottom: 24px;
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
  margin-bottom: 16px;
  color: var(--hc-text-3);
  font-size: 13px;
  text-align: right;
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
