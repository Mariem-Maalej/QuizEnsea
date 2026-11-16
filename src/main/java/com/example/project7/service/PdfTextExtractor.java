package com.example.project7.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

public class PdfTextExtractor {

    private static final String DEFAULT_PDF_PATH = "src/main/resources/exam.pdf";

    /** A horizontal gap wider than this many times the font size separates two answers. */
    private static final float COLUMN_GAP_FACTOR = 2.5f;

    /**
     * PDFBox inserts a space when the gap between two letters is wider than this fraction
     * of a space. The default (0.5) glues some words together in LaTeX PDFs ("Quevalent").
     */
    private static final float SPACING_TOLERANCE = 0.3f;

    // Kept in a static field so the logger (and its level) is not garbage-collected.
    private static final Logger PDFBOX_FONT_LOGGER =
            Logger.getLogger("org.apache.pdfbox.pdmodel.font");

    static {
        // AMC's corner marks use the LCIRCLE10 font, which has no Unicode mapping.
        PDFBOX_FONT_LOGGER.setLevel(Level.SEVERE);
    }

    /**
     * One visual line of the PDF.
     *
     * @param text     the line's text; a '\t' marks a wide horizontal gap (e.g. between
     *                 answers laid out side by side)
     * @param x        x position where the line starts (PDF points)
     * @param endX     x position where the line ends
     * @param fontSize largest font size on the line, in points
     * @param page     1-based page number
     */
    public record PdfLine(String text, float x, float endX, float fontSize, int page) {
    }

    public List<PdfLine> extractLines() {
        return extractLines(new File(DEFAULT_PDF_PATH));
    }

    public List<PdfLine> extractLines(File file) {
        try (PDDocument document = PDDocument.load(file)) {
            LineCollector collector = new LineCollector();
            collector.getText(document);
            return collector.lines;
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /** Plain text, kept for debugging. */
    public String extractText() {
        try (PDDocument document = PDDocument.load(new File(DEFAULT_PDF_PATH))) {
            return new PDFTextStripper().getText(document);
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * Collects lines with their position and font size instead of writing plain text.
     */
    private static class LineCollector extends PDFTextStripper {

        private final List<PdfLine> lines = new ArrayList<>();
        private final StringBuilder text = new StringBuilder();
        private float startX;
        private float endX;
        private float maxFontSize;

        LineCollector() throws IOException {
            super();
            setSpacingTolerance(SPACING_TOLERANCE);
        }

        @Override
        protected void writeString(String word, List<TextPosition> positions) {
            List<TextPosition> kept = new ArrayList<>();
            for (TextPosition tp : positions) {
                if (!isCornerMark(tp)) {
                    kept.add(tp);
                }
            }
            if (kept.isEmpty()) {
                return;
            }

            TextPosition first = kept.get(0);
            TextPosition last = kept.get(kept.size() - 1);
            float wordStart = first.getXDirAdj();

            if (text.length() == 0) {
                startX = wordStart;
            } else {
                float gap = wordStart - endX;
                text.append(gap > COLUMN_GAP_FACTOR * first.getFontSizeInPt() ? '\t' : ' ');
            }

            if (kept.size() == positions.size()) {
                text.append(word);
            } else {
                for (TextPosition tp : kept) {
                    if (tp.getUnicode() != null) {
                        text.append(tp.getUnicode());
                    }
                }
            }

            for (TextPosition tp : kept) {
                maxFontSize = Math.max(maxFontSize, tp.getFontSizeInPt());
            }
            endX = last.getXDirAdj() + last.getWidthDirAdj();
        }

        @Override
        protected void writeWordSeparator() {
            // Separators are added in writeString, based on the real gap between words.
        }

        @Override
        protected void writeLineSeparator() {
            flushLine();
        }

        @Override
        protected void endPage(PDPage page) throws IOException {
            flushLine();
            super.endPage(page);
        }

        private void flushLine() {
            if (text.length() > 0) {
                lines.add(new PdfLine(text.toString(), startX, endX, maxFontSize, getCurrentPageNo()));
            }
            text.setLength(0);
            maxFontSize = 0;
        }

        /** AMC draws the page-corner marks with the LCIRCLE10 font (they come out as "y"). */
        private static boolean isCornerMark(TextPosition tp) {
            return tp.getFont() != null
                    && tp.getFont().getName() != null
                    && tp.getFont().getName().contains("LCIRCLE");
        }
    }
}