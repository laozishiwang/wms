import request from '@/config/axios'

export interface BatchInventoryVO {
  id: number
  warehouseId: number
  skuId: number
  batchNo: string
  productionDate?: string | number
  expirationDate?: string | number
  quantity: number
}

export const getBatchInventoryList = (warehouseId: number, skuId: number): Promise<BatchInventoryVO[]> =>
  request.get({ url: '/wms/batch-inventory/list', params: { warehouseId, skuId } })

export interface ExpiryInventoryVO extends BatchInventoryVO {
  warehouseName: string
  itemName: string
  skuName: string
  skuCode: string
  unit?: string
  status: 'EXPIRED' | 'EXPIRING'
}

export const getExpiryInventoryPage = (params: { pageNo: number; pageSize: number; days: number }): Promise<{ list: ExpiryInventoryVO[]; total: number }> =>
  request.get({ url: '/wms/batch-inventory/expiry', params })

export interface SerialInventoryVO {
  skuId: number
  serialNo: string
  warehouseId: number
  batchNo?: string
  locationId?: number | null
  inStock: boolean
}

export const findSerialInventory = (skuId: number, serialNo: string): Promise<SerialInventoryVO[]> =>
  request.get({ url: '/wms/serial-inventory/find', params: { skuId, serialNo } })

export const listStockSerialNos = (warehouseId: number, skuId: number, batchNo?: string, locationId?: number): Promise<string[]> =>
  request.get({ url: '/wms/serial-inventory/list', params: { warehouseId, skuId, batchNo, locationId } })
