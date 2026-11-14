import { strict as assert } from 'node:assert'
import { buildDraftDetails, buildMovementDraftDetails, buildCheckDraftDetails } from './draft-details.ts'

const row = { id: 42, sku: { id: 9 }, quantity: 2.5, price: 8, locationId: 7, batchNo: 'B1', serialNos: ['S1'], palletNo: 'P1', netLength: 30, arrivalId: 12 }
const receipt = buildDraftDetails([row], true, true)[0]
assert.equal(receipt.id, 42)
assert.equal(receipt.skuId, 9)
assert.equal(receipt.quantity, 2.5)
assert.equal(receipt.locationId, 7)
assert.equal(receipt.batchNo, 'B1')
assert.deepEqual(receipt.serialNos, ['S1'])
assert.equal(receipt.palletNo, 'P1')
assert.equal(receipt.netLength, 30)
assert.equal(buildDraftDetails([{ ...row, id: undefined }], true, true)[0].id, undefined)
assert.equal(buildDraftDetails([row], false, true)[0].arrivalId, 12)
assert.equal(buildDraftDetails([row], false, true)[0].price, 8)
assert.equal(buildDraftDetails([row], false, false)[0].price, undefined)
const customPrice = { ...row, totalPrice: 17, originalQuantity: 2.5, originalPrice: 8 }
assert.equal(buildDraftDetails([customPrice], true, true)[0].totalPrice, 17)
assert.equal(buildDraftDetails([{ ...customPrice, quantity: 3 }], true, true)[0].totalPrice, undefined)
assert.equal(buildDraftDetails([{ ...customPrice, price: 9 }], false, true)[0].totalPrice, undefined)
const movement = { id: 20, skuLabel: '物品', locationId: 2, sourceLocationId: 2, targetLocationId: 3, quantity: 4, originalQuantity: 4, price: 6, originalPrice: 6, totalPrice: 23, serialNos: ['A'] }
assert.equal(buildMovementDraftDetails([movement])[0].id, 20)
assert.equal(buildMovementDraftDetails([movement])[0].totalPrice, 23)
assert.equal(buildMovementDraftDetails([{ ...movement, quantity: 5 }])[0].totalPrice, undefined)
assert.deepEqual(buildMovementDraftDetails([movement])[0].serialNos, ['A'])
assert.equal('locationId' in buildMovementDraftDetails([movement])[0], false)
assert.deepEqual(buildCheckDraftDetails([{ id: 9, label: '物品', bookSerialNos: ['A'], actualSerialNos: [], checkQuantity: 0 }])[0], { id: 9, bookSerialNos: ['A'], actualSerialNos: [], checkQuantity: 0 })
console.log('PASS: draft IDs, dimensions, batch, serials and shipment prices')
