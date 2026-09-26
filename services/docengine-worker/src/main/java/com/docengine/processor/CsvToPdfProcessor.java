package com.docengine.processor;

import com.docengine.storage.ObjectStorage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

@Service
public class CsvToPdfProcessor {
    private static final float LEFT = 48;
    private static final float TOP = 744;
    private static final float BOTTOM = 48;
    private static final float LINE_HEIGHT = 14;
    private static final int WRAP_AT = 100;

    private final ObjectStorage storage;
    private final String inputBucket;
    private final String resultBucket;

    public CsvToPdfProcessor(
            ObjectStorage storage,
            @Value("${docengine.storage.input-bucket}") String inputBucket,
            @Value("${docengine.storage.result-bucket}") String resultBucket) {
        this.storage = storage;
        this.inputBucket = inputBucket;
        this.resultBucket = resultBucket;
    }

    public String process(String inputReference, String ignoredLegacyMonth, String tenantId, String jobId) {
        String inputObject = objectName(inputReference);
        String pdfObject = "tenants/" + tenantId + "/jobs/" + jobId + "/results/csv-document.pdf";
        try (var input = storage.downloadInput(inputBucket, inputObject)) {
            byte[] csvBytes = input.readAllBytes();
            String csv = new String(csvBytes, StandardCharsets.UTF_8);
            List<List<String>> records = parseCsv(csv);
            if (records.isEmpty() || records.get(0).stream().allMatch(String::isBlank)) {
                throw new IllegalArgumentException("CSV must contain a header row and at least one column");
            }

            List<String> headers = normalizeHeaders(records.get(0));
            List<List<String>> rows = records.subList(1, records.size());
            validateRows(headers, rows);
            byte[] pdf = createPdf(inputObject, headers, rows);
            return storage.storeResult(resultBucket, pdfObject,
                    new ByteArrayInputStream(pdf), pdf.length, "application/pdf");
        } catch (Exception exception) {
            throw new IllegalStateException("Could not convert CSV to PDF: " + exception.getMessage(), exception);
        }
    }

    private byte[] createPdf(String sourceObject, List<String> headers, List<List<String>> rows) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDPageContentStream[] currentStream = new PDPageContentStream[1];
            float[] y = {TOP};

            addPage(document, currentStream, y);
            writeWrapped(document, currentStream, y,
                    "DocEngine CSV to PDF: " + fileName(sourceObject), bold, 16, 22);
            writeWrapped(document, currentStream, y,
                    rows.size() + " records | " + headers.size() + " columns", regular, 10, LINE_HEIGHT);
            y[0] -= 8;

            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                List<String> row = rows.get(rowIndex);
                writeWrapped(document, currentStream, y, "Record " + (rowIndex + 1), bold, 11, 17);
                for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
                    String label = safePdfText(headers.get(columnIndex));
                    String value = safePdfText(row.get(columnIndex));
                    writeWrapped(document, currentStream, y, label + ": " + value, regular, 9, LINE_HEIGHT);
                }
                y[0] -= 6;
            }

            currentStream[0].close();
            document.save(output);
            return output.toByteArray();
        }
    }

    private void writeWrapped(PDDocument document, PDPageContentStream[] stream, float[] y,
                              String value, PDType1Font font, float fontSize, float lineHeight) throws Exception {
        String text = safePdfText(value);
        int start = 0;
        do {
            if (y[0] < BOTTOM) addPage(document, stream, y);
            int end = Math.min(start + WRAP_AT, text.length());
            if (end < text.length()) {
                int breakAt = text.lastIndexOf(' ', end);
                if (breakAt > start) end = breakAt;
            }
            String line = text.substring(start, end);
            stream[0].beginText();
            stream[0].setFont(font, fontSize);
            stream[0].newLineAtOffset(LEFT, y[0]);
            stream[0].showText(line);
            stream[0].endText();
            y[0] -= lineHeight;
            start = end;
            while (start < text.length() && text.charAt(start) == ' ') start++;
        } while (start < text.length());
    }

    private void addPage(PDDocument document, PDPageContentStream[] stream, float[] y) throws Exception {
        if (stream[0] != null) stream[0].close();
        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        stream[0] = new PDPageContentStream(document, page);
        y[0] = TOP;
    }

    private List<List<String>> parseCsv(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean afterQuote = false;
        boolean fieldStarted = false;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                    afterQuote = true;
                } else {
                    field.append(ch);
                }
            } else if (afterQuote) {
                if (ch == ',') {
                    record.add(field.toString());
                    field.setLength(0);
                    fieldStarted = false;
                    afterQuote = false;
                } else if (ch == '\n' || ch == '\r') {
                    if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                    record.add(field.toString());
                    if (!(record.size() == 1 && record.get(0).isEmpty())) records.add(record);
                    record = new ArrayList<>();
                    field.setLength(0);
                    fieldStarted = false;
                    afterQuote = false;
                } else {
                    throw new IllegalArgumentException("CSV contains characters after a closing quote");
                }
            } else if (ch == '"' && !fieldStarted && field.length() == 0) {
                quoted = true;
                fieldStarted = true;
            } else if (ch == ',') {
                record.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                record.add(field.toString());
                if (!(record.size() == 1 && record.get(0).isEmpty())) records.add(record);
                record = new ArrayList<>();
                field.setLength(0);
                fieldStarted = false;
            } else {
                field.append(ch);
                fieldStarted = true;
            }
        }
        if (quoted) throw new IllegalArgumentException("CSV contains an unclosed quoted field");
        if (!record.isEmpty() || !field.isEmpty() || fieldStarted) {
            record.add(field.toString());
            if (!(record.size() == 1 && record.get(0).isEmpty())) records.add(record);
        }
        if (!records.isEmpty() && records.get(0).get(0).startsWith("\uFEFF")) {
            records.get(0).set(0, records.get(0).get(0).substring(1));
        }
        return records;
    }

    private List<String> normalizeHeaders(List<String> sourceHeaders) {
        List<String> headers = new ArrayList<>(sourceHeaders.size());
        for (int i = 0; i < sourceHeaders.size(); i++) {
            String header = sourceHeaders.get(i).trim();
            headers.add(header.isEmpty() ? "Column " + (i + 1) : header);
        }
        return headers;
    }

    private void validateRows(List<String> headers, List<List<String>> rows) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).size() != headers.size()) {
                throw new IllegalArgumentException("CSV record " + (i + 2) + " has " + rows.get(i).size()
                        + " columns; expected " + headers.size());
            }
        }
    }

    private String safePdfText(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[\\r\\n\\t]+", " ");
        StringBuilder safe = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(codePoint ->
                safe.append(codePoint >= 32 && codePoint <= 126 ? (char) codePoint : '?'));
        return safe.toString();
    }

    private String fileName(String objectName) {
        int slash = objectName.lastIndexOf('/');
        String name = slash < 0 ? objectName : objectName.substring(slash + 1);
        return name.toLowerCase().endsWith(".csv") ? name.substring(0, name.length() - 4) : name;
    }

    private String objectName(String reference) {
        int slash = reference.indexOf('/', reference.indexOf("//") + 2);
        return slash < 0 ? reference : reference.substring(slash + 1);
    }
}
