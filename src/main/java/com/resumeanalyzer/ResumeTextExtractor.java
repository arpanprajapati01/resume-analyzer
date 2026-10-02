package com.resumeanalyzer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class ResumeTextExtractor {
    public String extract(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        byte[] bytes = file.getBytes();
        if (name.endsWith(".pdf")) {
            try (PDDocument doc = Loader.loadPDF(bytes)) { return new PDFTextStripper().getText(doc); }
        }
        if (name.endsWith(".docx")) {
            try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
                 XWPFWordExtractor ex = new XWPFWordExtractor(doc)) { return ex.getText(); }
        }
        return new String(bytes, StandardCharsets.UTF_8); // .txt fallback
    }
}
