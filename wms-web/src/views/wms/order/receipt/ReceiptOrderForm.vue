<!-- WMS 入库单表单 -->
<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="1280px">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="92px"
    >
      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="入库单号" prop="no">
            <el-input v-model="formData.no" maxlength="64" placeholder="请输入入库单号" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="入库类型" prop="type">
            <el-select v-model="formData.type" class="w-1/1" placeholder="请选择入库类型">
              <el-option
                v-for="dict in getIntDictOptions(DICT_TYPE.WMS_RECEIPT_ORDER_TYPE)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="仓库" prop="warehouseId">
            <WarehouseSelect v-model="formData.warehouseId" />
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
        <el-col :span="8">
          <el-form-item label="供应商" prop="merchantId">
            <MerchantSelect v-model="formData.merchantId" placeholder="请选择供应商" supplier />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="业务单号" prop="bizOrderNo">
            <el-input v-model="formData.bizOrderNo" maxlength="64" placeholder="请输入业务单号" />
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

      <DetailEntryMethods title="入库明细">
        <template #settings><el-button plain @click="columnSettingsRef?.open()">字段排序</el-button></template>
        <template #select>
        <el-tooltip content="请先选择仓库" :disabled="!!formData.warehouseId" placement="top">
          <span>
            <el-button
              :disabled="!formData.warehouseId"
              plain
              type="primary"
              @click="handleAddDetail"
            >
              <Icon class="mr-5px" icon="ep:plus" />
              添加物品
            </el-button>
          </span>
        </el-tooltip>
        </template>
        <template #import><el-button plain @click="downloadImportTemplate">下载导入模板</el-button><el-button plain :disabled="!formData.warehouseId" @click="importFileRef?.click()">导入明细</el-button><input ref="importFileRef" type="file" accept=".xls,.xlsx" hidden @change="importDetails" /></template>
        <template #scan>
        <el-input v-model="scanValue" :disabled="scanLoading" class="!w-260px" clearable placeholder="扫描或输入物品码" @keyup.enter="addScannedItem" />
        <ScanItemScope v-model="scanSku" />
        <el-button :loading="scanLoading" :disabled="!formData.warehouseId" plain type="primary" @click="addScannedItem">扫码添加</el-button>
        <ScanButton label="连续扫码" continuous :disabled="!formData.warehouseId" :process-scan="queueScan" />
        </template>
      </DetailEntryMethods>

      <el-table
        :key="columnOrder.join('|')"
        :data="formData.details"
        :summary-method="getDetailSummaries"
        border
        empty-text="暂无物品明细"
        show-summary
      >
        <el-table-column label="物品信息" fixed="left" min-width="220">
          <template #default="scope">
            <div>{{ scope.row.itemName || '-' }}</div>
            <div v-if="scope.row.itemCode" class="text-12px text-gray-500">
              物品编号：{{ scope.row.itemCode }}
            </div>
          </template>
        </el-table-column>
        <template v-for="column in columnOrder" :key="column">
        <el-table-column v-if="column === 'sku'" label="规格信息" min-width="220">
          <template #default="scope">
            <div>{{ scope.row.skuName || '-' }}</div>
            <div v-if="scope.row.skuCode" class="text-12px text-gray-500">
              规格编号：{{ scope.row.skuCode }}
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="column === 'serial'" label="序列号" width="130"><template #default="scope"><SerialEditor v-model="scope.row.serialNos" /></template></el-table-column>
        <el-table-column v-if="column === 'location'" label="库位" min-width="180">
          <template #default="scope">
            <InboundLocationSelect v-model="scope.row.locationId" :warehouse-id="formData.warehouseId" />
          </template>
        </el-table-column>
        <el-table-column v-if="column === 'batch'" label="批次号" min-width="160">
          <template #default="scope">
            <el-input v-model="scope.row.batchNo" maxlength="64" placeholder="留空为无批次库存" />
          </template>
        </el-table-column>
        <el-table-column v-if="column === 'package'" label="托盘号 / 箱号 / 卷号" width="180">
          <template #default="scope">
            <div class="receipt-field-stack">
              <el-input v-model="scope.row.palletNo" maxlength="64" placeholder="托盘号" aria-label="托盘号" />
              <el-input v-model="scope.row.boxNo" maxlength="64" placeholder="箱号" aria-label="箱号" />
              <el-input v-model="scope.row.rollNo" maxlength="64" placeholder="卷号" aria-label="卷号" />
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="column === 'netLength'" label="净长 / 毛长 (cm)" width="160">
          <template #default="scope">
            <div class="receipt-field-stack">
              <el-input-number v-model="scope.row.netLength" :controls="false" :min="0" class="!w-1/1" placeholder="净长" aria-label="净长" />
              <el-input-number v-model="scope.row.grossLength" :controls="false" :min="0" class="!w-1/1" placeholder="毛长" aria-label="毛长" />
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="column === 'weight'" label="净重 / 毛重 (kg)" width="160">
          <template #default="scope">
            <div class="receipt-field-stack">
              <el-input-number v-model="scope.row.netWeight" :controls="false" :min="0" class="!w-1/1" placeholder="净重" aria-label="净重" />
              <el-input-number v-model="scope.row.grossWeight" :controls="false" :min="0" class="!w-1/1" placeholder="毛重" aria-label="毛重" />
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="column === 'dimensions'" label="长 / 宽 / 高 (cm)" width="150">
          <template #default="scope">
            <div class="receipt-field-stack">
              <el-input-number v-model="scope.row.length" :controls="false" :min="0" class="!w-1/1" placeholder="长" aria-label="长" />
              <el-input-number v-model="scope.row.width" :controls="false" :min="0" class="!w-1/1" placeholder="宽" aria-label="宽" />
              <el-input-number v-model="scope.row.height" :controls="false" :min="0" class="!w-1/1" placeholder="高" aria-label="高" />
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="column === 'dates'" label="生产 / 到期日期" width="180">
          <template #default="scope">
            <div class="receipt-field-stack">
              <el-date-picker v-model="scope.row.productionDate" type="date" value-format="YYYY-MM-DD HH:mm:ss" placeholder="生产日期" aria-label="生产日期" class="!w-1/1" />
              <el-date-picker v-model="scope.row.expirationDate" type="date" value-format="YYYY-MM-DD HH:mm:ss" placeholder="到期日期" aria-label="到期日期" class="!w-1/1" />
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="column === 'quantity'" label="入库数量" prop="quantity" width="160">
          <template #default="scope">
            <el-input-number
              v-model="scope.row.quantity"
              :controls="false"
              :min="0"
              :precision="QUANTITY_PRECISION"
              class="!w-1/1"
              placeholder="数量"
              @change="handleDetailQuantityChange(scope.row)"
            />
          </template>
        </el-table-column>
        <el-table-column v-if="column === 'price'" label="成本价 / 销售价 (元)" prop="price" width="170">
          <template #default="scope">
            <div class="receipt-field-stack">
            <el-input-number
              v-model="scope.row.price"
              :controls="false"
              :min="0"
              :precision="PRICE_PRECISION"
              class="!w-1/1"
              placeholder="成本价"
              aria-label="成本价"
              @change="handleDetailPriceChange(scope.row)"
            />
            <el-input-number v-model="scope.row.salesPrice" :controls="false" :min="0" class="!w-1/1" placeholder="销售价" aria-label="销售价" />
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="column === 'total'" label="金额(元)" prop="totalPrice" width="160">
          <template #default="scope">
            <el-input-number
              v-model="scope.row.totalPrice"
              :controls="false"
              :min="0"
              :precision="PRICE_PRECISION"
              class="!w-1/1"
              placeholder="金额"
              @change="handleDetailTotalPriceChange(scope.row)"
            />
          </template>
        </el-table-column>
        </template>
        <el-table-column align="center" fixed="right" label="操作" width="120">
          <template #default="scope">
            <el-button link type="primary" @click="copyDetail(scope.row)">复制</el-button>
            <el-button link type="danger" @click="handleDeleteDetail(scope.$index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <ItemSkuSelect ref="skuSelectRef" @change="handleSelectSku" />
      <ReceiptColumnSettings ref="columnSettingsRef" v-model="columnOrder" :options="columnOptions" />
    </el-form>
    <template #footer>
      <div class="flex items-center justify-between">
        <div>
          <el-button
            v-if="isSavedPrepareOrder && !approvalEnabled"
            v-hasPermi="['wms:receipt-order:complete']"
            :disabled="formLoading || scanLoading"
            type="success"
            @click="handleComplete"
          >
            完成入库
          </el-button>
          <el-button
            v-if="isSavedPrepareOrder"
            v-hasPermi="['wms:receipt-order:cancel']"
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
import request from '@/config/axios'
import DetailEntryMethods from '@/views/wms/order/components/DetailEntryMethods.vue'
import ScanButton from '@/views/wms/pda/ScanButton.vue'
import ScanItemScope from '@/views/wms/order/components/ScanItemScope.vue'
import download from '@/utils/download'
import { ApprovalApi } from '@/api/wms/approval'
const approvalEnabled = ref(true)
import SerialEditor from '@/views/wms/order/components/SerialEditor.vue'
import ReceiptColumnSettings from './ReceiptColumnSettings.vue'
import { getCurrentUserId, getTenantId } from '@/utils/auth'
const columnSettingsRef = ref<InstanceType<typeof ReceiptColumnSettings>>()
const columnOptions = [
  { key: 'sku', label: '规格信息' }, { key: 'serial', label: '序列号' },
  { key: 'location', label: '库位' }, { key: 'batch', label: '批次号' },
  { key: 'package', label: '托盘号 / 箱号 / 卷号' }, { key: 'netLength', label: '净长 / 毛长' },
  { key: 'weight', label: '净重 / 毛重' }, { key: 'dimensions', label: '长 / 宽 / 高' },
  { key: 'dates', label: '生产 / 到期日期' }, { key: 'quantity', label: '入库数量' },
  { key: 'price', label: '成本价 / 销售价' }, { key: 'total', label: '金额' }
]
const columnOrder = ref(columnOptions.map((item) => item.key))
const columnStorageKey = () => `wms-receipt-columns:${getTenantId() || 0}:${getCurrentUserId()}`
const loadColumnOrder = () => {
  const defaults = columnOptions.map((item) => item.key)
  try {
    const stored = JSON.parse(localStorage.getItem(columnStorageKey()) || '[]')
    const valid = Array.isArray(stored) ? stored.filter((key): key is string => defaults.includes(key)) : []
    columnOrder.value = [...new Set(valid), ...defaults.filter((key) => !valid.includes(key))]
  } catch { columnOrder.value = defaults }
}
watch(columnOrder, (value) => localStorage.setItem(columnStorageKey(), JSON.stringify(value)))
import InboundLocationSelect from '@/views/wms/inventory/components/InboundLocationSelect.vue'
import { FormRules } from 'element-plus'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { ReceiptOrderApi, ReceiptOrderVO } from '@/api/wms/order/receipt'
import { ReceiptOrderDetailVO } from '@/api/wms/order/receipt/detail'
import { ItemSkuVO } from '@/api/wms/md/item/sku'
import ItemSkuSelect from '@/views/wms/md/item/sku/components/ItemSkuSelect.vue'
import MerchantSelect from '@/views/wms/md/merchant/components/MerchantSelect.vue'
import WarehouseSelect from '@/views/wms/md/warehouse/components/WarehouseSelect.vue'
import { OrderStatusEnum, OrderUpdateStatusList } from '@/views/wms/utils/constants'
import {
  dividePrice,
  formatSumPrice,
  formatSumQuantity,
  multiplyPrice,
  PRICE_PRECISION,
  QUANTITY_PRECISION
} from '@/views/wms/utils/format'
import { generateOrderNo } from '@/views/wms/utils/order'

/** WMS 入库单表单 */
defineOptions({ name: 'WmsReceiptOrderForm' })

const { t } = useI18n() // 国际化
const message = useMessage() // 消息弹窗

const dialogVisible = ref(false) // 弹窗的是否展示
const dialogTitle = ref('') // 弹窗的标题
const formLoading = ref(false) // 表单的加载中
const formType = ref('') // 表单的类型：create - 新增；update - 修改
const originalFormData = ref('') // 表单原始数据快照
const formData = ref<ReceiptOrderVO>({
  id: undefined,
  no: undefined,
  type: undefined,
  orderTime: undefined,
  status: OrderStatusEnum.PREPARE,
  bizOrderNo: undefined,
  merchantId: undefined,
  warehouseId: undefined,
  remark: undefined,
  details: []
})
const formRules = reactive<FormRules>({
  no: [{ required: true, message: '入库单号不能为空', trigger: 'blur' }],
  type: [{ required: true, message: '入库类型不能为空', trigger: 'change' }],
  orderTime: [{ required: true, message: '单据日期不能为空', trigger: 'change' }],
  warehouseId: [{ required: true, message: '仓库不能为空', trigger: 'change' }]
})
const formRef = ref() // 表单 Ref
const skuSelectRef = ref() // 商品 SKU 选择弹窗 Ref

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

/** 打开弹窗 */
const open = async (type: string, id?: number) => {
  scanFormVersion++
  scanValue.value = ''
  scanSku.value = undefined
  loadColumnOrder()
  approvalEnabled.value = (await ApprovalApi.config()).enabled
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  resetForm()
  // 修改时，设置数据
  if (id) {
    formLoading.value = true
    try {
      const order = await ReceiptOrderApi.getReceiptOrder(id)
      formData.value = {
        ...order,
        details: normalizeDetails(order.details || [])
      }
    } finally {
      formLoading.value = false
    }
  }
  originalFormData.value = JSON.stringify(buildSubmitData())
}
defineExpose({ open }) // 提供 open 方法，用于打开弹窗

/** 构建入库明细 */
function buildDetail(sku: ItemSkuVO): ReceiptOrderDetailVO {
  return {
    id: undefined,
    itemId: sku.itemId,
    itemCode: sku.itemCode,
    itemName: sku.itemName,
    unit: sku.unit,
    skuId: sku.id,
    skuCode: sku.code,
    skuName: sku.name,
    netLength: sku.netLength,
    grossLength: sku.grossLength,
    netWeight: sku.netWeight,
    grossWeight: sku.grossWeight,
    length: sku.length,
    width: sku.width,
    height: sku.height,
    salesPrice: sku.sellingPrice,
    quantity: undefined,
    price: sku.costPrice,
    totalPrice: undefined
  }
}

const scanValue = ref('')
const scanSku = ref<ItemSkuVO>()
const pendingScans = ref(0)
const scanLoading = computed(() => pendingScans.value > 0)
let scanFormVersion = 0
let scanQueue = Promise.resolve()
const queueScan = (raw: string) => {
  const value = raw.trim()
  const warehouseId = formData.value.warehouseId
  const formVersion = scanFormVersion
  const scopeId = scanSku.value?.id
  if (!value || !warehouseId) return Promise.resolve({ ok: false, message: '请先选择仓库并输入物品码' })
  pendingScans.value++
  const result = scanQueue.then(async () => {
    if (!dialogVisible.value || scopeId !== scanSku.value?.id || formVersion !== scanFormVersion || formData.value.warehouseId !== warehouseId) return { ok: false, message: '仓库或单据已变更，请重新扫码' }
    let skus: ItemSkuVO[] = await request.get({ url: '/wms/item-sku/exact', params: { value } })
    let parsed: Record<string, string> | null = null
    if (skus.length === 0) {
      parsed = await request.get({ url: '/wms/scan-rule/resolve', params: { value, skuId: scopeId } })
      if (parsed) skus = await request.get({ url: '/wms/item-sku/exact', params: { value: parsed.skuCode } })
    }
    if (skus.length !== 1 || !skus[0].id) return { ok: false, message: '未找到唯一物品规格，请检查条码或二维码规则' }
    if (scopeId && skus[0].id !== scopeId) return { ok: false, message: '扫描的物品与指定扫码物品不一致' }
    if (!dialogVisible.value || scopeId !== scanSku.value?.id || formVersion !== scanFormVersion || formData.value.warehouseId !== warehouseId) return { ok: false, message: '仓库或单据已变更，请重新扫码' }
    const quantity = parsed?.quantity ? Number(parsed.quantity) : 1
    if (!Number.isFinite(quantity) || quantity <= 0 || !Number.isInteger(Number((quantity * 100).toFixed(6)))) return { ok: false, message: '二维码数量最多支持两位小数' }
    const detail = buildDetail(skus[0])
    detail.quantity = quantity
    if (parsed) {
      detail.batchNo = parsed.batchNo
      detail.palletNo = parsed.palletNo
      detail.boxNo = parsed.boxNo
      detail.rollNo = parsed.rollNo
      const fields = ['netLength', 'grossLength', 'netWeight', 'grossWeight', 'length', 'width', 'height', 'price', 'salesPrice'] as const
      for (const field of fields) {
        if (parsed[field] !== undefined) {
          const number = Number(parsed[field])
          if (!Number.isFinite(number)) return { ok: false, message: '二维码中的数值超出支持范围' }
          detail[field] = number
        }
      }
    }
    detail.totalPrice = multiplyPrice(detail.quantity, detail.price)
    formData.value.details = [...(formData.value.details || []), detail]
    return { ok: true, message: `已添加：${value}` }
  }).catch((error) => ({ ok: false, message: typeof error === 'string' && error !== 'error' ? error : error instanceof Error ? error.message : '扫码处理失败，请重试' })).finally(() => { pendingScans.value-- })
  scanQueue = result.then(() => {})
  return result
}
const addScannedItem = async () => { const value = scanValue.value.trim(); if (!value) return; scanValue.value = ''; const result = await queueScan(value); if (result.ok) message.success(result.message); else message.warning(result.message) }

function normalizeDetails(details: ReceiptOrderDetailVO[]) {
  return details.map((detail) => ({
    ...detail,
    totalPrice: detail.totalPrice ?? multiplyPrice(detail.quantity, detail.price)
  }))
}

const importFileRef = ref<HTMLInputElement>()
const downloadImportTemplate = async () => {
  const data = await request.download({ url: '/wms/receipt-order/import-template' })
  download.excel(data, '入库明细导入模板.xls')
}
const importDetails = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    formLoading.value = true
    const data = new FormData()
    data.append('file', file)
    const response: any = await request.upload({ url: '/wms/receipt-order/import-parse', data })
    const rows: Array<ReceiptOrderDetailVO & { skuCode?: string; quantity?: number; price?: number; costPrice?: number; serialNumbers?: string; locationCode?: string; batchNo?: string; productionDate?: string; expirationDate?: string }> = response.data
    if (!rows?.length) { message.error('模板没有明细'); return }
    const places: Array<{ id: number; type: string; code: string }> = await request.get({ url: '/wms/storage-place/list', params: { warehouseId: formData.value.warehouseId } })
    const parseDate = (value?: string) => {
      if (!value?.trim()) return undefined
      const raw = value.trim()
      let year: number, month: number, day: number
      if (/^\d{4,6}(?:\.\d+)?$/.test(raw)) {
        const date = new Date(Date.UTC(1899, 11, 30) + Math.floor(Number(raw)) * 86400000)
        year = date.getUTCFullYear(); month = date.getUTCMonth() + 1; day = date.getUTCDate()
      } else {
        const match = /^(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})(?:日)?(?:[ T]\d{1,2}:\d{2}(?::\d{2})?)?$/.exec(raw)
        if (!match) throw new Error('日期格式不正确')
        year = Number(match[1]); month = Number(match[2]); day = Number(match[3])
      }
      const date = new Date(Date.UTC(year, month - 1, day))
      if (!Number.isFinite(date.getTime()) || date.getUTCFullYear() !== year || date.getUTCMonth() + 1 !== month || date.getUTCDate() !== day) throw new Error('日期格式不正确')
      return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')} 00:00:00`
    }
    const details: ReceiptOrderDetailVO[] = []
    for (const [index, row] of rows.entries()) {
      const code = String(row.skuCode || '').trim()
      const quantity = Number(row.quantity)
      const cost = row.costPrice ?? row.price
      const total = row.totalPrice == null ? undefined : Number(row.totalPrice)
      const price = cost == null ? (total == null ? undefined : dividePrice(total, quantity)) : Number(cost)
      if (!code || !Number.isFinite(quantity) || quantity <= 0 || (price !== undefined && (!Number.isFinite(price) || price < 0)) || (total !== undefined && (!Number.isFinite(total) || total < 0))) {
        message.error(`第 ${index + 1} 条明细规格编号、数量或金额不正确`); return
      }
      const serialNos = (row.serialNumbers || '').split(/[\r\n,，;；]+/).map((value) => value.trim()).filter(Boolean)
      if (serialNos.length && (serialNos.length !== quantity || new Set(serialNos).size !== serialNos.length || serialNos.some((value) => value.length > 128))) {
        message.error(`第 ${index + 1} 条明细序列号数量需与入库数量一致，且不能重复`); return
      }
      const skus: ItemSkuVO[] = await request.get({ url: '/wms/item-sku/exact', params: { value: code } })
      const matched = skus.filter((sku) => sku.code === code)
      if (matched.length !== 1) { message.error(`第 ${index + 1} 条明细规格编号不存在或不唯一`); return }
      const locationCode = row.locationCode?.trim()
      const locations = locationCode ? places.filter((p) => p.type === 'LOCATION' && p.code === locationCode) : []
      if (locationCode && locations.length !== 1) { message.error(`第 ${index + 1} 条明细库位编号不存在或不唯一`); return }
      const batchNo = row.batchNo?.trim() || undefined
      let productionDate: string | undefined, expirationDate: string | undefined
      try { productionDate = parseDate(row.productionDate); expirationDate = parseDate(row.expirationDate) } catch { message.error(`第 ${index + 1} 条明细日期格式不正确`); return }
      if ((!batchNo && (productionDate || expirationDate)) || (productionDate && expirationDate && productionDate > expirationDate)) { message.error(`第 ${index + 1} 条明细批次或日期不正确`); return }
      const defaults = buildDetail(matched[0])
      const effectivePrice = price ?? defaults.price
      details.push({ ...defaults, palletNo: row.palletNo, boxNo: row.boxNo, rollNo: row.rollNo, netLength: row.netLength ?? defaults.netLength, grossLength: row.grossLength ?? defaults.grossLength, netWeight: row.netWeight ?? defaults.netWeight, grossWeight: row.grossWeight ?? defaults.grossWeight, length: row.length ?? defaults.length, width: row.width ?? defaults.width, height: row.height ?? defaults.height, salesPrice: row.salesPrice ?? defaults.salesPrice, quantity, price: effectivePrice, locationId: locations[0]?.id, batchNo, productionDate, expirationDate, serialNos: serialNos.length ? serialNos : undefined, totalPrice: total ?? multiplyPrice(quantity, effectivePrice) })
    }
    formData.value.details = [...(formData.value.details || []), ...details]
    message.success(`已导入 ${details.length} 行，请核对后保存`)
  } finally { formLoading.value = false; input.value = '' }
}

/** 添加商品 */
const handleAddDetail = () => {
  skuSelectRef.value?.open(getSelectedSkuIds())
}

/** 选择商品 SKU */
const handleSelectSku = (skus: ItemSkuVO[]) => {
  if (!skus.length) {
    return
  }
  formData.value.details = formData.value.details || []
  const selectedSkuIds = new Set(getSelectedSkuIds())
  skus.forEach((sku) => {
    if (!sku.id || selectedSkuIds.has(sku.id)) {
      return
    }
    formData.value.details!.push(buildDetail(sku))
    selectedSkuIds.add(sku.id)
  })
}

/** 获得已选 SKU 编号 */
const getSelectedSkuIds = () => {
  return (formData.value.details || [])
    .map((detail) => detail.skuId)
    .filter((id): id is number => !!id)
}

/** 删除明细 */
const copyDetail = (detail: ReceiptOrderDetailVO) => {
  formData.value.details?.push({ ...detail, id: undefined, orderId: undefined, batchNo: undefined, palletNo: undefined, boxNo: undefined, rollNo: undefined, productionDate: undefined, expirationDate: undefined, serialNos: [] })
}

const handleDeleteDetail = (index: number) => {
  formData.value.details?.splice(index, 1)
}

/** 明细数量变化 */
const handleDetailQuantityChange = (detail: ReceiptOrderDetailVO) => {
  if (detail.price !== undefined && detail.price !== null) {
    detail.totalPrice = multiplyPrice(detail.quantity, detail.price)
    return
  }
  detail.price = dividePrice(detail.totalPrice, detail.quantity)
}

/** 明细单价变化 */
const handleDetailPriceChange = (detail: ReceiptOrderDetailVO) => {
  detail.totalPrice = multiplyPrice(detail.quantity, detail.price)
}

/** 明细金额变化 */
const handleDetailTotalPriceChange = (detail: ReceiptOrderDetailVO) => {
  detail.price = dividePrice(detail.totalPrice, detail.quantity)
}

/** 计算表格的合计行数据 */
function getDetailSummaries({ columns, data }: { columns: any[]; data: ReceiptOrderDetailVO[] }) {
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
      message.error('至少包含一条入库明细')
      return false
    }
    return true
  }
  for (let i = 0; i < formData.value.details.length; i++) {
    const detail = formData.value.details[i]
    if (!detail.skuId) {
      message.error(`第 ${i + 1} 行明细请选择商品规格`)
      return false
    }
    if (!detail.quantity || detail.quantity <= 0) {
      message.error(`第 ${i + 1} 行明细入库数量必须大于 0`)
      return false
    }
  }
  return true
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
  } as ReceiptOrderVO
}

/** 提交表单 */
const emit = defineEmits(['success']) // 定义 success 事件，用于操作成功后的回调
const submitForm = async () => {
  // 校验表单
  await formRef.value.validate()
  if (!validateDetails(false)) {
    return
  }
  // 提交请求
  formLoading.value = true
  try {
    const data = buildSubmitData()
    if (formType.value === 'create') {
      await ReceiptOrderApi.createReceiptOrder(data)
      message.success(t('common.createSuccess'))
    } else {
      await ReceiptOrderApi.updateReceiptOrder(data)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    // 发送操作成功的事件
    emit('success')
  } finally {
    formLoading.value = false
  }
}

/** 完成入库：表单修改过则先保存，再完成 */
const handleComplete = async () => {
  await formRef.value.validate()
  if (!validateDetails(true)) {
    return
  }
  try {
    await message.confirm('确认完成入库？完成后将更新库存。')
    formLoading.value = true
    const data = buildSubmitData()
    if (JSON.stringify(data) !== originalFormData.value) {
      await ReceiptOrderApi.updateReceiptOrder(data)
    }
    await ReceiptOrderApi.completeReceiptOrder(formData.value.id!)
    message.success('入库成功')
    dialogVisible.value = false
    emit('success')
  } catch {
  } finally {
    formLoading.value = false
  }
}

/** 作废入库单 */
const handleCancel = async () => {
  try {
    await message.confirm('确认作废该入库单？作废后不可恢复。')
    formLoading.value = true
    await ReceiptOrderApi.cancelReceiptOrder(formData.value.id!)
    message.success('作废成功')
    dialogVisible.value = false
    emit('success')
  } catch {
  } finally {
    formLoading.value = false
  }
}

/** 重置表单 */
const resetForm = () => {
  formData.value = {
    id: undefined,
    no: generateOrderNo('RK'),
    type: undefined,
    orderTime: undefined,
    status: OrderStatusEnum.PREPARE,
    bizOrderNo: undefined,
    merchantId: undefined,
    warehouseId: undefined,
    remark: undefined,
    details: []
  }
  originalFormData.value = ''
  nextTick(() => formRef.value?.clearValidate())
}
</script>

<style scoped>
.receipt-field-stack { display: grid; gap: 5px; }
</style>
