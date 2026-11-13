<script setup lang="ts">
import { ElMessage } from 'element-plus'
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
const save = async () => {
  if (!sku.value?.id || !target.value || row.arrivalId === undefined || !row.quantity || row.quantity <= 0) { ElMessage.warning('请选择物品、目标仓库和到货记录，并填写数量'); return }
  if (target.value === props.sourceWarehouseId && row.sourceLocationId === row.targetLocationId) { ElMessage.warning('同仓移库的来源和目标库位不能相同'); return }
  if (row.serialNos.length > row.quantity || new Set(row.serialNos).size !== row.serialNos.length) { ElMessage.warning('序列号重复或数量超过移库数量'); return }
  const detail = { ...row, skuId: sku.value.id, sourceWarehouseId: props.sourceWarehouseId, targetWarehouseId: target.value, locationId: row.sourceLocationId }
  loading.value = true
  try {
    await validateArrivalBalances(props.sourceWarehouseId, [detail], false)
    const no = `MV${Date.now()}${Math.floor(Math.random() * 1000)}`
    await MovementOrderApi.createMovementOrder({ no, orderTime: String(Date.now()), sourceWarehouseId: props.sourceWarehouseId, targetWarehouseId: target.value, details: [detail] })
    ElMessage.success('移库草稿已保存'); emit('saved')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') } finally { loading.value = false }
}
</script>
<template>
  <section class="movement-create">
    <button class="back" @click="emit('close')">‹ 返回</button><h2>新建移库草稿</h2>
    <p>目标仓库</p><el-select v-model="target"><el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="w.id" /></el-select>
    <p>扫描物品规格编号或条码</p><form @submit.prevent="find"><el-input v-model="code" placeholder="扫描或输入编号" /><el-button native-type="submit">查找</el-button></form>
    <template v-if="sku"><p>{{ sku.name }}（{{ sku.code }}）</p>
      <p>批次</p><BatchSelect v-model="row.batchNo" :warehouse-id="sourceWarehouseId" :sku-id="sku.id" @update:model-value="row.sourceLocationId = undefined; row.arrivalId = undefined" />
      <p>来源库位（留空为未分配）</p><BatchLocationSelect v-model="row.sourceLocationId" :warehouse-id="sourceWarehouseId" :sku-id="sku.id" :batch-no="row.batchNo" @update:model-value="row.arrivalId = undefined" />
      <p>到货记录</p><ArrivalSelect v-model="row.arrivalId" :warehouse-id="sourceWarehouseId" :sku-id="sku.id" :location-id="row.sourceLocationId" :batch-no="row.batchNo" />
      <p>目标库位（留空为未分配）</p><InboundLocationSelect v-model="row.targetLocationId" :warehouse-id="target" />
      <p>数量</p><el-input-number v-model="row.quantity" :min="0.001" :precision="3" />
      <p>序列号</p><SerialEditor v-model="row.serialNos" />
    </template><p><el-button type="primary" :loading="loading" @click="save">保存草稿</el-button></p>
  </section>
</template>
<style scoped>.movement-create { padding: 16px; } .movement-create :deep(.el-select) { width: 100%; } .movement-create form { display:flex; gap:8px; }</style>
