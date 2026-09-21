package com.spendlocker.pdf;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class PdfService {

    /** Extracts all text content for search indexing (documents.extracted_text). */
    public String extractText(File pdfFile) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            return new PDFTextStripper().getText(document);
        }
    }

    /** Renders the first page as a JavaFX thumbnail image for the vault grid. */
    public Image renderThumbnail(File pdfFile, float dpi) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            if (document.getNumberOfPages() == 0) return null;
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage bufferedImage = renderer.renderImageWithDPI(0, dpi, ImageType.RGB);
            return SwingFXUtils.toFXImage(bufferedImage, null);
        }
    }
}
