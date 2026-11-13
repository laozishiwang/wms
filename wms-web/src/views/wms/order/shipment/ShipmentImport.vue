<script setup lang="ts">
import request from '@/config/axios'
import download from '@/utils/download'
import type { ShipmentOrderDetailVO } from '@/api/wms/order/shipment/detail'
import { dividePrice, multiplyPrice } from '@/views/wms/utils/format'
import { listArrivalBalances, listArrivalSerials } from '@/views/wms/inventory/components/arrival-api'

const props = defineProps<{ warehouseId?: number }>()
const emit = defineEmits<{ imported: [rows: ShipmentOrderDetailVO[]] }>()
const message = useMessage()
const input = ref<HTMLInputElement>()
const loading = ref(false)
const template = async () => download.excel(await request.download({ url: '/wms/shipment-order/import-template' }), '出库明细导入模板.xls')
const upload = async (event: Event) => {
  const element = event.target as HTMLInputElement
  const file = element.files?.[0]
  if (!file || !props.warehouseId) return
  loading.value = true
  try {
    const data = new FormData(); data.append('file', file)
    const response: any = await request.upload({ url: '/wms/shipment-order/import-parse', data })
    const records = response.data as any[]
    if (!records?.length) { message.warning('模板没有明细'); return }
    const places: any[] = await request.get({ url: '/wms/storage-place/list', params: { warehouseId: props.warehouseId } })
    const rows: Array<ShipmentOrderDetailVO & { arrivalId: number }> = []
    const requested = new Map<string, number>()
    const usedSerials = new Set<string>()
    for (const [index, record] of records.entries()) {
      const code = String(record.skuCode || '').trim(), quantity = Number(record.quantity)
      if (!code || !Number.isFinite(quantity) || quantity <= 0) { message.error(`第 ${index + 1} 行规格编号或数量不正确`); return }
      const skus: any[] = await request.get({ url: '/wms/item-sku/exact', params: { value: code } })
      const matches = skus.filter((sku) => sku.code === code)
      if (matches.length !== 1) { message.error(`第 ${index + 1} 行规格编号不存在或不唯一`); return }
      const sku = matches[0], batchNo = record.batchNo?.trim() || undefined, locationCode = record.locationCode?.trim()
      const locations = locationCode ? places.filter((item) => item.type === 'LOCATION' && item.code === locationCode) : []
      if (locationCode && locations.length !== 1) { message.error(`第 ${index + 1} 行库位不存在或不唯一`); return }
      const locationId = locations[0]?.id
      const rawArrival = record.arrivalId
      const arrivalId = rawArrival == null || String(rawArrival).trim() === '' ? undefined : Number(rawArrival)
      if (arrivalId === undefined || !Number.isSafeInteger(arrivalId) || arrivalId < 0) { message.error(`第 ${index + 1} 行请填写有效的到货记录 ID，历史未分配填 0`); return }
      const balances = await listArrivalBalances(props.warehouseId, sku.id)
      const balance = balances.find((item) => (item.locationId ?? undefined) === locationId && (item.batchNo || undefined) === batchNo && (item.arrivalId ?? 0) === arrivalId)
      const dimension = JSON.stringify([sku.id, locationId ?? null, batchNo ?? '', arrivalId])
      const cumulative = Number(((requested.get(dimension) || 0) + quantity).toFixed(2))
      if (!balance || Number(balance.quantity) < cumulative) { message.error(`第 ${index + 1} 行所选到货记录的可用数量不足`); return }
      requested.set(dimension, cumulative)
      const serialNos = String(record.serialNumbers || '').split(/[\r\n,，;；]+/).map((s) => s.trim()).filter(Boolean)
      if (serialNos.length && (serialNos.length !== quantity || new Set(serialNos).size !== serialNos.length || serialNos.some((serial) => serial.length > 128 || usedSerials.has(`${sku.id}:${serial}`)))) { message.error(`第 ${index + 1} 行序列号数量不符或重复`); return }
      if (serialNos.length) {
        const available = await listArrivalSerials(props.warehouseId, sku.id, locationId ?? null, batchNo ?? null, arrivalId)
        for (const serialNo of serialNos) {
          if (!available.includes(serialNo)) { message.error(`第 ${index + 1} 行序列号不属于所选到货记录`); return }
          usedSerials.add(`${sku.id}:${serialNo}`)
        }
      }
      const total = record.totalPrice == null ? undefined : Number(record.totalPrice)
      const price = record.price == null ? dividePrice(total, quantity) : Number(record.price)
      if ((price !== undefined && (!Number.isFinite(price) || price < 0)) || (total !== undefined && (!Number.isFinite(total) || total < 0))) { message.error(`第 ${index + 1} 行价格或金额不正确`); return }
      rows.push({ skuId: sku.id, itemId: sku.itemId, itemCode: sku.itemCode, itemName: sku.itemName, skuCode: sku.code, skuName: sku.name, unit: sku.unit, warehouseId: props.warehouseId, arrivalId, quantity, price, totalPrice: total ?? multiplyPrice(quantity, price), batchNo, locationId, serialNos })
    }
    emit('imported', rows); message.success(`已导入 ${rows.length} 行，请核对后保存`)
  } finally { loading.value = false; element.value = '' }
}
</script>

<template>
  <el-button plain :loading="loading" @click="template">下载导入模板</el-button>
  <el-button plain :disabled="!warehouseId" :loading="loading" @click="input?.click()">导入明细</el-button>
  <span class="text-12px text-gray-500">到货记录 ID 可在库存管理中查看；历史未分配填写 0</span>
  <input ref="input" type="file" accept=".xls,.xlsx" hidden @change="upload" />
</template>
