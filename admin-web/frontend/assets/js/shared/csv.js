const FORMULA_PREFIX = /^[\t\r\n ]*[=+\-@]/;

export function escapeCsvCell(value) {
  const text = String(value ?? "");
  const safeText = FORMULA_PREFIX.test(text) ? `'${text}` : text;
  return `"${safeText.replaceAll('"', '""')}"`;
}

export function buildCsvContent(keys = [], rows = []) {
  const columns = Array.isArray(keys) ? keys : [];
  const records = Array.isArray(rows) ? rows : [];
  return [
    columns.map(escapeCsvCell).join(","),
    ...records.map((row) => columns.map((key) => escapeCsvCell(row?.[key])).join(",")),
  ].join("\n");
}
