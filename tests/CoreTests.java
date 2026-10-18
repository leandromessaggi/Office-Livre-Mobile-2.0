import com.officelivre.mobile.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class CoreTests {
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    public static void main(String[] args) throws Exception {
        SheetData s=new SheetData();
        s.put("A1","10");s.put("B1","20");s.put("C1","=A1+B1*2");
        check("50".equals(s.display("C1")),"aritmética");
        s.put("C2","=SOMA(A1:B1)");check("30".equals(s.display("C2")),"SOMA");
        s.put("C3","=MEDIA(A1:B1)");check("15".equals(s.display("C3")),"MEDIA");
        s.put("C4","=MIN(A1:B1)");check("10".equals(s.display("C4")),"MIN");
        s.put("A5","=B5");s.put("B5","=A5");check("#CICLO!".equals(s.display("A5")),"ciclo");
        s.put("D1","Olá, \"mundo\"");
        ByteArrayOutputStream xlsx=new ByteArrayOutputStream(); OpenXml.writeXlsx(xlsx,s);
        SheetData sx=OpenXml.readXlsx(new ByteArrayInputStream(xlsx.toByteArray()));
        check(sx.get("D1").equals(s.get("D1")),"xlsx texto");
        check(sx.get("C1").equals(s.get("C1")),"xlsx fórmula");
        check(sx.display("C1").equals("50"),"xlsx cálculo");
        ByteArrayOutputStream csv=new ByteArrayOutputStream();s.writeCsv(csv);
        SheetData sc=new SheetData();sc.readCsv(new ByteArrayInputStream(csv.toByteArray()));
        check(sc.get("D1").equals("Olá, \"mundo\""),"csv quoting");
        check(SheetData.ref(0,26).equals("AA1"),"ref AA");
        OpenXml.WordDocument doc=new OpenXml.WordDocument();OpenXml.Paragraph p=new OpenXml.Paragraph();
        p.runs.add(new OpenXml.Run("Título",true,false,false));doc.paragraphs.add(p);
        OpenXml.Paragraph p2=new OpenXml.Paragraph();p2.runs.add(new OpenXml.Run("Olá & <teste>",false,true,true));doc.paragraphs.add(p2);
        ByteArrayOutputStream docx=new ByteArrayOutputStream();OpenXml.writeDocx(docx,doc);
        OpenXml.WordDocument decoded=OpenXml.readDocx(new ByteArrayInputStream(docx.toByteArray()));
        check(decoded.paragraphs.size()==2,"docx parágrafos");
        check(decoded.paragraphs.get(0).runs.get(0).bold,"docx negrito");
        check(decoded.paragraphs.get(1).runs.get(0).text.equals("Olá & <teste>"),"docx escape");
        System.out.println("OK: fórmulas, ciclos, referências, CSV, XLSX e DOCX.");
    }
}
