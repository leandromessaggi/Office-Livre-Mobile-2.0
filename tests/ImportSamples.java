import java.io.*;
import com.officelivre.mobile.core.*;
public class ImportSamples {
 public static void main(String[] args)throws Exception {
    OpenXml.WordDocument doc;
    try(InputStream i=new FileInputStream(args[0])){doc=OpenXml.readDocx(i);}
    if(doc.paragraphs.size()!=2)throw new AssertionError("DOCX esperado: 2 parágrafos");
    if(!doc.paragraphs.get(1).runs.get(1).bold)throw new AssertionError("Formatação em negrito não lida");
    SheetData sheet;
    try(InputStream i=new FileInputStream(args[1])){sheet=OpenXml.readXlsx(i);}
    if(!sheet.get("A1").equals("Produto"))throw new AssertionError("sharedStrings import falhou");
    if(!sheet.get("C2").equals("=B2*2"))throw new AssertionError("fórmula import falhou");
    if(!sheet.display("C2").equals("7"))throw new AssertionError("cálculo import falhou");
    System.out.println("OK: importação de arquivos gerados por python-docx e XlsxWriter.");
 }
}
