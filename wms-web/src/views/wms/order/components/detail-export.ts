export function toCsv(rows: unknown[][]): string {
  const cell = (value: unknown) => {
    let text = value == null ? '' : String(value)
    if (typeof value === 'string' && /^[\s]*[=+\-@]/.test(text)) text = `'${text}`
    return `"${text.replace(/"/g, '""')}"`
  }
  return '\ufeff' + rows.map(row => row.map(cell).join(',')).join('\r\n')
}
