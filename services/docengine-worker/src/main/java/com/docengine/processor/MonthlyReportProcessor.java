package com.docengine.processor;

import com.docengine.storage.ObjectStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class MonthlyReportProcessor {
    private final ObjectStorage storage;
    private final String inputBucket;
    private final String resultBucket;

    public MonthlyReportProcessor(
            ObjectStorage storage,
            @Value("${docengine.storage.input-bucket}") String inputBucket,
            @Value("${docengine.storage.result-bucket}") String resultBucket) {
        this.storage = storage;
        this.inputBucket = inputBucket;
        this.resultBucket = resultBucket;
    }

    public String process(String inputReference, String month, String tenantId, String jobId) {
        String inputObject = objectName(inputReference);
        String resultPrefix = "tenants/" + tenantId + "/jobs/" + jobId + "/results/";
        String resultObject = resultPrefix + "monthly-report.csv";
        String pdfObject = resultPrefix + "monthly-report.pdf";
        try (var input = storage.downloadInput(inputBucket, inputObject);
             var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
             var output = new ByteArrayOutputStream();
             var writer = new PrintWriter(output, true, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (!"orderId,customer,amount,date".equals(header)) {
                throw new IllegalArgumentException("CSV header is invalid");
            }
            List<String[]> rows = new ArrayList<>();
            writer.println(header);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.endsWith("," + month + "-01") || line.matches(".*," + month + "-[0-9]{2}")) {
                    writer.println(line);
                    rows.add(splitRow(line));
                }
            }
            writer.flush();
            byte[] result = output.toByteArray();
            storage.storeResult(resultBucket, resultObject,
                    new ByteArrayInputStream(result), result.length, "text/csv");
            byte[] pdf = createPdf(month, rows);
            return storage.storeResult(resultBucket, pdfObject,
                    new ByteArrayInputStream(pdf), pdf.length, "application/pdf");
        } catch (Exception exception) {
            throw new IllegalStateException("Could not generate monthly report", exception);
        }
    }

    private byte[] createPdf(String month, List<String[]> rows) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float y = 740;
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
                content.beginText();
                content.newLineAtOffset(50, y);
                content.showText("DocEngine Monthly Sales Report - " + month);
                content.endText();

                y -= 35;
                String[] headers = {"orderId", "customer", "amount", "date"};
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 9);
                y = writePdfRow(content, headers, y);
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                for (String[] row : rows) {
                    if (y < 50) {
                        break;
                    }
                    y = writePdfRow(content, row, y);
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private float writePdfRow(PDPageContentStream content, String[] values, float y) throws Exception {
        content.beginText();
        content.newLineAtOffset(50, y);
        for (int i = 0; i < values.length; i++) {
            content.showText(values[i].replaceAll("[\\r\\n]", " "));
            if (i < values.length - 1) {
                content.newLineAtOffset(new float[]{80, 240, 80}[i], 0);
            }
        }
        content.endText();
        return y - 16;
    }

    private String[] splitRow(String line) {
        String[] values = line.split(",", -1);
        if (values.length != 4) {
            throw new IllegalArgumentException("CSV row must have four columns");
        }
        return values;
    }

    private String objectName(String reference) {
        int slash = reference.indexOf('/', reference.indexOf("//") + 2);
        return slash < 0 ? reference : reference.substring(slash + 1);
    }
}
