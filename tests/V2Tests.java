import com.officelivre.mobile.core.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class V2Tests {
    private static int checked;
    static void ok(boolean b,String message){checked++;if(!b)throw new AssertionError(message);}
    static byte[] outputWorkbook(WorkbookData book)throws Exception {
        ByteArrayOutputStream b=new ByteArrayOutputStream();OpenXml.writeWorkbook(b,book);return b.toByteArray();
    }
    static byte[] outputDocx(OpenXml.WordDocument word)throws Exception {
        ByteArrayOutputStream b=new ByteArrayOutputStream();OpenXml.writeDocx(b,word);return b.toByteArray();
    }
    public static void main(String[] args)throws Exception {
        WorkbookData book=new WorkbookData();
        book.current().put("A1","50");book.current().put("B1","=SE(A1>=50;ARRED(10/3;2);0)");
        book.current().put("C1","=SUM(A1:A1)+POWER(2,3)");
        ok("3.33".equals(book.current().display("B1")),"SE + ARRED");
        ok("58".equals(book.current().display("C1")),"POWER + SUM");
        book.current().put("D1","=3^2+5%");ok("9.05".equals(book.current().display("D1")),"potência/percentual");
        book.current().put("E1","=1/0");ok("#DIV/0!".equals(book.current().display("E1")),"divisão por zero");
        book.current().put("F1","=SE(0;F1;42)");ok("42".equals(book.current().display("F1")),"SE curto-circuito");
        book.addSheet("Orçamento 2027");
        book.current().put("A1","Despesa");book.current().put("B1","125.75");
        book.addSheet("Resumo");book.current().put("A1","=SUM(1,2,3)");
        book.rename("Resumo","Indicadores");
        ok(book.names().contains("Indicadores"),"renomear");
        byte[] bytes=outputWorkbook(book);
        WorkbookData decoded=OpenXml.readWorkbook(new ByteArrayInputStream(bytes));
        ok(decoded.size()==3,"três abas xlsx");
        ok(new ArrayList<>(decoded.names()).equals(Arrays.asList("Planilha1","Orçamento 2027","Indicadores")),"ordem/nome de abas");
        ok("Despesa".equals(decoded.get("Orçamento 2027").get("A1")),"dados aba 2");
        ok("125.75".equals(decoded.get("Orçamento 2027").get("B1")),"número brasileiro como valor XLSX");
        SheetData semicolon=new SheetData();
        semicolon.readCsv(new ByteArrayInputStream("Nome;Valor\nLivro;12,5\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        ok("12,5".equals(semicolon.get("B2")),"CSV separado por ponto e vírgula");
        ok("3.33".equals(decoded.get("Planilha1").display("B1")),"fórmulas e normalização");
        ok("6".equals(decoded.get("Indicadores").display("A1")),"aba 3 fórmula");
        decoded.remove("Indicadores");ok(decoded.size()==2,"apagar aba");
        boolean rejected=false;try{decoded.addSheet("Nome/Inválido");}catch(IllegalArgumentException e){rejected=true;}
        ok(rejected,"nome de aba inválido");
        OpenXml.WordDocument word=new OpenXml.WordDocument();
        OpenXml.Paragraph title=new OpenXml.Paragraph();
        title.runs.add(new OpenXml.Run("Relatório",true,false,false,22));word.paragraphs.add(title);word.blocks.add(title);
        OpenXml.Table table=new OpenXml.Table();
        table.rows.add(Arrays.asList("Nome","Preço","Quantidade"));
        table.rows.add(Arrays.asList("Caderno","12,50","3"));word.blocks.add(table);
        OpenXml.Paragraph end=new OpenXml.Paragraph();end.runs.add(new OpenXml.Run("Resultado final",false,true,true));
        word.blocks.add(end);word.paragraphs.add(end);
        byte[] docx=outputDocx(word);
        OpenXml.WordDocument roundtrip=OpenXml.readDocx(new ByteArrayInputStream(docx));
        ok(roundtrip.blocks.size()==3,"blocos mistos");
        ok(roundtrip.blocks.get(1) instanceof OpenXml.Table,"tabela preservada");
        OpenXml.Table imported=(OpenXml.Table)roundtrip.blocks.get(1);
        ok("Caderno".equals(imported.rows.get(1).get(0)),"valor da célula");
        ok(((OpenXml.Paragraph)roundtrip.blocks.get(0)).runs.get(0).sizePt==22,"tamanho do título");
        ok(((OpenXml.Paragraph)roundtrip.blocks.get(2)).runs.get(0).underline,"sublinhado");
        if(args.length>0){
            Path dir=Paths.get(args[0]);Files.createDirectories(dir);
            Files.write(dir.resolve("Exemplo_Multiplas_Abas.xlsx"),bytes);
            Files.write(dir.resolve("Exemplo_Documento_Tabela.docx"),docx);
        }
        System.out.println("OK: "+checked+" testes V2 (planilhas, fórmulas, tabelas e formatação).");
    }
}
