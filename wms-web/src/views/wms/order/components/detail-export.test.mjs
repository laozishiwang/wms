import assert from 'node:assert/strict'
import { toCsv } from './detail-export.ts'

const csv = toCsv([['名称', '数量'], ['a,b', 0], ['=1+1', 'x"y']])
assert.ok(csv.startsWith('\ufeff'))
assert.ok(csv.includes('"a,b","0"'))
assert.ok(csv.includes('"\'=1+1","x""y"'))
console.log('CSV 导出转义检查通过')
