package com.officelivre.mobile;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Extrai os bookmarks/sumário estrutural de PDFs, sem interpretar o texto da página. */
public final class PdfOutlineReader {
    private PdfOutlineReader() {}

    public static final class Bookmark {
        public final String title;
        public final int pageIndex; // Base zero, -1 para títulos sem destino.
        public final int depth;
        public Bookmark(String title, int pageIndex, int depth) {
            this.title = title;
            this.pageIndex = pageIndex;
            this.depth = depth;
        }
    }

    public static List<Bookmark> read(InputStream stream) throws IOException {
        List<Bookmark> result = new ArrayList<>();
        try (PDDocument document = PDDocument.load(stream)) {
            PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
            if (outline == null) return result;
            // A identidade da página COS é estável mesmo que o wrapper PDPage seja recriado.
            Map<Object, Integer> pages = new IdentityHashMap<>();
            int number = 0;
            for (PDPage page : document.getPages()) pages.put(page.getCOSObject(), number++);
            append(outline.getFirstChild(), 0, document, pages, result, new IdentityHashMap<>());
        }
        return result;
    }

    private static void append(PDOutlineItem item, int depth, PDDocument document,
            Map<Object, Integer> pages, List<Bookmark> result,
            IdentityHashMap<PDOutlineItem, Boolean> visited) {
        if (depth > 12) return;
        int siblings = 0;
        while (item != null && result.size() < 600 && ++siblings < 1000) {
            // Previne laços em arquivos malformados.
            if (visited.put(item, Boolean.TRUE) != null) return;
            String title = item.getTitle();
            if (title == null || title.trim().isEmpty()) title = "(Sem título)";
            int pageIndex = -1;
            try {
                PDPage destination = item.findDestinationPage(document);
                if (destination != null) {
                    Integer resolved = pages.get(destination.getCOSObject());
                    if (resolved != null) pageIndex = resolved;
                }
            } catch (Exception ignored) {
                // Um bookmark defeituoso não deve impedir a leitura dos demais.
            }
            result.add(new Bookmark(title.trim(), pageIndex, depth));
            append(item.getFirstChild(), depth + 1, document, pages, result, visited);
            item = item.getNextSibling();
        }
    }
}
