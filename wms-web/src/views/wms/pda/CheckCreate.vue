<script setup lang="ts">
import { ElMessage } from 'element-plus'
import request from '@/config/axios'
import { InventoryApi } from '@/api/wms/inventory'
import { CheckOrderApi } from '@/api/wms/order/check'
import { listArrivalBalances, listArrivalSerials, type ArrivalBalance } from '@/views/wms/inventory/components/arrival-api'
import SerialEditor from '@/views/wms/order/components/SerialEditor.vue'
const props = defineProps<{ warehouseId: number }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const code = ref(''), sku = ref<any>(), inventory = ref<any>(), balances = ref<ArrivalBalance[]>([]), selected = ref<number>(), actual = ref(0), bookSerials = ref<string[]>([]), actualSerials = ref<string[]>([]), loading = ref(false)
let serialRequestId = 0
const serialLoading = ref(false)
const serialReady = ref(false)
const find = async () => {
  serialRequestId++; serialLoading.value = false; serialReady.value = false
  const found = await request.get({ url: '/wms/item-sku/exact', params: { value: code.value.trim() } })
  if (found.length !== 1) { ElMessage.warning('未找到唯一物品规格'); return }
  sku.value = found[0]; selected.value = undefined; bookSerials.value = []; actualSerials.value = []
  const stocks = await InventoryApi.getInventoryList({ warehouseId: props.warehouseId })
  inventory.value = stocks.find((item) => item.skuId === sku.value.id)
  if (!inventory.value) { balances.value = []; ElMessage.warning('该仓库没有此物品库存，暂不支持新物品盘盈'); return }
  balances.value = await listArrivalBalances(props.warehouseId, sku.value.id)
  if (!balances.value.length) ElMessage.warning('没有可盘点的到货库存')
}
const choose = async (index: number) => {
  const id = ++serialRequestId; const row = balances.value[index]; actual.value = Number(row.quantity); serialLoading.value = true; serialReady.value = false
  bookSerials.value = []; actualSerials.value = []
  try { const serials = await listArrivalSerials(props.warehouseId, sku.value.id, row.locationId, row.batchNo, row.arrivalId ?? 0); if (id === serialRequestId) { bookSerials.value = serials; actualSerials.value = [...serials]; serialReady.value = true } } finally { if (id === serialRequestId) serialLoading.value = false }
}
const save = async () => {
  if (serialLoading.value || !serialReady.value) { ElMessage.warning('请先成功读取账面序列号'); return }
  if (!sku.value || !inventory.value || selected.value === undefined || actual.value == null || actual.value < 0) { ElMessage.warning('请选择库存并填写实盘数量'); return }
  const b = balances.value[selected.value]
  if (actualSerials.value.length > actual.value || new Set(actualSerials.value).size !== actualSerials.value.length) { ElMessage.warning('序列号重复或数量超过实盘数量'); return }
  loading.value = true
  try {
    await CheckOrderApi.createCheckOrder({ no: `PD${Date.now()}${Math.floor(Math.random()*1000)}`, orderTime: String(Date.now()), warehouseId: props.warehouseId, details: [{ inventoryId: inventory.value.id, skuId: sku.value.id, warehouseId: props.warehouseId, locationId: b.locationId ?? undefined, batchNo: b.batchNo ?? undefined, arrivalId: b.arrivalId ?? 0, quantity: Number(b.quantity), checkQuantity: actual.value, bookSerialNos: bookSerials.value, actualSerialNos: actualSerials.value }] })
    ElMessage.success('盘点草稿已保存'); emit('saved')
  } finally { loading.value = false }
}
</script>
<template>
  <section class="check-create"><button class="back" @click="emit('close')">‹ 返回</button><h2>新建盘点草稿</h2><p>扫描物品规格编号或条码</p><form @submit.prevent="find"><el-input v-model="code" placeholder="扫描或输入编号" /><el-button native-type="submit">查找</el-button></form>
    <template v-if="sku && balances.length"><p>{{ sku.itemName }} {{ sku.name }}</p><p>选择账面库存（库位／批次／到货）</p><el-select v-model="selected" placeholder="请选择" @change="choose"><el-option v-for="(b, index) in balances" :key="index" :value="index" :label="`库位 ${b.locationId ?? '未分配'} · 批次 ${b.batchNo || '未分配'} · 到货 ${b.arrivalId ?? '历史未分配'} · ${b.quantity}`" /></el-select>
    <template v-if="selected !== undefined"><p>账面数量：{{ balances[selected].quantity }}</p><p>实盘数量</p><el-input-number v-model="actual" :min="0" :precision="2" /><p>实盘序列号</p><SerialEditor v-model="actualSerials" /></template></template>
    <p><el-button type="primary" :loading="loading" @click="save">保存草稿</el-button></p></section>
</template>
<style scoped>.check-create { padding: 16px; }.check-create :deep(.el-select) { width: 100%; }.check-create form { display: flex; gap: 8px; }</style>
