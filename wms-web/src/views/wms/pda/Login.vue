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
    <div class="login-shell">
      <div class="brand"><div class="mark" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none"><path d="M3 9.2 12 4l9 5.2v9.3a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9.2Z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" /><path d="M8 19v-7h8v7M3 9h18" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" /></svg></div><span>仓储管理系统<small>移动作业台</small></span></div>
      <div class="card"><div class="card-head"><span class="eyebrow">欢迎回来</span><h1>登录移动仓库</h1><p class="hint">使用您的账号开始仓库作业</p></div>
      <form @submit.prevent="login">
        <label>租户<input v-model="tenant" autocomplete="organization" placeholder="租户名称" /></label>
        <label>账号<input v-model="username" autocomplete="username" placeholder="请输入账号" /></label>
        <label>密码<input v-model="password" autocomplete="current-password" type="password" placeholder="请输入密码" /></label>
        <button :disabled="loading" type="submit">{{ loading ? '登录中…' : '登录' }}</button>
      </form>
    </div><p class="footnote">入库 · 出库 · 移库 · 盘点</p></div>
  </main>
</template>

<style scoped>
.pda-login{height:100vh;height:100dvh;overflow-y:auto;box-sizing:border-box;display:flex;align-items:center;justify-content:center;padding:32px 22px;background:linear-gradient(165deg,#eaf1ff 0%,#f5f8fd 38%,#f4f7fb 100%);color:#172a47;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif}.login-shell{width:100%;max-width:420px;margin:auto}.brand{display:flex;align-items:center;gap:13px;margin:0 2px 30px;font-weight:700;font-size:17px;letter-spacing:.02em}.brand small{display:block;margin-top:4px;color:#71819a;font-size:12px;font-weight:500;letter-spacing:.08em}.mark{display:grid;place-items:center;width:46px;height:46px;border-radius:14px;background:#245eea;color:white;box-shadow:0 8px 18px #245eea30}.mark svg{width:26px;height:26px}.card{padding:30px 24px 26px;border:1px solid #e7edf7;border-radius:22px;background:#fff;box-shadow:0 16px 45px #274b8112}.eyebrow{color:#245eea;font-size:13px;font-weight:700}h1{margin:8px 0;font-size:26px;letter-spacing:.02em}.hint{margin:0 0 28px;color:#8491a5;font-size:14px}form{display:grid;gap:18px}label{display:grid;gap:8px;font-size:13px;font-weight:600;color:#455772}input{box-sizing:border-box;width:100%;min-height:50px;padding:0 14px;border:1px solid #e2e9f3;border-radius:12px;background:#f8fafd;color:#172a47;font-size:16px}input::placeholder{color:#a7b2c2}input:focus{outline:none;border-color:#5283ef;background:#fff;box-shadow:0 0 0 3px #245eea18}button{min-height:52px;margin-top:6px;border:0;border-radius:12px;color:white;background:#245eea;font-size:16px;font-weight:700;box-shadow:0 8px 18px #245eea30}button:disabled{opacity:.6}.footnote{margin:24px 0;text-align:center;color:#99a7ba;font-size:12px;letter-spacing:.08em}
</style>
