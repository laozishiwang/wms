<script setup lang="ts">
import { ElMessageBox, ElMessage } from 'element-plus'
import SerialEditor from '@/views/wms/order/components/SerialEditor.vue'
import MovementCreate from './MovementCreate.vue'
import ReceiptDraftDetail from './ReceiptDraftDetail.vue'
import PlacePicker from './PlacePicker.vue'
import { placePath, type StoragePlace } from './place'
import { buildDraftDetails } from './draft-details'
import MerchantSelect from '@/views/wms/md/merchant/components/MerchantSelect.vue'
import ScanButton from './ScanButton.vue'
import CheckCreate from './CheckCreate.vue'
import ArrivalSelect from '@/views/wms/inventory/components/ArrivalSelect.vue'
import BatchSelect from '@/views/wms/inventory/components/BatchSelect.vue'
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
const scrollArea = ref<HTMLElement>()
const scrollTrack = ref<HTMLElement>()
const scrollIndicator = reactive({ visible: false, top: 0, height: 100 })
const updateScrollIndicator = () => {
  const el = document.scrollingElement
  if (!el) return
  const max = el.scrollHeight - el.clientHeight
  const height = Math.max(8, Math.min(100, (el.clientHeight / el.scrollHeight) * 100))
  const top = max > 0 ? (el.scrollTop / max) * (100 - height) : 0
  if (scrollIndicator.visible !== (max > 1) || Math.abs(scrollIndicator.top - top) > 0.1 || Math.abs(scrollIndicator.height - height) > 0.1) Object.assign(scrollIndicator, { visible: max > 1, top, height })
}
onUpdated(updateScrollIndicator)
onMounted(() => {
  const area = scrollArea.value!
  const roots = [document.documentElement, document.body, document.getElementById('app')!]
  const previousStyles = roots.map((root) => ({ overflow: root.style.overflow, height: root.style.height }))
  roots.forEach((root) => { root.style.overflow = 'visible'; root.style.height = 'auto' })
  document.documentElement.classList.add('pda-scroll-page')
  document.documentElement.style.overflowY = 'auto'
  const resize = new ResizeObserver(updateScrollIndicator)
  const mutations = new MutationObserver(updateScrollIndicator)
  resize.observe(area)
  mutations.observe(area, { childList: true, subtree: true })
  window.addEventListener('resize', updateScrollIndicator)
  window.addEventListener('scroll', updateScrollIndicator, { passive: true })
  updateScrollIndicator()
  onBeforeUnmount(() => {
    resize.disconnect()
    mutations.disconnect()
    window.removeEventListener('resize', updateScrollIndicator)
    window.removeEventListener('scroll', updateScrollIndicator)
    document.documentElement.classList.remove('pda-scroll-page')
    roots.forEach((root, index) => { root.style.overflow = previousStyles[index].overflow; root.style.height = previousStyles[index].height })
  })
})
const dragScroll = (event: PointerEvent) => {
  const area = document.scrollingElement!
  const track = scrollTrack.value!
  const bounds = track.getBoundingClientRect()
  const thumbHeight = bounds.height * scrollIndicator.height / 100
  const startY = event.clientY
  const startScroll = area.scrollTop
  const scrollRange = area.scrollHeight - area.clientHeight
  const trackRange = bounds.height - thumbHeight
  track.setPointerCapture(event.pointerId)
  const move = (e: PointerEvent) => { area.scrollTop = startScroll + (e.clientY - startY) * scrollRange / trackRange }
  const end = () => { track.removeEventListener('pointermove', move); track.removeEventListener('pointerup', end); track.removeEventListener('pointercancel', end) }
  track.addEventListener('pointermove', move)
  track.addEventListener('pointerup', end)
  track.addEventListener('pointercancel', end)
}
const home = ref(true)
const summary = ref<WmsHomeOrderSummaryVO[]>([])
const canViewSummary = checkPermi(['wms:home:query'])
const summaryCount = (type: number) => summary.value.find((item) => item.type === type)?.total ?? '—'
const summaryStatus = (type: number, status: number) => summary.value.find((item) => item.type === type)?.statuses?.find((item) => item.status === status)?.count ?? 0
const openDashboardWork = (value: Tab, status?: number) => { listStatus.value = status; openWork(value) }
const keyword = ref('')
const listStatus = ref<number>()
const listDates = ref<string[]>([])
const rows = ref<any[]>([])
const details = ref<any[]>([])
const selected = ref<any>()
const loading = ref(false)
const page = ref(1)
const total = ref(0)
const inventoryExpanded = ref('')
const inventoryBalances = ref<any[]>([])
const inventoryPlaces = ref<StoragePlace[]>([])
const inventoryLoading = ref(false)
const openInventory = async (row: any) => {
  const key = `${row.warehouseId}-${row.skuId}`
  if (inventoryExpanded.value === key) { inventoryExpanded.value = ''; return }
  inventoryExpanded.value = key
  inventoryLoading.value = true
  try {
    const [balances, places] = await Promise.all([
      listArrivalBalances(row.warehouseId, row.skuId),
      checkPermi(['wms:warehouse:query']) ? request.get({ url: '/wms/storage-place/list', params: { warehouseId: row.warehouseId } }) : Promise.resolve([])
    ])
    if (inventoryExpanded.value === key) { inventoryBalances.value = balances.filter((item) => Number(item.quantity) > 0); inventoryPlaces.value = places }
  } finally { if (inventoryExpanded.value === key) inventoryLoading.value = false }
}
const scanCode = ref('')
const warehouseCode = ref('')
const tabWarehouses = reactive<Partial<Record<Tab, { id?: number; name: string }>>>({})
const warehouseId = computed({ get: () => tabWarehouses[tab.value]?.id, set: (id) => { tabWarehouses[tab.value] = { id, name: tabWarehouses[tab.value]?.name || '' } } })
const warehouseName = computed({ get: () => tabWarehouses[tab.value]?.name || '', set: (name) => { tabWarehouses[tab.value] = { id: tabWarehouses[tab.value]?.id, name } } })
const warehouseChoices = ref<WarehouseVO[]>([])
const showWarehouseChoices = ref(false)
const creatingReceipt = ref(false)
const creatingMovement = ref(false)
const creatingCheck = ref(false)
const editingOrder = ref<any>()
const originalReceiptDate = ref('')
const receiptType = ref<number | undefined>(101)
const receiptMerchantId = ref<number>()
const receiptRemark = ref('')
const receiptDate = ref('')
watch(creatingReceipt, (creating) => {
  if (creating) {
    const now = new Date()
    receiptDate.value = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
    receiptMerchantId.value = undefined
    receiptRemark.value = ''
  }
})
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
const receiptRows = ref<Array<{ id?: number; rowId: number; arrivalId?: number; sku: ItemSkuVO; quantity: number; originalQuantity?: number; originalPrice?: number; totalPrice?: number; locationId?: number; locationName?: string; batchNo?: string; productionDate?: number; expirationDate?: number; available?: number; serialNos?: string[]; palletNo?: string; boxNo?: string; rollNo?: string; netLength?: number; grossLength?: number; netWeight?: number; grossWeight?: number; length?: number; width?: number; height?: number; price?: number; salesPrice?: number }>>([])
const dateValue = (value?: number) => { if (!value) return ''; const d = new Date(value); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}` }
const dateMillis = (event: Event) => { const value = (event.target as HTMLInputElement).value; return value ? new Date(`${value}T00:00:00`).getTime() : undefined }
const arrivalFields: Array<{ key: 'palletNo' | 'boxNo' | 'rollNo' | 'netLength' | 'grossLength' | 'netWeight' | 'grossWeight' | 'length' | 'width' | 'height' | 'price' | 'salesPrice'; label: string; text?: boolean }> = [
  { key: 'palletNo', label: '托盘号', text: true }, { key: 'boxNo', label: '箱号', text: true }, { key: 'rollNo', label: '卷号', text: true },
  { key: 'netLength', label: '净长(cm)' }, { key: 'grossLength', label: '毛长(cm)' }, { key: 'netWeight', label: '净重(kg)' }, { key: 'grossWeight', label: '毛重(kg)' },
  { key: 'length', label: '长(cm)' }, { key: 'width', label: '宽(cm)' }, { key: 'height', label: '高(cm)' }, { key: 'price', label: '成本价' }, { key: 'salesPrice', label: '销售价' }
]
const selectedLocation = ref<{ id: number; name: string }>()
const storagePlaces = ref<StoragePlace[]>([])
const selectedAreaId = ref<number>()
const placeCodes = reactive({ SHELF: '', LOCATION: '' })
const storageLocations = computed(() => storagePlaces.value.filter((place) => place.type === 'LOCATION' && (!selectedAreaId.value || place.parentId === selectedAreaId.value)))
watch(creatingReceipt, async (creating) => {
  if (!creating || !warehouseId.value) return
  selectedAreaId.value = undefined
  storagePlaces.value = await request.get({ url: '/wms/storage-place/list', params: { warehouseId: warehouseId.value } })
})
const changeArea = () => { selectedLocation.value = undefined }
const chooseLocation = (id?: number) => {
  const place = storagePlaces.value.find((item) => item.id === id && item.type === 'LOCATION')
  selectedLocation.value = place ? { id: place.id, name: `${place.name} (${place.code})` } : undefined
}
const choosePlaceByCode = (type: 'SHELF' | 'LOCATION', value: string) => {
  const input = value.trim()
  const match = /^WMS:(SHELF|LOCATION):(\d+)$/i.exec(input)
  const place = storagePlaces.value.find((item) => item.type === type && (match ? match[1].toUpperCase() === type && String(item.id) === match[2] : item.code === input))
  if (!place) { ElMessage.warning(`未找到当前仓库的${type === 'SHELF' ? '库区' : '库位'}`); return }
  if (type === 'SHELF') { selectedAreaId.value = place.id; changeArea() }
  else { selectedAreaId.value = place.parentId; chooseLocation(place.id) }
  placeCodes[type] = ''
}
const visibleDetails = computed(() => selectedLocation.value
  ? details.value.filter((item) => item.locationId === selectedLocation.value?.id)
  : details.value)
const checkBookTotal = computed(() => Number(details.value.reduce((sum, item) => sum + Number(item.quantity || 0), 0).toFixed(2)))
const checkActualTotal = computed(() => Number(details.value.reduce((sum, item) => sum + Number(item.checkQuantity || 0), 0).toFixed(2)))
const filterCheckLocation = (id?: number) => { selectedLocation.value = id ? { id, name: placePath(storagePlaces.value, id) } : undefined }
const savingReceipt = ref(false)
const dirty = ref(false)
const labels = { receipt: '入库', shipment: '出库', movement: '移库', check: '盘库', inventory: '库存' }
const orderApis = {
  receipt: { page: ReceiptOrderApi.getReceiptOrderPage, details: ReceiptOrderApi.getReceiptOrderDetailListByOrderId, complete: ReceiptOrderApi.completeReceiptOrder },
  shipment: { page: ShipmentOrderApi.getShipmentOrderPage, details: ShipmentOrderApi.getShipmentOrderDetailListByOrderId, complete: ShipmentOrderApi.completeShipmentOrder },
  movement: { page: MovementOrderApi.getMovementOrderPage, details: MovementOrderApi.getMovementOrderDetailListByOrderId, complete: MovementOrderApi.completeMovementOrder },
  check: { page: CheckOrderApi.getCheckOrderPage, details: CheckOrderApi.getCheckOrderDetailListByOrderId, complete: CheckOrderApi.completeCheckOrder }
}
const load = async () => {
  if (tab.value === 'inventory') inventoryExpanded.value = ''
  loading.value = true
  try {
    const params = { pageNo: page.value, pageSize: 20, ...(tab.value === 'inventory' ? { type: 'warehouse', skuCode: keyword.value || undefined } : { no: keyword.value || undefined, status: listStatus.value, orderTime: listDates.value?.length === 2 ? listDates.value : undefined }), ...(warehouseId.value ? (tab.value === 'movement' ? { sourceWarehouseId: warehouseId.value } : { warehouseId: warehouseId.value }) : {}) }
    const result = tab.value === 'inventory' ? await InventoryApi.getInventoryPage(params) : await orderApis[tab.value].page(params)
    rows.value = result.list
    total.value = result.total
  } finally { loading.value = false }
}
const confirmDiscard = async () => {
  if (pendingScans.value) { ElMessage.warning('物品扫码处理中，请稍候'); return false }
  const unsavedReceipt = (creatingReceipt.value && (!!editingOrder.value || receiptRows.value.length > 0)) || creatingMovement.value || creatingCheck.value
  if (!dirty.value && !unsavedReceipt) return true
  try {
    await ElMessageBox.confirm(unsavedReceipt ? '扫码明细尚未保存，离开将丢失修改。确定离开吗？' : '盘点数量尚未保存，离开将丢失修改。确定离开吗？', '提示', { type: 'warning' })
    dirty.value = false
    return true
  } catch { return false }
}
const changeTab = async (value: Tab) => {
  if (value === tab.value || !(await confirmDiscard())) return
  creatingCheck.value = false; creatingMovement.value = false; tab.value = value; warehouseCode.value = ''; showWarehouseChoices.value = false; keyword.value = ''; page.value = 1; selected.value = undefined; creatingReceipt.value = false; receiptRows.value = []; selectedLocation.value = undefined; load()
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
  if (dirty.value || (creatingReceipt.value && (!!editingOrder.value || receiptRows.value.length > 0)) || creatingMovement.value || creatingCheck.value) event.preventDefault()
}
onMounted(() => window.addEventListener('beforeunload', warnBeforeUnload))
onUnmounted(() => window.removeEventListener('beforeunload', warnBeforeUnload))
const search = () => { page.value = 1; load() }
const cancelCheck = async () => { if (await confirmDiscard()) { creatingCheck.value = false; editingOrder.value = undefined } }
const cancelMovement = async () => { if (await confirmDiscard()) { creatingMovement.value = false; editingOrder.value = undefined } }
const cancelReceipt = async () => {
  if (!(await confirmDiscard())) return
  creatingReceipt.value = false; editingOrder.value = undefined; selectedLocation.value = undefined; receiptRows.value = []
}
const editDraft = async () => {
  if (dirty.value) { ElMessage.warning('请先保存盘点数量'); return }
  const id = selected.value.id
  if (tab.value === 'movement') { editingOrder.value = await MovementOrderApi.getMovementOrder(id); if (editingOrder.value.status !== 0) { ElMessage.warning('单据状态已变化，请刷新'); return }; tabWarehouses.movement = { id: editingOrder.value.sourceWarehouseId, name: editingOrder.value.sourceWarehouseName || '' }; creatingMovement.value = true; return }
  if (tab.value === 'check') { editingOrder.value = await CheckOrderApi.getCheckOrder(id); if (editingOrder.value.status !== 0) { ElMessage.warning('单据状态已变化，请刷新'); return }; tabWarehouses.check = { id: editingOrder.value.warehouseId, name: editingOrder.value.warehouseName || '' }; creatingCheck.value = true; return }
  const order: any = tab.value === 'receipt' ? await ReceiptOrderApi.getReceiptOrder(id) : await ShipmentOrderApi.getShipmentOrder(id)
  if (order.status !== 0) { ElMessage.warning('单据状态已变化，请刷新'); return }
  editingOrder.value = order
  tabWarehouses[tab.value] = { id: order.warehouseId, name: order.warehouseName || '' }
  creatingReceipt.value = true
  await nextTick()
  receiptType.value = order.type; receiptMerchantId.value = order.merchantId; receiptRemark.value = order.remark || ''
  const time = typeof order.orderTime === 'number' || /^\d+$/.test(String(order.orderTime)) ? Number(order.orderTime) : new Date(order.orderTime).getTime()
  receiptDate.value = dateValue(time)
  originalReceiptDate.value = receiptDate.value
  receiptRows.value = (order.details || []).map((row: any) => ({ ...row, originalQuantity: row.quantity, originalPrice: row.price, arrivalId: tab.value === 'shipment' ? (row.arrivalId ?? 0) : row.arrivalId, rowId: ++nextReceiptRowId, sku: { id: row.skuId, code: row.skuCode, name: row.skuName, itemName: row.itemName, unit: row.unit } }))
  if (tab.value === 'shipment') await Promise.all(receiptRows.value.map((row) => selectRowArrival(row, row.arrivalId)))
}
const applyWarehouse = async (found: WarehouseVO) => {
  if (!found.id) return
  if (warehouseId.value !== found.id && creatingReceipt.value && !(await confirmDiscard())) return
  if (warehouseId.value !== found.id) { receiptRows.value = []; selectedLocation.value = undefined }
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
const clearWarehouse = async () => { if (creatingReceipt.value && !(await confirmDiscard())) return; warehouseId.value = undefined; warehouseName.value = ''; receiptRows.value = []; selectedLocation.value = undefined; search() }
const scanReceiptSku = async (code: string) => {
  if (!code) return
  const unique: ItemSkuVO[] = await request.get({ url: '/wms/item-sku/exact', params: { value: code } })
  if (unique.length !== 1 || !unique[0].id) { ElMessage.warning(unique.length ? '扫码结果不唯一，请使用规格编号' : '未找到商品规格'); return }
  if (tab.value === 'shipment') {
    if (!warehouseId.value) { ElMessage.warning('请先选择仓库'); return }
    const balances = await listArrivalBalances(warehouseId.value, unique[0].id)
    if (!balances.some((item) => (item.locationId ?? undefined) === selectedLocation.value?.id && Number(item.quantity) > 0)) { ElMessage.warning('该库位没有此物品的可用库存'); return }
  }
  const matchingRows = receiptRows.value.filter((item) => item.sku.id === unique[0].id && item.locationId === selectedLocation.value?.id && !item.batchNo)
  if (matchingRows.length > 1) { ElMessage.warning('该规格有多条明细，请手动调整对应数量'); return }
  const row = matchingRows[0]
  if (row?.serialNos?.length) { ElMessage.warning('该明细已录入序列号，请继续扫描序列号'); return }
  if (row?.arrivalId !== undefined && row.available === undefined) { ElMessage.warning('正在获取到货库存，请稍后重试'); return }
  const limit = row?.arrivalId !== undefined ? row.available : undefined
  if (limit !== undefined && (row?.quantity || 0) + 1 > limit) { ElMessage.warning('数量超过所选到货记录的可用库存'); return }
  if (row) row.quantity += 1
  else receiptRows.value.push({ rowId: ++nextReceiptRowId, sku: unique[0], quantity: 1, locationId: selectedLocation.value?.id, locationName: selectedLocation.value?.name, netLength: unique[0].netLength, grossLength: unique[0].grossLength, netWeight: unique[0].netWeight, grossWeight: unique[0].grossWeight, length: unique[0].length, width: unique[0].width, height: unique[0].height, price: tab.value === 'receipt' ? unique[0].costPrice : undefined, salesPrice: unique[0].sellingPrice })
}
const pendingScans = ref(0)
let scanQueue = Promise.resolve()
const queueReceiptScan = (value: string) => {
  const code = value.trim()
  if (!code) return
  receiptScan.value = ''
  pendingScans.value++
  scanQueue = scanQueue.then(() => scanReceiptSku(code)).catch(() => ElMessage.error(`处理 ${code} 失败，请重新扫描`)).finally(() => { pendingScans.value-- })
}
const selectSku = async (sku: ItemSkuVO) => {
  if (!sku.code) { ElMessage.warning('规格编号为空，无法添加'); return }
  queueReceiptScan(sku.code)
  skuPickerOpen.value = false
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
const selectRowBatch = (row: (typeof receiptRows.value)[number], batchNo?: string) => {
  row.batchNo = batchNo
  row.arrivalId = undefined
  row.available = undefined
  row.serialNos = []
}
const saveReceipt = async () => {
  if (pendingScans.value) { ElMessage.warning('物品扫码处理中，请稍候'); return }
  if (!receiptDate.value || receiptType.value == null || !warehouseId.value || !receiptRows.value.length || receiptRows.value.some((row) => !row.quantity || row.quantity <= 0 || (row.available !== undefined && row.quantity > row.available))) { ElMessage.warning('请选择单据类型、仓库并填写有效商品数量'); return }
  if (receiptRows.value.some((row) => row.id && row.totalPrice != null && row.price == null && Number(row.quantity) !== Number(row.originalQuantity))) { ElMessage.warning('修改数量前请先填写该明细单价'); return }
  if (tab.value === 'receipt' && receiptRows.value.some((row) => (!row.batchNo?.trim() && (row.productionDate || row.expirationDate)) || (row.productionDate && row.expirationDate && row.expirationDate < row.productionDate))) { ElMessage.warning('填写生产或到期日期时请填写批次号，且到期日期不能早于生产日期'); return }
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
    const data = { ...editingOrder.value, no: editingOrder.value?.no || `PDA${Date.now()}${Math.floor(Math.random() * 1000)}`, type: receiptType.value, orderTime: editingOrder.value && receiptDate.value === originalReceiptDate.value ? editingOrder.value.orderTime : String(new Date(`${receiptDate.value}T00:00:00`).getTime()), merchantId: receiptMerchantId.value, remark: receiptRemark.value || undefined, warehouseId: warehouseId.value, details: buildDraftDetails(receiptRows.value, tab.value === 'receipt', !!editingOrder.value) }
    let id = editingOrder.value?.id
    if (id) { if (tab.value === 'shipment') await ShipmentOrderApi.updateShipmentOrder(data); else await ReceiptOrderApi.updateReceiptOrder(data) }
    else id = tab.value === 'shipment' ? await ShipmentOrderApi.createShipmentOrder(data) : await ReceiptOrderApi.createReceiptOrder(data)
    ElMessage.success('草稿已保存')
    creatingReceipt.value = false
    editingOrder.value = undefined
    receiptRows.value = []
    selectedLocation.value = undefined
    const order = tab.value === 'shipment' ? await ShipmentOrderApi.getShipmentOrder(id) : await ReceiptOrderApi.getReceiptOrder(id)
    await open(order)
    await load()
  } finally { savingReceipt.value = false }
}
const open = async (row: any) => {
  if (tab.value === 'inventory') return
  selectedLocation.value = undefined
  scanCode.value = ''
  if (tab.value === 'receipt') {
    selected.value = await ReceiptOrderApi.getReceiptOrder(row.id)
    details.value = selected.value.details || []
  } else if (tab.value === 'check') {
    selected.value = await CheckOrderApi.getCheckOrder(row.id)
    details.value = (selected.value.details || []).map((item: any) => ({ ...item, arrivalId: item.id && item.arrivalId == null ? 0 : item.arrivalId }))
    storagePlaces.value = checkPermi(['wms:warehouse:query']) ? await request.get({ url: '/wms/storage-place/list', params: { warehouseId: selected.value.warehouseId } }) : []
    dirty.value = false
    scanCode.value = ''
  } else {
    details.value = (await orderApis[tab.value].details(row.id)).map((item: any) => ({ ...item, arrivalId: item.id && item.arrivalId == null ? 0 : item.arrivalId }))
    selected.value = row
  }
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
const deleteCheck = async () => {
  await ElMessageBox.confirm(`确定删除盘点草稿 ${selected.value.no}？`, '删除草稿', { type: 'warning' })
  await CheckOrderApi.deleteCheckOrder(selected.value.id)
  ElMessage.success('草稿已删除')
  selected.value = undefined
  await load()
}
const canSubmitApproval = () => {
  if (dirty.value) { ElMessage.warning('请先保存盘点数量'); return false }
  return true
}
const complete = async () => {
  if (dirty.value) { ElMessage.warning('请先保存盘点数量'); return }
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
  <main ref="scrollArea" class="pda">
    <header><h1 v-if="home">工作台</h1><button v-else class="header-home" @click="goHome">‹ 工作台</button><button @click="logout">退出登录</button></header>
    <section v-if="home" class="dashboard">
      <div v-if="canViewSummary" class="welcome"><div class="welcome-heading"><div><p>单据总览</p></div><Icon icon="ep:data-analysis" :size="25" /></div><div class="overview"><div v-for="item in [{type:1,label:'入库'},{type:2,label:'出库'},{type:3,label:'移库'},{type:4,label:'盘库'}]" :key="item.type"><span>{{ item.label }}</span><strong>{{ summaryCount(item.type) }}</strong></div></div></div>
      <h2>仓库作业</h2>
      <div class="work-grid"><button v-for="(label, key) in labels" :key="key" @click="openDashboardWork(key as Tab)"><span class="work-icon"><Icon :icon="{ receipt: 'ep:download', shipment: 'ep:upload', movement: 'ep:sort', check: 'ep:circle-check', inventory: 'ep:box' }[key]" /></span><strong>{{ label }}</strong></button></div>
      <template v-if="canViewSummary"><h2>单据进度</h2><div class="progress-grid"><div v-for="state in [{status:1,label:'待审批',icon:'ep:document-checked'}, {status:0,label:'草稿',icon:'ep:edit-pen'}]" :key="state.status" class="progress-card"><div class="progress-heading"><Icon :icon="state.icon" :size="19" /><strong>{{ state.label }}</strong></div><div class="progress-counts"><button v-for="item in [{type:1,label:'入库',tab:'receipt'},{type:2,label:'出库',tab:'shipment'},{type:3,label:'移库',tab:'movement'},{type:4,label:'盘库',tab:'check'}]" :key="item.type" type="button" @click="openDashboardWork(item.tab as Tab, state.status)"><span>{{ item.label }}</span><b>{{ summaryStatus(item.type, state.status) }}</b></button></div></div></div></template>
    </section>
    <template v-else>
    <nav><button v-for="(label, key) in labels" :key="key" :class="{ active: tab === key }" @click="changeTab(key as Tab)">{{ label }}</button></nav>
    <section v-if="creatingReceipt" class="content receipt-create">
      <button class="back" @click="cancelReceipt">‹ 返回列表</button>
      <h2>{{ editingOrder ? '修改' : '新建' }}{{ tab === 'shipment' ? '出库' : '入库' }}单</h2>
      <div class="receipt-info"><h3>仓库与位置</h3><div class="info-row"><span>仓库</span><strong>{{ warehouseName }}</strong></div>
      <div v-for="kind in (['SHELF', 'LOCATION'] as const)" :key="kind" class="place-group"><h4>{{ kind === 'SHELF' ? '库区' : '库位' }}（可选）</h4>
        <div class="method-row"><span>方式一</span><ScanButton :disabled="pendingScans > 0" :label="`扫码选择${kind === 'SHELF' ? '库区' : '库位'}`" @scan="(value) => choosePlaceByCode(kind, value)" /></div>
        <div class="method-row"><span>方式二</span><form class="search place-input" @submit.prevent="choosePlaceByCode(kind, placeCodes[kind])"><input v-model="placeCodes[kind]" :disabled="pendingScans > 0" :placeholder="`输入${kind === 'SHELF' ? '库区' : '库位'}编号`" /><button :disabled="pendingScans > 0">选择</button></form></div>
        <div class="method-row"><span>方式三</span><el-select v-if="kind === 'SHELF'" v-model="selectedAreaId" :disabled="pendingScans > 0" clearable filterable placeholder="从列表选择库区" @change="changeArea"><el-option v-for="place in storagePlaces.filter((item) => item.type === 'SHELF')" :key="place.id" :label="`${place.name} (${place.code})`" :value="place.id" /></el-select><el-select v-else :model-value="selectedLocation?.id" :disabled="pendingScans > 0" clearable filterable placeholder="从列表选择库位" @update:model-value="chooseLocation"><el-option v-for="place in storageLocations" :key="place.id" :label="`${place.name} (${place.code})`" :value="place.id" /></el-select></div>
      </div></div>
      <div class="receipt-info"><h3>基本信息</h3>
      <label class="info-row"><span>单据类型</span><select v-model.number="receiptType"><option v-for="option in getIntDictOptions(tab === 'shipment' ? DICT_TYPE.WMS_SHIPMENT_ORDER_TYPE : DICT_TYPE.WMS_RECEIPT_ORDER_TYPE)" :key="option.value" :value="option.value">{{ option.label }}</option></select></label>
      <label class="info-row"><span>往来单位</span><MerchantSelect v-model="receiptMerchantId" :supplier="tab === 'receipt'" :customer="tab === 'shipment'" /></label>
      <label class="info-row"><span>单据日期</span><input v-model="receiptDate" type="date" /></label>
      <label class="info-row"><span>备注</span><input v-model="receiptRemark" maxlength="255" placeholder="选填" /></label></div>
      <div class="receipt-tools"><h3>物品明细</h3>
      <div class="item-actions"><button type="button" class="item-action" @click="openSkuPicker"><Icon icon="ep:goods" :size="26" /><strong>选择物品</strong></button><ScanButton variant="card" label="连续扫码" continuous @scan="queueReceiptScan" /></div>
      <form class="search manual-scan" @submit.prevent="queueReceiptScan(receiptScan)"><input v-model="receiptScan" placeholder="扫码枪输入或手动输入物品条码" /><button>添加</button></form><p v-if="pendingScans">正在处理 {{ pendingScans }} 条扫码</p></div>
      <el-dialog v-model="skuPickerOpen" title="选择物品" width="min(520px, 95vw)" append-to-body>
        <form class="search" @submit.prevent="skuPage = 1; loadSkus()"><input v-model="skuKeyword" placeholder="输入物品名称" /><button>查询</button></form>
        <div v-loading="skuLoading" class="sku-picker">
          <button v-for="sku in skuRows" :key="sku.id" class="sku-option" @click="selectSku(sku)"><strong>{{ sku.itemName }} {{ sku.name }}</strong><span>规格编号：{{ sku.code }}</span></button>
          <el-empty v-if="!skuLoading && !skuRows.length" description="没有找到物品" />
        </div>
        <div class="pagination"><button :disabled="skuPage === 1" @click="skuPage--; loadSkus()">上一页</button><span>{{ skuPage }} / {{ Math.max(1, Math.ceil(skuTotal / 20)) }}</span><button :disabled="skuPage * 20 >= skuTotal" @click="skuPage++; loadSkus()">下一页</button></div>
      </el-dialog>
      <article v-for="(row, index) in receiptRows" :key="row.rowId" class="receipt-row">
        <strong>{{ row.sku.itemName }} {{ row.sku.name }}</strong>
        <p>{{ row.sku.code }} · {{ row.locationName || '未分配库位' }} · {{ row.batchNo || '未分配批次' }}</p>
        <div class="quantity-row"><span>数量</span><div class="quantity-control"><button type="button" :disabled="!!row.serialNos?.length || row.quantity <= 1" @click="row.quantity = Number((row.quantity - 1).toFixed(2))">−</button><input v-model.number="row.quantity" type="number" min="0.01" step="any" :disabled="!!row.serialNos?.length" /><button type="button" :disabled="!!row.serialNos?.length || (row.available !== undefined && row.quantity + 1 > row.available)" @click="row.quantity = Number((row.quantity + 1).toFixed(2))">＋</button></div><SerialEditor :model-value="row.serialNos" @update:model-value="row.serialNos = $event || []; if (row.serialNos.length) row.quantity = row.serialNos.length" /></div>
        <div v-if="tab === 'shipment'" class="shipment-stock"><label>库存批次<BatchSelect :model-value="row.batchNo" :warehouse-id="warehouseId" :sku-id="row.sku.id" :location-id="row.locationId" filter-location @update:model-value="selectRowBatch(row, $event)" /></label><label>到货记录<ArrivalSelect :model-value="row.arrivalId" @update:model-value="selectRowArrival(row, $event)" :warehouse-id="warehouseId" :sku-id="row.sku.id" :location-id="row.locationId" :batch-no="row.batchNo" /></label><label v-if="editingOrder">单价<input v-model.number="row.price" type="number" min="0" step="any" placeholder="选填" /></label></div>
        <details v-if="tab === 'receipt'" class="arrival-details"><summary>到货信息</summary><div class="arrival-grid"><label>批次号<input v-model="row.batchNo" maxlength="64" placeholder="选填" /></label><label>生产日期<input type="date" :value="dateValue(row.productionDate)" @input="row.productionDate = dateMillis($event)" /></label><label>到期日期<input type="date" :value="dateValue(row.expirationDate)" @input="row.expirationDate = dateMillis($event)" /></label><label v-for="field in arrivalFields" :key="field.key">{{ field.label }}<input v-if="field.text" v-model="row[field.key]" maxlength="64" :placeholder="field.label" /><input v-else v-model.number="row[field.key]" type="number" min="0" step="any" :placeholder="field.label" /></label></div></details>
        <div class="row-actions"><button class="back" @click="receiptRows.push({ ...row, id: undefined, rowId: ++nextReceiptRowId, quantity: 1, serialNos: [], arrivalId: undefined, available: undefined, palletNo: undefined, boxNo: undefined, rollNo: undefined })">复制明细</button><button class="back" @click="receiptRows.splice(index, 1)">删除</button></div>
      </article>
      <div class="receipt-save"><span>共 {{ receiptRows.length }} 条明细</span><button class="primary" :disabled="savingReceipt || pendingScans > 0" @click="saveReceipt">保存草稿</button></div>
    </section>
    <CheckCreate v-else-if="creatingCheck && warehouseId" :warehouse-id="warehouseId" :order="editingOrder" @close="cancelCheck" @saved="creatingCheck = false; editingOrder = undefined; selected = undefined; load()" />
    <MovementCreate v-else-if="creatingMovement && warehouseId" :source-warehouse-id="warehouseId" :order="editingOrder" @close="cancelMovement" @saved="creatingMovement = false; editingOrder = undefined; selected = undefined; load()" />
    <template v-else-if="!selected">
      <section class="work-section warehouse-section"><div class="section-heading"><h2>仓库</h2></div>
      <template v-if="!warehouseId">
        <div class="method-row"><span>方式一</span><ScanButton label="扫码选择仓库" @scan="(value) => { warehouseCode = value; selectWarehouse() }" /></div>
        <div class="method-row"><span>方式二</span><form class="search warehouse-input" @submit.prevent="selectWarehouse"><input v-model="warehouseCode" placeholder="输入仓库编号" /><button type="submit">选择仓库</button></form></div>
        <div class="method-row"><span>方式三</span><button class="warehouse-list-button" type="button" @click="showWarehouseChoices ? showWarehouseChoices = false : chooseWarehouse()">{{ showWarehouseChoices ? '收起仓库列表' : '从列表选择仓库' }} <span>{{ showWarehouseChoices ? '⌃' : '›' }}</span></button></div>
        <div v-if="showWarehouseChoices" class="warehouse-choices"><button v-for="warehouse in warehouseChoices" :key="warehouse.id" @click="applyWarehouse(warehouse)">{{ warehouse.name }}（{{ warehouse.code }}）</button><span v-if="!warehouseChoices.length">暂无仓库</span></div>
      </template>
      <template v-else>
        <div class="warehouse-current"><div><span>当前仓库</span><strong>{{ warehouseName }}</strong></div><button type="button" @click="clearWarehouse">更换</button></div>
        <button v-if="tab !== 'inventory'" v-hasPermi="[`wms:${tab}-order:create`]" class="create-order" @click="editingOrder = undefined; tab === 'check' ? creatingCheck = true : tab === 'movement' ? creatingMovement = true : (selectedLocation = undefined, receiptRows = [], receiptType = getIntDictOptions(tab === 'shipment' ? DICT_TYPE.WMS_SHIPMENT_ORDER_TYPE : DICT_TYPE.WMS_RECEIPT_ORDER_TYPE)[0]?.value, creatingReceipt = true)">＋ 新建{{ labels[tab] }}单</button>
      </template>
      </section>
      <section class="work-section list-section"><div class="section-heading"><h2>{{ tab === 'inventory' ? '库存列表' : '单据列表' }}</h2><span>共 {{ total }} 条</span></div>
      <div v-if="tab !== 'inventory'" class="list-filters"><el-select v-model="listStatus" clearable placeholder="单据状态" @change="search"><el-option v-for="option in getIntDictOptions(DICT_TYPE.WMS_ORDER_STATUS)" :key="option.value" :label="option.label" :value="option.value" /></el-select><el-date-picker v-model="listDates" type="daterange" value-format="YYYY-MM-DD HH:mm:ss" :default-time="[new Date(2000, 0, 1, 0, 0, 0), new Date(2000, 0, 1, 23, 59, 59)]" start-placeholder="开始日期" end-placeholder="结束日期" @change="search" /></div>
      <form class="search" @submit.prevent="search"><input v-model="keyword" :placeholder="tab === 'inventory' ? '扫描或输入 SKU 编号' : '扫描或输入单号'" /><ScanButton @scan="(value) => { keyword = value; search() }" /><button>查询</button></form>
      <div v-loading="loading" class="content">
        <article v-for="row in rows" :key="tab === 'inventory' ? `${row.warehouseId}-${row.skuId}` : row.id" @click="tab === 'inventory' ? openInventory(row) : open(row)">
          <template v-if="tab === 'inventory'"><strong>{{ row.itemName }} {{ row.skuName }}</strong><p>{{ row.skuCode }} · {{ row.warehouseName }}</p><b>库存 {{ row.quantity }} {{ row.unit }}</b><div v-if="inventoryExpanded === `${row.warehouseId}-${row.skuId}`" v-loading="inventoryLoading" class="inventory-places"><div v-for="(balance, index) in inventoryBalances" :key="index"><span>{{ placePath(inventoryPlaces, balance.locationId) }} · {{ balance.batchNo || '无批次' }}</span><strong>{{ balance.quantity }} {{ row.unit }}</strong></div><p v-if="!inventoryLoading && !inventoryBalances.length">暂无可显示的位置明细</p></div></template>
          <template v-else><strong>{{ tab === 'movement' ? `${row.sourceWarehouseName || '未分配'} → ${row.targetWarehouseName || '未分配'}` : (row.warehouseName || '未分配仓库') }}</strong><span class="status"><dict-tag :type="DICT_TYPE.WMS_ORDER_STATUS" :value="row.status" /></span><p>{{ row.no }}</p><div class="card-totals"><span>数量 {{ row.totalQuantity ?? 0 }}</span><span>¥ {{ Number(row.totalPrice ?? 0).toFixed(2) }}</span></div><small>点击查看明细 ›</small></template>
        </article>
        <el-empty v-if="!loading && !rows.length" description="暂无数据" />
        <div class="pagination"><button :disabled="page === 1" @click="page--; load()">上一页</button><span>{{ page }} / {{ Math.max(1, Math.ceil(total / 20)) }}</span><button :disabled="page * 20 >= total" @click="page++; load()">下一页</button></div>
      </div>
      </section>
    </template>
    <ReceiptDraftDetail v-else-if="tab === 'receipt' || tab === 'shipment' || tab === 'movement'" :kind="tab" :order="selected" :details="details" :approval-enabled="approvalEnabled" @back="backToList" @edit="editDraft" @updated="open(selected); load()" @changed="selected = undefined; load()" />
    <section v-else-if="tab === 'check'" class="content">
      <ApprovalActions v-if="(approvalEnabled || selected.status === 1) && !dirty" :before-submit="canSubmitApproval" type="check" :id="selected.id" :status="selected.status" @changed="selected = undefined; load()" />
      <button class="back" @click="backToList">‹ 返回列表</button><div class="check-summary"><span>盘库单</span><strong>{{ selected.no }}</strong><span>{{ selected.warehouseName }}</span><div class="check-stats"><div>明细<b>{{ details.length }}</b></div><div>账面数量<b>{{ checkBookTotal }}</b></div><div>实盘数量<b>{{ checkActualTotal }}</b></div></div></div><h2 class="check-heading">盘点明细</h2>
      <template v-if="selected.status === 0">
        <details class="check-tools"><summary>位置筛选与扫码录入</summary><PlacePicker :model-value="selectedLocation?.id" :warehouse-id="selected.warehouseId" @update:model-value="filterCheckLocation" /><form class="search" @submit.prevent="scanCheck"><input v-model="scanCode" placeholder="扫描 SKU 或批次编号，数量加 1" /><ScanButton @scan="(value) => { scanCode = value; scanCheck() }" /><button>扫码录入</button></form><button class="back" @click="resetCheck">实盘数量清零</button></details>
      </template>
      <article v-for="item in visibleDetails" :key="item.id"><strong>{{ item.itemName }} {{ item.skuName }}</strong><p>{{ item.skuCode }}</p><p>库区 / 库位：{{ placePath(storagePlaces, item.locationId) }}</p><p v-if="item.batchNo">批次：{{ item.batchNo }}</p><p>到货记录：{{ item.arrivalId === 0 ? '历史未分配' : item.arrivalId == null ? '未选择' : item.arrivalId }}</p><b>账面数量 {{ item.quantity }} {{ item.unit }}</b><p v-if="selected.status === 0">实盘序列号 <SerialEditor :model-value="item.actualSerialNos" @update:model-value="setCheckSerials(item, $event)" /></p><p>实盘数量 <el-input-number v-if="selected.status === 0" v-model="item.checkQuantity" :min="0" :precision="QUANTITY_PRECISION" :controls="false" @change="dirty = true" /><span v-else>{{ item.checkQuantity }}</span></p></article>
      <button v-if="selected.status === 0" v-hasPermi="['wms:check-order:update']" class="primary" @click="saveCheck">保存盘点数量</button>
      <button v-if="selected.status === 0" v-hasPermi="['wms:check-order:update']" class="primary" @click="editDraft">修改草稿明细</button>
      <button v-if="selected.status === 0" v-hasPermi="['wms:check-order:delete']" class="back" @click="deleteCheck">删除草稿</button>
      <button v-if="selected.status === 0 && !approvalEnabled" v-hasPermi="['wms:check-order:complete']" class="primary" @click="complete">确认完成盘库</button>
    </section>
    </template>
    <nav class="bottom-nav" aria-label="移动端导航"><button :class="{ chosen: home }" @click="goHome"><Icon icon="ep:house" :size="22" />工作台</button><button :class="{ chosen: !home && tab === 'inventory' }" @click="openWork('inventory')"><Icon icon="ep:box" :size="22" />库存</button><button :class="{ chosen: !home && tab !== 'inventory' }" @click="openWork('receipt')"><Icon icon="ep:document" :size="22" />单据</button></nav>
  </main>
  <Teleport to="body"><div v-if="scrollIndicator.visible" ref="scrollTrack" class="scroll-indicator" aria-label="页面滚动条" @pointerdown.prevent="dragScroll"><span :style="{ top: `${scrollIndicator.top}%`, height: `${scrollIndicator.height}%` }"></span></div></Teleport>
</template>

<style scoped>
.pda { max-width: 640px; min-height: 100vh; margin: auto; background: #f4f7fb; color: #17263c; font-family: system-ui, sans-serif; } header { padding: 25px 20px 18px; display:flex; justify-content:space-between; align-items:center; background:#fff; } header small { color:#528ee8; font-weight:700; letter-spacing:.16em; } h1 { margin:4px 0; font-size:25px; } header a { color:#438ce7; } nav { display:flex; gap:8px; padding:12px 16px; background:#fff; } button { border:0; cursor:pointer; } nav button { flex:1; padding:12px; background:#f0f4f9; border-radius:10px; color:#56667b; } nav .active,.primary { background:#428ff3; color:white; } .search { display:flex; gap:8px; padding:16px; } .search input { flex:1; min-width:0; border:1px solid #dce5f1; border-radius:10px; padding:12px; font-size:16px; } .search button:not(.scan-trigger),.primary { background:#428ff3; color:white; padding:12px 20px; border-radius:10px; } .content { padding:0 16px 30px; } .hint, p, small { color:#77879c; } article { background:white; border-radius:14px; padding:17px; margin-bottom:10px; box-shadow:0 3px 14px #203f6810; } article p { margin:10px 0; } article b { color:#287bdc; } .status { float:right; color:#418ae5; } .pagination { display:flex; justify-content:space-between; align-items:center; padding:12px 0; } .pagination button,.back { background:white; color:#438ce7; border-radius:8px; padding:10px; } .primary { width:100%; margin-top:12px; }
.dashboard { padding: 20px 16px; } .welcome { background: #245eea; color: white; padding: 26px 22px; border-radius: 18px; } .welcome p { color: #dbe8ff; margin: 0; } .welcome h2 { margin: 16px 0 8px; font-size: 24px; } .welcome span { color: #dbe8ff; } .dashboard > h2 { font-size: 18px; margin: 28px 0 14px; } .work-grid { display: grid; grid-template-columns: repeat(2,1fr); gap: 12px; } .work-grid button { display: grid; text-align: left; gap: 8px; background: white; padding: 18px; border-radius: 14px; color: #17263c; } .work-grid button span { color: #245eea; font-size: 26px; } .work-grid button strong { font-size: 17px; } .home-link { margin: 10px 16px 0; background: transparent; color: #245eea; padding: 8px 0; }
.overview { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-top: 20px; } .overview div { display: grid; gap: 8px; text-align: center; } .overview strong { font-size: 24px; }
.arrival-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:10px; margin-top:12px; } .arrival-grid label { display:grid; gap:5px; color:#56667b; font-size:13px; } .arrival-grid input { min-width:0; width:100%; border:1px solid #dce5f1; border-radius:8px; padding:9px; font-size:16px; }
.warehouse-choices { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:8px; margin-top:8px; } .warehouse-choices button { padding:12px; text-align:left; background:white; border:1px solid #dce5f1; border-radius:8px; color:#17263c; }
.sku-picker { max-height: 48vh; overflow-y: auto; } .sku-option { display:grid; width:100%; gap:6px; text-align:left; padding:12px; background:white; border-bottom:1px solid #e5ebf3; color:#17263c; } .sku-option span { color:#77879c; font-size:13px; }

.pda { height: auto; min-height: 100vh; overflow: visible; padding-bottom: calc(90px + env(safe-area-inset-bottom)); box-sizing: border-box; }
.pda > header { position: sticky; top: 0; z-index: 4; padding: 14px 18px; border-bottom: 1px solid #edf1f6; }
.pda > header h1 { font-size: 21px; font-weight: 700; }.pda > header button { background: #f3f6fb; color: #61728a; border-radius: 10px; padding: 9px 12px; }
nav:not(.bottom-nav) { margin: 8px 16px; padding: 5px; gap: 3px; border-radius: 12px; background: #e9eef6; overflow-x: auto; }
nav:not(.bottom-nav) button { min-width: 62px; padding: 11px 8px; background: transparent; white-space: nowrap; font-weight: 600; }
nav:not(.bottom-nav) .active { background: #2461ee; box-shadow: 0 5px 12px #245eea38; color: #fff; }
.dashboard { padding-top: 16px; }.welcome { background: linear-gradient(125deg, #3077ff, #1947d7); box-shadow: 0 10px 24px #245eea30; }.work-grid { grid-template-columns: repeat(2,1fr); }.work-grid button { min-height: 104px; box-shadow: 0 4px 16px #233e6010; }
.content article { border: 1px solid #edf1f6; }.status { font-size: 13px; }
.bottom-nav { position: fixed; z-index: 9; bottom: 0; left: 50%; transform: translateX(-50%); width: min(100%, 640px); padding: 8px 12px calc(8px + env(safe-area-inset-bottom)); box-sizing: border-box; display: flex; background: #fff; border-top: 1px solid #e9edf5; box-shadow: 0 -5px 18px #172b4d0a; }.bottom-nav button { display: grid; place-items: center; gap: 2px; flex: 1; background: transparent; color: #7d889b; font-size: 12px; }.bottom-nav button span { font-size: 23px; line-height: 1; }.bottom-nav button.chosen { color: #2461ee; font-weight: 700; }
.floating-add { position: fixed; z-index: 8; right: max(20px, calc((100vw - 640px)/2 + 20px)); bottom: calc(90px + env(safe-area-inset-bottom)); width: 58px; height: 58px; border-radius: 50%; background: #2461ee; color: #fff; font-size: 34px; box-shadow: 0 7px 20px #245eea66; }

.receipt-create { padding-top: 8px; padding-bottom: 35px; }.receipt-create h2 { font-size: 21px; margin: 12px 0 16px; }.receipt-create h3 { font-size: 17px; margin: 0 0 12px; color: #192a41; }.receipt-info,.receipt-tools { background: #fff; border-radius: 16px; padding: 18px; margin: 12px 0; box-shadow: 0 4px 18px #263f6010; }.receipt-info > label:not(.info-row) { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 12px 0; color: #61728a; }.receipt-info input,.receipt-info select { min-width: 0; max-width: 64%; border: 0; background: #f4f7fb; border-radius: 8px; padding: 9px; color: #273b56; font-size: 15px; }.info-row { display: flex; justify-content: space-between; align-items: center; gap: 12px; min-height: 47px; border-bottom: 1px solid #eff2f6; color: #61728a; }.info-row strong { color: #243752; }.info-row :deep(.el-select) { width: 64%; }.receipt-tools p { font-size: 13px; margin: 4px 0 10px; }.receipt-tools .search { padding: 6px 0; gap: 6px; }.receipt-tools .search input { width: 0; padding: 10px; }.receipt-tools .search button:not(.scan-trigger) { white-space: nowrap; padding: 10px; }.select-item { width: 100%; background: #eaf2ff; color: #2461ee; border-radius: 10px; padding: 13px; margin-top: 8px; font-weight: 700; }.receipt-save { display:flex; align-items:center; gap:12px; position:sticky; bottom:calc(69px + env(safe-area-inset-bottom)); z-index:3; background:#fff; border:1px solid #edf1f6; border-radius:14px; padding:10px 14px; box-shadow:0 -4px 18px #203f6810; }.receipt-save span { flex:1; font-size:13px; color:#718198; white-space:nowrap; }.receipt-save .primary { width:auto; min-width:120px; margin:0; }
.list-filters { display:flex; gap:8px; padding:8px 16px; }.list-filters :deep(.el-select) { width:125px; flex:none; }.list-filters :deep(.el-date-editor) { min-width:0; flex:1; }.card-totals { display:flex; justify-content:space-between; margin:12px 0; color:#26374e; font-weight:600; }
.warehouse-hint { margin: 0 16px 10px; padding: 10px 12px; border-radius: 10px; background: #edf4ff; color: #426794; font-size: 13px; }

/* Mobile workspace: clear hierarchy and generous touch targets. */
.welcome-heading { display:flex; align-items:center; justify-content:space-between; }.welcome-heading span { display:block; font-size:12px; color:#d8e5ff; margin-bottom:5px; }.welcome-heading p { font-size:19px; }
.quick-create { display:grid; grid-template-columns:1fr 1fr; gap:10px; margin-top:14px; }.quick-create button { display:flex; align-items:center; gap:9px; padding:15px; background:#eaf1ff; color:#245eea; border:1px solid #dce8ff; border-radius:14px; font-size:15px; font-weight:700; }.quick-create button :last-child { margin-left:auto; }
.dashboard > h2 { display:flex; align-items:baseline; justify-content:space-between; }.dashboard > h2 small { color:#9aa8ba; font-size:12px; font-weight:400; }
.progress-grid { display:grid; grid-template-columns:1fr 1fr; gap:10px; }.progress-card { padding:16px 13px; border:1px solid #e8eef8; border-radius:16px; background:#fff; text-align:left; color:#172840; }.progress-heading { display:flex; gap:8px; align-items:center; color:#245eea; }.progress-heading strong { color:#172840; }.progress-heading :last-child { margin-left:auto; color:#9caabd; }.progress-counts { display:grid; grid-template-columns:repeat(2,1fr); gap:12px; margin-top:17px; }.progress-counts div { display:grid; gap:5px; }.progress-counts span { font-size:12px; color:#91a0b4; }.progress-counts b { font-size:20px; }

.pda { background: #f3f6fb; color: #172840; }
.pda > header { min-height: 60px; background: rgba(255,255,255,.97); backdrop-filter: blur(12px); }
.pda > header h1 { font-size: 20px; letter-spacing: .02em; }
.dashboard { padding: 20px 16px 28px; }
.welcome { position: relative; overflow: hidden; padding: 23px 20px 24px; border-radius: 20px; background: linear-gradient(130deg,#367bff 0%,#245eea 55%,#1748cb 100%); box-shadow: 0 12px 26px rgba(36,94,234,.22); }
.welcome::after { content: ''; position: absolute; width: 150px; height: 150px; right: -58px; top: -64px; border: 22px solid rgba(255,255,255,.08); border-radius: 50%; pointer-events: none; }
.welcome p { font-size: 16px; font-weight: 650; color: #fff; }
.overview { margin-top: 24px; }
.overview div + div { border-left: 1px solid rgba(255,255,255,.18); }
.overview span { font-size: 13px; }.overview strong { font-size: 27px; line-height: 1.1; }
.dashboard > h2 { margin: 24px 2px 12px; font-size: 17px; }
.work-grid { grid-template-columns: repeat(5,minmax(0,1fr)); gap: 4px; padding: 14px 6px; border-radius: 16px; background:#fff; }
.work-grid button { min-height: 76px; padding: 5px 0; display:grid; justify-items:center; text-align:center; border:0; background:#fff; box-shadow:none; }
.work-grid .work-icon { display: grid; place-items: center; width: 38px; height: 38px; border-radius: 12px; background: #eaf1ff; color: #245eea; font-size: 22px; }
.work-grid strong { font-size: 13px; }.work-grid small { font-size: 12px; color: #8796ab; }
.content article { margin-bottom: 12px; padding: 18px; border-radius: 16px; box-shadow: 0 4px 16px rgba(31,54,88,.05); }
.content article strong { font-size: 16px; }.card-totals { padding-top: 12px; border-top: 1px solid #edf1f7; }
.list-filters { align-items: center; }.list-filters :deep(.el-input__wrapper), .list-filters :deep(.el-select__wrapper) { min-height: 42px; border-radius: 10px; }
.bottom-nav button { min-height: 45px; }.floating-add { width: 56px; height: 56px; }
@media (max-width: 380px) { .overview strong { font-size: 23px; } .list-filters { flex-wrap: wrap; } .list-filters :deep(.el-date-editor) { min-width: 210px; } }

.item-actions { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:10px; margin:12px 0; }
.item-action { min-height:94px; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:8px; border:1px solid #dce9ff; border-radius:14px; background:linear-gradient(145deg,#f0f6ff,#e2edff); color:#245eea; }
.item-action strong { color:#253954; font-size:14px; }.manual-scan { margin-top:4px; }
.search :deep(.el-button) { min-height:44px; border-radius:12px; font-weight:600; }
.warehouse-choices button,.back { border:1px solid #dce7f8; background:#f7faff; color:#245eea; font-weight:600; }
.pda::-webkit-scrollbar { width: 5px; }
.pda::-webkit-scrollbar-thumb { background: #8ca5cc; border-radius: 8px; }
.scroll-indicator { position: fixed; z-index: 100; top: 66px; bottom: calc(80px + env(safe-area-inset-bottom)); right: max(0px, calc((100vw - 640px) / 2)); width: 20px; touch-action: none; }
.scroll-indicator span { position: absolute; right: 3px; width: 5px; border-radius: 5px; background: #8195b3; box-shadow: 0 0 0 1px rgba(255,255,255,.7); }
.work-section { margin: 14px 16px; padding: 16px; border: 1px solid #e8eef8; border-radius: 16px; background: #fff; }
.work-section h2, .results-heading h2 { margin: 0; color: #172840; font-size: 16px; }
.work-section .search { padding: 12px 0 0; }
.work-section .content { padding: 10px 0 0; }
.work-section .list-filters { padding: 12px 0 0; }
.results-heading { display: flex; align-items: center; justify-content: space-between; margin: 22px 2px 12px; }
.results-heading span { color: #8290a5; font-size: 13px; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.section-heading span { color: #8290a5; font-size: 13px; }
.warehouse-section :deep(.warehouse-scan) { width: 100%; min-height: 50px; margin-top: 16px; background: #edf4ff; border-color: #dce9ff; }
.warehouse-section .warehouse-input { padding-top: 10px; }
.warehouse-input input { width: 0; }
.warehouse-input button { padding: 10px 16px !important; white-space: nowrap; }
.warehouse-list-button { display: flex; width: 100%; min-height: 45px; align-items: center; justify-content: space-between; margin-top: 10px; padding: 0 14px; border: 1px solid #e2e9f3; border-radius: 10px; background: #fff; color: #456487; text-align: left; }
.warehouse-list-button span { color: #8ea1b9; font-size: 20px; }
.warehouse-current { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 14px; padding: 14px; border-radius: 12px; background: #f4f7fc; }
.warehouse-current div { display: grid; min-width: 0; gap: 5px; }.warehouse-current span { color: #8190a5; font-size: 12px; }.warehouse-current strong { overflow-wrap: anywhere; color: #172840; font-size: 16px; }
.warehouse-current button { flex: none; padding: 8px 10px; border-radius: 8px; background: #fff; color: #245eea; }
.create-order { width: 100%; min-height: 46px; margin-top: 12px; padding: 0 12px; border-radius: 10px; background: #245eea; color: #fff; font-size: 15px; font-weight: 600; }
.list-section .content { padding-top: 16px; }
.list-section .content article { background: #f7f9fd; box-shadow: none; }
.progress-counts button { display: grid; gap: 5px; padding: 2px 0; background: transparent; text-align: left; }
.receipt-info .location-search { padding: 14px 0 0; gap: 6px; }
.receipt-info .location-search input { flex: 1; width: 0; max-width: none; border: 1px solid #dce5f1; background: #fff; }
.receipt-info .location-search button:not(.scan-trigger) { padding: 10px; white-space: nowrap; }
.check-summary { display: grid; gap: 6px; padding: 20px; border-radius: 18px; background: linear-gradient(130deg,#3478f7,#1b4bd2); color: #fff; }.check-summary > span { color: #dbe8ff; font-size: 13px; }.check-summary > strong { overflow-wrap: anywhere; font-size: 19px; }.check-stats { display: grid; grid-template-columns: repeat(3,1fr); margin-top: 16px; }.check-stats div { display: grid; gap: 6px; color: #dbe8ff; font-size: 13px; }.check-stats b { color: #fff; font-size: 21px; }.check-heading { margin: 24px 2px 12px; font-size: 17px; }.check-tools { margin: 12px 0; padding: 14px; border-radius: 14px; background: #fff; }.check-tools summary { color: #245eea; font-weight: 600; }.check-tools .search { padding: 12px 0; }
.inventory-places { margin-top: 14px; border-top: 1px solid #e5ebf3; }.inventory-places > div { display: flex; justify-content: space-between; gap: 12px; padding: 10px 0; color: #61728a; font-size: 13px; }.inventory-places strong { flex: none; color: #172840; }
.pda > header .header-home { padding: 8px 0; background: transparent; color: #245eea; font-size: 16px; font-weight: 600; }
.method-row { display: flex; align-items: center; gap: 10px; margin-top: 12px; }
.method-row > span { flex: none; width: 48px; color: #718198; font-size: 13px; }
.method-row :deep(.scan-trigger), .method-row :deep(.el-select), .method-row form, .method-row .warehouse-list-button { flex: 1; min-width: 0; width: 100%; margin: 0; }
.method-row .search { padding: 0; }.method-row .search input { width: 0; max-width: none; }
.method-row .search button { white-space: nowrap; padding: 10px 12px; }
.place-group { padding-top: 16px; }.place-group + .place-group { margin-top: 16px; border-top: 1px solid #edf1f6; }
.place-group h4 { margin: 0; color: #243752; font-size: 15px; }.place-input :deep(.el-select) { width: 100%; }
.receipt-row .quantity-row { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin: 16px 0; }
.quantity-row > span { color: #61728a; font-size: 14px; }.quantity-control { display: flex; align-items: stretch; height: 42px; border: 1px solid #dce5f1; border-radius: 10px; overflow: hidden; }
.quantity-control button { width: 42px; background: #f4f7fb; color: #245eea; font-size: 20px; }.quantity-control button:disabled { color: #b5c0ce; }
.quantity-control input { width: 74px; min-width: 0; border: 0; text-align: center; font-size: 16px; appearance: textfield; }.quantity-control input::-webkit-inner-spin-button { appearance: none; }
.arrival-details { border-top: 1px solid #edf1f6; padding-top: 14px; }.arrival-details summary { color: #245eea; cursor: pointer; font-weight: 600; }.row-actions { display: flex; gap: 8px; margin-top: 16px; }
.shipment-stock { display: grid; gap: 12px; margin: 14px 0; }.shipment-stock label { display: grid; gap: 6px; color: #61728a; font-size: 13px; }
.shipment-stock input { min-height: 42px; padding: 0 12px; border: 1px solid #dce5f1; border-radius: 10px; font-size: 16px; }
@media (pointer: fine) { .scroll-indicator { display: none; } }
@media (pointer: coarse) { :global(html.pda-scroll-page) { scrollbar-width: none; } :global(html.pda-scroll-page::-webkit-scrollbar) { display: none; } }
@media (max-width: 768px) { .pda input, .pda select, .pda textarea, .pda :deep(input), .pda :deep(textarea), :global(.el-dialog input), :global(.el-dialog textarea) { font-size: 16px !important; } }
</style>
