package org.example.util;
/**
 * @project backend
 *
 * @author qy
 * @date 2026/10/14 13:05
 */

import org.apache.tika.Tika;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDecimalNumber;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTVMerge;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * @description
 * @author qy
 * @Date 2026/10/14  13:05
 */
@Component
public class DocParseUtil {
    private final Tika tika = new Tika();

    /**
     * 解析上传文件为文本。
     * 对于 .docx 使用 POI 结构化解析，识别表格的合并单元格（gridSpan / vMerge），
     * 保留行列表头与行列对应关系，确保合计行等关键单元格的值在向量检索与回答时能被正确引用。
     */
    public String parseFile(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();
        if (filename != null && filename.toLowerCase().endsWith(".docx")) {
            return parseDocx(file);
        }
        return tika.parseToString(file.getInputStream());
    }

    /**
     * 使用 POI 解析 docx，按文档原始顺序输出段落与表格。
     * 表格以"键值对"形式保留：表头行（可能多行）合并为列名，
     * 数据行通过 gridSpan / vMerge 还原为按"列"对齐的数组，
     * 输出 "表头：值" 形式以使每个单元格都能与它的列名对应。
     * 合计/总计行会被显式标记为 "[汇总行]"，并附加一条表级汇总，
     * 提升复杂表格（含合并单元格、合计行）的检索召回与回答准确性。
     */
    private String parseDocx(MultipartFile file) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = file.getInputStream();
             XWPFDocument document = new XWPFDocument(in)) {
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph) {
                    String text = paragraphText((XWPFParagraph) element);
                    if (text != null && !text.trim().isEmpty()) {
                        sb.append(text).append("\n");
                    }
                } else if (element instanceof XWPFTable) {
                    sb.append(renderTable((XWPFTable) element));
                }
            }
        }
        return sb.toString();
    }

    private String paragraphText(XWPFParagraph paragraph) {
        StringBuilder sb = new StringBuilder();
        for (XWPFRun run : paragraph.getRuns()) {
            sb.append(run.getText(0) == null ? "" : run.getText(0));
        }
        if (sb.length() == 0) {
            return paragraph.getText();
        }
        return sb.toString();
    }

    // ---------------- 表格解析：合并单元格感知 ----------------

    private String renderTable(XWPFTable table) {
        List<XWPFTableRow> rows = table.getRows();
        if (rows.isEmpty()) {
            return "";
        }
        int totalCols = getTableColumnCount(table);
        if (totalCols == 0) {
            return fallbackRenderTable(table);
        }

        // 1) 识别表头结束位置：可能存在多行表头（如 "营业收入" 跨多列）
        int headerEndIdx = findHeaderRowEnd(rows, totalCols);
        // 2) 合并表头行，得到最终列名
        String[] headers = buildHeaders(rows, headerEndIdx, totalCols);

        StringBuilder sb = new StringBuilder();
        sb.append("表头：").append(String.join(" | ", headers)).append("\n");

        // 3) 处理数据行：vMerge 续行单元格向上补全，并按 gridSpan 对齐列
        String[] lastValues = new String[totalCols];
        // 用于生成表级汇总
        String[] totalValues = null;

        int dataRowSeq = 0;
        for (int r = headerEndIdx; r < rows.size(); r++) {
            XWPFTableRow row = rows.get(r);
            String[] cells = expandRowToColumns(row, totalCols);
            // vMerge：空字符串视为被合并，向上补全
            for (int c = 0; c < totalCols; c++) {
                if (cells[c] != null && !cells[c].isEmpty()) {
                    lastValues[c] = cells[c];
                } else if (lastValues[c] != null) {
                    cells[c] = lastValues[c];
                }
            }

            boolean isTotal = isTotalRow(cells, headers);
            String prefix = isTotal ? "[汇总行] " : ("[行" + (++dataRowSeq) + "] ");

            StringBuilder line = new StringBuilder(prefix);
            boolean first = true;
            for (int c = 0; c < totalCols; c++) {
                String value = cells[c] == null ? "" : cells[c];
                if (value.isEmpty()) {
                    continue;
                }
                String name = (c < headers.length && headers[c] != null && !headers[c].isEmpty())
                        ? headers[c] : ("列" + (c + 1));
                if (!first) {
                    line.append(" | ");
                }
                first = false;
                line.append(name).append("：").append(value);
            }
            sb.append(line).append("\n");

            if (isTotal) {
                totalValues = cells;
            }
        }

        // 4) 附加表级汇总，冗余强调合计值，便于 LLM 直接命中
        if (totalValues != null) {
            String summary = buildTableSummary(headers, totalValues);
            if (!summary.isEmpty()) {
                sb.append("【表格汇总】").append(summary).append("\n");
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    /**
     * 取表格的逻辑列数。优先从 CTTblGrid 取，缺失时回退为各行 gridSpan 展开后的最大值。
     */
    private int getTableColumnCount(XWPFTable table) {
        try {
            CTTbl ctTbl = table.getCTTbl();
            if (ctTbl != null) {
                CTTblGrid grid = ctTbl.getTblGrid();
                if (grid != null) {
                    return grid.sizeOfGridColArray();
                }
            }
        } catch (Exception ignore) {
        }
        int max = 0;
        for (XWPFTableRow row : table.getRows()) {
            max = Math.max(max, countLogicalColumns(row));
        }
        return max;
    }

    private int countLogicalColumns(XWPFTableRow row) {
        int total = 0;
        for (XWPFTableCell cell : row.getTableCells()) {
            total += getGridSpan(cell);
        }
        return total;
    }

    private int getGridSpan(XWPFTableCell cell) {
        try {
            CTTcPr tcPr = cell.getCTTc().getTcPr();
            if (tcPr == null) {
                return 1;
            }
            // poi-ooxml-lite 中 getGridSpan() 返回 CTDecimalNumber（不是 CTGridSpan）
            CTDecimalNumber gs = tcPr.getGridSpan();
            if (gs == null || gs.getVal() == null) {
                return 1;
            }
            int v = gs.getVal().intValue();
            return v < 1 ? 1 : v;
        } catch (Exception e) {
            return 1;
        }
    }

    /**
     * 单元格是否处于 vMerge 续行（应忽略自身文本，向上补全）。
     */
    private boolean isVMergeContinue(XWPFTableCell cell) {
        try {
            CTTcPr tcPr = cell.getCTTc().getTcPr();
            if (tcPr == null) {
                return false;
            }
            CTVMerge vMerge = tcPr.getVMerge();
            if (vMerge == null) {
                return false;
            }
            if (vMerge.getVal() == null) {
                return true;
            }
            String val = vMerge.getVal().toString();
            return "continue".equalsIgnoreCase(val);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 把一行单元格按 gridSpan 展开成"按列"对齐的字符串数组，长度等于表格总列数。
     * 对 vMerge 续行单元格强制视为空，避免 POI getText 在某些情况下返回陈旧文本。
     */
    private String[] expandRowToColumns(XWPFTableRow row, int totalCols) {
        String[] cells = new String[totalCols];
        int colIdx = 0;
        for (XWPFTableCell cell : row.getTableCells()) {
            if (colIdx >= totalCols) {
                break;
            }
            int span = getGridSpan(cell);
            String text = isVMergeContinue(cell) ? "" : cleanCellText(cell.getText());
            cells[colIdx] = text;
            colIdx += span;
        }
        return cells;
    }

    private String cleanCellText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\n', ' ').replace('\r', ' ').trim();
    }

    /**
     * 启发式识别表头结束位置：遇到第一行包含 Q1-Q4 / "季度" / 数字行 / 合计 / 总计时停止。
     * 这样可兼容多行表头（如顶层"营业收入" 跨列合并 + 下方子列）。
     */
    private int findHeaderRowEnd(List<XWPFTableRow> rows, int totalCols) {
        int end = 1;
        for (int r = 1; r < rows.size(); r++) {
            String[] cells = expandRowToColumns(rows.get(r), totalCols);
            if (looksLikeDataRow(cells)) {
                break;
            }
            end = r + 1;
        }
        return Math.max(1, end);
    }

    private boolean looksLikeDataRow(String[] cells) {
        for (String cell : cells) {
            if (cell == null) {
                continue;
            }
            String t = cell.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.matches(".*Q[1-4].*") || t.contains("季度")) {
                return true;
            }
            if (t.matches(".*\\d{2,}.*")) {
                return true;
            }
            if (t.contains("合计") || t.contains("总计") || t.contains("汇总")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 合并多行表头为最终列名：列 c 的名字 = headerRows 第 0..headerEnd-1 行中该列非空值以 "-" 连接。
     */
    private String[] buildHeaders(List<XWPFTableRow> rows, int headerEndIdx, int totalCols) {
        String[] headers = new String[totalCols];
        for (int c = 0; c < totalCols; c++) {
            StringBuilder name = new StringBuilder();
            for (int r = 0; r < headerEndIdx; r++) {
                String[] cells = expandRowToColumns(rows.get(r), totalCols);
                if (c < cells.length) {
                    String v = cells[c];
                    if (v != null && !v.isEmpty()) {
                        if (name.length() > 0) {
                            name.append('-');
                        }
                        name.append(v);
                    }
                }
            }
            headers[c] = name.toString();
        }
        return headers;
    }

    /**
     * 是否为合计/总计行：依据首列或任意列出现"合计/总计/汇总"等关键词。
     */
    private boolean isTotalRow(String[] cells, String[] headers) {
        for (String cell : cells) {
            if (cell == null) {
                continue;
            }
            if (cell.contains("合计") || cell.contains("总计") || cell.contains("汇总")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构造表级汇总文本：把"汇总行"中带数值的列直接列出，作为冗余命中点。
     */
    private String buildTableSummary(String[] headers, String[] totalValues) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (int c = 0; c < headers.length && c < totalValues.length; c++) {
            String name = headers[c];
            String value = totalValues[c];
            if (name == null || name.isEmpty() || value == null || value.isEmpty()) {
                continue;
            }
            if (!first) {
                sb.append("；");
            }
            first = false;
            sb.append(name).append("=").append(value);
        }
        return sb.toString();
    }

    /**
     * 退化渲染：无法识别列数时，仅用 Tika 风格纯文本输出，避免崩溃。
     */
    private String fallbackRenderTable(XWPFTable table) {
        StringBuilder sb = new StringBuilder("表格内容：\n");
        for (XWPFTableRow row : table.getRows()) {
            StringBuilder line = new StringBuilder();
            for (XWPFTableCell cell : row.getTableCells()) {
                if (line.length() > 0) {
                    line.append(" | ");
                }
                line.append(cleanCellText(cell.getText()));
            }
            sb.append(line).append("\n");
        }
        return sb.toString();
    }
}