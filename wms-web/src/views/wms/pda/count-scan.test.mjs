import { strict as assert } from 'node:assert'
import { applyCountScan } from './count-scan.ts'

const plain = { checkQuantity: 0 }
assert.equal(applyCountScan(plain), 'counted')
assert.equal(plain.checkQuantity, 1)
const tracked = { checkQuantity: 0, bookSerialNos: ['S1', 'S2'], actualSerialNos: [] }
assert.equal(applyCountScan(tracked), 'serial-required')
assert.equal(tracked.checkQuantity, 0)
assert.equal(applyCountScan(tracked, 'S1'), 'counted')
assert.deepEqual(tracked.actualSerialNos, ['S1'])
assert.equal(tracked.checkQuantity, 1)
assert.equal(applyCountScan(tracked, 'S1'), 'duplicate')
assert.equal(tracked.checkQuantity, 1)
console.log('PASS: stock count scanning and serial numbers')
