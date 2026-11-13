// WMS 移库单明细 VO
export interface MovementOrderDetailVO {
  arrivalId?: number | null
  id?: number
  orderId?: number
  itemId?: number
  itemCode?: string
  itemName?: string
  unit?: string
  skuId?: number
  skuCode?: string
  skuName?: string
  sourceWarehouseId?: number
  sourceWarehouseName?: string
  targetWarehouseId?: number
  targetWarehouseName?: string
  sourceLocationId?: number
  targetLocationId?: number
  batchNo?: string
  serialNos?: string[]
  productionDate?: string | number
  expirationDate?: string | number
  quantity?: number
  availableQuantity?: number
  price?: number
  totalPrice?: number
  createTime?: Date
}
