<script setup lang="ts">
import { ElMessage } from 'element-plus'
import request from '@/config/axios'
import ScanButton from './ScanButton.vue'

type Place = { id: number; parentId?: number; type: 'SHELF' | 'LOCATION'; code: string; name: string }
const props = defineProps<{ warehouseId?: number; skuId?: number; batchNo?: string; modelValue?: number; stockOnly?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: number | undefined] }>()
const places = ref<Place[]>([])
const stockIds = ref<number[]>([])
const areaId = ref<number>()
const codes = reactive({ SHELF: '', LOCATION: '' })
const areas = computed(() => places.value.filter((place) => place.type === 'SHELF'))
const locations = computed(() => places.value.filter((place) => place.type === 'LOCATION' && (!areaId.value || place.parentId === areaId.value) && (!props.stockOnly || stockIds.value.includes(place.id))))
let loadVersion = 0
const load = async () => {
  const version = ++loadVersion
  places.value = []; stockIds.value = []; areaId.value = undefined
  if (!props.warehouseId) return
  const nextPlaces: Place[] = await request.get({ url: '/wms/storage-place/list', params: { warehouseId: props.warehouseId } })
  if (version !== loadVersion) return
  places.value = nextPlaces
  if (props.stockOnly && props.skuId) {
    const rows: Array<{ locationId: number | null; quantity: number }> = await request.get({ url: '/wms/storage-place/dimension-inventory', params: { warehouseId: props.warehouseId, skuId: props.skuId, batchNo: props.batchNo || '' } })
    if (version !== loadVersion) return
    stockIds.value = rows.filter((row) => row.locationId != null && Number(row.quantity) > 0).map((row) => row.locationId!)
  }
  areaId.value = places.value.find((place) => place.id === props.modelValue)?.parentId
}
watch(() => [props.warehouseId, props.skuId, props.batchNo], load, { immediate: true })
const chooseArea = (id?: number) => { areaId.value = id; emit('update:modelValue', undefined) }
const chooseLocation = (id?: number) => { if (id) areaId.value = places.value.find((place) => place.id === id)?.parentId; emit('update:modelValue', id) }
const selectCode = (type: 'SHELF' | 'LOCATION', input: string) => {
  const code = input.trim()
  const match = /^WMS:(SHELF|LOCATION):(\d+)$/i.exec(code)
  const place = places.value.find((item) => item.type === type && (match ? match[1].toUpperCase() === type && String(item.id) === match[2] : item.code === code))
  if (!place || (type === 'LOCATION' && props.stockOnly && !stockIds.value.includes(place.id))) { ElMessage.warning(type === 'SHELF' ? '未找到当前仓库的库区' : '未找到可用库位'); return }
  if (type === 'SHELF') chooseArea(place.id); else chooseLocation(place.id)
  codes[type] = ''
}
</script>

<template>
  <div class="place-picker">
    <div v-for="kind in (['SHELF', 'LOCATION'] as const)" :key="kind" class="place-group">
      <strong>{{ kind === 'SHELF' ? '库区' : '库位' }}（可选）</strong>
      <div class="method"><span>方式一</span><ScanButton :label="`扫码选择${kind === 'SHELF' ? '库区' : '库位'}`" @scan="(value) => selectCode(kind, value)" /></div>
      <div class="method"><span>方式二</span><form @submit.prevent="selectCode(kind, codes[kind])"><input v-model="codes[kind]" :placeholder="`输入${kind === 'SHELF' ? '库区' : '库位'}编号`" /><button type="submit">选择</button></form></div>
      <div class="method"><span>方式三</span><el-select v-if="kind === 'SHELF'" :model-value="areaId" clearable filterable placeholder="从列表选择库区" @update:model-value="chooseArea"><el-option v-for="item in areas" :key="item.id" :value="item.id" :label="`${item.name} (${item.code})`" /></el-select><el-select v-else :model-value="modelValue" clearable filterable placeholder="从列表选择库位" @update:model-value="chooseLocation"><el-option v-for="item in locations" :key="item.id" :value="item.id" :label="`${item.name} (${item.code})`" /></el-select></div>
    </div>
  </div>
</template>

<style scoped>
.place-group { margin-top: 12px; }.place-group + .place-group { padding-top: 14px; border-top: 1px solid #edf1f6; }.place-group strong { color: #243752; font-size: 14px; }.method { display: flex; align-items: center; gap: 10px; margin-top: 10px; }.method > span { flex: none; width: 48px; color: #718198; font-size: 13px; }.method :deep(.scan-trigger), .method :deep(.el-select), .method form { flex: 1; width: 100%; min-width: 0; }.method form { display: flex; gap: 8px; margin: 0; }.method input { box-sizing: border-box; flex: 1; width: 0; min-width: 0; padding: 10px; border: 1px solid #dce5f1; border-radius: 10px; font-size: 16px; }.method form button { border: 0; border-radius: 10px; padding: 0 14px; background: #245eea; color: #fff; }
</style>
