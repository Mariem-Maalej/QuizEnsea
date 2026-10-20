package com.example.project7.service;

import java.io.File;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

public class PdfTextExtractor {

    public void extractText() {
        String pdfPath = "src/main/resources/exam.pdf";

        File file = new File(pdfPath);

        try (PDDocument document = PDDocument.load(file)) {

            PDFTextStripper stripper = new PDFTextStripper();

            String text = stripper.getText(document);

            System.out.println("========== EXTRACTED TEXT ==========");
            System.out.println(text);
            System.out.println("====================================");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}