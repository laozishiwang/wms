<script setup lang="ts">
import request from '@/config/axios'
import * as LoginApi from '@/api/login'
import { setTenantId, setToken } from '@/utils/auth'
import type { TokenType } from '@/api/login/types'
import { ElMessage } from 'element-plus'

const router = useRouter()
const tenant = ref(import.meta.env.VITE_APP_DEFAULT_LOGIN_TENANT || '芋道源码')
const username = ref('')
const password = ref('')
const loading = ref(false)
const login = async () => {
  if (!tenant.value.trim() || !username.value.trim() || !password.value) {
    ElMessage.warning('请填写租户、账号和密码')
    return
  }
  loading.value = true
  try {
    const id = await LoginApi.getTenantIdByName(tenant.value.trim())
    if (!id) { ElMessage.warning('租户不存在，请检查租户名称'); return }
    setTenantId(id)
    const token = await request.post<TokenType>({ url: '/system/auth/pda-login', data: { username: username.value.trim(), password: password.value }, headers: { isToken: false, isEncrypt: false } })
    if (!token?.accessToken || !token?.refreshToken) { ElMessage.error('登录未成功，请检查账号和密码'); return }
    setToken(token)
    await router.replace('/pda')
  } finally { loading.value = false }
}
</script>

<template>
  <main class="pda-login">
    <div class="card">
      <div class="mark" aria-hidden="true">仓</div>
      <h1>移动仓库</h1>
      <p class="hint">登录后开始扫码作业</p>
      <form @submit.prevent="login">
        <label>租户<input v-model="tenant" autocomplete="organization" placeholder="租户名称" /></label>
        <label>账号<input v-model="username" autocomplete="username" placeholder="请输入账号" /></label>
        <label>密码<input v-model="password" autocomplete="current-password" type="password" placeholder="请输入密码" /></label>
        <button :disabled="loading" type="submit">{{ loading ? '登录中…' : '登录' }}</button>
      </form>
    </div>
  </main>
</template>

<style scoped>
.pda-login{min-height:100dvh;display:flex;align-items:center;justify-content:center;padding:24px;background:#f3f6fb;color:#15233c}.card{width:100%;max-width:420px;padding:32px 24px;background:white;border-radius:20px;box-shadow:0 12px 40px #18385a16}.mark{display:grid;place-items:center;width:48px;height:48px;border-radius:14px;background:#eaf2ff;color:#2165dc;font-size:22px;font-weight:700}h1{font-size:28px;margin:20px 0 8px}.hint{color:#718096;margin:0 0 28px}form{display:grid;gap:18px}label{display:grid;gap:8px;font-size:14px;font-weight:600}input{width:100%;min-height:48px;border:1px solid #d9e2ef;border-radius:9px;padding:0 12px;font-size:16px}input:focus-visible{outline:2px solid #2165dc;outline-offset:2px}button{min-height:48px;border:0;border-radius:9px;color:white;background:#2165dc;font-size:16px;font-weight:600}button:disabled{opacity:.6}
</style>
