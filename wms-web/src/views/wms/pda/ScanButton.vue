<script setup lang="ts">
import { ElMessage } from 'element-plus'
const props = withDefaults(defineProps<{ variant?: 'compact' | 'card'; label?: string; continuous?: boolean; disabled?: boolean }>(), { variant: 'compact', label: '扫码', continuous: false, disabled: false })
const emit = defineEmits<{ scan: [value: string] }>()
const visible = ref(false), text = ref(''), active = ref(false), video = ref<HTMLVideoElement>()
const input = ref<{ focus: () => void }>()
let stream: MediaStream | undefined, timer: number | undefined
let scanner: { stop: () => void } | undefined
let cameraRequest = 0
let lastDetected = '', lastSeenAt = 0
const scanCount = ref(0)
const supported = computed(() => window.isSecureContext && !!navigator.mediaDevices?.getUserMedia)
const stop = () => { cameraRequest++; active.value = false; if (timer) window.clearTimeout(timer); timer = undefined; scanner?.stop(); scanner = undefined; stream?.getTracks().forEach((track) => track.stop()); stream = undefined }
const submit = () => { const value = text.value.trim(); if (!value) return; emit('scan', value); text.value = ''; if (props.continuous) { scanCount.value++; input.value?.focus() } else { visible.value = false; stop() } }
const detected = (value: string) => { const now = Date.now(); const repeat = value === lastDetected && now - lastSeenAt < 1200; lastDetected = value; lastSeenAt = now; if (!repeat) { text.value = value; submit() } }
const open = async () => { visible.value = true; text.value = ''; scanCount.value = 0; lastDetected = ''; lastSeenAt = 0; await nextTick(); input.value?.focus(); if (supported.value) await camera() }
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
onBeforeUnmount(stop)
</script>
<template>
  <button type="button" class="scan-trigger" :class="`scan-trigger--${variant}`" :disabled="disabled" @click="open"><svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M4 9V5a1 1 0 0 1 1-1h4M15 4h4a1 1 0 0 1 1 1v4M20 15v4a1 1 0 0 1-1 1h-4M9 20H5a1 1 0 0 1-1-1v-4" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" /><rect x="9" y="9" width="6" height="6" rx="1.5" fill="currentColor" /></svg><span>{{ label }}</span></button>
  <el-dialog v-model="visible" title="扫码录入" width="min(420px, 94vw)" append-to-body @closed="stop">
    <div class="scan-intro"><span class="scan-icon" aria-hidden="true">⌗</span><div><strong>扫描条码或二维码</strong><p>使用 PDA 扫码枪，或手动输入后确认</p></div></div>
    <el-input ref="input" v-model="text" placeholder="扫描或输入条码" @keydown.enter.prevent.stop="submit" />
    <p v-if="!supported">当前地址无法启用摄像头，请使用扫码枪或 HTTPS 地址。</p>
    <div v-else><el-button v-if="!active" native-type="button" @click="camera">打开摄像头扫码</el-button><el-button v-else native-type="button" @click="stop">关闭摄像头</el-button><video v-if="active" ref="video" playsinline muted class="scan-video"></video></div>
    <template #footer><span v-if="continuous">已扫描 {{ scanCount }} 次</span><el-button native-type="button" @click="visible = false">{{ continuous ? '完成扫描' : '取消' }}</el-button><el-button v-if="!continuous || text" native-type="button" type="primary" @click="submit">确认</el-button></template>
  </el-dialog>
</template>
<style scoped>
.scan-trigger { box-sizing:border-box; display:inline-flex; align-items:center; justify-content:center; gap:6px; min-width:72px; min-height:44px; padding:0 12px; border:1px solid #d7e6ff; border-radius:12px; background:#edf4ff; color:#2462e9; font-size:14px; font-weight:650; cursor:pointer; white-space:nowrap; }
.scan-trigger svg { width:20px; height:20px; flex:none; }
.scan-trigger--card { display:flex; flex-direction:column; gap:9px; width:100%; min-height:104px; border-color:#e1ebff; border-radius:16px; background:linear-gradient(150deg,#eff5ff,#e5efff); color:#213b65; font-size:15px; }
.scan-trigger--card svg { width:29px; height:29px; color:#2867ed; }
.scan-trigger:focus-visible { outline:3px solid #95b6ff; outline-offset:2px; }
.scan-intro { display:flex; align-items:center; gap:12px; margin-bottom:18px; color:#1b3152; }.scan-intro strong { font-size:15px; }.scan-intro p { margin:4px 0 0; color:#8190a5; font-size:13px; }.scan-icon { display:grid; place-items:center; width:44px; height:44px; border-radius:13px; background:#edf3ff; color:#245eea; font-size:26px; }.scan-video { display:block; width:100%; max-height:45vh; margin-top:12px; border-radius:12px; background:#101828; object-fit:cover; }</style>

