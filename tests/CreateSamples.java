import java.io.*;
import com.officelivre.mobile.core.*;
public class CreateSamples {
 public static void main(String[] args)throws Exception {
    OpenXml.WordDocument d=new OpenXml.WordDocument();
    OpenXml.Paragraph p=new OpenXml.Paragraph();p.runs.add(new OpenXml.Run("Relatório de teste",true,false,false));d.paragraphs.add(p);
    p=new OpenXml.Paragraph();p.runs.add(new OpenXml.Run("Linha 2, com <acentuação>",false,true,true));d.paragraphs.add(p);
    try(OutputStream o=new FileOutputStream(args[0])){OpenXml.writeDocx(o,d);}
    SheetData s=new SheetData();s.put("A1","Preço");s.put("B1","Quantidade");s.put("A2","12.5");s.put("B2","4");s.put("C2","=A2*B2");
    try(OutputStream o=new FileOutputStream(args[1])){OpenXml.writeXlsx(o,s);}
 }
}
