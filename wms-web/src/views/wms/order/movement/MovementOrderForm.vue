<!-- WMS 移库单表单 -->
<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="1280px">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="98px"
    >
      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="移库单号" prop="no">
            <el-input v-model="formData.no" maxlength="64" placeholder="请输入移库单号" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="来源仓库" prop="sourceWarehouseId">
            <WarehouseSelect
              v-model="formData.sourceWarehouseId"
              @change="handleSourceWarehouseChange"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="目标仓库" prop="targetWarehouseId">
            <WarehouseSelect
              v-model="formData.targetWarehouseId"
              @change="handleTargetWarehouseChange"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="单据日期" prop="orderTime">
            <el-date-picker
              v-model="formData.orderTime"
              class="!w-1/1"
              placeholder="请选择单据日期"
              type="date"
              value-format="x"
            />
          </el-form-item>
        </el-col>
        <el-col :span="16">
          <el-form-item label="备注" prop="remark">
            <el-input
              v-model="formData.remark"
              maxlength="255"
              placeholder="请输入备注"
              :rows="3"
              type="textarea"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <DetailEntryMethods title="移库明细">
        <template #select>
        <el-tooltip content="请先选择来源仓库和目标仓库" :disabled="canAddDetail" placement="top">
          <span>
            <el-button :disabled="!canAddDetail" plain type="primary" @click="handleAddDetail">
              <Icon class="mr-5px" icon="ep:plus" />
              添加商品
            </el-button>
          </span>
        </el-tooltip>
        </template>
        <template #scan>

        <ScanItemScope v-model="scanSku" />

          <ScanButton variant="standard" label="扫码或输入" continuous :disabled="!canAddDetail" :process-scan="queueScan" />
        </template>
      </DetailEntryMethods>

      <el-table
        :data="formData.details"
        :summary-method="getDetailSummaries"
        border
        empty-text="暂无商品明细"
        show-summary
      >
        <el-table-column label="商品信息" min-width="210">
          <template #default="{ row }">
            <div>{{ row.itemName || '-' }}</div>
            <div v-if="row.itemCode" class="text-12px text-gray-500">
              商品编号：{{ row.itemCode }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="规格信息" min-width="210">
          <template #default="{ row }">
            <div>{{ row.skuName || '-' }}</div>
            <div v-if="row.skuCode" class="text-12px text-gray-500">
              规格编号：{{ row.skuCode }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="来源库位" min-width="200"><template #default="{ row }"><BatchLocationSelect v-model="row.sourceLocationId" :warehouse-id="formData.sourceWarehouseId" :sku-id="row.skuId" :batch-no="row.batchNo" @update:model-value="row.arrivalId = undefined" /></template></el-table-column>
        <el-table-column label="到货记录" min-width="230"><template #default="{ row }"><ArrivalSelect v-model="row.arrivalId" :warehouse-id="formData.sourceWarehouseId" :sku-id="row.skuId" :location-id="row.sourceLocationId" :batch-no="row.batchNo" /></template></el-table-column>
        <el-table-column label="目标库位" min-width="200"><template #default="{ row }"><InboundLocationSelect v-model="row.targetLocationId" :warehouse-id="formData.targetWarehouseId" /></template></el-table-column>
        <el-table-column label="序列号" width="130"><template #default="{ row }"><SerialEditor v-model="row.serialNos" /></template></el-table-column>
        <el-table-column label="批次号" min-width="160">
          <template #default="{ row }">
            <BatchSelect v-model="row.batchNo" :warehouse-id="formData.sourceWarehouseId" :sku-id="row.skuId" @change="setBatchDates(row, $event)" />
          </template>
        </el-table-column>
        <el-table-column align="right" label="可用库存" width="120">
          <template #default="{ row }">{{ formatQuantity(row.availableQuantity) || '-' }}</template>
        </el-table-column>
        <el-table-column label="移库数量" prop="quantity" width="160">
          <template #default="{ row }">
            <el-input-number
              v-model="row.quantity"
              :controls="false"
              :min="0"
              :precision="QUANTITY_PRECISION"
              class="!w-1/1"
              placeholder="数量"
              @change="handleDetailQuantityChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="单价(元)" prop="price" width="160">
          <template #default="{ row }">
            <el-input-number
              v-model="row.price"
              :controls="false"
              :min="0"
              :precision="PRICE_PRECISION"
              class="!w-1/1"
              placeholder="单价"
              @change="handleDetailPriceChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="金额(元)" prop="totalPrice" width="160">
          <template #default="{ row }">
            <el-input-number
              v-model="row.totalPrice"
              :controls="false"
              :min="0"
              :precision="PRICE_PRECISION"
              class="!w-1/1"
              placeholder="金额"
              @change="handleDetailTotalPriceChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column align="center" label="操作" width="120">
          <template #default="{ row, $index }">
            <el-button link type="primary" @click="copyDetail(row)">复制</el-button>
            <el-button link type="danger" @click="handleDeleteDetail($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <InventorySelect
        ref="inventorySelectRef"
        :warehouse-id="formData.sourceWarehouseId"
        @change="handleSelectInventory"
      />
    </el-form>
    <template #footer>
      <div class="flex items-center justify-between">
        <div>
          <el-button
            v-if="isSavedPrepareOrder && !approvalEnabled"
            v-hasPermi="['wms:movement-order:complete']"
            :disabled="formLoading || scanLoading"
            type="success"
            @click="handleComplete"
          >
            完成移库
          </el-button>
          <el-button
            v-if="isSavedPrepareOrder"
            v-hasPermi="['wms:movement-order:cancel']"
            :disabled="formLoading || scanLoading"
            type="danger"
            @click="handleCancel"
          >
            作废
          </el-button>
        </div>
        <div>
          <el-button
            v-if="isPrepareOrder"
            :disabled="formLoading || scanLoading"
            type="primary"
            @click="submitForm"
          >
            保存
          </el-button>
          <el-button @click="dialogVisible = false">取 消</el-button>
        </div>
      </div>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import { ApprovalApi } from '@/api/wms/approval'
const approvalEnabled = ref(true)
import { FormRules } from 'element-plus'
import SerialEditor from '@/views/wms/order/components/SerialEditor.vue'
import ArrivalSelect from '@/views/wms/inventory/components/ArrivalSelect.vue'
import { listArrivalBalances, validateArrivalBalances } from '@/views/wms/inventory/components/arrival-api'
import { matchesScanAttributes } from '@/views/wms/pda/scan-attributes'
import request from '@/config/axios'
import DetailEntryMethods from '@/views/wms/order/components/DetailEntryMethods.vue'
import type { ItemSkuVO } from '@/api/wms/md/item/sku'
import ScanButton from '@/views/wms/pda/ScanButton.vue'
import ScanItemScope from '@/views/wms/order/components/ScanItemScope.vue'
import { MovementOrderApi, MovementOrderVO } from '@/api/wms/order/movement'
import { MovementOrderDetailVO } from '@/api/wms/order/movement/detail'
import InventorySelect, {
  InventorySelectRow
} from '@/views/wms/inventory/components/InventorySelect.vue'
import BatchSelect from '@/views/wms/inventory/components/BatchSelect.vue'
import BatchLocationSelect from '@/views/wms/inventory/components/BatchLocationSelect.vue'
import InboundLocationSelect from '@/views/wms/inventory/components/InboundLocationSelect.vue'
import type { BatchInventoryVO } from '@/api/wms/inventory/batch'
import WarehouseSelect from '@/views/wms/md/warehouse/components/WarehouseSelect.vue'
import { OrderStatusEnum, OrderUpdateStatusList } from '@/views/wms/utils/constants'
import {
  dividePrice,
  formatQuantity,
  formatSumPrice,
  formatSumQuantity,
  multiplyPrice,
  PRICE_PRECISION,
  QUANTITY_PRECISION
} from '@/views/wms/utils/format'
import { generateOrderNo } from '@/views/wms/utils/order'

/** WMS 移库单表单 */
defineOptions({ name: 'WmsMovementOrderForm' })

const { t } = useI18n()
const message = useMessage()

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')
const originalFormData = ref('')
const formData = ref<MovementOrderVO>({
  id: undefined,
  no: undefined,
  orderTime: undefined,
  status: OrderStatusEnum.PREPARE,
  sourceWarehouseId: undefined,
  targetWarehouseId: undefined,
  remark: undefined,
  details: []
})
const formRules = reactive<FormRules>({
  no: [{ required: true, message: '移库单号不能为空', trigger: 'blur' }],
  orderTime: [{ required: true, message: '单据日期不能为空', trigger: 'change' }],
  sourceWarehouseId: [{ required: true, message: '来源仓库不能为空', trigger: 'change' }],
  targetWarehouseId: [{ required: true, message: '目标仓库不能为空', trigger: 'change' }]
})
const formRef = ref()
const inventorySelectRef = ref()
const scanSku = ref<ItemSkuVO>()
const pendingScans = ref(0)
const scanLoading = computed(() => pendingScans.value > 0)
let scanQueue = Promise.resolve()
let scanFormVersion = 0

const isPrepareOrder = computed(
  () =>
    !formData.value.id ||
    (formData.value.status !== undefined && OrderUpdateStatusList.includes(formData.value.status))
)
const isSavedPrepareOrder = computed(
  () =>
    !!formData.value.id &&
    formData.value.status !== undefined &&
    OrderUpdateStatusList.includes(formData.value.status)
)
const canAddDetail = computed(
  () => !!formData.value.sourceWarehouseId && !!formData.value.targetWarehouseId
)

/** 打开弹窗 */
const open = async (type: string, id?: number) => {
  scanFormVersion++
  scanSku.value = undefined
  approvalEnabled.value = (await ApprovalApi.config()).enabled
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  resetForm()
  if (id) {
    formLoading.value = true
    try {
      const order = await MovementOrderApi.getMovementOrder(id)
      formData.value = { ...order, details: normalizeDetails(order.details || []) }
    } finally {
      formLoading.value = false
    }
  }
  originalFormData.value = JSON.stringify(buildSubmitData())
}
defineExpose({ open })

/** 构建移库明细 */
function buildDetail(inventory: InventorySelectRow): MovementOrderDetailVO {
  return {
    id: undefined,
    itemId: inventory.itemId,
    itemCode: inventory.itemCode,
    itemName: inventory.itemName,
    unit: inventory.unit,
    skuId: inventory.skuId,
    skuCode: inventory.skuCode,
    skuName: inventory.skuName,
    sourceWarehouseId: inventory.warehouseId,
    sourceWarehouseName: inventory.warehouseName,
    targetWarehouseId: formData.value.targetWarehouseId,
    quantity: undefined,
    availableQuantity: inventory.availableQuantity,
    price: undefined,
    totalPrice: undefined
  }
}

const queueScan = (raw: string) => {
  const value = raw.trim(), source = formData.value.sourceWarehouseId, target = formData.value.targetWarehouseId, formVersion = scanFormVersion
  if (!value || !source || !target) return Promise.resolve({ ok: false, message: '请先选择仓库并输入物品码' })
  const scopeId = scanSku.value?.id
  pendingScans.value++
  const result = scanQueue.then(async () => {
    if (!dialogVisible.value || formVersion !== scanFormVersion || scopeId !== scanSku.value?.id || source !== formData.value.sourceWarehouseId || target !== formData.value.targetWarehouseId) return { ok: false, message: '仓库或单据已变更，请重新扫码' }
    let skus: ItemSkuVO[] = await request.get({ url: '/wms/item-sku/exact', params: { value } })
    let parsed: Record<string, string> | null = null
    if (!skus.length) {
      parsed = await request.get({ url: '/wms/scan-rule/resolve', params: { value, skuId: scopeId } })
      if (parsed) skus = await request.get({ url: '/wms/item-sku/exact', params: { value: parsed.skuCode } })
    }
    if (skus.length !== 1 || !skus[0].id) return { ok: false, message: '未找到唯一物品规格' }
    if (scopeId && skus[0].id !== scopeId) return { ok: false, message: '扫描的物品与指定扫码物品不一致' }
    const sku = skus[0], quantity = parsed?.quantity ? Number(parsed.quantity) : 1
    if (!Number.isFinite(quantity) || quantity <= 0 || !Number.isInteger(Number((quantity * 100).toFixed(6)))) return { ok: false, message: '二维码数量最多支持两位小数' }
    const balances = await listArrivalBalances(source, sku.id!)
    if (!dialogVisible.value || scopeId !== scanSku.value?.id || formVersion !== scanFormVersion || formData.value.sourceWarehouseId !== source || formData.value.targetWarehouseId !== target) return { ok: false, message: '仓库或单据已变更，请重新扫码' }
    const candidates = balances.filter(item => Number(item.quantity) > 0 && matchesScanAttributes({ ...item }, parsed || {}))
    if (candidates.length !== 1) return { ok: false, message: candidates.length ? '匹配到多条来源库存，请手动选择' : '没有匹配的来源库存' }
    const stock = candidates[0], arrivalId = stock.arrivalId ?? 0
    const rows = (formData.value.details || []).filter(item => item.skuId === sku.id && (item.sourceLocationId ?? null) === stock.locationId && (item.batchNo || '') === (stock.batchNo || '') && item.arrivalId === arrivalId)
    if (rows.some(item => item.serialNos?.length)) return { ok: false, message: '明细含序列号，请手动核对' }
    const total = rows.reduce((sum, row) => sum + Number(row.quantity || 0), 0) + quantity
    if (Number(total.toFixed(2)) > Number(stock.quantity)) return { ok: false, message: '数量超过可用库存' }
    formData.value.details = [...(formData.value.details || []), { itemId: sku.itemId, itemCode: sku.itemCode, itemName: sku.itemName, unit: sku.unit, skuId: sku.id, skuCode: sku.code, skuName: sku.name, sourceWarehouseId: source, targetWarehouseId: target, sourceLocationId: stock.locationId ?? undefined, batchNo: stock.batchNo || undefined, arrivalId, quantity }]
    return { ok: true, message: `扫码成功：${value}` }
  }).catch((error) => ({ ok: false, message: typeof error === 'string' && error !== 'error' ? error : error instanceof Error ? error.message : '扫码处理失败，请重试' })).finally(() => { pendingScans.value-- })
  scanQueue = result.then(() => {})
  return result
}

function normalizeDetails(details: MovementOrderDetailVO[]) {
  return details.map((detail) => ({
    ...detail,
    arrivalId: detail.id && (detail as typeof detail & { arrivalId?: number | null }).arrivalId == null ? 0 : (detail as typeof detail & { arrivalId?: number | null }).arrivalId,
    totalPrice: detail.totalPrice ?? multiplyPrice(detail.quantity, detail.price)
  }))
}

/** 打开库存选择弹窗 */
const handleAddDetail = () => {
  if (!canAddDetail.value) {
    return
  }
  inventorySelectRef.value?.open(getSelectedInventoryKeys())
}

/** 选择库存 */
const handleSelectInventory = (inventories: InventorySelectRow[]) => {
  if (!inventories.length) {
    return
  }
  formData.value.details = formData.value.details || []
  inventories.forEach((inventory) => {
    if (isInventorySelected(inventory)) {
      return
    }
    formData.value.details!.push(buildDetail(inventory))
  })
}

/** 判断库存是否已选择 */
function isInventorySelected(inventory: InventorySelectRow) {
  return (formData.value.details || []).some((detail) => {
    return detail.skuId === inventory.skuId && detail.sourceWarehouseId === inventory.warehouseId
  })
}

/** 获得已选择的库存标识 */
function getSelectedInventoryKeys() {
  return (formData.value.details || [])
    .map((detail) =>
      detail.skuId && detail.sourceWarehouseId
        ? `${detail.skuId}-${detail.sourceWarehouseId}`
        : undefined
    )
    .filter((key): key is string => !!key)
}

const copyDetail = (detail: MovementOrderDetailVO) => {
  formData.value.details?.push({ ...detail, id: undefined, orderId: undefined, batchNo: undefined, arrivalId: undefined, productionDate: undefined, expirationDate: undefined, serialNos: [] })
}

const setBatchDates = (detail: MovementOrderDetailVO, batch?: BatchInventoryVO) => {
  detail.sourceLocationId = undefined
  detail.arrivalId = undefined
  detail.productionDate = batch?.productionDate
  detail.expirationDate = batch?.expirationDate
}

const handleDeleteDetail = (index: number) => {
  formData.value.details?.splice(index, 1)
}

const handleSourceWarehouseChange = () => {
  formData.value.details = []
}
const handleTargetWarehouseChange = () => {
  formData.value.details?.forEach((detail) => { detail.targetLocationId = undefined })
  refreshTargetToDetails()
}
const refreshTargetToDetails = () => {
  const details = formData.value.details || []
  details.forEach((detail) => {
    detail.targetWarehouseId = formData.value.targetWarehouseId
  })
}

const handleDetailQuantityChange = (detail: MovementOrderDetailVO) => {
  if (detail.price !== undefined && detail.price !== null) {
    detail.totalPrice = multiplyPrice(detail.quantity, detail.price)
    return
  }
  detail.price = dividePrice(detail.totalPrice, detail.quantity)
}

const handleDetailPriceChange = (detail: MovementOrderDetailVO) => {
  detail.totalPrice = multiplyPrice(detail.quantity, detail.price)
}

const handleDetailTotalPriceChange = (detail: MovementOrderDetailVO) => {
  detail.price = dividePrice(detail.totalPrice, detail.quantity)
}

/** 计算表格的合计行数据 */
function getDetailSummaries({ columns, data }: { columns: any[]; data: MovementOrderDetailVO[] }) {
  return columns.map((column, index) => {
    if (index === 0) {
      return '合计'
    }
    if (column.property === 'quantity') {
      return formatSumQuantity(data, (detail) => detail.quantity)
    }
    if (column.property === 'totalPrice') {
      return formatSumPrice(data, (detail) => detail.totalPrice)
    }
    return ''
  })
}

/** 校验明细 */
const validateDetails = (required: boolean) => {
  if (!formData.value.details?.length) {
    if (required) {
      message.error('至少包含一条移库明细')
      return false
    }
    return true
  }
  for (let i = 0; i < formData.value.details.length; i++) {
    const detail = formData.value.details[i]
    if (formData.value.sourceWarehouseId === formData.value.targetWarehouseId && detail.sourceLocationId === detail.targetLocationId) {
      message.error(`第 ${i + 1} 行来源库位和目标库位不能相同`)
      return false
    }
    if ((detail as typeof detail & { arrivalId?: number }).arrivalId === undefined) {
      message.error(`第 ${i + 1} 行请选择到货记录或历史未分配`)
      return false
    }
    if (!detail.quantity || detail.quantity <= 0) {
      message.error(`第 ${i + 1} 行明细移库数量必须大于 0`)
      return false
    }
    if (detail.availableQuantity !== undefined && detail.quantity > detail.availableQuantity) {
      message.error(`第 ${i + 1} 行明细移库数量不能大于可用库存`)
      return false
    }
  }
  return true
}


const verifyArrivalBalances = async () => {
  try { await validateArrivalBalances(formData.value.sourceWarehouseId!, (formData.value.details || []).map((row) => ({ ...row, locationId: row.sourceLocationId })), false); return true }
  catch (error) { message.error(error instanceof Error ? error.message : '到货库存查询失败'); return false }
}

/** 构建提交数据 */
const buildSubmitData = () => {
  const {
    totalQuantity: _totalQuantity,
    totalPrice: _totalPrice,
    details,
    ...order
  } = formData.value
  return {
    ...order,
    details: details || []
  } as MovementOrderVO
}

const emit = defineEmits(['success'])
const submitForm = async () => {
  await formRef.value.validate()
  if (!validateDetails(false) || !(await verifyArrivalBalances())) {
    return
  }
  formLoading.value = true
  try {
    const data = buildSubmitData()
    if (formType.value === 'create') {
      await MovementOrderApi.createMovementOrder(data)
      message.success(t('common.createSuccess'))
    } else {
      await MovementOrderApi.updateMovementOrder(data)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

/** 完成移库：表单修改过则先保存，再完成 */
const handleComplete = async () => {
  await formRef.value.validate()
  if (!validateDetails(true) || !(await verifyArrivalBalances())) {
    return
  }
  try {
    await message.confirm('确认完成移库？完成后将更新库存。')
    formLoading.value = true
    const data = buildSubmitData()
    if (JSON.stringify(data) !== originalFormData.value) {
      await MovementOrderApi.updateMovementOrder(data)
    }
    await MovementOrderApi.completeMovementOrder(formData.value.id!)
    message.success('移库成功')
    dialogVisible.value = false
    emit('success')
  } catch {
    // 用户点击确认框取消按钮、或网络异常时跳过
  } finally {
    formLoading.value = false
  }
}

/** 作废移库单 */
const handleCancel = async () => {
  try {
    await message.confirm('确认作废该移库单？作废后不可恢复。')
    formLoading.value = true
    await MovementOrderApi.cancelMovementOrder(formData.value.id!)
    message.success('作废成功')
    dialogVisible.value = false
    emit('success')
  } catch {
    // 用户点击确认框取消按钮、或网络异常时跳过
  } finally {
    formLoading.value = false
  }
}

/** 重置表单 */
const resetForm = () => {
  formData.value = {
    id: undefined,
    no: generateOrderNo('YK'),
    orderTime: undefined,
    status: OrderStatusEnum.PREPARE,
    sourceWarehouseId: undefined,
    targetWarehouseId: undefined,
    remark: undefined,
    details: []
  }
  originalFormData.value = ''
  nextTick(() => formRef.value?.clearValidate())
}
</script>
