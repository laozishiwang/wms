
import request from '@/config/axios'

export interface ArrivalBalance {
  warehouseId: number
  skuId: number
  locationId: number | null
  batchNo: string | null
  arrivalId: number | null
  quantity: number
  palletNo?: string | null
  boxNo?: string | null
  rollNo?: string | null
  netLength?: number | null
  grossLength?: number | null
  netWeight?: number | null
  grossWeight?: number | null
  length?: number | null
  width?: number | null
  height?: number | null
  costPrice?: number | null
  salesPrice?: number | null
}

export const listArrivalBalances = (warehouseId: number, skuId: number): Promise<ArrivalBalance[]> =>
  request.get({ url: '/wms/arrival-inventory/list', params: { warehouseId, skuId } })

export const listArrivalSerials = (warehouseId: number, skuId: number, locationId: number | null, batchNo: string | null, arrivalId: number): Promise<string[]> =>
  request.get({ url: '/wms/serial-inventory/list', params: { warehouseId, skuId, locationId: locationId ?? undefined, batchNo: batchNo || undefined, arrivalId } })

export async function validateArrivalBalances(warehouseId: number, details: Array<{ skuId?: number; locationId?: number | null; batchNo?: string | null; arrivalId?: number | null; quantity?: number }>, count: boolean) {
  const cache = new Map<number, ArrivalBalance[]>()
  const totals = new Map<string, number>()
  for (const [index, row] of details.entries()) {
    if (row.skuId == null || row.arrivalId == null) throw new Error(`第 ${index + 1} 行请选择到货记录`)
    if (!cache.has(row.skuId)) cache.set(row.skuId, await listArrivalBalances(warehouseId, row.skuId))
    const balance = cache.get(row.skuId)!.find((item) => (item.arrivalId ?? 0) === row.arrivalId && (item.locationId ?? null) === (row.locationId ?? null) && (item.batchNo || '') === (row.batchNo || ''))
    const available = Number(balance?.quantity || 0)
    const quantity = Number(row.quantity || 0)
    const key = JSON.stringify([row.skuId, row.locationId ?? null, row.batchNo || '', row.arrivalId])
    const total = Number(((totals.get(key) || 0) + quantity).toFixed(2))
    totals.set(key, total)
    if (count ? Math.round((quantity - available) * 100) !== 0 : total > available) {
      throw new Error(`第 ${index + 1} 行到货库存已变化，请重新选择或刷新`)
    }
  }
}
