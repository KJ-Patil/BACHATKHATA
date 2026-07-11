package com.example.bachatkhata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Minimal, dependency-free writer for the OOXML {@code .xlsx} (SpreadsheetML) format.
 * An xlsx file is simply a ZIP archive of a few XML parts; this builds the smallest set
 * that Excel, Google Sheets and LibreOffice all open. Text cells use inline strings so
 * no shared-strings table is required. Numbers are written as numeric cells.
 */
public final class XlsxExporter {

    private XlsxExporter() {}

    /**
     * @param sheetName visible worksheet name
     * @param headers   column header labels (rendered as the first row)
     * @param rows      data rows; each cell may be a {@link Number} (numeric) or any other
     *                  object rendered via {@code toString()} (text). {@code null} = blank.
     */
    public static byte[] build(String sheetName, String[] headers, List<Object[]> rows) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(baos);

        writeEntry(zip, "[Content_Types].xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                        + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                        + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                        + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                        + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                        + "</Types>");

        writeEntry(zip, "_rels/.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                        + "</Relationships>");

        writeEntry(zip, "xl/workbook.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                        + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                        + "<sheets><sheet name=\"" + escape(sheetName) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
                        + "</workbook>");

        writeEntry(zip, "xl/_rels/workbook.xml.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                        + "</Relationships>");

        StringBuilder sheet = new StringBuilder();
        sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");

        int rowNum = 1;
        sheet.append(buildRow(rowNum++, toObjects(headers)));
        for (Object[] row : rows) {
            sheet.append(buildRow(rowNum++, row));
        }
        sheet.append("</sheetData></worksheet>");
        writeEntry(zip, "xl/worksheets/sheet1.xml", sheet.toString());

        zip.close();
        return baos.toByteArray();
    }

    private static Object[] toObjects(String[] arr) {
        Object[] out = new Object[arr.length];
        System.arraycopy(arr, 0, out, 0, arr.length);
        return out;
    }

    private static String buildRow(int rowNum, Object[] cells) {
        StringBuilder sb = new StringBuilder();
        sb.append("<row r=\"").append(rowNum).append("\">");
        for (int c = 0; c < cells.length; c++) {
            Object value = cells[c];
            if (value == null) continue;
            String ref = colName(c) + rowNum;
            if (value instanceof Number) {
                sb.append("<c r=\"").append(ref).append("\"><v>")
                        .append(value).append("</v></c>");
            } else {
                sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(escape(value.toString())).append("</t></is></c>");
            }
        }
        sb.append("</row>");
        return sb.toString();
    }

    /** 0 -> A, 1 -> B, ... 26 -> AA, etc. */
    private static String colName(int index) {
        StringBuilder sb = new StringBuilder();
        index++;
        while (index > 0) {
            int rem = (index - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            index = (index - 1) / 26;
        }
        return sb.toString();
    }

    private static void writeEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
