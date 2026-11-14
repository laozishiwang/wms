<script setup lang="ts">
import { ElMessage } from 'element-plus'
const props = withDefaults(defineProps<{ variant?: 'compact' | 'card' | 'standard'; label?: string; continuous?: boolean; disabled?: boolean; processScan?: (value: string) => Promise<{ ok: boolean; message: string; bindable?: boolean }> }>(), { variant: 'compact', label: '扫码', continuous: false, disabled: false })
const emit = defineEmits<{ scan: [value: string]; bind: [value: string] }>()
const visible = ref(false), text = ref(''), active = ref(false), video = ref<HTMLVideoElement>()
const input = ref<{ focus: () => void }>()
let stream: MediaStream | undefined, timer: number | undefined
let scanner: { stop: () => void } | undefined
let cameraRequest = 0
let lastDetected = '', lastSeenAt = 0
const successCount = ref(0), failureCount = ref(0), pendingCount = ref(0)
const lastResult = ref<{ ok: boolean; message: string; bindable?: boolean }>()
const lastValue = ref('')
const soundEnabled = ref(true)
let audio: AudioContext | undefined
let session = 0
const beep = (ok: boolean) => {
  if (!soundEnabled.value || !audio || audio.state !== 'running') return
  const oscillator = audio.createOscillator(), gain = audio.createGain()
  oscillator.frequency.value = ok ? 880 : 260
  gain.gain.setValueAtTime(0.08, audio.currentTime)
  gain.gain.exponentialRampToValueAtTime(0.001, audio.currentTime + (ok ? 0.12 : 0.3))
  oscillator.connect(gain); gain.connect(audio.destination); oscillator.start(); oscillator.stop(audio.currentTime + (ok ? 0.12 : 0.3))
}
const supported = computed(() => window.isSecureContext && !!navigator.mediaDevices?.getUserMedia)
const stop = () => { cameraRequest++; active.value = false; if (timer) window.clearTimeout(timer); timer = undefined; scanner?.stop(); scanner = undefined; stream?.getTracks().forEach((track) => track.stop()); stream = undefined }
const submit = () => {
  const value = text.value.trim(); if (!value) return
  text.value = ''
  if (props.continuous && props.processScan) {
    const current = session; pendingCount.value++
    props.processScan(value).then((result) => {
      if (current !== session) return
      lastResult.value = result
      lastValue.value = value
      if (result.ok) successCount.value++; else failureCount.value++
      beep(result.ok)
    }).finally(() => { if (current === session) pendingCount.value-- })
    input.value?.focus()
  } else { emit('scan', value); visible.value = false; stop() }
}
const detected = (value: string) => { const now = Date.now(); const repeat = value === lastDetected && now - lastSeenAt < 1200; lastDetected = value; lastSeenAt = now; if (!repeat) { text.value = value; submit() } }
const open = async () => { session++; visible.value = true; text.value = ''; successCount.value = 0; failureCount.value = 0; pendingCount.value = 0; lastResult.value = undefined; lastDetected = ''; lastSeenAt = 0; if (props.continuous) { try { audio ||= new AudioContext(); void audio.resume().catch(() => {}) } catch { /* Sound is optional. */ } }; await nextTick(); input.value?.focus(); if (supported.value) await camera() }
const camera = async () => {
  if (!supported.value) return
  const requestId = ++cameraRequest
  try {
    const media = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
    if (!visible.value || requestId !== cameraRequest) { media.getTracks().forEach((track) => track.stop()); return }
    stream = media; active.value = true; await nextTick()
    if (!video.value || requestId !== cameraRequest) { stop(); return }
    if ('BarcodeDetector' in window) {
      const Detector = (window as any).BarcodeDetector
      const preferred = ['qr_code', 'code_128', 'code_39', 'ean_13', 'ean_8', 'upc_a', 'upc_e']
      const available: string[] = await Detector.getSupportedFormats()
      const formats = preferred.filter((format) => available.includes(format))
      if (formats.length) {
        const detector = new Detector({ formats }); video.value.srcObject = media; await video.value.play()
        const detect = async () => { if (!active.value || !video.value || requestId !== cameraRequest) return; try { const codes = await detector.detect(video.value); if (codes.length) detected(codes[0].rawValue) } catch { /* Continue with the next frame. */ } if (active.value) timer = window.setTimeout(detect, 200) }
        await detect(); return
      }
    }
    const { BrowserMultiFormatReader } = await import('@zxing/browser')
    if (!video.value || requestId !== cameraRequest) return
    const controls = await new BrowserMultiFormatReader().decodeFromStream(media, video.value, (result) => { if (result && requestId === cameraRequest) detected(result.getText()) })
    if (requestId !== cameraRequest) controls.stop(); else scanner = controls
  } catch { if (requestId === cameraRequest) { stop(); ElMessage.warning('无法开启摄像头，请检查权限或使用扫码枪输入') } }
}
onBeforeUnmount(() => { stop(); audio?.close() })
</script>
<template>
  <button type="button" class="scan-trigger" :class="`scan-trigger--${variant}`" :disabled="disabled" @click="open"><svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M4 9V5a1 1 0 0 1 1-1h4M15 4h4a1 1 0 0 1 1 1v4M20 15v4a1 1 0 0 1-1 1h-4M9 20H5a1 1 0 0 1-1-1v-4" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" /><rect x="9" y="9" width="6" height="6" rx="1.5" fill="currentColor" /></svg><span>{{ label }}</span></button>
  <el-dialog v-model="visible" title="扫码录入" width="min(420px, 94vw)" append-to-body :show-close="pendingCount === 0" :close-on-click-modal="pendingCount === 0" :close-on-press-escape="pendingCount === 0" @closed="stop">
    <div class="scan-intro"><span class="scan-icon" aria-hidden="true">⌗</span><div><strong>扫描条码或二维码</strong><p>使用 PDA 扫码枪，或手动输入后确认</p></div></div>
    <el-input ref="input" v-model="text" placeholder="扫描或输入条码" @keydown.enter.prevent.stop="submit" />
    <div v-if="continuous && lastResult" class="scan-result" :class="{ failed: !lastResult.ok }" role="status">{{ lastResult.message }}</div>
    <el-button v-if="lastResult?.bindable" :disabled="pendingCount > 0" link type="primary" @click="visible = false; emit('bind', lastValue)">处理未识别条码</el-button>
    <p v-if="!supported">当前地址无法启用摄像头，请使用扫码枪或 HTTPS 地址。</p>
    <div v-else><el-button v-if="!active" native-type="button" @click="camera">打开摄像头扫码</el-button><el-button v-else native-type="button" @click="stop">关闭摄像头</el-button><video v-if="active" ref="video" playsinline muted class="scan-video"></video></div>
    <template #footer><div v-if="continuous" class="scan-summary"><span>成功 {{ successCount }} · 失败 {{ failureCount }}<template v-if="pendingCount"> · 处理中 {{ pendingCount }}</template></span><el-switch v-model="soundEnabled" active-text="提示音" /></div><el-button native-type="button" :disabled="pendingCount > 0" @click="visible = false">{{ continuous ? '完成扫描' : '取消' }}</el-button><el-button v-if="!continuous || text" native-type="button" type="primary" @click="submit">确认</el-button></template>
  </el-dialog>
</template>
<style scoped>
.scan-trigger { box-sizing:border-box; display:inline-flex; align-items:center; justify-content:center; gap:6px; min-width:72px; min-height:44px; padding:0 12px; border:1px solid #d7e6ff; border-radius:12px; background:#edf4ff; color:#2462e9; font-size:14px; font-weight:650; cursor:pointer; white-space:nowrap; }
.scan-trigger svg { width:20px; height:20px; flex:none; }
.scan-trigger--card { display:flex; flex-direction:column; gap:9px; width:100%; min-height:104px; border-color:#e1ebff; border-radius:16px; background:linear-gradient(150deg,#eff5ff,#e5efff); color:#213b65; font-size:15px; }
.scan-trigger--standard { min-height:32px; border-radius:4px; padding:0 15px; background:var(--el-color-primary-light-9); border-color:var(--el-color-primary-light-5); color:var(--el-color-primary); font-size:14px; font-weight:500; }
.scan-trigger--card svg { width:29px; height:29px; color:#2867ed; }
.scan-trigger:focus-visible { outline:3px solid #95b6ff; outline-offset:2px; }
.scan-intro { display:flex; align-items:center; gap:12px; margin-bottom:18px; color:#1b3152; }.scan-intro strong { font-size:15px; }.scan-intro p { margin:4px 0 0; color:#8190a5; font-size:13px; }.scan-icon { display:grid; place-items:center; width:44px; height:44px; border-radius:13px; background:#edf3ff; color:#245eea; font-size:26px; }.scan-video { display:block; width:100%; max-height:45vh; margin-top:12px; border-radius:12px; background:#101828; object-fit:cover; }.scan-result { padding:10px 12px; margin-top:10px; border-radius:10px; color:#16713c; background:#eaf8ef; overflow-wrap:anywhere; }.scan-result.failed { color:#ba3737; background:#fff0f0; }.scan-summary { display:flex; align-items:center; justify-content:space-between; gap:8px; margin-bottom:12px; color:#53657d; font-size:13px; }</style>

