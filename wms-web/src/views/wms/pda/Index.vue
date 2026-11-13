<script setup lang="ts">
import { ElMessageBox, ElMessage } from 'element-plus'
import SerialEditor from '@/views/wms/order/components/SerialEditor.vue'
import MovementCreate from './MovementCreate.vue'
import CheckCreate from './CheckCreate.vue'
import ArrivalSelect from '@/views/wms/inventory/components/ArrivalSelect.vue'
import { listArrivalBalances } from '@/views/wms/inventory/components/arrival-api'
import ApprovalActions from '@/views/wms/order/components/ApprovalActions.vue'
import { ApprovalApi } from '@/api/wms/approval'
const approvalEnabled = ref(true)
import { ReceiptOrderApi } from '@/api/wms/order/receipt'
import { ShipmentOrderApi } from '@/api/wms/order/shipment'
import { InventoryApi } from '@/api/wms/inventory'
import { MovementOrderApi } from '@/api/wms/order/movement'
import { CheckOrderApi } from '@/api/wms/order/check'
import { WmsHomeStatisticsApi, type WmsHomeOrderSummaryVO } from '@/api/wms/home'
import { checkPermi } from '@/utils/permission'
import { WarehouseApi, type WarehouseVO } from '@/api/wms/md/warehouse'
import { ItemSkuApi, type ItemSkuVO } from '@/api/wms/md/item/sku'
import request from '@/config/axios'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { QUANTITY_PRECISION } from '@/views/wms/utils/format'
import { onBeforeRouteLeave } from 'vue-router'
import { loginOut } from '@/api/login'
import { removeToken } from '@/utils/auth'
import { deleteUserCache } from '@/hooks/web/useCache'

const logout = async () => {
  if (!(await confirmDiscard())) return
  try { await loginOut() } finally {
    removeToken()
    deleteUserCache()
    window.location.href = '/pda-login'
  }
}

type Tab = 'receipt' | 'shipment' | 'movement' | 'check' | 'inventory'
const tab = ref<Tab>('receipt')
const home = ref(true)
const summary = ref<WmsHomeOrderSummaryVO[]>([])
const canViewSummary = checkPermi(['wms:home:query'])
const summaryCount = (type: number) => summary.value.find((item) => item.type === type)?.total ?? '—'
const keyword = ref('')
const rows = ref<any[]>([])
const details = ref<any[]>([])
const selected = ref<any>()
const loading = ref(false)
const page = ref(1)
const total = ref(0)
const scanCode = ref('')
const warehouseCode = ref('')
const warehouseId = ref<number>()
const warehouseName = ref('')
const warehouseChoices = ref<WarehouseVO[]>([])
const showWarehouseChoices = ref(false)
const creatingReceipt = ref(false)
const creatingMovement = ref(false)
const creatingCheck = ref(false)
const receiptType = ref<number | undefined>(101)
const receiptScan = ref('')
const skuPickerOpen = ref(false)
const skuKeyword = ref('')
const skuPage = ref(1)
const skuTotal = ref(0)
const skuRows = ref<ItemSkuVO[]>([])
const skuLoading = ref(false)
const loadSkus = async () => {
  skuLoading.value = true
  try {
    const result = await ItemSkuApi.getItemSkuPage({ pageNo: skuPage.value, pageSize: 20, itemName: skuKeyword.value.trim() || undefined })
    skuRows.value = result.list
    skuTotal.value = result.total
  } finally { skuLoading.value = false }
}
const openSkuPicker = async () => { skuPickerOpen.value = true; skuPage.value = 1; skuKeyword.value = ''; await loadSkus() }
let nextReceiptRowId = 0
const receiptRows = ref<Array<{ rowId: number; arrivalId?: number; sku: ItemSkuVO; quantity: number; locationId?: number; locationName?: string; batchNo?: string; productionDate?: number; expirationDate?: number; available?: number; serialNos?: string[]; palletNo?: string; boxNo?: string; rollNo?: string; netLength?: number; grossLength?: number; netWeight?: number; grossWeight?: number; length?: number; width?: number; height?: number; price?: number; salesPrice?: number }>>([])
const arrivalFields: Array<{ key: 'palletNo' | 'boxNo' | 'rollNo' | 'netLength' | 'grossLength' | 'netWeight' | 'grossWeight' | 'length' | 'width' | 'height' | 'price' | 'salesPrice'; label: string; text?: boolean }> = [
  { key: 'palletNo', label: '托盘号', text: true }, { key: 'boxNo', label: '箱号', text: true }, { key: 'rollNo', label: '卷号', text: true },
  { key: 'netLength', label: '净长(cm)' }, { key: 'grossLength', label: '毛长(cm)' }, { key: 'netWeight', label: '净重(kg)' }, { key: 'grossWeight', label: '毛重(kg)' },
  { key: 'length', label: '长(cm)' }, { key: 'width', label: '宽(cm)' }, { key: 'height', label: '高(cm)' }, { key: 'price', label: '成本价' }, { key: 'salesPrice', label: '销售价' }
]
const batchInput = ref('')
const productionDate = ref('')
const expirationDate = ref('')
const clearBatchInput = () => { batchInput.value = ''; productionDate.value = ''; expirationDate.value = '' }
const locationScan = ref('')
const selectedLocation = ref<{ id: number; name: string }>()
const visibleDetails = computed(() => (tab.value === 'check' || tab.value === 'receipt' || tab.value === 'shipment' || tab.value === 'movement') && selectedLocation.value
  ? details.value.filter((item) => (tab.value === 'movement' ? item.sourceLocationId : item.locationId) === selectedLocation.value?.id)
  : details.value)
const savingReceipt = ref(false)
const verified = ref<Record<number, number>>({})
const verifiedSerials = ref<Record<number, string[]>>({})
const dirty = ref(false)
const labels = { receipt: '入库', shipment: '出库', movement: '移库', check: '盘库', inventory: '库存' }
const orderApis = {
  receipt: { page: ReceiptOrderApi.getReceiptOrderPage, details: ReceiptOrderApi.getReceiptOrderDetailListByOrderId, complete: ReceiptOrderApi.completeReceiptOrder },
  shipment: { page: ShipmentOrderApi.getShipmentOrderPage, details: ShipmentOrderApi.getShipmentOrderDetailListByOrderId, complete: ShipmentOrderApi.completeShipmentOrder },
  movement: { page: MovementOrderApi.getMovementOrderPage, details: MovementOrderApi.getMovementOrderDetailListByOrderId, complete: MovementOrderApi.completeMovementOrder },
  check: { page: CheckOrderApi.getCheckOrderPage, details: CheckOrderApi.getCheckOrderDetailListByOrderId, complete: CheckOrderApi.completeCheckOrder }
}
const load = async () => {
  loading.value = true
  try {
    const params = { pageNo: page.value, pageSize: 20, ...(tab.value === 'inventory' ? { type: 'warehouse', skuCode: keyword.value || undefined } : { no: keyword.value || undefined }), ...((tab.value === 'receipt' || tab.value === 'shipment' || tab.value === 'inventory' || tab.value === 'check') && warehouseId.value ? { warehouseId: warehouseId.value } : {}) }
    const result = tab.value === 'inventory' ? await InventoryApi.getInventoryPage(params) : await orderApis[tab.value].page(params)
    rows.value = result.list
    total.value = result.total
  } finally { loading.value = false }
}
const confirmDiscard = async () => {
  const unsavedReceipt = (creatingReceipt.value && receiptRows.value.length > 0) || creatingMovement.value || creatingCheck.value
  if (!dirty.value && !unsavedReceipt) return true
  try {
    await ElMessageBox.confirm(unsavedReceipt ? '扫码明细尚未保存，离开将丢失修改。确定离开吗？' : '盘点数量尚未保存，离开将丢失修改。确定离开吗？', '提示', { type: 'warning' })
    dirty.value = false
    return true
  } catch { return false }
}
const changeTab = async (value: Tab) => {
  if (value === tab.value || !(await confirmDiscard())) return
  creatingCheck.value = false; creatingMovement.value = false; tab.value = value; keyword.value = ''; page.value = 1; selected.value = undefined; creatingReceipt.value = false; receiptRows.value = []; selectedLocation.value = undefined; clearBatchInput(); load()
}
const openWork = async (value: Tab) => {
  if (!home.value) { await changeTab(value); return }
  home.value = false; creatingCheck.value = false; creatingMovement.value = false; tab.value = value; keyword.value = ''; page.value = 1; selected.value = undefined; creatingReceipt.value = false
  await load()
}
const goHome = async () => {
  if (!(await confirmDiscard())) return
  creatingCheck.value = false; creatingMovement.value = false; home.value = true; selected.value = undefined; creatingReceipt.value = false; receiptRows.value = []
}
const backToList = async () => { if (await confirmDiscard()) selected.value = undefined }
onBeforeRouteLeave(() => confirmDiscard())
const warnBeforeUnload = (event: BeforeUnloadEvent) => {
  if (dirty.value || (creatingReceipt.value && receiptRows.value.length > 0)) event.preventDefault()
}
onMounted(() => window.addEventListener('beforeunload', warnBeforeUnload))
onUnmounted(() => window.removeEventListener('beforeunload', warnBeforeUnload))
const search = () => { page.value = 1; load() }
const cancelCheck = async () => { if (await confirmDiscard()) creatingCheck.value = false }
const cancelMovement = async () => { if (await confirmDiscard()) creatingMovement.value = false }
const cancelReceipt = async () => {
  if (!(await confirmDiscard())) return
  creatingReceipt.value = false; selectedLocation.value = undefined; receiptRows.value = []; clearBatchInput()
}
const applyWarehouse = async (found: WarehouseVO) => {
  if (!found.id) return
  if (warehouseId.value !== found.id && creatingReceipt.value && !(await confirmDiscard())) return
  if (warehouseId.value !== found.id) { receiptRows.value = []; selectedLocation.value = undefined; clearBatchInput() }
  warehouseId.value = found.id
  warehouseName.value = found.name || found.code || ''
  warehouseCode.value = ''
  showWarehouseChoices.value = false
  search()
}
const chooseWarehouse = async () => {
  warehouseChoices.value = await WarehouseApi.getWarehouseSimpleList()
  showWarehouseChoices.value = true
}
const selectWarehouse = async () => {
  const raw = warehouseCode.value.trim()
  const match = /^WMS:WAREHOUSE:(\d+)$/i.exec(raw)
  const warehouses: WarehouseVO[] = await WarehouseApi.getWarehouseSimpleList()
  const found = warehouses.find((item) => match ? String(item.id) === match[1] : item.code === raw)
  if (!found?.id) { ElMessage.warning('未找到仓库，请扫描仓库二维码或输入仓库编号'); return }
  await applyWarehouse(found)
}
const clearWarehouse = async () => { if (creatingReceipt.value && !(await confirmDiscard())) return; warehouseId.value = undefined; warehouseName.value = ''; receiptRows.value = []; selectedLocation.value = undefined; clearBatchInput(); search() }
const scanReceiptSku = async () => {
  const code = receiptScan.value.trim()
  if (!code) return
  const unique: ItemSkuVO[] = await request.get({ url: '/wms/item-sku/exact', params: { value: code } })
  if (unique.length !== 1 || !unique[0].id) { ElMessage.warning(unique.length ? '扫码结果不唯一，请使用规格编号' : '未找到商品规格'); return }
  const batchNo = batchInput.value.trim() || undefined
  if (!batchNo && (productionDate.value || expirationDate.value)) { ElMessage.warning('填写日期时必须填写批次号'); return }
  if (productionDate.value && expirationDate.value && expirationDate.value < productionDate.value) { ElMessage.warning('到期日期不能早于生产日期'); return }
  const produced = productionDate.value ? new Date(productionDate.value + 'T00:00:00').getTime() : undefined
  const expires = expirationDate.value ? new Date(expirationDate.value + 'T00:00:00').getTime() : undefined
  let available: number | undefined
  if (tab.value === 'shipment') {
    if (!warehouseId.value) { ElMessage.warning('请先选择仓库'); return }
    const balances: Array<{ locationId: number | null; quantity: number }> = await request.get({ url: '/wms/storage-place/dimension-inventory', params: { warehouseId: warehouseId.value, skuId: unique[0].id, batchNo: batchNo || '' } })
    const balance = balances.find((item) => (item.locationId ?? undefined) === selectedLocation.value?.id)
    if (!balance || Number(balance.quantity) <= 0) { ElMessage.warning('该库位和批次没有此商品的可用库存'); return }
    available = Number(balance.quantity)
  }
  const matchingRows = receiptRows.value.filter((item) => item.sku.id === unique[0].id && item.locationId === selectedLocation.value?.id && item.batchNo === batchNo)
  if (matchingRows.length > 1) { ElMessage.warning('该规格有多条明细，请手动调整对应数量'); return }
  const row = matchingRows[0]
  if (row?.serialNos?.length) { ElMessage.warning('该明细已录入序列号，请继续扫描序列号'); return }
  if (row && tab.value === 'receipt' && (row.productionDate !== produced || row.expirationDate !== expires)) { ElMessage.warning('同一明细的批次日期不一致，请核对'); return }
  if (row?.arrivalId !== undefined && row.available === undefined) { ElMessage.warning('正在获取到货库存，请稍后重试'); return }
  const limit = row?.arrivalId !== undefined ? row.available : available
  if (limit !== undefined && (row?.quantity || 0) + 1 > limit) { ElMessage.warning('数量超过所选到货记录的可用库存'); return }
  if (row) row.quantity += 1
  else receiptRows.value.push({ rowId: ++nextReceiptRowId, sku: unique[0], quantity: 1, locationId: selectedLocation.value?.id, locationName: selectedLocation.value?.name, batchNo, productionDate: produced, expirationDate: expires, available, netLength: unique[0].netLength, grossLength: unique[0].grossLength, netWeight: unique[0].netWeight, grossWeight: unique[0].grossWeight, length: unique[0].length, width: unique[0].width, height: unique[0].height, price: unique[0].costPrice, salesPrice: unique[0].sellingPrice })
  receiptScan.value = ''
}
const selectSku = async (sku: ItemSkuVO) => {
  if (!sku.code) { ElMessage.warning('规格编号为空，无法添加'); return }
  receiptScan.value = sku.code
  await scanReceiptSku()
  if (!receiptScan.value) skuPickerOpen.value = false
}
const selectRowArrival = async (row: (typeof receiptRows.value)[number], arrivalId: number | undefined) => {
  if (row.arrivalId !== arrivalId) row.serialNos = []
  row.arrivalId = arrivalId
  row.available = undefined
  if (arrivalId === undefined || !warehouseId.value || !row.sku.id) return
  const balances = await listArrivalBalances(warehouseId.value, row.sku.id)
  if (row.arrivalId !== arrivalId) return
  const selected = balances.find((item) => (item.arrivalId ?? 0) === arrivalId && (item.locationId ?? undefined) === row.locationId && (item.batchNo || undefined) === row.batchNo)
  row.available = Number(selected?.quantity || 0)
}
const scanLocation = async () => {
  const currentWarehouse = (tab.value === 'movement' ? selected.value?.sourceWarehouseId : selected.value?.warehouseId) || warehouseId.value
  if (!currentWarehouse) { ElMessage.warning('请先选择仓库'); return }
  const match = /^WMS:LOCATION:(\d+)$/i.exec(locationScan.value.trim())
  if (!match) { ElMessage.warning('请扫描库位二维码'); return }
  const places: Array<{ id: number; type: string; name: string; code: string }> = await request.get({ url: '/wms/storage-place/list', params: { warehouseId: currentWarehouse } })
  const place = places.find((item) => item.type === 'LOCATION' && String(item.id) === match[1])
  if (!place) { ElMessage.warning('该库位不属于当前仓库或不存在'); return }
  selectedLocation.value = { id: place.id, name: `${place.name} (${place.code})` }
  locationScan.value = ''
}
const saveReceipt = async () => {
  if (receiptType.value == null || !warehouseId.value || !receiptRows.value.length || receiptRows.value.some((row) => !row.quantity || row.quantity <= 0 || (row.available !== undefined && row.quantity > row.available))) { ElMessage.warning('请选择单据类型、仓库并填写有效商品数量'); return }
  const numericFields = ['netLength', 'grossLength', 'netWeight', 'grossWeight', 'length', 'width', 'height', 'price', 'salesPrice'] as const
  if (tab.value === 'receipt' && receiptRows.value.some((row) => numericFields.some((key) => row[key] != null && String(row[key]) !== '' && (!Number.isFinite(Number(row[key])) || Number(row[key]) < 0)))) { ElMessage.warning('尺寸、重量和价格不能小于零'); return }
  if (tab.value === 'shipment' && receiptRows.value.some((row) => row.arrivalId === undefined)) { ElMessage.warning('请选择每行的到货记录或历史未分配'); return }
  if (tab.value === 'shipment') {
    const totals = new Map<string, number>()
    for (const row of receiptRows.value) {
      const key = JSON.stringify([row.sku.id, row.locationId ?? null, row.batchNo || '', row.arrivalId])
      const total = Number(((totals.get(key) || 0) + row.quantity).toFixed(2))
      if (row.available === undefined || total > row.available) { ElMessage.warning('同一到货记录的数量合计超过可用库存，请重新选择'); return }
      totals.set(key, total)
    }
  }
  savingReceipt.value = true
  try {
    const seen = new Set<string>()
    for (const row of receiptRows.value) {
      for (const serialNo of row.serialNos || []) {
        const key = `${row.sku.id}:${serialNo}`
        if (seen.has(key)) { ElMessage.warning('同一商品的序列号不能重复'); return }
        seen.add(key)
        if (tab.value === 'shipment') {
          const matches: Array<{ warehouseId: number; locationId?: number | null; batchNo?: string | null; arrivalId?: number | null; inStock: boolean }> = await request.get({ url: '/wms/serial-inventory/find', params: { skuId: row.sku.id, serialNo } })
          if (!matches.some((item) => item.inStock && item.warehouseId === warehouseId.value && (item.locationId ?? undefined) === row.locationId && (item.batchNo || undefined) === row.batchNo && (item.arrivalId ?? 0) === row.arrivalId)) { ElMessage.warning(`序列号 ${serialNo} 不在所选仓库、库位和批次`); return }
        }
      }
    }
    const data = { no: `PDA${Date.now()}${Math.floor(Math.random() * 1000)}`, type: receiptType.value, orderTime: String(Date.now()), warehouseId: warehouseId.value, details: receiptRows.value.map((row) => ({ skuId: row.sku.id, quantity: row.quantity, locationId: row.locationId, batchNo: row.batchNo, ...(tab.value === 'shipment' ? { arrivalId: row.arrivalId } : {}), productionDate: row.productionDate, expirationDate: row.expirationDate, serialNos: row.serialNos?.length ? row.serialNos : undefined, ...(tab.value === 'receipt' ? { palletNo: row.palletNo, boxNo: row.boxNo, rollNo: row.rollNo, ...Object.fromEntries(numericFields.map((key) => [key, row[key] == null || String(row[key]) === '' ? undefined : Number(row[key])])) } : {}) })) }
    const id = tab.value === 'shipment' ? await ShipmentOrderApi.createShipmentOrder(data) : await ReceiptOrderApi.createReceiptOrder(data)
    ElMessage.success(`${tab.value === 'shipment' ? '出库' : '入库'}草稿已创建，请核对后完成`)
    creatingReceipt.value = false
    receiptRows.value = []
    selectedLocation.value = undefined
    clearBatchInput()
    const order = tab.value === 'shipment' ? await ShipmentOrderApi.getShipmentOrder(id) : await ReceiptOrderApi.getReceiptOrder(id)
    await open(order)
  } finally { savingReceipt.value = false }
}
const open = async (row: any) => {
  if (tab.value === 'inventory') return
  selectedLocation.value = undefined
  locationScan.value = ''
  verified.value = {}
  verifiedSerials.value = {}
  scanCode.value = ''
  if (tab.value === 'check') {
    selected.value = await CheckOrderApi.getCheckOrder(row.id)
    details.value = (selected.value.details || []).map((item: any) => ({ ...item, arrivalId: item.id && item.arrivalId == null ? 0 : item.arrivalId }))
    dirty.value = false
    scanCode.value = ''
  } else {
    details.value = (await orderApis[tab.value].details(row.id)).map((item: any) => ({ ...item, arrivalId: item.id && item.arrivalId == null ? 0 : item.arrivalId }))
    selected.value = row
  }
}
const scanOrder = async () => {
  const code = scanCode.value.trim()
  const activeDetails = visibleDetails.value
  const matchingDetails = activeDetails.filter((item) => item.skuCode === code || (item.serialNos || []).includes(code))
  if (matchingDetails.length > 1) { ElMessage.warning('扫描码对应多条明细，请在电脑端处理'); return }
  const serialMatches = activeDetails.filter((item) => (item.serialNos || []).includes(code))
  if (serialMatches.length === 1) {
    const item = serialMatches[0]
    const scanned = verifiedSerials.value[item.id] || []
    if (scanned.includes(code)) { ElMessage.warning('序列号已核对'); return }
    verifiedSerials.value[item.id] = [...scanned, code]
    verified.value[item.id] = scanned.length + 1
    scanCode.value = ''
    return
  }
  let matches = activeDetails.filter((item) => item.skuCode === code)
  if (!matches.length && code) {
    const skus: ItemSkuVO[] = await request.get({ url: '/wms/item-sku/exact', params: { value: code } })
    if (skus.length > 1) { ElMessage.warning('扫码结果不唯一，请使用规格编号'); return }
    if (skus.length === 1) matches = activeDetails.filter((item) => item.skuId === skus[0].id)
  }
  if (matches.length !== 1) {
    ElMessage.warning(matches.length ? '该编号对应多条明细，请手动核对' : '单据中未找到此 SKU 编号')
    return
  }
  const item = matches[0]
  if (item.serialNos?.length) { ElMessage.warning('请逐个扫描序列号'); return }
  if ((verified.value[item.id] || 0) + 1 > Number(item.quantity)) {
    ElMessage.warning('核对数量不能超过单据数量')
    return
  }
  verified.value[item.id] = (verified.value[item.id] || 0) + 1
  scanCode.value = ''
}
const scanCheck = async () => {
  const code = scanCode.value.trim()
  if (!code) return
  let matches = visibleDetails.value.filter((item) => item.skuCode === code || item.batchNo === code)
  if (!matches.length) {
    const skus: ItemSkuVO[] = await request.get({ url: '/wms/item-sku/exact', params: { value: code } })
    if (skus.length > 1) { ElMessage.warning('扫码结果不唯一，请使用规格编号'); return }
    if (skus.length === 1) matches = visibleDetails.value.filter((item) => item.skuId === skus[0].id)
  }
  if (matches.length !== 1) {
    ElMessage.warning(matches.length ? '该编号对应多条明细，请手动录入' : '单据中未找到此商品或批次编号')
    return
  }
  matches[0].checkQuantity = Number(matches[0].checkQuantity || 0) + 1
  dirty.value = true
  scanCode.value = ''
}
const setCheckSerials = (item: any, serials: string[]) => {
  const ordinary = Number(item.checkQuantity || 0) - (item.actualSerialNos?.length || 0)
  item.actualSerialNos = serials
  item.checkQuantity = ordinary + serials.length
  dirty.value = true
}
const resetCheck = () => {
  details.value.forEach((item) => { item.checkQuantity = 0; if (item.actualSerialNos) item.actualSerialNos = [] })
  dirty.value = true
}
const saveCheck = async () => {
  if (details.value.some((item) => Number(item.checkQuantity) < (item.actualSerialNos?.length || 0))) {
    ElMessage.warning('实盘数量不能少于序列号数量')
    return
  }
  if (details.value.some((item) => item.checkQuantity === null || item.checkQuantity === '' || !Number.isFinite(Number(item.checkQuantity)) || Number(item.checkQuantity) < 0)) {
    ElMessage.warning('请输入有效的非负实盘数量')
    return
  }
  await CheckOrderApi.updateCheckOrder({ ...selected.value, details: details.value })
  dirty.value = false
  ElMessage.success('盘点数量已保存')
}
const canSubmitApproval = () => {
  if (dirty.value) { ElMessage.warning('请先保存盘点数量'); return false }
  if ((tab.value === 'receipt' || tab.value === 'shipment' || tab.value === 'movement') && (!details.value.length || details.value.some((item) => item.serialNos?.length ? (verifiedSerials.value[item.id]?.length || 0) !== item.serialNos.length : Number(verified.value[item.id] || 0) !== Number(item.quantity)))) {
    ElMessage.warning('请先核对所有明细数量或序列号'); return false
  }
  return true
}
const complete = async () => {
  if (dirty.value) { ElMessage.warning('请先保存盘点数量'); return }
  if ((tab.value === 'receipt' || tab.value === 'shipment' || tab.value === 'movement') && (!details.value.length || details.value.some((item) => (item.serialNos?.length ? (verifiedSerials.value[item.id]?.length || 0) !== item.serialNos.length : Number(verified.value[item.id] || 0) !== Number(item.quantity))))) {
    ElMessage.warning('请先核对所有明细数量')
    return
  }
  await ElMessageBox.confirm('确认完成该单据并更新库存？', '操作确认', { type: 'warning' })
  if (tab.value === 'inventory') return
  await orderApis[tab.value].complete(selected.value.id)
  ElMessage.success('操作成功')
  selected.value = undefined
  await load()
}
onMounted(async () => { approvalEnabled.value = (await ApprovalApi.config()).enabled; if (canViewSummary) summary.value = await WmsHomeStatisticsApi.getOrderSummary() })
</script>

<template>
  <main class="pda">
    <header><div><h1>{{ home ? '工作台' : labels[tab] }}</h1></div><button @click="logout">退出登录</button></header>
    <section v-if="home" class="dashboard">
      <div v-if="canViewSummary" class="welcome"><p>单据总览</p><div class="overview"><div><span>入库</span><strong>{{ summaryCount(1) }}</strong></div><div><span>出库</span><strong>{{ summaryCount(2) }}</strong></div><div><span>移库</span><strong>{{ summaryCount(3) }}</strong></div><div><span>盘库</span><strong>{{ summaryCount(4) }}</strong></div></div></div>
      <h2>常用操作</h2>
      <div class="work-grid"><button v-for="(label, key) in labels" :key="key" @click="openWork(key as Tab)"><span>{{ { receipt: '↓', shipment: '↑', movement: '↔', check: '✓', inventory: '▤' }[key] }}</span><strong>{{ label }}</strong><small>{{ key === 'inventory' ? '查看库存' : '查看单据' }}</small></button></div>
    </section>
    <template v-else>
    <button class="home-link" @click="goHome">‹ 工作台</button>
    <nav><button v-for="(label, key) in labels" :key="key" :class="{ active: tab === key }" @click="changeTab(key as Tab)">{{ label }}</button></nav>
    <section v-if="creatingReceipt" class="content">
      <button class="back" @click="cancelReceipt">‹ 返回列表</button>
      <h2>扫码新建{{ tab === 'shipment' ? '出库' : '入库' }}</h2><p>仓库：{{ warehouseName }}</p>
      <p>扫描库位、批次及商品后，可在明细中扫描序列号；未选择库位时使用未分配库位库存。序列号数量即该明细数量。</p>
      <label>批次号（可选）<input v-model="batchInput" placeholder="输入或扫描批次号" /></label>
      <template v-if="tab === 'receipt' && batchInput"><label>生产日期 <input v-model="productionDate" type="date" /></label><label>到期日期 <input v-model="expirationDate" type="date" /></label></template>
      <label>单据类型 <select v-model.number="receiptType"><option v-for="option in getIntDictOptions(tab === 'shipment' ? DICT_TYPE.WMS_SHIPMENT_ORDER_TYPE : DICT_TYPE.WMS_RECEIPT_ORDER_TYPE)" :key="option.value" :value="option.value">{{ option.label }}</option></select></label>
      <form class="search" @submit.prevent="scanLocation"><input v-model="locationScan" placeholder="扫描当前仓库的库位二维码" /><button>选择库位</button></form>
      <p>当前库位：{{ selectedLocation?.name || '未分配' }} <button v-if="selectedLocation" class="back" @click="selectedLocation = undefined">清除</button></p>
      <form class="search" @submit.prevent="scanReceiptSku"><input v-model="receiptScan" placeholder="扫描规格编号或商品条码" /><button>添加</button></form>
      <button class="back" @click="openSkuPicker">从物品列表选择</button>
      <el-dialog v-model="skuPickerOpen" title="选择物品" width="min(520px, 95vw)" append-to-body>
        <form class="search" @submit.prevent="skuPage = 1; loadSkus()"><input v-model="skuKeyword" placeholder="输入物品名称" /><button>查询</button></form>
        <div v-loading="skuLoading" class="sku-picker">
          <button v-for="sku in skuRows" :key="sku.id" class="sku-option" @click="selectSku(sku)"><strong>{{ sku.itemName }} {{ sku.name }}</strong><span>规格编号：{{ sku.code }}</span></button>
          <el-empty v-if="!skuLoading && !skuRows.length" description="没有找到物品" />
        </div>
        <div class="pagination"><button :disabled="skuPage === 1" @click="skuPage--; loadSkus()">上一页</button><span>{{ skuPage }} / {{ Math.max(1, Math.ceil(skuTotal / 20)) }}</span><button :disabled="skuPage * 20 >= skuTotal" @click="skuPage++; loadSkus()">下一页</button></div>
      </el-dialog>
      <article v-for="(row, index) in receiptRows" :key="row.rowId"><strong>{{ row.sku.itemName }} {{ row.sku.name }}</strong><p>{{ row.sku.code }} · {{ row.locationName || '未分配库位' }} · {{ row.batchNo || '未分配批次' }}</p><el-input-number v-model="row.quantity" :disabled="!!row.serialNos?.length" :min="0.01" :max="row.available" :precision="QUANTITY_PRECISION" /><ArrivalSelect v-if="tab === 'shipment'" :model-value="row.arrivalId" @update:model-value="selectRowArrival(row, $event)" :warehouse-id="warehouseId" :sku-id="row.sku.id" :location-id="row.locationId" :batch-no="row.batchNo" /><SerialEditor :model-value="row.serialNos" @update:model-value="row.serialNos = $event || []; if (row.serialNos.length) row.quantity = row.serialNos.length" /><details v-if="tab === 'receipt'"><summary>到货信息</summary><div class="arrival-grid"><label v-for="field in arrivalFields" :key="field.key">{{ field.label }}<input v-if="field.text" v-model="row[field.key]" maxlength="64" :placeholder="field.label" /><input v-else v-model.number="row[field.key]" type="number" min="0" step="any" :placeholder="field.label" /></label></div></details><button class="back" @click="receiptRows.push({ ...row, rowId: ++nextReceiptRowId, quantity: 1, serialNos: [], arrivalId: undefined, available: undefined, palletNo: undefined, boxNo: undefined, rollNo: undefined })">复制为新明细</button><button class="back" @click="receiptRows.splice(index, 1)">删除</button></article>
      <button class="primary" :disabled="savingReceipt" @click="saveReceipt">保存{{ tab === 'shipment' ? '出库' : '入库' }}草稿</button>
    </section>
    <CheckCreate v-else-if="creatingCheck && warehouseId" :warehouse-id="warehouseId" @close="cancelCheck" @saved="creatingCheck = false; load()" />
    <MovementCreate v-else-if="creatingMovement && warehouseId" :source-warehouse-id="warehouseId" @close="cancelMovement" @saved="creatingMovement = false; load()" />
    <template v-else-if="!selected">
      <form class="search" @submit.prevent="selectWarehouse"><input v-model="warehouseCode" placeholder="扫描仓库二维码或输入仓库编号" /><button>选择仓库</button></form>
      <div class="content"><button class="back" @click="chooseWarehouse">从列表选择仓库</button><div v-if="showWarehouseChoices" class="warehouse-choices"><button v-for="warehouse in warehouseChoices" :key="warehouse.id" @click="applyWarehouse(warehouse)">{{ warehouse.name }}（{{ warehouse.code }}）</button><span v-if="!warehouseChoices.length">暂无仓库</span></div></div>
      <div v-if="warehouseId" class="content">当前仓库：{{ warehouseName }} <button class="back" @click="clearWarehouse">清除</button></div>
      <div v-if="tab === 'check' && warehouseId" class="content"><button v-hasPermi="['wms:check-order:create']" class="primary" @click="creatingCheck = true">新建盘点草稿</button></div>
      <div v-if="tab === 'movement' && warehouseId" class="content"><button v-hasPermi="['wms:movement-order:create']" class="primary" @click="creatingMovement = true">新建移库草稿</button></div>
      <div v-if="tab === 'receipt' && warehouseId" class="content"><button v-hasPermi="['wms:receipt-order:create']" class="primary" @click="selectedLocation = undefined; receiptRows = []; clearBatchInput(); receiptType = getIntDictOptions(DICT_TYPE.WMS_RECEIPT_ORDER_TYPE)[0]?.value; creatingReceipt = true">扫码新建入库</button></div>
      <div v-if="tab === 'shipment' && warehouseId" class="content"><button v-hasPermi="['wms:shipment-order:create']" class="primary" @click="selectedLocation = undefined; receiptRows = []; clearBatchInput(); receiptType = getIntDictOptions(DICT_TYPE.WMS_SHIPMENT_ORDER_TYPE)[0]?.value; creatingReceipt = true">扫码新建出库</button></div>
      <form class="search" @submit.prevent="search"><input v-model="keyword" :placeholder="tab === 'inventory' ? '扫描或输入 SKU 编号' : '扫描或输入单号'" /><button>查询</button></form>
      <div v-loading="loading" class="content">
        <p class="hint">{{ labels[tab] }} · 共 {{ total }} 条</p>
        <article v-for="row in rows" :key="tab === 'inventory' ? `${row.warehouseId}-${row.skuId}` : row.id" @click="tab !== 'inventory' && open(row)">
          <template v-if="tab === 'inventory'"><strong>{{ row.itemName }} {{ row.skuName }}</strong><p>{{ row.skuCode }} · {{ row.warehouseName }}</p><b>库存 {{ row.quantity }} {{ row.unit }}</b></template>
          <template v-else><strong>{{ row.no }}</strong><span class="status">{{ row.status === 0 ? '草稿' : row.status === 1 ? '待审批' : row.status === 4 ? '已完成' : '已作废' }}</span><p>{{ tab === 'movement' ? `${row.sourceWarehouseName} → ${row.targetWarehouseName}` : row.warehouseName }} · 数量 {{ row.totalQuantity }}</p><small>点击查看明细 ›</small></template>
        </article>
        <el-empty v-if="!loading && !rows.length" description="暂无数据" />
        <div class="pagination"><button :disabled="page === 1" @click="page--; load()">上一页</button><span>{{ page }} / {{ Math.max(1, Math.ceil(total / 20)) }}</span><button :disabled="page * 20 >= total" @click="page++; load()">下一页</button></div>
      </div>
    </template>
    <section v-else class="content"><ApprovalActions v-if="(approvalEnabled || selected.status === 1) && !dirty && tab !== 'inventory'" :before-submit="canSubmitApproval" :type="tab" :id="selected.id" :status="selected.status" @changed="selected = undefined; load()" /><button class="back" @click="backToList">‹ 返回列表</button><h2>{{ selected.no }}</h2><p>{{ tab === 'movement' ? `${selected.sourceWarehouseName} → ${selected.targetWarehouseName}` : selected.warehouseName }}</p><form v-if="tab === 'check' && selected.status === 0" class="search" @submit.prevent="scanCheck"><input v-model="scanCode" placeholder="扫描 SKU 或批次编号，数量加 1" /><button>扫码录入</button></form><p v-if="tab === 'check' && selected.status === 0">扫码会在当前实盘数量上累加。<button class="back" @click="resetCheck">实盘数量清零</button></p><form v-if="(tab === 'receipt' || tab === 'shipment' || tab === 'check' || tab === 'movement') && selected.status === 0" class="search" @submit.prevent="scanLocation"><input v-model="locationScan" placeholder="扫描库位二维码以核对对应明细" /><button>选择库位</button></form><p v-if="(tab === 'receipt' || tab === 'shipment' || tab === 'check' || tab === 'movement') && selectedLocation">核对库位：{{ selectedLocation.name }} <button class="back" @click="selectedLocation = undefined">清除</button></p><form v-if="(tab === 'receipt' || tab === 'shipment' || tab === 'movement') && selected.status === 0" class="search" @submit.prevent="scanOrder"><input v-model="scanCode" placeholder="扫描序列号或 SKU 编号" /><button>扫码核对</button></form><p v-if="(tab === 'receipt' || tab === 'shipment' || tab === 'movement') && selected.status === 0">核对数据仅在本次操作中保留</p><article v-for="item in visibleDetails" :key="item.id"><strong>{{ item.itemName }} {{ item.skuName }}</strong><p>{{ item.skuCode }}</p><p v-if="item.batchNo">批次：{{ item.batchNo }}</p><p v-if="tab !== 'receipt'">到货记录：{{ item.arrivalId === 0 ? '历史未分配' : item.arrivalId == null ? '未选择' : item.arrivalId }}</p><p v-if="item.locationId">库位 ID：{{ item.locationId }}</p><p v-if="tab === 'movement'">来源库位：{{ item.sourceLocationId || '未分配' }} → 目标库位：{{ item.targetLocationId || '未分配' }}</p><b>{{ tab === 'check' ? '账面数量' : '数量' }} {{ item.quantity }} {{ item.unit }}</b><p v-if="(tab === 'receipt' || tab === 'shipment' || tab === 'movement') && selected.status === 0">已核对 <el-input-number v-if="!item.serialNos?.length" v-model="verified[item.id]" :min="0" :max="Number(item.quantity)" :precision="QUANTITY_PRECISION" :controls="false" /><span v-else>{{ verified[item.id] || 0 }}</span> / {{ item.quantity }}</p><p v-if="tab === 'check' && selected.status === 0">实盘序列号 <SerialEditor :model-value="item.actualSerialNos" @update:model-value="setCheckSerials(item, $event)" /></p><p v-if="tab === 'check'">实盘数量 <el-input-number v-if="selected.status === 0" v-model="item.checkQuantity" :min="0" :precision="QUANTITY_PRECISION" :controls="false" @change="dirty = true" /><span v-else>{{ item.checkQuantity }}</span></p></article><button v-if="tab === 'check' && selected.status === 0" v-hasPermi="['wms:check-order:update']" class="primary" @click="saveCheck">保存盘点数量</button><button v-if="selected.status === 0 && !approvalEnabled" v-hasPermi="[`wms:${tab}-order:complete`]" class="primary" @click="complete">确认完成{{ labels[tab] }}</button></section>
    </template>
  </main>
</template>

<style scoped>
.pda { max-width: 640px; min-height: 100vh; margin: auto; background: #f4f7fb; color: #17263c; font-family: system-ui, sans-serif; } header { padding: 25px 20px 18px; display:flex; justify-content:space-between; align-items:center; background:#fff; } header small { color:#528ee8; font-weight:700; letter-spacing:.16em; } h1 { margin:4px 0; font-size:25px; } header a { color:#438ce7; } nav { display:flex; gap:8px; padding:12px 16px; background:#fff; } button { border:0; cursor:pointer; } nav button { flex:1; padding:12px; background:#f0f4f9; border-radius:10px; color:#56667b; } nav .active,.primary { background:#428ff3; color:white; } .search { display:flex; gap:8px; padding:16px; } .search input { flex:1; min-width:0; border:1px solid #dce5f1; border-radius:10px; padding:12px; font-size:16px; } .search button,.primary { background:#428ff3; color:white; padding:12px 20px; border-radius:10px; } .content { padding:0 16px 30px; } .hint, p, small { color:#77879c; } article { background:white; border-radius:14px; padding:17px; margin-bottom:10px; box-shadow:0 3px 14px #203f6810; } article p { margin:10px 0; } article b { color:#287bdc; } .status { float:right; color:#418ae5; } .pagination { display:flex; justify-content:space-between; align-items:center; padding:12px 0; } .pagination button,.back { background:white; color:#438ce7; border-radius:8px; padding:10px; } .primary { width:100%; margin-top:12px; }
.dashboard { padding: 20px 16px; } .welcome { background: #245eea; color: white; padding: 26px 22px; border-radius: 18px; } .welcome p { color: #dbe8ff; margin: 0; } .welcome h2 { margin: 16px 0 8px; font-size: 24px; } .welcome span { color: #dbe8ff; } .dashboard > h2 { font-size: 18px; margin: 28px 0 14px; } .work-grid { display: grid; grid-template-columns: repeat(2,1fr); gap: 12px; } .work-grid button { display: grid; text-align: left; gap: 8px; background: white; padding: 18px; border-radius: 14px; color: #17263c; } .work-grid button span { color: #245eea; font-size: 26px; } .work-grid button strong { font-size: 17px; } .home-link { margin: 10px 16px 0; background: transparent; color: #245eea; padding: 8px 0; }
.overview { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-top: 20px; } .overview div { display: grid; gap: 8px; text-align: center; } .overview strong { font-size: 24px; }
.arrival-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:10px; margin-top:12px; } .arrival-grid label { display:grid; gap:5px; color:#56667b; font-size:13px; } .arrival-grid input { min-width:0; width:100%; border:1px solid #dce5f1; border-radius:8px; padding:9px; font-size:16px; }
.warehouse-choices { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:8px; margin-top:8px; } .warehouse-choices button { padding:12px; text-align:left; background:white; border:1px solid #dce5f1; border-radius:8px; color:#17263c; }
.sku-picker { max-height: 48vh; overflow-y: auto; } .sku-option { display:grid; width:100%; gap:6px; text-align:left; padding:12px; background:white; border-bottom:1px solid #e5ebf3; color:#17263c; } .sku-option span { color:#77879c; font-size:13px; }
</style>
