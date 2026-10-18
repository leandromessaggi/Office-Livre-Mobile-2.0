package com.officelivre.mobile.core;

import org.w3c.dom.*;
import org.xml.sax.SAXException;
import javax.xml.parsers.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Leitura/escrita OOXML simplificada: Word com tabelas de texto e Excel com múltiplas abas. */
public final class OpenXml {
    private OpenXml() { }
    public static final class Run {
        public String text; public boolean bold, italic, underline;public int sizePt;
        public Run(String text,boolean b,boolean i,boolean u){this(text,b,i,u,0);}
        public Run(String text,boolean b,boolean i,boolean u,int sizePt){this.text=text;bold=b;italic=i;underline=u;this.sizePt=sizePt;}
    }
    public static final class Paragraph {
        public final List<Run> runs=new ArrayList<>();
    }
    public static final class Table {
        public final List<List<String>> rows=new ArrayList<>();
    }
    public static final class WordDocument {
        /** Blocos em ordem: Paragraph ou Table. */
        public final List<Object> blocks=new ArrayList<>();
        /** Parágrafos fora das tabelas, mantidos por compatibilidade. */
        public final List<Paragraph> paragraphs=new ArrayList<>();
    }
    private static final String W="http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String X="http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String CP="http://schemas.openxmlformats.org/package/2006/content-types";
    private static final String R="http://schemas.openxmlformats.org/package/2006/relationships";

    public static WordDocument readDocx(InputStream in) throws Exception {
        Map<String,byte[]> zip=unzip(in);
        Document xml=parse(required(zip,"word/document.xml"));
        WordDocument result=new WordDocument();
        Element body=(Element)xml.getElementsByTagNameNS(W,"body").item(0);
        if(body==null)throw new IOException("DOCX sem corpo");
        int blocks=0;
        for(Node n=body.getFirstChild();n!=null;n=n.getNextSibling()) {
            if(!(n instanceof Element)||!W.equals(n.getNamespaceURI()))continue;
            if(++blocks>10000)throw new IOException("DOCX com muitos blocos");
            if("p".equals(n.getLocalName())){
                Paragraph p=readParagraph((Element)n);result.paragraphs.add(p);result.blocks.add(p);
            }else if("tbl".equals(n.getLocalName())){
                Table table=new Table();
                for(Node row=n.getFirstChild();row!=null;row=row.getNextSibling()) {
                    if(!(row instanceof Element)||!"tr".equals(row.getLocalName())||!W.equals(row.getNamespaceURI()))continue;
                    List<String> items=new ArrayList<>();
                    for(Node cell=row.getFirstChild();cell!=null;cell=cell.getNextSibling()){
                        if(!(cell instanceof Element)||!"tc".equals(cell.getLocalName())||!W.equals(cell.getNamespaceURI()))continue;
                        StringBuilder value=new StringBuilder();
                        for(Node inner=cell.getFirstChild();inner!=null;inner=inner.getNextSibling()){
                            if(inner instanceof Element&&"p".equals(inner.getLocalName())&&W.equals(inner.getNamespaceURI())){
                                if(value.length()>0)value.append(" / ");
                                for(Run r:readParagraph((Element)inner).runs)value.append(r.text);
                            }
                        }
                        items.add(value.toString());
                    }
                    if(!items.isEmpty())table.rows.add(items);
                    if(table.rows.size()>500)throw new IOException("Tabela com muitas linhas");
                }
                if(!table.rows.isEmpty())result.blocks.add(table);
            }
        }
        return result;
    }
    private static Paragraph readParagraph(Element pe) {
        Paragraph para=new Paragraph();
        NodeList runs=pe.getElementsByTagNameNS(W,"r");
        for(int j=0;j<runs.getLength();j++) {
            Element re=(Element)runs.item(j);
            Element props=firstChild(re,W,"rPr");
            boolean b=props!=null&&hasEnabled(props,"b");
            boolean i=props!=null&&hasEnabled(props,"i");
            boolean u=props!=null&&hasEnabled(props,"u");
            int size=0;
            if(props!=null){Element sz=firstChild(props,W,"sz");if(sz!=null){
                try{size=Integer.parseInt(sz.getAttributeNS(W,"val"))/2;}catch(NumberFormatException ignored){}
            }}
            StringBuilder text=new StringBuilder();
            for(Node node=re.getFirstChild();node!=null;node=node.getNextSibling()) {
                if(node.getNodeType()!=Node.ELEMENT_NODE||!W.equals(node.getNamespaceURI()))continue;
                if("t".equals(node.getLocalName()))text.append(node.getTextContent());
                if("tab".equals(node.getLocalName()))text.append('\t');
                if("br".equals(node.getLocalName()))text.append('\n');
            }
            if(text.length()>0)para.runs.add(new Run(text.toString(),b,i,u,size));
        }
        return para;
    }
    private static boolean hasEnabled(Element parent, String tag) {
        Element e=firstChild(parent,W,tag);
        if(e==null)return false;
        String v=e.getAttributeNS(W,"val");
        return !"0".equals(v)&&!"false".equalsIgnoreCase(v)&&!"none".equalsIgnoreCase(v);
    }
    public static void writeDocx(OutputStream out,WordDocument doc) throws IOException {
        try(ZipOutputStream zip=new ZipOutputStream(out)) {
            entry(zip,"[Content_Types].xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+
                "<Types xmlns=\""+CP+"\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"+
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"+
                "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>");
            entry(zip,"_rels/.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+
                "<Relationships xmlns=\""+R+"\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>");
            StringBuilder xml=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document xmlns:w=\"").append(W).append("\"><w:body>");
            List<Object> blocks=doc.blocks.isEmpty()?new ArrayList<>(doc.paragraphs):doc.blocks;
            for(Object block:blocks) {
                if(block instanceof Paragraph)appendParagraph(xml,(Paragraph)block);
                else if(block instanceof Table) {
                    Table t=(Table)block;
                    xml.append("<w:tbl><w:tblPr><w:tblBorders><w:top w:val=\"single\" w:sz=\"4\"/>")
                        .append("<w:left w:val=\"single\" w:sz=\"4\"/><w:bottom w:val=\"single\" w:sz=\"4\"/>")
                        .append("<w:right w:val=\"single\" w:sz=\"4\"/><w:insideH w:val=\"single\" w:sz=\"4\"/>")
                        .append("<w:insideV w:val=\"single\" w:sz=\"4\"/></w:tblBorders></w:tblPr>");
                    int columns=0;for(List<String> row:t.rows)columns=Math.max(columns,row.size());
                    xml.append("<w:tblGrid>");for(int i=0;i<columns;i++)xml.append("<w:gridCol w:w=\"1800\"/>");xml.append("</w:tblGrid>");
                    for(List<String> row:t.rows){
                        xml.append("<w:tr>");
                        for(int i=0;i<columns;i++){
                            xml.append("<w:tc><w:tcPr><w:tcW w:w=\"1800\" w:type=\"dxa\"/></w:tcPr>");
                            Paragraph cell=new Paragraph();cell.runs.add(new Run(i<row.size()?row.get(i):"",false,false,false));
                            appendParagraph(xml,cell);xml.append("</w:tc>");
                        }
                        xml.append("</w:tr>");
                    }
                    xml.append("</w:tbl>");
                }
            }
            xml.append("<w:sectPr/></w:body></w:document>");
            entry(zip,"word/document.xml",xml.toString());
        }
    }
    private static void appendParagraph(StringBuilder xml,Paragraph p) {
        xml.append("<w:p>");
        for(Run run:p.runs){
            xml.append("<w:r>");
            if(run.bold||run.italic||run.underline||run.sizePt>0){
                xml.append("<w:rPr>");
                if(run.bold)xml.append("<w:b/>");
                if(run.italic)xml.append("<w:i/>");
                if(run.underline)xml.append("<w:u w:val=\"single\"/>");
                if(run.sizePt>0)xml.append("<w:sz w:val=\"").append(Math.min(144,run.sizePt*2)).append("\"/>");
                xml.append("</w:rPr>");
            }
            String[] lines=run.text.split("\\n",-1);
            for(int i=0;i<lines.length;i++){
                if(i>0)xml.append("<w:br/>");
                String[] tabs=lines[i].split("\\t",-1);
                for(int j=0;j<tabs.length;j++){
                    if(j>0)xml.append("<w:tab/>");
                    xml.append("<w:t xml:space=\"preserve\">").append(escape(tabs[j])).append("</w:t>");
                }
            }
            xml.append("</w:r>");
        }
        xml.append("</w:p>");
    }
    /** Compatibilidade: fornece a primeira aba do livro aberto. */
    public static SheetData readXlsx(InputStream in) throws Exception {
        WorkbookData book=readWorkbook(in);
        book.select(book.names().iterator().next());
        return book.current();
    }
    /** Compatibilidade: salva uma única aba. */
    public static void writeXlsx(OutputStream out,SheetData sheet) throws IOException {
        WorkbookData book=new WorkbookData();
        book.replaceSheet("Planilha1",sheet);
        writeWorkbook(out,book);
    }
    /** Carrega todas as abas do arquivo XLSX. Macros, gráficos e estilos não são importados. */
    public static WorkbookData readWorkbook(InputStream in) throws Exception {
        Map<String,byte[]> zip=unzip(in);
        Document workbook=parse(required(zip,"xl/workbook.xml"));
        Document rels=parse(required(zip,"xl/_rels/workbook.xml.rels"));
        Map<String,String> targets=new HashMap<>();
        NodeList relations=rels.getElementsByTagNameNS(R,"Relationship");
        for(int j=0;j<relations.getLength();j++) {
            Element rel=(Element)relations.item(j);
            if("External".equalsIgnoreCase(rel.getAttribute("TargetMode")))continue;
            String target=rel.getAttribute("Target");
            String path;
            if(target.startsWith("/"))path=target.substring(1);
            else path=java.nio.file.Paths.get("xl").resolve(target).normalize().toString().replace('\\','/');
            if(!path.startsWith("xl/"))throw new IOException("Relação fora da pasta xl");
            targets.put(rel.getAttribute("Id"),path);
        }
        List<String> shared=new ArrayList<>();
        if(zip.containsKey("xl/sharedStrings.xml")) {
            Document sharedXml=parse(zip.get("xl/sharedStrings.xml"));
            NodeList sis=sharedXml.getElementsByTagNameNS(X,"si");
            if(sis.getLength()>100000)throw new IOException("Excesso de strings");
            for(int i=0;i<sis.getLength();i++) {
                NodeList texts=((Element)sis.item(i)).getElementsByTagNameNS(X,"t");
                StringBuilder value=new StringBuilder();
                for(int j=0;j<texts.getLength();j++) value.append(texts.item(j).getTextContent());
                shared.add(value.toString());
            }
        }
        NodeList nodes=workbook.getElementsByTagNameNS(X,"sheet");
        if(nodes.getLength()<1||nodes.getLength()>32)throw new IOException("Quantidade de abas fora do limite (1–32)");
        WorkbookData result=new WorkbookData();
        result.removeForImport();
        for(int n=0;n<nodes.getLength();n++) {
            Element e=(Element)nodes.item(n);
            String name=e.getAttribute("name");
            String rel=e.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships","id");
            String target=targets.get(rel);
            if(target==null)throw new IOException("Planilha ausente: " + name);
            SheetData sheet=readWorksheet(required(zip,target),shared);
            result.addSheet(name);
            result.replaceSheet(name,sheet);
        }
        result.select(result.names().iterator().next());
        return result;
    }
    private static SheetData readWorksheet(byte[] bytes,List<String> shared)throws Exception {
        SheetData sheet=new SheetData();
        Document xml=parse(bytes);
        NodeList cs=xml.getElementsByTagNameNS(X,"c");
        if(cs.getLength()>50000)throw new IOException("Muitas células nesta aba");
        for(int j=0;j<cs.getLength();j++) {
            Element c=(Element)cs.item(j);String ref=c.getAttribute("r");
            if(ref.isEmpty())continue;
            Element f=firstChild(c,X,"f"),v=firstChild(c,X,"v"),inline=firstChild(c,X,"is");
            String content="";
            if(f!=null && !f.getTextContent().isEmpty())content="="+f.getTextContent();
            else if("s".equals(c.getAttribute("t")) && v!=null) {
                int index=Integer.parseInt(v.getTextContent());
                if(index>=0&&index<shared.size())content=shared.get(index);
            } else if(inline!=null) {
                NodeList texts=inline.getElementsByTagNameNS(X,"t");
                StringBuilder value=new StringBuilder();
                for(int k=0;k<texts.getLength();k++)value.append(texts.item(k).getTextContent());
                content=value.toString();
            } else if("b".equals(c.getAttribute("t")) && v!=null)content="1".equals(v.getTextContent())?"VERDADEIRO":"FALSO";
            else if(v!=null)content=v.getTextContent();
            if(!content.isEmpty())sheet.put(ref,content);
        }
        return sheet;
    }
    public static void writeWorkbook(OutputStream out,WorkbookData book)throws IOException {
        if(book.size()==0)throw new IOException("Nenhuma aba para salvar");
        try(ZipOutputStream zip=new ZipOutputStream(out)) {
            StringBuilder types=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"").append(CP).append("\">")
                .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
                .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
                .append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
            StringBuilder links=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"").append(R).append("\">");
            StringBuilder workbook=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"").append(X)
                    .append("\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
            int index=0;
            for(String name:book.names()) {
                int number=++index;
                String file="xl/worksheets/sheet"+number+".xml";
                types.append("<Override PartName=\"/").append(file).append("\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
                links.append("<Relationship Id=\"rId").append(number).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet").append(number).append(".xml\"/>");
                workbook.append("<sheet name=\"").append(escape(name)).append("\" sheetId=\"").append(number).append("\" r:id=\"rId").append(number).append("\"/>");
                entry(zip,file,writeWorksheet(book.get(name)));
            }
            entry(zip,"[Content_Types].xml",types.append("</Types>").toString());
            entry(zip,"_rels/.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\""+R+"\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            entry(zip,"xl/_rels/workbook.xml.rels",links.append("</Relationships>").toString());
            entry(zip,"xl/workbook.xml",workbook.append("</sheets></workbook>").toString());
        }
    }
    private static String writeWorksheet(SheetData sheet) {
        TreeMap<Integer,TreeMap<Integer,String>> rows=new TreeMap<>();
        for(Map.Entry<String,String> e:sheet.cells().entrySet()) {
            int[] rc=SheetData.parseRef(e.getKey());
            rows.computeIfAbsent(rc[0],k->new TreeMap<>()).put(rc[1],e.getValue());
        }
        StringBuilder xml=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"").append(X).append("\"><sheetData>");
        for(Map.Entry<Integer,TreeMap<Integer,String>> row:rows.entrySet()) {
            xml.append("<row r=\"").append(row.getKey()+1).append("\">");
            for(Map.Entry<Integer,String> col:row.getValue().entrySet()) {
                String ref=SheetData.ref(row.getKey(),col.getKey()),v=col.getValue();
                if(v.startsWith("=")) xml.append("<c r=\"").append(ref).append("\"><f>").append(escape(canonicalFormula(v.substring(1)))).append("</f></c>");
                else if(v.matches("[+-]?(?:[0-9]+(?:[.,][0-9]*)?|[.,][0-9]+)(?:[eE][+-]?[0-9]+)?")) xml.append("<c r=\"").append(ref).append("\"><v>").append(v.replace(',','.')).append("</v></c>");
                else xml.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(escape(v)).append("</t></is></c>");
            }
            xml.append("</row>");
        }
        return xml.append("</sheetData></worksheet>").toString();
    }
    private static String canonicalFormula(String formula) {
        // Excel usa nomes internacionais e vírgulas como separador de argumentos em OOXML.
        String s=formula.replace(';',',');
        String[][] names={{"SOMA","SUM"},{"MEDIA","AVERAGE"},{"MÉDIA","AVERAGE"},{"CONTAR","COUNT"},
            {"SE","IF"},{"ARRED","ROUND"},{"RAIZ","SQRT"},{"POTENCIA","POWER"},
            {"OU","OR"},{"E","AND"}};
        for(String[] pair:names)s=s.replaceAll("(?i)\\b"+pair[0]+"(?=\\s*\\()",pair[1]);
        return s;
    }
    private static byte[] required(Map<String,byte[]> map,String key) throws IOException {
        byte[] found=map.get(key);if(found==null)throw new IOException("Parte não encontrada: "+key);return found;
    }
    private static Map<String,byte[]> unzip(InputStream input) throws IOException {
        Map<String,byte[]> entries=new HashMap<>();
        try(ZipInputStream zip=new ZipInputStream(input)) {
            ZipEntry e;int count=0;long total=0;
            while((e=zip.getNextEntry())!=null){
                if(++count>500)throw new IOException("Muitas partes ZIP");
                if(e.isDirectory())continue;
                byte[] b=SheetData.readLimited(zip,16_000_000);
                total+=b.length;if(total>48_000_000)throw new IOException("Documento muito grande");
                entries.put(e.getName(),b);
            }
        }
        return entries;
    }
    private static Document parse(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // Bloqueia DTD/XXE; nem todas as implementações Android suportam todas as features.
        String head=new String(bytes,0,Math.min(bytes.length,4096),StandardCharsets.UTF_8);
        if(head.toUpperCase(Locale.ROOT).contains("<!DOCTYPE") || head.toUpperCase(Locale.ROOT).contains("<!ENTITY"))
            throw new IOException("XML com DTD/entidades não é aceito");
        try{factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);}catch(ParserConfigurationException ignored){}
        try{factory.setFeature("http://xml.org/sax/features/external-general-entities",false);}catch(ParserConfigurationException ignored){}
        try{factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);}catch(ParserConfigurationException ignored){}
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }
    private static Element firstChild(Element parent,String ns,String local){
        for(Node n=parent.getFirstChild();n!=null;n=n.getNextSibling())
            if(n instanceof Element && local.equals(n.getLocalName())&&ns.equals(n.getNamespaceURI()))return(Element)n;
        return null;
    }
    private static String escape(String s){
        StringBuilder result=new StringBuilder();
        for(int k=0;k<s.length();k++){
            char c=s.charAt(k);
            if(c=='&')result.append("&amp;");else if(c=='<')result.append("&lt;");else if(c=='>')result.append("&gt;");
            else if(c=='\"')result.append("&quot;");else if(c=='\'')result.append("&apos;");
            else if(c=='\t'||c=='\n'||c=='\r'||c>=32)result.append(c);
        }
        return result.toString();
    }
    private static void entry(ZipOutputStream zip,String name,String xml) throws IOException {
        zip.putNextEntry(new ZipEntry(name));zip.write(xml.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
    }
}
