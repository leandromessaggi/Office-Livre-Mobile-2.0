package com.officelivre.mobile;

import com.tom_roush.pdfbox.cos.COSDictionary;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDAction;
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bookmarks e hyperlinks internos de um PDF. PDFBox trata tanto destinos explícitos
 * quanto destinos nomeados. A UI usa páginas base zero e retângulos normalizados.
 */
public final class PdfOutlineReader {
    private PdfOutlineReader() { }

    public static final class Bookmark {
        public final String title;
        public final int pageIndex;
        public final int depth;
        public Bookmark(String title, int pageIndex, int depth) {
            this.title = title;
            this.pageIndex = pageIndex;
            this.depth = depth;
        }
    }
    public static final class PageLink {
        public final float left, top, right, bottom;
        public final int pageIndex;
        public PageLink(float left, float top, float right, float bottom, int pageIndex) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.pageIndex = pageIndex;
        }
        public boolean contains(float x, float y) {
            return x >= left && x <= right && y >= top && y <= bottom;
        }
    }
    public static final class Result {
        public final List<Bookmark> bookmarks = new ArrayList<>();
        public final Map<Integer, List<PageLink>> links = new HashMap<>();
        public int countLinks() {
            int n = 0;
            for (List<PageLink> list : links.values()) n += list.size();
            return n;
        }
    }
    public static Result read(InputStream stream) throws IOException {
        Result result = new Result();
        try (PDDocument document = PDDocument.load(stream)) {
            // Um HashMap/COSDictionary evita depender de wrappers PDPage idênticos.
            Map<COSDictionary, Integer> index = new HashMap<>();
            int n = 0;
            for (PDPage page : document.getPages()) index.put(page.getCOSObject(), n++);
            PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
            if (outline != null) append(outline.getFirstChild(), 0, document, index,
                    result.bookmarks, new HashSet<>());
            for (int pageNo = 0; pageNo < document.getNumberOfPages(); pageNo++) {
                PDPage page = document.getPage(pageNo);
                PDRectangle crop = page.getCropBox();
                if (crop == null || crop.getWidth() <= 0 || crop.getHeight() <= 0) continue;
                for (PDAnnotation annotation : page.getAnnotations()) {
                    if (!(annotation instanceof PDAnnotationLink)) continue;
                    try {
                        PDAnnotationLink link = (PDAnnotationLink) annotation;
                        PDDestination dest = link.getDestination();
                        PDAction action = link.getAction();
                        if (dest == null && action instanceof PDActionGoTo)
                            dest = ((PDActionGoTo) action).getDestination();
                        int target = resolveDestination(dest, document, index);
                        PDRectangle rect = link.getRectangle();
                        if (target < 0 || rect == null) continue;
                        // PDF usa origem inferior esquerda; Canvas Android usa superior esquerda.
                        float l = clamp((rect.getLowerLeftX() - crop.getLowerLeftX()) / crop.getWidth());
                        float r = clamp((rect.getUpperRightX() - crop.getLowerLeftX()) / crop.getWidth());
                        float t = clamp((crop.getUpperRightY() - rect.getUpperRightY()) / crop.getHeight());
                        float b = clamp((crop.getUpperRightY() - rect.getLowerLeftY()) / crop.getHeight());
                        if (l >= r || t >= b) continue;
                        result.links.computeIfAbsent(pageNo, k -> new ArrayList<>())
                                .add(new PageLink(l, t, r, b, target));
                    } catch (Exception ignored) { /* annotation incompleta */ }
                }
            }
        }
        return result;
    }
    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

    private static int resolveDestination(PDDestination dest, PDDocument document,
            Map<COSDictionary,Integer> pages) throws IOException {
        if (dest instanceof PDNamedDestination)
            dest = document.getDocumentCatalog().findNamedDestinationPage((PDNamedDestination) dest);
        if (!(dest instanceof PDPageDestination)) return -1;
        PDPageDestination ref = (PDPageDestination) dest;
        PDPage targetPage = ref.getPage();
        if (targetPage != null) {
            Integer index = pages.get(targetPage.getCOSObject());
            if (index != null) return index;
            // Fallback para casos em que o PDFBox reconstrói um wrapper da página.
            for (Map.Entry<COSDictionary,Integer> e : pages.entrySet())
                if (e.getKey().equals(targetPage.getCOSObject())) return e.getValue();
        }
        int ordinal = ref.getPageNumber();
        return ordinal >= 0 && ordinal < document.getNumberOfPages() ? ordinal : -1;
    }

    private static void append(PDOutlineItem first, int depth, PDDocument document,
            Map<COSDictionary,Integer> pages, List<Bookmark> output, Set<COSDictionary> visited) {
        if (depth > 16) return;
        for (PDOutlineItem item = first; item != null && output.size() < 1000;
                item = item.getNextSibling()) {
            if (!visited.add(item.getCOSObject())) return;
            String title = item.getTitle();
            if (title == null || title.trim().isEmpty()) title = "(Sem título)";
            int pageNumber = -1;
            try {
                PDDestination destination = item.getDestination();
                if (destination == null && item.getAction() instanceof PDActionGoTo)
                    destination = ((PDActionGoTo)item.getAction()).getDestination();
                pageNumber = resolveDestination(destination, document, pages);
                if (pageNumber < 0) {
                    PDPage fallback = item.findDestinationPage(document);
                    if (fallback != null) {
                        Integer n = pages.get(fallback.getCOSObject());
                        if (n != null) pageNumber = n;
                    }
                }
            } catch (Exception ignored) { /* Alguns marcadores são apenas seções */ }
            output.add(new Bookmark(title.trim(), pageNumber, depth));
            append(item.getFirstChild(), depth + 1, document, pages, output, visited);
        }
    }
}
