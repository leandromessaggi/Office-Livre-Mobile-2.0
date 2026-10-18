package com.officelivre.mobile.core;

import java.util.*;

/** Livro com várias abas. Os nomes são validados antes de gerar OOXML. */
public final class WorkbookData {
    private final LinkedHashMap<String,SheetData> sheets = new LinkedHashMap<>();
    private String selected;

    public WorkbookData() { addSheet("Planilha1"); }
    public Set<String> names() { return Collections.unmodifiableSet(sheets.keySet()); }
    public int size() { return sheets.size(); }
    public String selectedName() { return selected; }
    public SheetData current() { return sheets.get(selected); }
    public SheetData get(String name) { return sheets.get(name); }
    public void select(String name) {
        if (!sheets.containsKey(name)) throw new IllegalArgumentException("Aba não encontrada: " + name);
        selected = name;
    }
    public void addSheet(String name) {
        verifyName(name);
        if (sheets.size() >= 32) throw new IllegalArgumentException("Máximo de 32 abas nesta versão");
        if (findIgnoreCase(name) != null) throw new IllegalArgumentException("Este nome já existe");
        sheets.put(name, new SheetData());
        selected = name;
    }
    public void rename(String oldName, String newName) {
        if (!sheets.containsKey(oldName)) throw new IllegalArgumentException("Aba não encontrada");
        verifyName(newName);
        String similar = findIgnoreCase(newName);
        if (similar != null && !similar.equals(oldName)) throw new IllegalArgumentException("Nome duplicado");
        LinkedHashMap<String,SheetData> updated = new LinkedHashMap<>();
        for(Map.Entry<String,SheetData> s : sheets.entrySet())
            updated.put(s.getKey().equals(oldName) ? newName : s.getKey(), s.getValue());
        sheets.clear();sheets.putAll(updated);
        if(selected.equals(oldName)) selected = newName;
    }
    public void remove(String name) {
        if(sheets.size()==1)throw new IllegalStateException("É necessário manter pelo menos uma aba");
        if(sheets.remove(name)==null)throw new IllegalArgumentException("Aba não encontrada");
        if(name.equals(selected)) selected = sheets.keySet().iterator().next();
    }
    public void replaceSheet(String name, SheetData data) {
        if(!sheets.containsKey(name) || data == null)throw new IllegalArgumentException("Aba inválida");
        sheets.put(name,data);
    }
    /** Apenas para carregar um XLSX validado, antes de adicionar as abas. */
    void removeForImport() { sheets.clear();selected=null; }
    public static void verifyName(String name) {
        if (name == null || name.trim().isEmpty() || name.length()>31 || !name.equals(name.trim()) ||
            name.matches(".*[\\[\\]\\*\\?/\\\\:].*") || name.startsWith("'") || name.endsWith("'"))
            throw new IllegalArgumentException("Nome inválido (até 31 caracteres, sem : / ? * [ ] \\)");
    }
    private String findIgnoreCase(String query) {
        for(String name:sheets.keySet())if(name.equalsIgnoreCase(query))return name;
        return null;
    }
}
