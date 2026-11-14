<script setup lang="ts">
import { ElMessage } from 'element-plus'
import ScanButton from './ScanButton.vue'
import SkuPicker from './SkuPicker.vue'
import { MovementOrderApi } from '@/api/wms/order/movement'
import { WarehouseApi, type WarehouseVO } from '@/api/wms/md/warehouse'
import request from '@/config/axios'
import type { ItemSkuVO } from '@/api/wms/md/item/sku'
import BatchSelect from '@/views/wms/inventory/components/BatchSelect.vue'
import PlacePicker from './PlacePicker.vue'
import ArrivalSelect from '@/views/wms/inventory/components/ArrivalSelect.vue'
import SerialEditor from '@/views/wms/order/components/SerialEditor.vue'
import { validateArrivalBalances } from '@/views/wms/inventory/components/arrival-api'
import { buildMovementDraftDetails } from './draft-details'
import { placePath, type StoragePlace } from './place'
import { checkPermi } from '@/utils/permission'
const props = defineProps<{ sourceWarehouseId: number; order?: any }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const warehouses = ref<WarehouseVO[]>([])
const target = ref<number>(props.sourceWarehouseId)
const code = ref('')
const pickerOpen = ref(false)
const sku = ref<ItemSkuVO>()
const row = reactive<any>({ quantity: 1, serialNos: [] })
const loading = ref(false)
const details = ref<any[]>([])
const editingLocation = ref<number>()
const targetLocation = ref<number>()
const showTargetLocation = (index: number) => {
  editingLocation.value = index
  targetLocation.value = details.value[index].targetLocationId ?? undefined
}
const saveTargetLocation = () => {
  if (editingLocation.value === undefined) return
  const detail = details.value[editingLocation.value]
  if (target.value === props.sourceWarehouseId && (detail.sourceLocationId ?? undefined) === targetLocation.value) {
    ElMessage.warning('同仓移库的来源和目标库位不能相同'); return
  }
  detail.targetLocationId = targetLocation.value
  editingLocation.value = undefined
}
const sourcePlaces = ref<StoragePlace[]>([])
const targetPlaces = ref<StoragePlace[]>([])
watch(() => props.sourceWarehouseId, async (id) => {
  if (!checkPermi(['wms:warehouse:query'])) return
  sourcePlaces.value = await request.get({ url: '/wms/storage-place/list', params: { warehouseId: id } })
}, { immediate: true })
watch(target, async (id) => {
  if (!checkPermi(['wms:warehouse:query'])) return
  const places: StoragePlace[] = await request.get({ url: '/wms/storage-place/list', params: { warehouseId: id } })
  if (id === target.value) targetPlaces.value = places
}, { immediate: true })
watch(target, () => { row.targetLocationId = undefined })
onMounted(async () => {
  warehouses.value = await WarehouseApi.getWarehouseSimpleList()
  if (props.order) {
    target.value = props.order.targetWarehouseId
    details.value = (props.order.details || []).map((item: any) => ({ ...item, originalQuantity: item.quantity, originalPrice: item.price, arrivalId: item.arrivalId ?? 0, locationId: item.sourceLocationId, serialNos: item.serialNos || [], skuLabel: `${item.itemName || ''} ${item.skuName || ''}（${item.skuCode || ''}）` }))
  }
})
let findRequestId = 0
const find = async (picked?: ItemSkuVO) => {
  const requestId = ++findRequestId
  sku.value = undefined
  Object.assign(row, { batchNo: undefined, sourceLocationId: undefined, targetLocationId: undefined, arrivalId: undefined, quantity: 1, serialNos: [] })
  const found: ItemSkuVO[] = picked ? [picked] : await request.get({ url: '/wms/item-sku/exact', params: { value: code.value.trim() } })
  if (requestId !== findRequestId) return
  if (found.length !== 1) { ElMessage.warning('未找到唯一物品规格'); return }
  sku.value = found[0]
}
const add = () => {
  if (!sku.value?.id || !target.value || row.arrivalId === undefined || !row.quantity || row.quantity <= 0) { ElMessage.warning('请选择物品、目标仓库和到货记录，并填写数量'); return }
  if (target.value === props.sourceWarehouseId && row.sourceLocationId === row.targetLocationId) { ElMessage.warning('同仓移库的来源和目标库位不能相同'); return }
  if (row.serialNos.length > row.quantity || new Set(row.serialNos).size !== row.serialNos.length || details.value.some(d => d.serialNos.some((v: string) => row.serialNos.includes(v)))) { ElMessage.warning('序列号重复或数量超过移库数量'); return }
  details.value.push({ ...row, serialNos: [...row.serialNos], skuId: sku.value.id, skuLabel: `${sku.value.itemName || ''} ${sku.value.name || ''}（${sku.value.code}）`, sourceWarehouseId: props.sourceWarehouseId, targetWarehouseId: target.value, locationId: row.sourceLocationId })
  sku.value = undefined; code.value = ''
}
const save = async () => {
  if (sku.value) { ElMessage.warning('请先添加或取消当前物品'); return }
  if (!details.value.length) { ElMessage.warning('请先添加移库明细'); return }
  if (target.value === props.sourceWarehouseId && details.value.some((item) => (item.sourceLocationId ?? undefined) === (item.targetLocationId ?? undefined))) { ElMessage.warning('同仓移库的来源和目标库位不能相同'); return }
  if (details.value.some((item) => item.id && item.totalPrice != null && item.price == null && Number(item.quantity) !== Number(item.originalQuantity))) { ElMessage.warning('修改数量前请先填写该明细单价'); return }
  const serials = details.value.flatMap((item) => item.serialNos || [])
  if (details.value.some((item) => !item.quantity || item.quantity <= 0 || (item.serialNos?.length || 0) > item.quantity) || new Set(serials).size !== serials.length) { ElMessage.warning('请检查移库数量和序列号'); return }
  loading.value = true
  try {
    await validateArrivalBalances(props.sourceWarehouseId, details.value, false)
    const no = `MV${Date.now()}${Math.floor(Math.random() * 1000)}`
    const items = buildMovementDraftDetails(details.value)
    const data = { ...props.order, no: props.order?.no || no, orderTime: props.order?.orderTime || String(Date.now()), sourceWarehouseId: props.sourceWarehouseId, targetWarehouseId: target.value, details: items }
    if (props.order) await MovementOrderApi.updateMovementOrder(data); else await MovementOrderApi.createMovementOrder(data)
    ElMessage.success('移库草稿已保存'); emit('saved')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') } finally { loading.value = false }
}
</script>
<template>
  <section class="movement-create">
    <button class="back" @click="emit('close')">‹ 返回</button><h2>{{ order ? '修改' : '新建' }}移库草稿</h2>
    <div class="detail-panel"><p>目标仓库</p><el-select v-model="target" :disabled="details.length > 0"><el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="w.id" /></el-select></div>
    <div class="scan-panel"><p>选择物品</p><form @submit.prevent="find()"><el-input v-model="code" placeholder="扫描或输入规格编号" /><el-button native-type="submit">查找</el-button></form><div class="picker-actions"><ScanButton @scan="(value: string) => { code = value; find() }" /><el-button @click="pickerOpen = true">选择物品</el-button></div><SkuPicker v-model="pickerOpen" @select="find" /></div>
    <div v-if="sku" class="detail-panel"><p>移库信息 · {{ sku.name }}（{{ sku.code }}）</p>
      <p>批次</p><BatchSelect v-model="row.batchNo" :warehouse-id="sourceWarehouseId" :sku-id="sku.id" @update:model-value="row.sourceLocationId = undefined; row.arrivalId = undefined" />
      <p>来源位置</p><PlacePicker v-model="row.sourceLocationId" :warehouse-id="sourceWarehouseId" :sku-id="sku.id" :batch-no="row.batchNo" stock-only @update:model-value="row.arrivalId = undefined" />
      <p>到货记录</p><ArrivalSelect v-model="row.arrivalId" :warehouse-id="sourceWarehouseId" :sku-id="sku.id" :location-id="row.sourceLocationId" :batch-no="row.batchNo" />
      <p>目标位置</p><PlacePicker v-model="row.targetLocationId" :warehouse-id="target" />
      <p>数量</p><el-input-number v-model="row.quantity" :min="0.01" :precision="2" />
      <p>序列号</p><SerialEditor v-model="row.serialNos" />
    </div><p class="action-row"><el-button class="add-action" :disabled="!sku" @click="add">添加明细</el-button><el-button v-if="sku" @click="sku = undefined; code = ''">取消当前物品</el-button></p>
    <h3 v-if="details.length">移库明细</h3><article v-for="(item, index) in details" :key="index" class="move-item"><div class="move-item-head"><strong>{{ item.skuLabel }}</strong><el-button link type="danger" @click="details.splice(index, 1)">删除</el-button></div><p>批次：{{ item.batchNo || '无批次' }} · {{ item.arrivalId ? `到货 #${item.arrivalId}` : '历史未分配' }}</p><div class="move-route"><div><span>来源</span><strong>{{ placePath(sourcePlaces, item.sourceLocationId) }}</strong></div><div><span>目标</span><strong>{{ placePath(targetPlaces, item.targetLocationId) }}</strong><el-button link type="primary" @click="showTargetLocation(index)">更换</el-button></div></div><div class="move-quantity"><span>移库数量</span><el-input-number v-model="item.quantity" :min="0.01" :precision="2" :controls="false" /></div><details class="move-extra"><summary>单价与序列号</summary><p>单价</p><el-input-number v-model="item.price" :min="0" :precision="2" :controls="false" /><p>序列号 <SerialEditor v-model="item.serialNos" /></p></details></article>
    <el-dialog :model-value="editingLocation !== undefined" title="更换目标库位" width="min(520px, 95vw)" append-to-body @close="editingLocation = undefined"><PlacePicker v-model="targetLocation" :warehouse-id="target" /><template #footer><el-button @click="editingLocation = undefined">取消</el-button><el-button type="primary" @click="saveTargetLocation">确定</el-button></template></el-dialog>
    <p class="save-row"><el-button type="primary" :loading="loading" @click="save">保存草稿（{{ details.length }} 条）</el-button></p>
  </section>
</template>
<style scoped>
.movement-create, .check-create { min-height: 100%; padding: 18px 16px 100px; color: #16243a; background: #f3f6fc; }
h2 { margin: 12px 0 20px; font-size: 22px; }
p { margin: 14px 0 7px; font-size: 14px; font-weight: 600; color: #52637a; }
.back { border: 0; background: transparent; color: #3266d9; padding: 8px 0; font-size: 16px; }
form { display:flex; gap:8px; margin-bottom: 10px; }
:deep(.el-select), :deep(.el-input-number) { width:100%; }
:deep(.el-input__wrapper), :deep(.el-select__wrapper) { min-height: 46px; border-radius: 10px; }
article { margin: 12px 0; padding: 16px; border-radius: 14px; background: white; box-shadow: 0 5px 20px #2137580a; }
article p { font-weight: normal; }
.scan-panel, .detail-panel { margin: 14px 0; padding: 18px; background: #fff; border: 1px solid #e9eef7; border-radius: 18px; box-shadow: 0 8px 24px rgba(30, 59, 100, .06); }
.scan-panel p:first-child, .detail-panel p:first-child { margin-top: 0; color: #182b47; font-size: 16px; }
.scan-panel form { margin-bottom: 8px; }
.picker-actions { display:flex; gap:8px; }.picker-actions > * { flex:1; }
.move-item-head,.move-quantity { display:flex; align-items:center; justify-content:space-between; gap:12px; }.move-item-head strong { min-width:0; overflow-wrap:anywhere; }.move-route { margin:12px 0; padding:4px 12px; border-radius:12px; background:#f5f8fd; }.move-route div { display:flex; align-items:center; gap:10px; min-height:42px; }.move-route div + div { border-top:1px solid #e5edf8; }.move-route span { flex:none; color:#8191a8; font-size:13px; }.move-route strong { flex:1; font-size:14px; font-weight:500; }.move-quantity { margin-top:14px; color:#52637a; }.move-quantity :deep(.el-input-number) { width:130px; }.move-extra { margin-top:14px; padding-top:12px; border-top:1px solid #edf1f6; }.move-extra summary { color:#627693; font-size:13px; cursor:pointer; }
.scan-panel :deep(.el-button), .detail-panel :deep(.el-button) { min-height: 44px; border-radius: 10px; }
:deep(.el-button--primary) { min-height: 46px; border-radius: 11px; font-weight: 650; }
article { border: 1px solid #e8eef8; color: #243854; line-height: 1.6; }
.action-row { display:flex; gap:10px; margin:16px 0; }.action-row :deep(.el-button) { margin:0; min-height:48px; padding:0 16px; border-radius:13px; font-weight:650; }.action-row :deep(.add-action) { flex:1; background:#eaf2ff; border-color:#dce9ff; color:#245eea; }.save-row :deep(.el-button) { width:100%; min-height:52px; border-radius:14px; background:#245eea; border-color:#245eea; box-shadow:0 8px 18px #245eea2b; }.scan-panel :deep(.el-button) { min-height:46px; font-weight:600; }.back { min-height:44px; }
</style>
