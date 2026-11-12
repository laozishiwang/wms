<script setup lang="ts">
import request from '@/config/axios'
import download from '@/utils/download'
import type { ShipmentOrderDetailVO } from '@/api/wms/order/shipment/detail'
import { dividePrice, multiplyPrice } from '@/views/wms/utils/format'

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
    const rows: ShipmentOrderDetailVO[] = []
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
      const balances: any[] = await request.get({ url: '/wms/storage-place/dimension-inventory', params: { warehouseId: props.warehouseId, skuId: sku.id, batchNo: batchNo || '' } })
      const balance = balances.find((item) => (item.locationId ?? undefined) === locationId)
      const dimension = JSON.stringify([sku.id, locationId ?? null, batchNo ?? ''])
      const cumulative = Number(((requested.get(dimension) || 0) + quantity).toFixed(2))
      if (!balance || Number(balance.quantity) < cumulative) { message.error(`第 ${index + 1} 行与前面相同库位和批次的数量合计超过库存`); return }
      requested.set(dimension, cumulative)
      const serialNos = String(record.serialNumbers || '').split(/[\r\n,，;；]+/).map((s) => s.trim()).filter(Boolean)
      if (serialNos.length && (serialNos.length !== quantity || new Set(serialNos).size !== serialNos.length || serialNos.some((serial) => serial.length > 128 || usedSerials.has(`${sku.id}:${serial}`)))) { message.error(`第 ${index + 1} 行序列号数量不符或重复`); return }
      for (const serialNo of serialNos) {
        const matches: Array<{ warehouseId: number; locationId?: number | null; batchNo?: string | null; inStock: boolean }> = await request.get({ url: '/wms/serial-inventory/find', params: { skuId: sku.id, serialNo } })
        if (!matches.some((item) => item.inStock && item.warehouseId === props.warehouseId && (item.locationId ?? undefined) === locationId && (item.batchNo || undefined) === batchNo)) { message.error(`第 ${index + 1} 行序列号不在所选仓库、库位和批次`); return }
        usedSerials.add(`${sku.id}:${serialNo}`)
      }
      const total = record.totalPrice == null ? undefined : Number(record.totalPrice)
      const price = record.price == null ? dividePrice(total, quantity) : Number(record.price)
      if ((price !== undefined && (!Number.isFinite(price) || price < 0)) || (total !== undefined && (!Number.isFinite(total) || total < 0))) { message.error(`第 ${index + 1} 行价格或金额不正确`); return }
      rows.push({ skuId: sku.id, itemId: sku.itemId, itemCode: sku.itemCode, itemName: sku.itemName, skuCode: sku.code, skuName: sku.name, unit: sku.unit, warehouseId: props.warehouseId, quantity, price, totalPrice: total ?? multiplyPrice(quantity, price), batchNo, locationId, serialNos, availableQuantity: Number(balance.quantity) })
    }
    emit('imported', rows); message.success(`已导入 ${rows.length} 行，请核对后保存`)
  } finally { loading.value = false; element.value = '' }
}
</script>

<template>
  <el-button plain :loading="loading" @click="template">下载导入模板</el-button>
  <el-button plain :disabled="!warehouseId" :loading="loading" @click="input?.click()">导入明细</el-button>
  <input ref="input" type="file" accept=".xls,.xlsx" hidden @change="upload" />
</template>
