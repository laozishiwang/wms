// WMS 盘库单明细 VO
export interface CheckOrderDetailVO {
  id?: number
  orderId?: number
  itemId?: number
  itemCode?: string
  itemName?: string
  unit?: string
  skuId?: number
  skuCode?: string
  skuName?: string
  inventoryId?: number
  warehouseId?: number
  warehouseName?: string
  locationId?: number
  bookSerialNos?: string[] | null
  actualSerialNos?: string[] | null
  batchNo?: string
  productionDate?: string | number
  expirationDate?: string | number
  receiptTime?: Date
  quantity?: number
  checkQuantity?: number
  availableQuantity?: number
  price?: number
  createTime?: Date
}
