<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/config/axios'
import { ReceiptOrderApi } from '@/api/wms/order/receipt'
import { ShipmentOrderApi } from '@/api/wms/order/shipment'
import { MovementOrderApi } from '@/api/wms/order/movement'
import { DICT_TYPE } from '@/utils/dict'
import { checkPermi } from '@/utils/permission'
import ApprovalActions from '@/views/wms/order/components/ApprovalActions.vue'
import ScanButton from './ScanButton.vue'
import { placePath, type StoragePlace } from './place'

const props = defineProps<{ order: any; details: any[]; approvalEnabled: boolean; kind: 'receipt' | 'shipment' | 'movement' }>()
const emit = defineEmits<{ back: []; changed: []; updated: [] }>()
const totalQuantity = computed(() => Number(props.details.reduce((sum, row) => sum + Number(row.quantity || 0), 0).toFixed(2)))
const skuCount = computed(() => new Set(props.details.map((row) => row.skuId)).size)
const canChoosePlace = checkPermi(['wms:warehouse:query'])
const kindName = computed(() => ({ receipt: '入库', shipment: '出库', movement: '移库' })[props.kind])
const warehouseId = computed(() => props.kind === 'movement' ? props.order.sourceWarehouseId : props.order.warehouseId)
const showPlace = ref(false)
const places = ref<StoragePlace[]>([])
const targetPlaces = ref<StoragePlace[]>([])
const currentRow = ref<any>()
const areaId = ref<number>()
const locationId = ref<number>()
const codes = reactive({ SHELF: '', LOCATION: '' })
const saving = ref(false)
const areas = computed(() => places.value.filter((place) => place.type === 'SHELF'))
const locations = computed(() => places.value.filter((place) => place.type === 'LOCATION' && (!areaId.value || place.parentId === areaId.value)))
const chooseLocation = (id?: number) => { locationId.value = id; if (id) areaId.value = places.value.find((place) => place.id === id)?.parentId }
const selectCode = (type: 'SHELF' | 'LOCATION', raw: string) => {
  const input = raw.trim()
  const match = /^WMS:(SHELF|LOCATION):(\d+)$/i.exec(input)
  const place = places.value.find((item) => item.type === type && (match ? match[1].toUpperCase() === type && String(item.id) === match[2] : item.code === input))
  if (!place) { ElMessage.warning(`未找到当前仓库的${type === 'SHELF' ? '库区' : '库位'}`); return }
  if (type === 'SHELF') { areaId.value = place.id; locationId.value = undefined }
  else { areaId.value = place.parentId; locationId.value = place.id }
  codes[type] = ''
}
const openPlace = async (row: any) => {
  currentRow.value = row
  places.value = await request.get({ url: '/wms/storage-place/list', params: { warehouseId: warehouseId.value } })
  locationId.value = row.locationId || undefined
  areaId.value = places.value.find((place) => place.id === locationId.value)?.parentId
  codes.SHELF = ''; codes.LOCATION = ''
  showPlace.value = true
}
const savePlace = async () => {
  if (!locationId.value) { ElMessage.warning('请选择库位'); return }
  saving.value = true
  try {
    const latest: any = await ReceiptOrderApi.getReceiptOrder(props.order.id)
    if (latest.status !== 0) { ElMessage.warning('单据状态已变化，请返回列表刷新'); return }
    const row = latest.details?.find((item: any) => item.id === currentRow.value.id)
    if (!row) { ElMessage.warning('明细已变化，请返回列表刷新'); return }
    row.locationId = locationId.value
    await ReceiptOrderApi.updateReceiptOrder(latest)
    ElMessage.success('库位已保存')
    showPlace.value = false
    emit('updated')
  } finally { saving.value = false }
}
const complete = async () => {
  await ElMessageBox.confirm(`确认完成${kindName.value}？共 ${props.details.length} 条明细，数量 ${totalQuantity.value}。`, `完成${kindName.value}`, { type: 'warning' })
  if (props.kind === 'receipt') await ReceiptOrderApi.completeReceiptOrder(props.order.id)
  else if (props.kind === 'shipment') await ShipmentOrderApi.completeShipmentOrder(props.order.id)
  else await MovementOrderApi.completeMovementOrder(props.order.id)
  ElMessage.success(`${kindName.value}已完成`)
  emit('changed')
}
onMounted(async () => {
  if (!canChoosePlace || !warehouseId.value) return
  places.value = await request.get({ url: '/wms/storage-place/list', params: { warehouseId: warehouseId.value } })
  if (props.kind === 'movement' && props.order.targetWarehouseId) {
    targetPlaces.value = props.order.targetWarehouseId === warehouseId.value ? places.value : await request.get({ url: '/wms/storage-place/list', params: { warehouseId: props.order.targetWarehouseId } })
  }
})
</script>

<template>
  <section class="receipt-detail">
    <button class="back" @click="emit('back')">‹ 返回列表</button>
    <div class="summary"><div class="summary-top"><span>{{ kindName }}单</span><dict-tag :type="DICT_TYPE.WMS_ORDER_STATUS" :value="order.status" /></div><strong class="order-no">{{ order.no }}</strong><div class="warehouse">{{ kind === 'movement' ? `${order.sourceWarehouseName || '未分配'} → ${order.targetWarehouseName || '未分配'}` : (order.warehouseName || '未分配仓库') }}</div><div class="stats"><div><span>物品</span><b>{{ skuCount }}</b></div><div><span>明细</span><b>{{ details.length }}</b></div><div><span>总数量</span><b>{{ totalQuantity }}</b></div></div></div>
    <div class="detail-heading"><h2>物品明细</h2><span>共 {{ details.length }} 条</span></div>
    <article v-for="row in details" :key="row.id" class="item-card"><div class="item-head"><div><strong>{{ row.itemName }} {{ row.skuName }}</strong><p>{{ row.skuCode }}</p></div><div class="item-quantity"><b>{{ row.quantity }}</b><span>{{ row.unit || '' }}</span></div></div><div class="item-meta"><span>{{ kind === 'movement' ? '来源位置' : '库区 / 库位' }}</span><strong>{{ placePath(places, kind === 'movement' ? row.sourceLocationId : row.locationId) }}</strong><button v-if="kind === 'receipt' && order.status === 0 && canChoosePlace" v-hasPermi="['wms:receipt-order:update']" @click="openPlace(row)">{{ row.locationId ? '更换' : '设置' }}</button></div><div v-if="kind === 'movement'" class="item-meta"><span>目标位置</span><strong>{{ placePath(targetPlaces, row.targetLocationId) }}</strong></div><div v-if="row.batchNo" class="item-meta"><span>批次</span><strong>{{ row.batchNo }}</strong></div><div v-if="row.serialNos?.length" class="item-meta"><span>序列号</span><strong>{{ row.serialNos.length }} 个</strong></div></article>
    <div class="actions"><ApprovalActions v-if="approvalEnabled || order.status === 1" :type="kind" :id="order.id" :status="order.status" @changed="emit('changed')" /><button v-if="order.status === 0 && !approvalEnabled" v-hasPermi="[`wms:${kind}-order:complete`]" class="primary" @click="complete">确认完成{{ kindName }}</button></div>
    <el-dialog v-model="showPlace" title="设置库区与库位" width="min(520px, 94vw)" append-to-body>
      <div v-for="placeType in (['SHELF', 'LOCATION'] as const)" :key="placeType" class="place-group"><h3>{{ placeType === 'SHELF' ? '库区（可选）' : '库位' }}</h3>
        <div class="method"><span>方式一</span><ScanButton :label="`扫码选择${placeType === 'SHELF' ? '库区' : '库位'}`" @scan="(value) => selectCode(placeType, value)" /></div>
        <div class="method"><span>方式二</span><form @submit.prevent="selectCode(placeType, codes[placeType])"><input v-model="codes[placeType]" :placeholder="`输入${placeType === 'SHELF' ? '库区' : '库位'}编号`" /><button>选择</button></form></div>
        <div class="method"><span>方式三</span><el-select v-if="placeType === 'SHELF'" v-model="areaId" clearable filterable placeholder="从列表选择库区" @change="locationId = undefined"><el-option v-for="place in areas" :key="place.id" :value="place.id" :label="`${place.name} (${place.code})`" /></el-select><el-select v-else :model-value="locationId" clearable filterable placeholder="从列表选择库位" @update:model-value="chooseLocation"><el-option v-for="place in locations" :key="place.id" :value="place.id" :label="`${place.name} (${place.code})`" /></el-select></div>
      </div>
      <template #footer><el-button @click="showPlace = false">取消</el-button><el-button type="primary" :loading="saving" @click="savePlace">保存库位</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.receipt-detail { padding: 8px 16px 30px; color: #172840; }.back { border: 0; background: transparent; color: #245eea; padding: 10px 0; font-size: 15px; }.summary { padding: 20px; border-radius: 18px; background: linear-gradient(130deg,#3478f7,#1b4bd2); color: #fff; box-shadow: 0 8px 22px #245eea2b; }.summary-top { display: flex; align-items: center; justify-content: space-between; color: #dbe8ff; font-size: 13px; }.order-no { display: block; margin-top: 8px; font-size: 19px; overflow-wrap: anywhere; }.warehouse { margin-top: 6px; color: #dbe8ff; font-size: 14px; }.stats { display: grid; grid-template-columns: repeat(3,1fr); margin-top: 22px; }.stats div { display: grid; gap: 6px; padding-left: 12px; border-left: 1px solid #ffffff40; }.stats div:first-child { padding-left: 0; border: 0; }.stats span { color: #dbe8ff; font-size: 13px; }.stats b { font-size: 23px; line-height: 1.15; }.detail-heading { display: flex; align-items: center; justify-content: space-between; margin: 24px 2px 12px; }.detail-heading h2 { margin: 0; font-size: 17px; }.detail-heading span { color: #8a99ac; font-size: 13px; }.item-card { padding: 17px; margin-bottom: 10px; border: 1px solid #e8eef8; border-radius: 16px; background: #fff; }.item-head { display: flex; justify-content: space-between; gap: 12px; }.item-head strong { font-size: 15px; }.item-head p { margin: 6px 0 0; color: #8998ac; font-size: 13px; }.item-quantity { display: flex; align-items: baseline; gap: 4px; white-space: nowrap; color: #245eea; }.item-quantity b { font-size: 22px; }.item-quantity span { font-size: 13px; }.item-meta { display: flex; align-items: center; gap: 10px; margin-top: 14px; color: #8998ac; font-size: 13px; }.item-meta strong { flex: 1; color: #405570; font-weight: 500; overflow-wrap: anywhere; }.item-meta button { border: 0; border-radius: 8px; padding: 7px 12px; background: #edf4ff; color: #245eea; }.actions { display: flex; flex-wrap: wrap; gap: 8px; padding: 12px 0; }.primary { width: 100%; min-height: 48px; border: 0; border-radius: 12px; background: #245eea; color: #fff; font-size: 16px; font-weight: 600; }.place-group + .place-group { border-top: 1px solid #edf1f6; margin-top: 18px; padding-top: 10px; }.place-group h3 { margin: 8px 0; color: #243752; font-size: 15px; }.method { display: flex; align-items: center; gap: 10px; margin-top: 12px; }.method > span { flex: none; width: 48px; color: #718198; font-size: 13px; }.method :deep(.scan-trigger), .method :deep(.el-select), .method form { flex: 1; min-width: 0; width: 100%; }.method form { display: flex; gap: 8px; }.method input { box-sizing: border-box; flex: 1; width: 0; min-width: 0; padding: 10px; border: 1px solid #dce5f1; border-radius: 10px; font-size: 16px; }.method form button { border: 0; border-radius: 10px; padding: 0 14px; background: #245eea; color: #fff; }
</style>
