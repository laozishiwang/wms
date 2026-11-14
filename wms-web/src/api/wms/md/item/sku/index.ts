import request from '@/config/axios'

// WMS 商品 SKU VO
export interface ItemSkuVO {
  id?: number
  name?: string
  itemId?: number
  itemCode?: string
  itemName?: string
  categoryId?: number
  categoryName?: string
  unit?: string
  brandId?: number
  brandName?: string
  barCode?: string
  code?: string
  length?: number
  netLength?: number
  grossLength?: number
  width?: number
  height?: number
  grossWeight?: number
  netWeight?: number
  costPrice?: number
  sellingPrice?: number
  createTime?: Date
}

// WMS 商品 SKU API
export const ItemSkuApi = {
  bindBarcode: async (id: number, barcode: string) => {
    return await request.put({ url: '/wms/item-sku/bind-barcode', params: { id, barcode } })
  },
  getAdditionalBarcodes: async (id: number): Promise<string[]> => {
    return await request.get({ url: '/wms/item-sku/additional-barcodes', params: { id } })
  },
  deleteAdditionalBarcode: async (id: number, barcode: string) => {
    return await request.delete({ url: '/wms/item-sku/additional-barcode', params: { id, barcode } })
  },
  // 按 SKU 维度分页（支持商品 / 品牌 / 分类多表联查筛选）
  getItemSkuPage: async (params: any) => {
    return await request.get({ url: '/wms/item-sku/page', params })
  }
}
