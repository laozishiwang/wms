<script setup lang="ts">
import { ElMessage } from 'element-plus'
import ScanButton from './ScanButton.vue'
import SkuPicker from './SkuPicker.vue'
import type { ItemSkuVO } from '@/api/wms/md/item/sku'
import PlacePicker from './PlacePicker.vue'
import request from '@/config/axios'
import { InventoryApi } from '@/api/wms/inventory'
import { CheckOrderApi } from '@/api/wms/order/check'
import { listArrivalBalances, listArrivalSerials, type ArrivalBalance } from '@/views/wms/inventory/components/arrival-api'
import SerialEditor from '@/views/wms/order/components/SerialEditor.vue'
import { buildCheckDraftDetails } from './draft-details'
const props = defineProps<{ warehouseId: number; order?: any }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const code = ref(''), sku = ref<any>(), inventory = ref<any>(), balances = ref<ArrivalBalance[]>([]), selected = ref<number>(), actual = ref(0), bookSerials = ref<string[]>([]), actualSerials = ref<string[]>([]), loading = ref(false)
const details = ref<any[]>([])
const pickerOpen = ref(false)
onMounted(() => {
  if (props.order) details.value = (props.order.details || []).map((item: any) => ({ ...item, arrivalId: item.arrivalId ?? 0, bookSerialNos: item.bookSerialNos || [], actualSerialNos: item.actualSerialNos || [], label: `${item.itemName || ''} ${item.skuName || ''}` }))
})
const locationFilter = ref<number>()
const balanceOptions = computed(() => balances.value.map((balance, index) => ({ balance, index })).filter(({ balance }) => locationFilter.value === undefined || balance.locationId === locationFilter.value))
let serialRequestId = 0
let findRequestId = 0
const serialLoading = ref(false)
const serialReady = ref(false)
const setLocationFilter = (value?: number) => { locationFilter.value = value; selected.value = undefined; serialRequestId++; serialLoading.value = false; serialReady.value = false }
const find = async (picked?: ItemSkuVO) => {
  const findId = ++findRequestId; serialRequestId++; serialLoading.value = false; serialReady.value = false
  sku.value = undefined; inventory.value = undefined; balances.value = []; selected.value = undefined; locationFilter.value = undefined
  const found = picked ? [picked] : await request.get({ url: '/wms/item-sku/exact', params: { value: code.value.trim() } })
  if (findId !== findRequestId) return
  if (found.length !== 1) { ElMessage.warning('未找到唯一物品规格'); return }
  sku.value = found[0]; selected.value = undefined; bookSerials.value = []; actualSerials.value = []
  const stocks = await InventoryApi.getInventoryList({ warehouseId: props.warehouseId })
  if (findId !== findRequestId) return
  inventory.value = stocks.find((item) => item.skuId === sku.value.id)
  if (!inventory.value) { balances.value = [{ quantity: 0, arrivalId: 0 } as ArrivalBalance]; selected.value = 0; actual.value = 0; bookSerials.value = []; actualSerials.value = []; serialReady.value = true; return }
  const rows = await listArrivalBalances(props.warehouseId, sku.value.id)
  if (findId !== findRequestId) return
  balances.value = rows
  if (!rows.length && Number(inventory.value.quantity) === 0) { balances.value = [{ quantity: 0, arrivalId: 0 } as ArrivalBalance]; selected.value = 0; actual.value = 0; bookSerials.value = []; actualSerials.value = []; serialReady.value = true }
  else if (!rows.length) ElMessage.warning('没有可盘点的到货库存')
}
const choose = async (index: number) => {
  const id = ++serialRequestId; const row = balances.value[index]; actual.value = Number(row.quantity); serialLoading.value = true; serialReady.value = false
  bookSerials.value = []; actualSerials.value = []
  try { const serials = await listArrivalSerials(props.warehouseId, sku.value.id, row.locationId, row.batchNo, row.arrivalId ?? 0); if (id === serialRequestId) { bookSerials.value = serials; actualSerials.value = [...serials]; serialReady.value = true } } finally { if (id === serialRequestId) serialLoading.value = false }
}
const save = async () => {
  if (serialLoading.value || !serialReady.value) { ElMessage.warning('请先成功读取账面序列号'); return }
  if (!sku.value || selected.value === undefined || actual.value == null || actual.value < 0) { ElMessage.warning('请选择库存并填写实盘数量'); return }
  const b = balances.value[selected.value]
  if (actualSerials.value.length > actual.value || new Set(actualSerials.value).size !== actualSerials.value.length) { ElMessage.warning('序列号重复或数量超过实盘数量'); return }
  const detail = { inventoryId: inventory.value?.id, skuId: sku.value.id, warehouseId: props.warehouseId, locationId: b.locationId ?? undefined, batchNo: b.batchNo ?? undefined, arrivalId: b.arrivalId ?? 0, quantity: Number(b.quantity), checkQuantity: actual.value, bookSerialNos: [...bookSerials.value], actualSerialNos: [...actualSerials.value], label: `${sku.value.itemName} ${sku.value.name}` }
  if (details.value.some((d) => d.skuId === detail.skuId && d.locationId === detail.locationId && d.batchNo === detail.batchNo && d.arrivalId === detail.arrivalId)) { ElMessage.warning('该库存维度已添加'); return }
  details.value.push(detail); sku.value = undefined; selected.value = undefined; serialReady.value = false; code.value = ''
}
const submit = async () => {
  if (sku.value) { ElMessage.warning('当前物品尚未添加到明细，请先添加或取消当前物品'); return }
  if (!details.value.length) { ElMessage.warning('请先添加盘点明细'); return }
  if (details.value.some((item) => item.checkQuantity == null || Number(item.checkQuantity) < 0 || (item.actualSerialNos?.length || 0) > Number(item.checkQuantity))) { ElMessage.warning('请检查实盘数量和序列号'); return }
  loading.value = true
  try {
    const data = { ...props.order, no: props.order?.no || `PD${Date.now()}${Math.floor(Math.random()*1000)}`, orderTime: props.order?.orderTime || String(Date.now()), warehouseId: props.warehouseId, details: buildCheckDraftDetails(details.value) }
    if (props.order) await CheckOrderApi.updateCheckOrder(data); else await CheckOrderApi.createCheckOrder(data)
    ElMessage.success('盘点草稿已保存'); emit('saved')
  } finally { loading.value = false }
}
</script>
<template>
  <section class="check-create"><button class="back" @click="emit('close')">‹ 返回</button><h2>{{ order ? '修改' : '新建' }}盘点草稿</h2><div class="scan-panel"><p>选择物品</p><form @submit.prevent="find()"><el-input v-model="code" placeholder="扫描或输入规格编号" /><el-button native-type="submit">查找</el-button></form><div class="picker-actions"><ScanButton @scan="(value: string) => { code = value; find() }" /><el-button @click="pickerOpen = true">选择物品</el-button></div><SkuPicker v-model="pickerOpen" @select="find" /></div>
    <div v-if="sku && balances.length" class="detail-panel"><p>盘点信息 · {{ sku.itemName }} {{ sku.name }}</p><p>按位置筛选（可选）</p><PlacePicker :model-value="locationFilter" :warehouse-id="warehouseId" @update:model-value="setLocationFilter" /><p>选择账面库存（库位／批次／到货）</p><el-select v-model="selected" placeholder="请选择" @change="choose"><el-option v-for="{ balance: b, index } in balanceOptions" :key="index" :value="index" :label="`库位 ${b.locationId ?? '未分配'} · 批次 ${b.batchNo || '未分配'} · 到货 ${b.arrivalId ?? '历史未分配'} · ${b.quantity}`" /></el-select>
    <template v-if="selected !== undefined"><p>账面数量：{{ balances[selected].quantity }}</p><p>实盘数量</p><el-input-number v-model="actual" :min="0" :precision="2" /><p>实盘序列号</p><SerialEditor v-model="actualSerials" /></template></div>
    <p class="action-row"><el-button class="add-action" @click="save">添加明细</el-button><el-button v-if="sku" @click="sku = undefined; serialRequestId++; serialReady = false">取消当前物品</el-button></p><h3 v-if="details.length">盘点明细</h3><article v-for="(item, index) in details" :key="index"><strong>{{ item.label }}</strong><p>账面数量 {{ item.quantity }}</p><p>实盘数量</p><el-input-number v-model="item.checkQuantity" :min="0" :precision="2" :controls="false" /><p>实盘序列号 <SerialEditor v-model="item.actualSerialNos" /></p><el-button link type="danger" @click="details.splice(index, 1)">删除明细</el-button></article><p class="save-row"><el-button type="primary" :loading="loading" @click="submit">保存草稿</el-button></p></section>
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
.scan-panel :deep(.el-button), .detail-panel :deep(.el-button) { min-height: 44px; border-radius: 10px; }
:deep(.el-button--primary) { min-height: 46px; border-radius: 11px; font-weight: 650; }
article { border: 1px solid #e8eef8; color: #243854; line-height: 1.6; }
.action-row { display:flex; gap:10px; margin:16px 0; }.action-row :deep(.el-button) { margin:0; min-height:48px; padding:0 16px; border-radius:13px; font-weight:650; }.action-row :deep(.add-action) { flex:1; background:#eaf2ff; border-color:#dce9ff; color:#245eea; }.save-row :deep(.el-button) { width:100%; min-height:52px; border-radius:14px; background:#245eea; border-color:#245eea; box-shadow:0 8px 18px #245eea2b; }.scan-panel :deep(.el-button) { min-height:46px; font-weight:600; }.back { min-height:44px; }
</style>
