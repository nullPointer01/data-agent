import fs from "node:fs/promises";
import path from "node:path";
import { Workbook, SpreadsheetFile } from "@oai/artifact-tool";

const outputDir = path.resolve("outputs/shift_schedule_excel");
const outputPath = path.join(outputDir, "仓库排班表_4到5人_避免上一休一.xlsx");

const days = Array.from({ length: 30 }, (_, i) => i + 1);
const weekdays = [
  "一", "二", "三", "四", "五", "六", "日", "一", "二", "三",
  "四", "五", "六", "日", "一", "二", "三", "四", "五", "六",
  "日", "一", "二", "三", "四", "五", "六", "日", "一", "二",
];

const staffConfig = [
  { name: "郑予佳" },
  { name: "朱俊蓉" },
  { name: "解道蝶" },
  { name: "申佩瑶" },
  { name: "史雪倩" },
];

const staffRows = staffConfig.map((staff, staffIndex) => {
  const shifts = days.map((day) => {
    if (day >= 3 && (day - 3) % staffConfig.length === staffIndex) return "休";
    return (day + staffIndex) % 2 === 0 ? "通" : "晚";
  });

  return { name: staff.name, shifts };
});

const shiftColors = {
  "休": "#FF0000",
  "通": "#0070C0",
  "早": "#0070C0",
  "晚": "#000000",
};

const weekendFill = "#F4B183";
const positionFill = "#B4C7E7";
const highlightFill = "#FFFF00";
function colName(colNumber) {
  let dividend = colNumber;
  let name = "";
  while (dividend > 0) {
    const modulo = (dividend - 1) % 26;
    name = String.fromCharCode(65 + modulo) + name;
    dividend = Math.floor((dividend - modulo) / 26);
  }
  return name;
}

function a1(row, col) {
  return `${colName(col)}${row}`;
}

function setAllBorders(range) {
  range.format.borders = { preset: "all", style: "thin", color: "#000000" };
}

const workbook = Workbook.create();
const sheet = workbook.worksheets.add("仓库排班");
sheet.showGridLines = false;

const matrix = [
  ["岗位", "姓名", ...days],
  ["", "", ...weekdays],
  ...staffRows.map((row, index) => [index === 0 ? "仓库" : "", row.name, ...row.shifts]),
  ["", "在班", ...Array(days.length).fill(null)],
];

const totalRows = matrix.length;
const totalCols = matrix[0].length;
const staffStartRow = 3;
const staffEndRow = staffStartRow + staffRows.length - 1;
const tableRange = sheet.getRangeByIndexes(0, 0, totalRows, totalCols);
tableRange.values = matrix;

sheet.getRange(`A${staffStartRow}:A${staffEndRow}`).merge();
sheet.getRangeByIndexes(totalRows - 1, 2, 1, days.length).formulas = [
  days.map((_, idx) => {
    const col = colName(idx + 3);
    return `=COUNTIF(${col}${staffStartRow}:${col}${staffEndRow},"<>休")`;
  }),
];

for (let col = 1; col <= totalCols; col += 1) {
  const width = col === 1 || col === 2 ? 90 : 53;
  sheet.getRangeByIndexes(0, col - 1, totalRows, 1).format.columnWidthPx = width;
}

for (let row = 1; row <= totalRows; row += 1) {
  sheet.getRangeByIndexes(row - 1, 0, 1, totalCols).format.rowHeightPx = row <= 2 ? 34 : 37;
}

tableRange.format.font = { name: "Arial", size: 16, bold: true, color: "#000000" };
tableRange.format.horizontalAlignment = "center";
tableRange.format.verticalAlignment = "center";
tableRange.format.wrapText = true;
setAllBorders(tableRange);

sheet.getRange("A1:B2").format.fill = { color: "#FFFFFF" };
sheet.getRange(`A${staffStartRow}:A${staffEndRow}`).format.fill = { color: positionFill };
sheet.getRange(`A${totalRows}:A${totalRows}`).format.fill = { color: "#FFFFFF" };
sheet.getRange(`B3:B${totalRows}`).format.fill = { color: "#FFFFFF" };

for (let i = 0; i < days.length; i += 1) {
  const col = i + 3;
  const isWeekend = weekdays[i] === "六" || weekdays[i] === "日";
  const headerRange = sheet.getRange(`${a1(1, col)}:${a1(2, col)}`);
  headerRange.format.fill = { color: isWeekend ? weekendFill : "#FFFFFF" };
  if (isWeekend) {
    headerRange.format.font = { name: "Arial", size: 16, bold: true, color: "#FF0000" };
  }
}

for (let r = 0; r < staffRows.length; r += 1) {
  const staff = staffRows[r];
  const rowNumber = r + 3;
  sheet.getRange(a1(rowNumber, 2)).format.font = { name: "Arial", size: 16, bold: true, color: "#000000" };
  for (let c = 0; c < staff.shifts.length; c += 1) {
    const day = c + 1;
    const shift = staff.shifts[c];
    const cell = sheet.getRange(a1(rowNumber, c + 3));
    cell.format.font = { name: "Arial", size: 16, bold: true, color: shiftColors[shift] ?? "#000000" };
    cell.format.fill = { color: staff.highlights?.includes(day) ? highlightFill : "#FFFFFF" };
  }
}

const totalRowNumber = totalRows;
sheet.getRange(`${a1(totalRowNumber, 2)}:${a1(totalRowNumber, totalCols)}`).format.font = {
  name: "Arial",
  size: 16,
  bold: true,
  color: "#000000",
};
sheet.getRange(a1(totalRowNumber, 2)).format.fill = { color: "#FFFFFF" };

sheet.freezePanes.freezeRows(2);
sheet.freezePanes.freezeColumns(2);

await fs.mkdir(outputDir, { recursive: true });

const preview = await workbook.render({ sheetName: "仓库排班", autoCrop: "all", scale: 1, format: "png" });
await fs.writeFile(path.join(outputDir, "preview.png"), new Uint8Array(await preview.arrayBuffer()));

const inspected = await workbook.inspect({
  kind: "table",
  range: `仓库排班!A1:AF${totalRows}`,
  include: "values",
  tableMaxRows: 12,
  tableMaxCols: 32,
  maxChars: 5000,
});
console.log(inspected.ndjson);

const errorScan = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, maxResults: 50 },
  summary: "formula error scan",
  maxChars: 2000,
});
console.log(errorScan.ndjson);

const xlsx = await SpreadsheetFile.exportXlsx(workbook);
await xlsx.save(outputPath);
console.log(outputPath);
