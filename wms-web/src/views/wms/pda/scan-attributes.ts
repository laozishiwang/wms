const numericFields = new Set(['netLength', 'grossLength', 'netWeight', 'grossWeight', 'length', 'width', 'height'])
const fields = ['batchNo', 'palletNo', 'boxNo', 'rollNo', ...numericFields]

export function matchesScanAttributes(item: Record<string, unknown>, parsed: Record<string, string>): boolean {
  return fields.every(key => parsed[key] === undefined || (item[key] != null && (numericFields.has(key) ? Number(item[key]) === Number(parsed[key]) : String(item[key]) === parsed[key])))
}
