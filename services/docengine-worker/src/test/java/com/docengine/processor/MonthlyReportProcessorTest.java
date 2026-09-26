package com.docengine.processor;

import com.docengine.storage.WorkerObjectStorage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonthlyReportProcessorTest {

    @Test
    void generatesCsvAndReadablePdfWithTheSameMonthlyRows() throws Exception {
        WorkerObjectStorage storage = mock(WorkerObjectStorage.class);
        String csv = "orderId,customer,amount,date\n"
                + "ORD-1001,Acme Co,1250.00,2026-09-01\n"
                + "ORD-1002,Beta Ltd,749.50,2026-09-02\n"
                + "ORD-2000,Other Co,10.00,2026-08-01\n";
        Map<String, byte[]> uploads = new HashMap<>();
        when(storage.download("inputs", "tenant/input.csv"))
                .thenReturn(new ByteArrayInputStream(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        when(storage.upload(eq("results"), any(String.class), any(), anyLong(), any(String.class)))
                .thenAnswer(invocation -> {
                    String objectName = invocation.getArgument(1);
                    try (var input = (java.io.InputStream) invocation.getArgument(2)) {
                        uploads.put(objectName, input.readAllBytes());
                    }
                    return "s3://results/" + objectName;
                });

        MonthlyReportProcessor processor = new MonthlyReportProcessor(storage, "inputs", "results");
        String resultReference = processor.process("s3://inputs/tenant/input.csv", "2026-09", "tenant-1", "job-1");

        assertTrue(resultReference.endsWith("monthly-report.pdf"));
        assertEquals(2, uploads.keySet().size());
        byte[] pdf = uploads.get("tenants/tenant-1/jobs/job-1/results/monthly-report.pdf");
        assertFalse(pdf.length == 0);
        try (var document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("DocEngine Monthly Sales Report - 2026-09"));
            assertTrue(text.contains("ORD-1001"));
            assertTrue(text.contains("ORD-1002"));
            assertFalse(text.contains("ORD-2000"));
        }
    }
}
