export function applyCountScan(item: { checkQuantity?: number; bookSerialNos?: string[]; actualSerialNos?: string[] }, serial?: string) {
  if (serial) {
    const actual = item.actualSerialNos || []
    if (actual.includes(serial)) return 'duplicate'
    item.actualSerialNos = [...actual, serial]
  } else if (item.bookSerialNos?.length) {
    return 'serial-required'
  }
  item.checkQuantity = Number(item.checkQuantity || 0) + 1
  return 'counted'
}
