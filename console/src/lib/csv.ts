// Shared CSV helpers - bulk-import templates (Calendar schedule, program
// invites) and the existing roster export all go through the same
// escaping/parsing logic instead of three hand-rolled copies.

// Roster exports include free-text a member set themselves (fullName, via
// the app's own profile editor) - without this, a value like
// `=HYPERLINK("http://evil","click")` or `=cmd|'/c calc'!A0` sails straight
// into the exported CSV and Excel/Sheets executes or renders it as a live
// formula/link the moment staff opens the file (the well-known "CSV
// injection" class, CWE-1236). A leading single quote is Excel's own
// "treat as literal text" escape for exactly these characters, applied
// before the normal comma/quote/newline quoting below so it survives
// either way the cell ends up wrapped.
const FORMULA_TRIGGER = /^[=+\-@\t\r]/

export function csvCell(value: string): string {
  const escaped = FORMULA_TRIGGER.test(value) ? `'${value}` : value
  // Quote whenever the cell could otherwise be misread (comma/quote/newline),
  // and double any embedded quotes - the standard CSV escaping rule, not
  // just enough to look right in a spreadsheet preview.
  if (/[",\n]/.test(escaped)) return `"${escaped.replace(/"/g, '""')}"`
  return escaped
}

export function toCsv(rows: string[][]): string {
  return rows.map((row) => row.map(csvCell).join(',')).join('\n')
}

export function downloadCsv(filename: string, content: string) {
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

// Minimal RFC4180 parser - handles quoted fields (with embedded commas,
// newlines, and doubled-quote escapes), which a naive split(',') would
// mangle the moment someone pastes a description with a comma in it.
export function parseCsv(text: string): string[][] {
  const rows: string[][] = []
  let row: string[] = []
  let field = ''
  let inQuotes = false
  let i = 0
  // Strip a leading UTF-8 BOM - Excel/Sheets exports commonly include one.
  if (text.charCodeAt(0) === 0xfeff) text = text.slice(1)

  while (i < text.length) {
    const ch = text[i]
    if (inQuotes) {
      if (ch === '"') {
        if (text[i + 1] === '"') { field += '"'; i += 2; continue }
        inQuotes = false; i += 1; continue
      }
      field += ch; i += 1; continue
    }
    if (ch === '"') { inQuotes = true; i += 1; continue }
    if (ch === ',') { row.push(field); field = ''; i += 1; continue }
    if (ch === '\r') { i += 1; continue }
    if (ch === '\n') { row.push(field); rows.push(row); row = []; field = ''; i += 1; continue }
    field += ch; i += 1
  }
  // Final field/row (files don't always end with a trailing newline).
  if (field.length > 0 || row.length > 0) { row.push(field); rows.push(row) }
  return rows.filter((r) => r.some((cell) => cell.trim().length > 0))
}

/** Parses a CSV with a header row into an array of column->value records, trimmed. */
export function parseCsvRecords(text: string): Record<string, string>[] {
  const rows = parseCsv(text)
  if (rows.length === 0) return []
  const header = rows[0].map((h) => h.trim().toLowerCase())
  return rows.slice(1).map((row) => {
    const record: Record<string, string> = {}
    header.forEach((key, idx) => { record[key] = (row[idx] ?? '').trim() })
    return record
  })
}
