package com.docengine.processor;

import com.docengine.storage.ObjectStorage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CsvToPdfProcessorTest {

    @Test
    void convertsArbitraryHeadersQuotedCommasAndMultilineFieldsIntoPdf() throws Exception {
        ObjectStorage storage = mock(ObjectStorage.class);
        String csv = "product,notes,quantity\r\n"
                + "Widget,\"blue, large\",12\r\n"
                + "Book,\"first line\nsecond line\",3\r\n";
        Map<String, byte[]> uploads = new HashMap<>();
        when(storage.downloadInput("inputs", "tenant/input.csv"))
                .thenReturn(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        when(storage.storeResult(eq("results"), any(String.class), any(), anyLong(), any(String.class)))
                .thenAnswer(invocation -> {
                    String objectName = invocation.getArgument(1);
                    try (var input = (java.io.InputStream) invocation.getArgument(2)) {
                        uploads.put(objectName, input.readAllBytes());
                    }
                    return "s3://results/" + objectName;
                });

        CsvToPdfProcessor processor = new CsvToPdfProcessor(storage, "inputs", "results");
        String resultReference = processor.process("s3://inputs/tenant/input.csv", null, "tenant-1", "job-1");

        assertTrue(resultReference.endsWith("csv-document.pdf"));
        assertEquals(1, uploads.size());
        byte[] pdf = uploads.get("tenants/tenant-1/jobs/job-1/results/csv-document.pdf");
        try (var document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("DocEngine CSV to PDF: input"));
            assertTrue(text.contains("2 records | 3 columns"));
            assertTrue(text.contains("product: Widget"));
            assertTrue(text.contains("notes: blue, large"));
            assertTrue(text.contains("notes: first line second line"));
            assertTrue(text.contains("quantity: 3"));
        }
    }

    @Test
    void rejectsRowsThatDoNotMatchTheHeaderColumnCount() {
        ObjectStorage storage = mock(ObjectStorage.class);
        String csv = "name,price\nItem,10,extra\n";
        when(storage.downloadInput("inputs", "tenant/input.csv"))
                .thenReturn(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        CsvToPdfProcessor processor = new CsvToPdfProcessor(storage, "inputs", "results");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> processor.process("s3://inputs/tenant/input.csv", null, "tenant-1", "job-1"));

        assertTrue(error.getMessage().contains("record 2 has 3 columns; expected 2"));
    }
}
