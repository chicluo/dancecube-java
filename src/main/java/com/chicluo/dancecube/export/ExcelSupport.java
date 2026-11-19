package com.chicluo.dancecube.export;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 导出公共逻辑，对标 Python 版 {@code core/safe.py::safe_cell} 与 export/catalog 共用的表格样式。
 *
 * <ul>
 *   <li>{@link #safeCell}：防止 xlsx 公式注入（CWE-1236），以 = + - @ 制表符 回车 开头的文本会被
 *       前置一个半角单引号，数字原样返回；</li>
 *   <li>{@link #finish}：表头深蓝底白字加粗居中、数字列右对齐、文本列左对齐、按显示宽度自适应列宽、
 *       冻结首行、加筛选、表头行高 22。</li>
 * </ul>
 */
final class ExcelSupport {

    /** 表头底色 #1F3B4D，和 Python 版 HEADER_FILL 一致。 */
    private static final byte[] HEADER_RGB = {0x1F, 0x3B, 0x4D};

    /** Excel / WPS 会把这些前缀当公式（CWE-1236）。 */
    private static final String INJECT_PREFIX = "=+-@\t\r";

    private ExcelSupport() {
    }

    // ------------------------------------------------------------------ 单元格净化

    /**
     * xlsx 写入前的净化；数字原样返回，文本命中公式前缀时加 {@code '} 破坏公式。
     * 与 Python 的 {@code safe_cell} 对齐：None → ""、bool → 字符串、int/float 原样。
     */
    static Object safeCell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Boolean b) {
            return b ? "True" : "False";
        }
        if (value instanceof Number) {
            return value;
        }
        String s = String.valueOf(value);
        if (!s.isEmpty() && INJECT_PREFIX.indexOf(s.charAt(0)) >= 0) {
            return "'" + s;
        }
        return s;
    }

    /** 净化后强制按文本返回（列宽、拼接等需要 String 的场景）。 */
    static String safeText(Object value) {
        Object safe = safeCell(value);
        return safe instanceof String s ? s : String.valueOf(safe);
    }

    /** 去掉 Windows 文件名非法字符并削掉首尾空格/点（对标 Python 的 {@code safe_filename}）。 */
    static String safeFileName(String name) {
        String s = name == null ? "" : name;
        s = s.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]+", "_");
        s = s.replaceAll("^[ .]+", "").replaceAll("[ .]+$", "");
        return s.isEmpty() ? "unknown" : s;
    }

    /** 显示宽度：中日韩等宽字符按 2 计（对标 Python 的 {@code _disp_width}）。 */
    static int displayWidth(String value) {
        int width = 0;
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            width += cp > 0x2E7F ? 2 : 1;
            i += Character.charCount(cp);
        }
        return width;
    }

    // ------------------------------------------------------------------ 写入

    /** 数字写数值单元格，其余写文本单元格。 */
    static void writeCell(Row row, int column, Object value) {
        Cell cell = row.createCell(column);
        if (value instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else {
            cell.setCellValue(value == null ? "" : String.valueOf(value));
        }
    }

    /**
     * 统一样式收尾：表头、数据对齐、可选列数字格式、自适应列宽、冻结首行、自动筛选。
     *
     * @param formats 额外的列数字格式，key 为表头文本，例如 {@code ACC → 0.00"%" }
     */
    static void finish(Sheet sheet, List<String> headers, Set<String> numericHeaders,
                       Map<String, String> formats) {
        Workbook wb = sheet.getWorkbook();
        CellStyle headerStyle = createHeaderStyle(wb);
        CellStyle left = createBodyStyle(wb, HorizontalAlignment.LEFT);
        CellStyle right = createBodyStyle(wb, HorizontalAlignment.RIGHT);

        Row headerRow = sheet.getRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                cell.setCellStyle(headerStyle);
            }
        }

        int lastRow = sheet.getLastRowNum();
        for (int col = 0; col < headers.size(); col++) {
            String header = headers.get(col);
            CellStyle base = numericHeaders.contains(header) ? right : left;
            CellStyle style = base;
            String format = formats.get(header);
            if (format != null && !format.isEmpty()) {
                DataFormat dataFormat = wb.createDataFormat();
                style = wb.createCellStyle();
                style.cloneStyleFrom(base);
                style.setDataFormat(dataFormat.getFormat(format));
            }
            for (int r = 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Cell cell = row.getCell(col);
                if (cell != null) {
                    cell.setCellStyle(style);
                }
            }
        }

        for (int col = 0; col < headers.size(); col++) {
            int dataWidth = 0;
            for (int r = 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                dataWidth = Math.max(dataWidth, displayWidth(cellText(row.getCell(col))));
            }
            int headWidth = displayWidth(headers.get(col)) + 5;
            int width = Math.min(Math.max(headWidth, dataWidth + 3), 60);
            sheet.setColumnWidth(col, width * 256);
        }

        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, lastRow), 0, headers.size() - 1));
        if (lastRow >= 0) {
            sheet.getRow(0).setHeightInPoints(22f);
        }
    }

    // ------------------------------------------------------------------ 样式

    private static CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        if (style instanceof XSSFCellStyle xssf) {
            xssf.setFillForegroundColor(new XSSFColor(HEADER_RGB, new DefaultIndexedColorMap()));
        } else {
            style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        }
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private static CellStyle createBodyStyle(Workbook wb, HorizontalAlignment alignment) {
        CellStyle style = wb.createCellStyle();
        style.setAlignment(alignment);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    // ------------------------------------------------------------------ 列宽用文本

    /** 单元格的显示文本，用于估算列宽（数字 12.0 显示成 12）。 */
    private static String cellText(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        if (type == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (value == Math.rint(value) && Math.abs(value) < 1e15) {
                return Long.toString((long) value);
            }
            return Double.toString(value);
        }
        if (type == CellType.STRING) {
            return cell.getStringCellValue();
        }
        if (type == CellType.BOOLEAN) {
            return cell.getBooleanCellValue() ? "True" : "False";
        }
        return "";
    }
}
