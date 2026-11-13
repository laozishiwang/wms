<script setup lang="ts">
import { ElMessage } from 'element-plus'
import ScanButton from './ScanButton.vue'
import { MovementOrderApi } from '@/api/wms/order/movement'
import { WarehouseApi, type WarehouseVO } from '@/api/wms/md/warehouse'
import request from '@/config/axios'
import type { ItemSkuVO } from '@/api/wms/md/item/sku'
import BatchSelect from '@/views/wms/inventory/components/BatchSelect.vue'
import BatchLocationSelect from '@/views/wms/inventory/components/BatchLocationSelect.vue'
import InboundLocationSelect from '@/views/wms/inventory/components/InboundLocationSelect.vue'
import ArrivalSelect from '@/views/wms/inventory/components/ArrivalSelect.vue'
import SerialEditor from '@/views/wms/order/components/SerialEditor.vue'
import { validateArrivalBalances } from '@/views/wms/inventory/components/arrival-api'
const props = defineProps<{ sourceWarehouseId: number }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const warehouses = ref<WarehouseVO[]>([])
const target = ref<number>(props.sourceWarehouseId)
const code = ref('')
const sku = ref<ItemSkuVO>()
const row = reactive<any>({ quantity: 1, serialNos: [] })
const loading = ref(false)
const details = ref<any[]>([])
watch(target, () => { row.targetLocationId = undefined })
onMounted(async () => { warehouses.value = await WarehouseApi.getWarehouseSimpleList() })
let findRequestId = 0
const find = async () => {
  const requestId = ++findRequestId
  sku.value = undefined
  Object.assign(row, { batchNo: undefined, sourceLocationId: undefined, targetLocationId: undefined, arrivalId: undefined, quantity: 1, serialNos: [] })
  const found: ItemSkuVO[] = await request.get({ url: '/wms/item-sku/exact', params: { value: code.value.trim() } })
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
  loading.value = true
  try {
    await validateArrivalBalances(props.sourceWarehouseId, details.value, false)
    const no = `MV${Date.now()}${Math.floor(Math.random() * 1000)}`
    const items = details.value.map(({ skuLabel: _label, locationId: _location, ...detail }) => detail)
    await MovementOrderApi.createMovementOrder({ no, orderTime: String(Date.now()), sourceWarehouseId: props.sourceWarehouseId, targetWarehouseId: target.value, details: items })
    ElMessage.success('移库草稿已保存'); emit('saved')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') } finally { loading.value = false }
}
</script>
<template>
  <section class="movement-create">
    <button class="back" @click="emit('close')">‹ 返回</button><h2>新建移库草稿</h2>
    <div class="detail-panel"><p>目标仓库</p><el-select v-model="target" :disabled="details.length > 0"><el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="w.id" /></el-select></div>
    <div class="scan-panel"><p>选择物品</p><form @submit.prevent="find"><el-input v-model="code" placeholder="扫描或输入规格编号" /><el-button native-type="submit">查找</el-button></form><ScanButton @scan="(value: string) => { code = value; find() }" /></div>
    <div v-if="sku" class="detail-panel"><p>移库信息 · {{ sku.name }}（{{ sku.code }}）</p>
      <p>批次</p><BatchSelect v-model="row.batchNo" :warehouse-id="sourceWarehouseId" :sku-id="sku.id" @update:model-value="row.sourceLocationId = undefined; row.arrivalId = undefined" />
      <p>来源库位（留空为未分配）</p><BatchLocationSelect v-model="row.sourceLocationId" :warehouse-id="sourceWarehouseId" :sku-id="sku.id" :batch-no="row.batchNo" @update:model-value="row.arrivalId = undefined" />
      <p>到货记录</p><ArrivalSelect v-model="row.arrivalId" :warehouse-id="sourceWarehouseId" :sku-id="sku.id" :location-id="row.sourceLocationId" :batch-no="row.batchNo" />
      <p>目标库位（留空为未分配）</p><InboundLocationSelect v-model="row.targetLocationId" :warehouse-id="target" />
      <p>数量</p><el-input-number v-model="row.quantity" :min="0.001" :precision="3" />
      <p>序列号</p><SerialEditor v-model="row.serialNos" />
    </div><p class="action-row"><el-button class="add-action" :disabled="!sku" @click="add">添加明细</el-button><el-button v-if="sku" @click="sku = undefined; code = ''">取消当前物品</el-button></p>
    <h3 v-if="details.length">移库明细</h3><article v-for="(item, index) in details" :key="index"><strong>{{ item.skuLabel }}</strong><p>批次：{{ item.batchNo || '未分配' }} · 到货：{{ item.arrivalId }} · 数量：{{ item.quantity }}</p><p>库位：{{ item.sourceLocationId ?? '未分配' }} → {{ item.targetLocationId ?? '未分配' }}</p><el-button link @click="details.splice(index, 1)">删除</el-button></article>
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
.scan-panel :deep(.el-button), .detail-panel :deep(.el-button) { min-height: 44px; border-radius: 10px; }
:deep(.el-button--primary) { min-height: 46px; border-radius: 11px; font-weight: 650; }
article { border: 1px solid #e8eef8; color: #243854; line-height: 1.6; }
.action-row { display:flex; gap:10px; margin:16px 0; }.action-row :deep(.el-button) { margin:0; min-height:48px; padding:0 16px; border-radius:13px; font-weight:650; }.action-row :deep(.add-action) { flex:1; background:#eaf2ff; border-color:#dce9ff; color:#245eea; }.save-row :deep(.el-button) { width:100%; min-height:52px; border-radius:14px; background:#245eea; border-color:#245eea; box-shadow:0 8px 18px #245eea2b; }.scan-panel :deep(.el-button) { min-height:46px; font-weight:600; }.back { min-height:44px; }
</style>
