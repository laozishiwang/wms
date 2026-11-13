<script setup lang="ts">
import { ElMessage } from 'element-plus'
const emit = defineEmits<{ scan: [value: string] }>()
const visible = ref(false), text = ref(''), active = ref(false), video = ref<HTMLVideoElement>()
const input = ref<{ focus: () => void }>()
let stream: MediaStream | undefined, timer: number | undefined
const supported = computed(() => window.isSecureContext && 'BarcodeDetector' in window && !!navigator.mediaDevices?.getUserMedia)
const stop = () => { active.value = false; if (timer) window.clearTimeout(timer); timer = undefined; stream?.getTracks().forEach((track) => track.stop()); stream = undefined }
const submit = () => { const value = text.value.trim(); if (!value) return; emit('scan', value); text.value = ''; visible.value = false; stop() }
const open = async () => { visible.value = true; text.value = ''; await nextTick(); input.value?.focus() }
const camera = async () => {
  if (!supported.value) return
  try {
    const Detector = (window as any).BarcodeDetector
    const preferred = ['qr_code', 'code_128', 'code_39', 'ean_13', 'ean_8', 'upc_a', 'upc_e']
    const available: string[] = await Detector.getSupportedFormats()
    const formats = preferred.filter((format) => available.includes(format))
    if (!formats.length) { ElMessage.warning('浏览器不支持常用条码格式，请使用扫码枪输入'); return }
    const detector = new Detector({ formats })
    if (!visible.value) return
    stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
    if (!visible.value) { stop(); return }
    active.value = true; await nextTick(); if (!video.value) { stop(); return }; video.value.srcObject = stream; await video.value.play()
    const detect = async () => { if (!active.value || !video.value) return; try { const codes = await detector.detect(video.value); if (codes.length) { text.value = codes[0].rawValue; submit(); return } } catch { /* Continue scanning subsequent frames. */ } if (active.value) timer = window.setTimeout(detect, 200) }
    await detect()
  } catch { stop(); ElMessage.warning('无法开启摄像头，请检查权限或使用扫码枪输入') }
}
onBeforeUnmount(stop)
</script>
<template>
  <el-button native-type="button" type="primary" plain @click="open">扫码</el-button>
  <el-dialog v-model="visible" title="扫码录入" width="min(420px, 94vw)" append-to-body @closed="stop">
    <p>使用 PDA 扫码枪扫描，或输入条码后确认。</p>
    <el-input ref="input" v-model="text" placeholder="扫描或输入条码" @keydown.enter.prevent.stop="submit" />
    <p v-if="!supported">当前页面或浏览器不支持摄像头扫码，请使用 PDA 扫码枪；摄像头通常需要 HTTPS。</p>
    <div v-else><el-button v-if="!active" native-type="button" @click="camera">打开摄像头扫码</el-button><el-button v-else native-type="button" @click="stop">关闭摄像头</el-button><video v-if="active" ref="video" playsinline muted class="scan-video"></video></div>
    <template #footer><el-button native-type="button" @click="visible = false">取消</el-button><el-button native-type="button" type="primary" @click="submit">确认</el-button></template>
  </el-dialog>
</template>
<style scoped>.scan-video { display:block; width:100%; max-height:45vh; margin-top:12px; border-radius:12px; background:#101828; object-fit:cover; }</style>

