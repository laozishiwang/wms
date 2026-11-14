import { strict as assert } from 'node:assert'
import { placePath } from './place.ts'

const places = [
  { id: 1, type: 'SHELF', code: 'A', name: 'A区' },
  { id: 2, parentId: 1, type: 'LOCATION', code: 'A01', name: '01位' },
  { id: 3, type: 'LOCATION', code: 'X', name: '散放区' }
]
assert.equal(placePath(places, 2), 'A区 / 01位')
assert.equal(placePath(places, 3), '散放区')
assert.equal(placePath(places, 99), '库位 99')
assert.equal(placePath(places), '未分配')
console.log('PASS: area, location and unassigned display')
