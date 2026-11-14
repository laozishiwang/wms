const numericFields = ['netLength', 'grossLength', 'netWeight', 'grossWeight', 'length', 'width', 'height', 'price', 'salesPrice'] as const

export function buildDraftDetails(rows: any[], receipt: boolean, editing: boolean) {
  return rows.map((row) => ({
    id: row.id, skuId: row.sku.id, quantity: row.quantity,
    price: receipt || editing ? row.price : undefined,
    totalPrice: editing && row.id && Number(row.quantity) === Number(row.originalQuantity) && row.price === row.originalPrice ? row.totalPrice : undefined,
    locationId: row.locationId, batchNo: row.batchNo,
    ...(!receipt ? { arrivalId: row.arrivalId } : {}),
    productionDate: row.productionDate, expirationDate: row.expirationDate,
    serialNos: row.serialNos,
    ...(receipt ? { palletNo: row.palletNo, boxNo: row.boxNo, rollNo: row.rollNo,
      ...Object.fromEntries(numericFields.map((key) => [key, row[key] == null || String(row[key]) === '' ? undefined : Number(row[key])])) } : {})
  }))
}

export function buildMovementDraftDetails(rows: any[]) {
  return rows.map(({ skuLabel: _label, locationId: _location, originalQuantity, originalPrice, ...detail }) => ({
    ...detail,
    totalPrice: originalQuantity != null && (Number(detail.quantity) !== Number(originalQuantity) || detail.price !== originalPrice) ? undefined : detail.totalPrice
  }))
}

export function buildCheckDraftDetails(rows: any[]) {
  return rows.map(({ label: _label, ...detail }) => detail)
}
