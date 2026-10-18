package com.officelivre.mobile.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Grade esparsa. Armazena fórmulas como digitadas, sem sobrescrever com resultado. */
public final class SheetData {
    private final LinkedHashMap<String, String> cells = new LinkedHashMap<>();
    private static final Pattern REF = Pattern.compile("([A-Za-z]+)([1-9][0-9]*)");

    public static String ref(int row, int col) {
        if (row < 0 || col < 0) throw new IllegalArgumentException("Índice negativo");
        StringBuilder sb = new StringBuilder();
        for (int n = col + 1; n > 0; n = (n - 1) / 26) sb.insert(0, (char) ('A' + (n - 1) % 26));
        return sb.append(row + 1).toString();
    }
    public static int[] parseRef(String s) {
        Matcher m = REF.matcher(s.trim());
        if (!m.matches()) throw new IllegalArgumentException("Célula inválida: " + s);
        int c = 0;
        for (char ch : m.group(1).toUpperCase(Locale.ROOT).toCharArray()) c = c * 26 + ch - 'A' + 1;
        int r = Integer.parseInt(m.group(2));
        if (r > 100000 || c > 16384) throw new IllegalArgumentException("Fora do limite");
        return new int[]{r - 1, c - 1};
    }
    public String get(String cell) { return cells.getOrDefault(cell.trim().toUpperCase(Locale.ROOT), ""); }
    public void put(String cell, String value) {
        String key = ref(parseRef(cell)[0], parseRef(cell)[1]);
        if (value == null || value.isEmpty()) cells.remove(key); else cells.put(key, value);
    }
    public Map<String,String> cells() { return Collections.unmodifiableMap(cells); }
    public String display(String cell) {
        try { return new FormulaEngine(this).display(cell); }
        catch (RuntimeException ex) { return "#ERRO"; }
    }

    public void readCsv(InputStream input) throws IOException {
        cells.clear();
        String text = new String(readLimited(input, 4_000_000), StandardCharsets.UTF_8);
        if(text.startsWith("\uFEFF"))text=text.substring(1);
        // RFC4180, com detecção adicional de ponto e vírgula comum em CSVs brasileiros.
        char separator=detectSeparator(text);
        int r = 0, c = 0;
        StringBuilder val = new StringBuilder();
        boolean quotes = false;
        for (int i = 0; i <= text.length(); i++) {
            char ch = i == text.length() ? '\n' : text.charAt(i);
            if (ch == '"') {
                if (quotes && i + 1 < text.length() && text.charAt(i + 1) == '"') { val.append('"'); i++; }
                else quotes = !quotes;
            } else if (ch == separator && !quotes) {
                if (r < 5000 && c < 256) put(ref(r,c),val.toString());
                c++; val.setLength(0);
            } else if ((ch == '\r' || ch == '\n') && !quotes) {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i+1) == '\n') i++;
                if (r < 5000 && c < 256) put(ref(r,c),val.toString());
                r++; c=0; val.setLength(0);
            } else val.append(ch);
        }
    }
    private static char detectSeparator(String text) {
        int commas=0,semicolons=0;boolean quotes=false;
        for(int i=0;i<text.length();i++) {
            char ch=text.charAt(i);
            if(ch=='"'){
                if(quotes&&i+1<text.length()&&text.charAt(i+1)=='"'){i++;continue;}
                quotes=!quotes;
            }else if(!quotes){
                if(ch=='\n'||ch=='\r')break;
                if(ch==',')commas++;
                if(ch==';')semicolons++;
            }
        }
        return semicolons>0&&semicolons>=commas?';':',';
    }
    public void writeCsv(OutputStream output) throws IOException {
        int maxRow = 0, maxCol = 0;
        for (String key : cells.keySet()) {
            int[] rc = parseRef(key);
            maxRow = Math.max(maxRow, rc[0]); maxCol = Math.max(maxCol, rc[1]);
        }
        Writer w = new OutputStreamWriter(output, StandardCharsets.UTF_8);
        for (int r=0; r<=maxRow; r++) {
            for(int c=0; c<=maxCol; c++) {
                if (c>0) w.write(',');
                String value = get(ref(r,c));
                if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r"))
                    w.write('"' + value.replace("\"", "\"\"") + '"');
                else w.write(value);
            }
            w.write("\r\n");
        }
        w.flush();
    }
    static byte[] readLimited(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n, total=0;
        while ((n = in.read(buf)) != -1) { total += n; if (total > max) throw new IOException("Arquivo muito grande"); out.write(buf,0,n); }
        return out.toByteArray();
    }
}
