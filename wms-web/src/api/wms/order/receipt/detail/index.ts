// WMS 入库单明细 VO
export interface ReceiptOrderDetailVO {
  id?: number
  orderId?: number
  itemId?: number
  itemCode?: string
  itemName?: string
  unit?: string
  skuId?: number
  skuCode?: string
  skuName?: string
  warehouseId?: number
  warehouseName?: string
  locationId?: number
  serialNos?: string[]
  batchNo?: string
  productionDate?: string
  expirationDate?: string
  quantity?: number
  price?: number
  totalPrice?: number
  createTime?: Date
}
