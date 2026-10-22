package com.officelivre.mobile;

import android.app.*;
import android.os.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import com.officelivre.mobile.core.OpenXml;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.officelivre.mobile.core.SheetData;
import com.officelivre.mobile.core.WorkbookData;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class MainActivity extends Activity {
    private static final int OPEN_FILE=7, SAVE_FILE=8;
    private static final int BLUE=Color.rgb(18,78,174), INK=Color.rgb(28,43,65), GRAY=Color.rgb(101,115,135);
    private static final int LIGHT=Color.rgb(245,248,252), EDGE=Color.rgb(220,228,237);
    private static final String DOCX="application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String XLSX="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private enum Mode { HOME, DOC, SHEET, PDF }
    private Mode mode=Mode.HOME;
    private LinearLayout main, frame;
    private DocZoomEditText docInput;
    private EditText formulaInput;
    private float docZoom=1f, pdfZoom=1f, docFontSp=17f;
    private TextView docZoomLabel,pdfZoomLabel;
    private HorizontalScrollView pdfHorizontal;
    private ScrollView pdfVertical;
    private final ArrayList<PdfOutlineReader.Bookmark> pdfBookmarks=new ArrayList<>();
    private volatile boolean pdfIndexLoading=false;
    private WorkbookData book=new WorkbookData();
    private SheetData sheet=book.current();
    private String selectedCell="A1", filename="Novo documento";
    private TextView selectedLabel, pageLabel;
    private GridLayout sheetGrid;
    private int rowOffset=0,colOffset=0;
    private String exportType="";
    private Uri pdfSource;
    private ParcelFileDescriptor pdfFd;
    private PdfRenderer pdfRenderer;
    private int currentPage=0;
    private PdfPageView pdfView;
    private final HashMap<Integer,List<PdfPageView.Note>> pdfNotes=new HashMap<>();
    private final HashMap<Integer,List<PdfPageView.Stroke>> pdfStrokes=new HashMap<>();

    @Override public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(10,49,116));
        if(Build.VERSION.SDK_INT>=35) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        PDFBoxResourceLoader.init(getApplicationContext());
        renderHome();
        if(getIntent()!=null && Intent.ACTION_VIEW.equals(getIntent().getAction()) && getIntent().getData()!=null)
            openFile(getIntent().getData());
    }
    private int dp(float v){return (int)(getResources().getDisplayMetrics().density*v+0.5f);}
    private LinearLayout vertical(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout horizontal(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private void pad(View v,int h,int z){v.setPadding(dp(h),dp(z),dp(h),dp(z));}
    private TextView label(String text,int sp,boolean heavy,int color){
        TextView t=new TextView(this);t.setText(text);t.setTextColor(color);t.setTextSize(sp);
        if(heavy)t.setTypeface(null,android.graphics.Typeface.BOLD);return t;
    }
    private android.graphics.drawable.GradientDrawable bg(int color,int stroke,int radius){
        android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();
        g.setColor(color);g.setCornerRadius(dp(radius));if(stroke!=0)g.setStroke(dp(1),stroke);return g;
    }
    private void setup(Mode m,String heading,String description){
        mode=m;
        frame=vertical();frame.setBackgroundColor(LIGHT);
        frame.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets s=insets.getInsets(WindowInsets.Type.statusBars());
                android.graphics.Insets n=insets.getInsets(WindowInsets.Type.navigationBars());
                v.setPadding(0,s.top,0,n.bottom);
            } else {
                v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        setContentView(frame);
        LinearLayout head=vertical();head.setBackgroundColor(BLUE);pad(head,18,14);
        TextView title=label("▣   Office Livre",22,true,Color.WHITE);head.addView(title);
        TextView subtitle=label(description,12,false,Color.rgb(222,236,255));subtitle.setPadding(0,dp(3),0,0);head.addView(subtitle);
        frame.addView(head);
        LinearLayout nav=horizontal();nav.setBackgroundColor(Color.WHITE);pad(nav,6,6);
        tab(nav,"Início",Mode.HOME);tab(nav,"Texto",Mode.DOC);tab(nav,"Planilha",Mode.SHEET);tab(nav,"PDF",Mode.PDF);
        frame.addView(nav);
        main=vertical();frame.addView(main,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void tab(LinearLayout row,String text,Mode dest){
        TextView t=label(text,13,mode==dest,mode==dest?BLUE:GRAY);
        t.setGravity(Gravity.CENTER);t.setBackground(bg(mode==dest?Color.rgb(229,239,255):Color.TRANSPARENT,0,10));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1);lp.setMargins(dp(2),0,dp(2),0);
        row.addView(t,lp);
        t.setOnClickListener(v->{if(dest==Mode.HOME)renderHome();else if(dest==Mode.DOC)renderDoc();else if(dest==Mode.SHEET)renderSheet();else renderPdf();});
    }
    private TextView action(String text,LinearLayout row,Runnable onClick){
        TextView b=label(text,13,true,BLUE);b.setGravity(Gravity.CENTER);pad(b,13,10);
        b.setBackground(bg(Color.WHITE,EDGE,9));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(43));lp.setMargins(0,0,dp(7),0);
        row.addView(b,lp);b.setOnClickListener(v->onClick.run());return b;
    }
    private LinearLayout buttons(boolean horizontalScroll){
        LinearLayout row=horizontal();pad(row,15,10);
        if(horizontalScroll){
            HorizontalScrollView hsv=new HorizontalScrollView(this);hsv.setHorizontalScrollBarEnabled(false);
            hsv.addView(row);main.addView(hsv);return row;
        }main.addView(row);return row;
    }
    private void card(LinearLayout parent,String symbol,String title,String description,Runnable onClick){
        LinearLayout box=horizontal();pad(box,15,17);
        box.setBackground(bg(Color.WHITE,EDGE,14));
        TextView icon=label(symbol,28,true,BLUE);icon.setGravity(Gravity.CENTER);
        box.addView(icon,new LinearLayout.LayoutParams(dp(50),dp(56)));
        LinearLayout info=vertical();info.addView(label(title,17,true,INK));
        TextView descr=label(description,13,false,GRAY);pad(descr,0,5);info.addView(descr);
        box.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=label("›",28,false,GRAY);box.addView(arrow);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(dp(15),dp(12),dp(15),0);
        parent.addView(box,lp);box.setOnClickListener(v->onClick.run());
    }
    private void renderHome(){
        setup(Mode.HOME,"Início","Seu escritório offline no celular");
        ScrollView scroll=new ScrollView(this);main.addView(scroll);
        LinearLayout content=vertical();scroll.addView(content);
        TextView welcome=label("O que você deseja criar?",23,true,INK);pad(welcome,18,22);content.addView(welcome);
        card(content,"▤","Documento de texto","DOCX, TXT, zoom, títulos, tabelas e PDF",()->renderDoc());
        card(content,"▦","Planilha de cálculos","XLSX, CSV, múltiplas abas e novas funções",()->renderSheet());
        card(content,"▧","Visualizar e anotar PDF","Zoom, índice, texto, caneta e marca-texto",()->renderPdf());
        card(content,"＋","Abrir arquivo existente","Escolher documentos no armazenamento",this::chooseFile);
        TextView footer=label("Sem cadastro • Sem internet • Arquivos no seu aparelho",12,false,GRAY);
        pad(footer,20,25);content.addView(footer);
    }
    private void renderDoc(){
        String saved=docInput==null?"":docInput.getText().toString();
        SpannableStringBuilder previous=docInput==null?null:new SpannableStringBuilder(docInput.getText());
        setup(Mode.DOC,"Texto","Editor de documentos");
        LinearLayout actions=buttons(true);
        action("Novo",actions,()->{docInput.setText("");filename="Novo documento";});
        action("Abrir",actions,this::chooseFile);
        action("Salvar DOCX",actions,()->saveAs("docx"));
        action("Salvar TXT",actions,()->saveAs("txt"));
        action("Exportar PDF",actions,()->saveAs("docpdf"));
        LinearLayout zoomRow=buttons(true);
        zoomRow.addView(label("Zoom",13,true,GRAY));
        action("−",zoomRow,()->setDocZoom(docZoom-0.2f));
        docZoomLabel=label("100%",15,true,BLUE);pad(docZoomLabel,10,0);zoomRow.addView(docZoomLabel);
        action("+",zoomRow,()->setDocZoom(docZoom+0.2f));
        action("100%",zoomRow,()->setDocZoom(1f));
        LinearLayout formats=buttons(true);
        action("N",formats,()->styleSelection("b"));
        action("I",formats,()->styleSelection("i"));
        action("S̲",formats,()->styleSelection("u"));
        action("A+",formats,()->resizeText(2));
        action("A−",formats,()->resizeText(-2));
        action("Título",formats,()->styleHeading());
        action("Tabela",formats,this::insertTable);
        action("Localizar",formats,this::findReplace);
        action("• Lista",formats,()->insertAtCursor("• "));
        action("↶ Limpar formato",formats,()->clearSelectionStyle());
        ScrollView editorScroll=new ScrollView(this);editorScroll.setFillViewport(true);
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,0,1);ep.setMargins(dp(12),0,dp(12),dp(14));
        main.addView(editorScroll,ep);
        docInput=new DocZoomEditText(this);docInput.setTextColor(INK);docInput.setTextSize(docFontSp*docZoom);docInput.setGravity(Gravity.TOP|Gravity.START);
        docInput.setZoomListener(factor -> setDocZoom(docZoom*factor));
        docInput.setHint("Comece a digitar seu documento…");
        docInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        docInput.setBackground(bg(Color.WHITE,EDGE,8));pad(docInput,20,21);
        docInput.setMinHeight(dp(480));docInput.setSingleLine(false);
        editorScroll.addView(docInput,new ScrollView.LayoutParams(-1,-1));
        if(previous!=null)docInput.setText(previous);
        updateDocZoomLabel();
    }
    private void setDocZoom(float target){
        float next=Math.max(0.6f,Math.min(2.4f,target));
        if(Math.abs(next-docZoom)<0.002f)return;
        docZoom=next;
        if(docInput!=null)docInput.setTextSize(docFontSp*docZoom);
        updateDocZoomLabel();
    }
    private void updateDocZoomLabel(){if(docZoomLabel!=null)docZoomLabel.setText(Math.round(docZoom*100f)+"%");}
    private void styleSelection(String style){
        if(docInput==null)return;
        int a=Math.min(docInput.getSelectionStart(),docInput.getSelectionEnd());
        int b=Math.max(docInput.getSelectionStart(),docInput.getSelectionEnd());
        if(a<0 || a==b){toast("Selecione um trecho do texto para formatar");return;}
        Object span=style.equals("u")?new UnderlineSpan():new StyleSpan(style.equals("b")?Typeface.BOLD:Typeface.ITALIC);
        docInput.getText().setSpan(span,a,b,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    private void clearSelectionStyle(){
        if(docInput==null)return;
        int a=docInput.getSelectionStart(),b=docInput.getSelectionEnd();if(a==b)return;
        for(Object span:docInput.getText().getSpans(Math.min(a,b),Math.max(a,b),Object.class))
            if(span instanceof StyleSpan||span instanceof UnderlineSpan)docInput.getText().removeSpan(span);
    }
    private void resizeText(int delta){
        docFontSp=Math.max(12f,Math.min(30f,docFontSp+delta));
        if(docInput!=null)docInput.setTextSize(docFontSp*docZoom);
    }
    private void insertAtCursor(String text){if(docInput==null)return;int a=Math.max(0,docInput.getSelectionStart());docInput.getText().insert(a,text);}
    private void styleHeading(){
        if(docInput==null)return;
        int a=Math.max(0,Math.min(docInput.getSelectionStart(),docInput.getSelectionEnd()));
        int b=Math.max(0,Math.max(docInput.getSelectionStart(),docInput.getSelectionEnd()));
        String text=docInput.getText().toString();
        if(a==b){a=text.lastIndexOf('\n',Math.max(0,a-1))+1;int end=text.indexOf('\n',b);b=end<0?text.length():end;}
        if(a==b){toast("Digite ou selecione uma linha para transformar em título");return;}
        docInput.getText().setSpan(new RelativeSizeSpan(22f/docFontSp),a,b,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        docInput.getText().setSpan(new StyleSpan(Typeface.BOLD),a,b,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    private void insertTable(){
        LinearLayout body=vertical();pad(body,18,5);
        EditText rows=new EditText(this),columns=new EditText(this);
        rows.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);columns.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        rows.setText("3");columns.setText("3");rows.setHint("Linhas (1 a 15)");columns.setHint("Colunas (1 a 8)");
        body.addView(label("Linhas",13,true,INK));body.addView(rows);
        body.addView(label("Colunas",13,true,INK));body.addView(columns);
        new AlertDialog.Builder(this).setTitle("Inserir tabela").setView(body).setNegativeButton("Cancelar",null)
            .setPositiveButton("Inserir",(d,w)->{
                try{
                    int r=Integer.parseInt(rows.getText().toString()),c=Integer.parseInt(columns.getText().toString());
                    if(r<1||r>15||c<1||c>8)throw new IllegalArgumentException("Máximo 15 linhas e 8 colunas");
                    StringBuilder table=new StringBuilder("\n");
                    for(int i=0;i<r;i++){
                        for(int j=0;j<c;j++){if(j>0)table.append('\t');table.append(i==0?"Coluna "+(j+1):" ");}
                        table.append('\n');
                    }
                    insertAtCursor(table.toString());
                }catch(Exception ex){toast("Dimensões inválidas: "+ex.getMessage());}
            }).show();
    }
    private void findReplace(){
        if(docInput==null)return;
        LinearLayout body=vertical();pad(body,18,8);
        EditText find=new EditText(this),replace=new EditText(this);
        find.setSingleLine(true);replace.setSingleLine(true);
        find.setHint("Texto para localizar");replace.setHint("Substituir por...");
        body.addView(find);body.addView(replace);
        new AlertDialog.Builder(this).setTitle("Localizar e substituir").setView(body)
            .setNegativeButton("Cancelar",null)
            .setNeutralButton("Localizar",(d,w)->{
                String query=find.getText().toString();if(query.isEmpty())return;
                int start=Math.max(0,docInput.getSelectionEnd());
                String whole=docInput.getText().toString();int idx=whole.indexOf(query,start);
                if(idx<0)idx=whole.indexOf(query);
                if(idx<0)toast("Trecho não encontrado");else{docInput.requestFocus();docInput.setSelection(idx,idx+query.length());}
            })
            .setPositiveButton("Substituir todos",(d,w)->{
                String query=find.getText().toString();if(query.isEmpty())return;
                Editable whole=docInput.getText();int idx=0,count=0;
                while(count<10000 && (idx=whole.toString().indexOf(query,idx))>=0){
                    whole.replace(idx,idx+query.length(),replace.getText());idx+=replace.getText().length();count++;
                }
                toast(count+" substituições");
            }).show();
    }
    private OpenXml.WordDocument documentData(){
        OpenXml.WordDocument doc=new OpenXml.WordDocument();
        Editable content=docInput.getText();String s=content.toString();
        int start=0;
        OpenXml.Table activeTable=null;
        while(start<=s.length()){
            int end=s.indexOf('\n',start);if(end<0)end=s.length();
            String line=s.substring(start,end);
            if(line.contains("\t")){
                if(activeTable==null){activeTable=new OpenXml.Table();doc.blocks.add(activeTable);}
                activeTable.rows.add(Arrays.asList(line.split("\\t",-1)));
            }else{
                activeTable=null;
                OpenXml.Paragraph paragraph=new OpenXml.Paragraph();
                int i=start;
                while(i<end){
                    int flags=styleAt(content,i),j=i+1;
                    while(j<end&&styleAt(content,j)==flags)j++;
                    paragraph.runs.add(new OpenXml.Run(s.substring(i,j),(flags&1)!=0,(flags&2)!=0,(flags&4)!=0,flags>>8));i=j;
                }
                doc.paragraphs.add(paragraph);doc.blocks.add(paragraph);
            }
            if(end==s.length())break;start=end+1;
        }
        return doc;
    }
    private int styleAt(Editable text,int i){
        int flags=0;
        for(StyleSpan sp:text.getSpans(i,i+1,StyleSpan.class)){
            int style=sp.getStyle();if((style&Typeface.BOLD)!=0)flags|=1;if((style&Typeface.ITALIC)!=0)flags|=2;
        }
        if(text.getSpans(i,i+1,UnderlineSpan.class).length>0)flags|=4;
        RelativeSizeSpan[] relative=text.getSpans(i,i+1,RelativeSizeSpan.class);
        if(relative.length>0)flags|=Math.min(72,Math.max(1,Math.round(docFontSp*relative[relative.length-1].getSizeChange())))<<8;
        else {
            AbsoluteSizeSpan[] sizes=text.getSpans(i,i+1,AbsoluteSizeSpan.class);
            if(sizes.length>0)flags|=Math.min(72,sizes[sizes.length-1].getSize())<<8;
        }
        return flags;
    }
    private void renderSheet(){
        setup(Mode.SHEET,"Planilha","Células, números e fórmulas");
        LinearLayout actions=buttons(true);
        action("Nova",actions,()->{book=new WorkbookData();sheet=book.current();filename="Nova planilha";selectedCell="A1";rowOffset=0;colOffset=0;renderSheet();});
        action("Abrir",actions,this::chooseFile);
        action("Salvar XLSX",actions,()->saveAs("xlsx"));
        action("Salvar CSV",actions,()->saveAs("csv"));
        LinearLayout tools=buttons(true);
        action("＋ Aba",tools,this::addSheetDialog);
        action("Renomear aba",tools,this::renameSheetDialog);
        action("Excluir aba",tools,this::deleteSheetDialog);
        LinearLayout sheets=buttons(true);
        for(String tab:book.names()){
            TextView option=action((tab.equals(book.selectedName())?"● ":"○ ")+tab,sheets,()->{
                book.select(tab);sheet=book.current();selectedCell="A1";renderSheet();
            });
            if(tab.equals(book.selectedName()))option.setBackground(bg(Color.rgb(215,232,255),BLUE,9));
        }
        LinearLayout bar=horizontal();pad(bar,12,8);
        selectedLabel=label(selectedCell,15,true,BLUE);selectedLabel.setGravity(Gravity.CENTER);
        bar.addView(selectedLabel,new LinearLayout.LayoutParams(dp(51),dp(45)));
        formulaInput=new EditText(this);formulaInput.setSingleLine(true);formulaInput.setTextSize(14);
        formulaInput.setText(sheet.get(selectedCell));
        formulaInput.setHint("Valor ou =SOMA(A1:B5)");
        bar.addView(formulaInput,new LinearLayout.LayoutParams(0,dp(52),1));
        action("Aplicar",bar,this::applyCell);main.addView(bar);
        LinearLayout navigate=buttons(true);
        action("← Colunas",navigate,()->{colOffset=Math.max(0,colOffset-5);fillGrid();});
        action("Colunas →",navigate,()->{colOffset+=5;fillGrid();});
        action("↑ Linhas",navigate,()->{rowOffset=Math.max(0,rowOffset-15);fillGrid();});
        action("Linhas ↓",navigate,()->{rowOffset+=15;fillGrid();});
        ScrollView gridScroll=new ScrollView(this);gridScroll.setFillViewport(false);
        main.addView(gridScroll,new LinearLayout.LayoutParams(-1,0,1));
        HorizontalScrollView gridHorizontal=new HorizontalScrollView(this);gridScroll.addView(gridHorizontal);
        sheetGrid=new GridLayout(this);sheetGrid.setColumnCount(7);sheetGrid.setBackgroundColor(EDGE);
        gridHorizontal.addView(sheetGrid);fillGrid();
        TextView help=label("Edite acima. Exemplos: =SE(A1>5;100;0)  =ARRED(B2/3;2)  =SOMA(A1:B10)",12,false,GRAY);
        pad(help,15,12);main.addView(help);
    }
    private void addSheetDialog(){
        EditText field=new EditText(this);field.setSingleLine(true);field.setText("Planilha"+(book.size()+1));
        new AlertDialog.Builder(this).setTitle("Criar aba").setView(field).setNegativeButton("Cancelar",null)
            .setPositiveButton("Criar",(d,w)->{
                try{book.addSheet(field.getText().toString());sheet=book.current();selectedCell="A1";renderSheet();}
                catch(Exception ex){toast(ex.getMessage());}
            }).show();
    }
    private void renameSheetDialog(){
        String from=book.selectedName();EditText field=new EditText(this);field.setSingleLine(true);field.setText(from);
        new AlertDialog.Builder(this).setTitle("Renomear aba").setView(field).setNegativeButton("Cancelar",null)
            .setPositiveButton("Renomear",(d,w)->{
                try{book.rename(from,field.getText().toString());renderSheet();}catch(Exception ex){toast(ex.getMessage());}
            }).show();
    }
    private void deleteSheetDialog(){
        String from=book.selectedName();
        new AlertDialog.Builder(this).setTitle("Excluir "+from+"?")
            .setMessage("As células desta aba serão perdidas ao salvar o arquivo.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Excluir",(d,w)->{
                try{book.remove(from);sheet=book.current();selectedCell="A1";renderSheet();}catch(Exception ex){toast(ex.getMessage());}
            }).show();
    }
    private void fillGrid(){
        if(sheetGrid==null)return;
        sheetGrid.removeAllViews();
        for(int r=-1;r<20;r++){
            for(int c=-1;c<6;c++){
                final String ref=r<0||c<0?"":SheetData.ref(rowOffset+r,colOffset+c);
                String val=r<0&&c<0?"▦":r<0?SheetData.ref(0,colOffset+c).replaceAll("[0-9]+",""):
                    c<0?String.valueOf(rowOffset+r+1):sheet.display(ref);
                TextView tv=label(val,13,r<0||c<0,INK);tv.setSingleLine(true);tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                tv.setGravity(Gravity.CENTER_VERTICAL | (c<0?Gravity.CENTER_HORIZONTAL:Gravity.START));
                tv.setPadding(dp(8),0,dp(6),0);
                int color=r<0||c<0?Color.rgb(233,239,248):ref.equals(selectedCell)?Color.rgb(206,224,255):Color.WHITE;
                tv.setBackground(bg(color,EDGE,0));
                GridLayout.LayoutParams lp=new GridLayout.LayoutParams(GridLayout.spec(r+1),GridLayout.spec(c+1));
                lp.width=dp(c<0?48:125);lp.height=dp(41);lp.setMargins(dp(1),dp(1),0,0);
                sheetGrid.addView(tv,lp);
                if(!ref.isEmpty())tv.setOnClickListener(v->{selectedCell=ref;selectedLabel.setText(ref);formulaInput.setText(sheet.get(ref));fillGrid();});
            }
        }
    }
    private void applyCell(){
        sheet.put(selectedCell,formulaInput.getText().toString());
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(formulaInput.getWindowToken(),0);
        formulaInput.clearFocus();fillGrid();
    }
    private void renderPdf(){
        if(pdfView!=null)pdfView.release();
        setup(Mode.PDF,"PDF","Leitura e anotações sobre páginas");
        LinearLayout actions=buttons(true);
        action("Abrir PDF",actions,this::chooseFile);
        action("Salvar PDF anotado",actions,()->{
            if(pdfRenderer==null){toast("Abra um PDF primeiro");return;}saveAs("annotatedpdf");
        });
        action("Texto",actions,()->changePdfTool(PdfPageView.Tool.TEXT));
        action("Caneta",actions,()->changePdfTool(PdfPageView.Tool.PEN));
        action("Marca-texto",actions,()->changePdfTool(PdfPageView.Tool.HIGHLIGHT));
        action("Índice / sumário",actions,this::showPdfIndex);
        action("↶ Traço",actions,()->{
            List<PdfPageView.Stroke> items=pdfStrokes.get(currentPage);
            if(items!=null&&!items.isEmpty()){items.remove(items.size()-1);if(pdfView!=null)pdfView.invalidate();}
        });
        action("↶ Texto",actions,()->{
            List<PdfPageView.Note> items=pdfNotes.get(currentPage);
            if(items!=null&&!items.isEmpty()){items.remove(items.size()-1);if(pdfView!=null)pdfView.invalidate();}
        });
        LinearLayout paging=horizontal();pad(paging,12,4);
        action("◀",paging,()->showPage(Math.max(0,currentPage-1)));
        pageLabel=label("Nenhum PDF aberto",14,true,INK);
        pageLabel.setGravity(Gravity.CENTER);paging.addView(pageLabel,new LinearLayout.LayoutParams(0,dp(40),1));
        action("▶",paging,()->showPage(currentPage+1));
        action("Ir para…",paging,this::goToPdfPage);main.addView(paging);
        LinearLayout zoomRow=buttons(true);
        zoomRow.addView(label("Zoom PDF",13,true,GRAY));
        action("−",zoomRow,()->setPdfZoom(pdfZoom-0.25f,true));
        pdfZoomLabel=label("100%",15,true,BLUE);pad(pdfZoomLabel,10,0);zoomRow.addView(pdfZoomLabel);
        action("+",zoomRow,()->setPdfZoom(pdfZoom+0.25f,true));
        action("Ajustar",zoomRow,()->setPdfZoom(1f,true));
        pdfHorizontal=new HorizontalScrollView(this);
        pdfHorizontal.setFillViewport(true);
        main.addView(pdfHorizontal,new LinearLayout.LayoutParams(-1,0,1));
        pdfVertical=new ScrollView(this);
        pdfVertical.setFillViewport(true);
        pdfHorizontal.addView(pdfVertical,new HorizontalScrollView.LayoutParams(-1,-1));
        pdfView=new PdfPageView(this);
        pdfVertical.addView(pdfView,new ScrollView.LayoutParams(-1,-2));
        pdfView.setOnZoomGesture(new PdfPageView.OnZoomGesture(){
            @Override public void onScale(float factor){setPdfZoom(pdfZoom*factor,false);}
            @Override public void onEnd(){if(pdfRenderer!=null)showPage(currentPage,false);}
        });
        pdfHorizontal.post(this::updatePdfZoomWidth);
        updatePdfZoomLabel();
        pdfView.setOnPdfTap((x,y)->{
            if(pdfRenderer==null){toast("Abra um arquivo PDF");return;}
            EditText field=new EditText(this);field.setTextSize(17);field.setHint("Texto para inserir na página");
            LinearLayout layout=vertical();pad(layout,18,8);layout.addView(field);
            new AlertDialog.Builder(this).setTitle("Adicionar texto ao PDF").setView(layout)
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Inserir",(d,w)->{
                    String text=field.getText().toString().trim();if(text.isEmpty())return;
                    pdfNotes.computeIfAbsent(currentPage,k->new ArrayList<>()).add(new PdfPageView.Note(x,y,text));
                    pdfView.invalidate();
                }).show();
        });
        TextView help=label("Zoom: botões + / − ou pinça. Índice: marcadores internos do PDF; se não houver, use Ir para… Anotações são exportadas como imagem, sem preservar textos, links ou índice.",12,false,GRAY);
        pad(help,15,10);main.addView(help);
        if(pdfRenderer!=null)showPage(currentPage);
    }
    private void updatePdfZoomLabel(){
        if(pdfZoomLabel!=null)pdfZoomLabel.setText(Math.round(pdfZoom*100)+"%");
    }
    private void updatePdfZoomWidth(){
        if(pdfHorizontal==null||pdfVertical==null)return;
        final int base=pdfHorizontal.getWidth()>0 ? pdfHorizontal.getWidth() : getResources().getDisplayMetrics().widthPixels;
        int width=(int)(base*pdfZoom);
        android.view.ViewGroup.LayoutParams params=pdfVertical.getLayoutParams();
        if(params!=null && params.width!=width){
            float hCenter=(pdfHorizontal.getScrollX()+base*0.5f)/Math.max(1f,params.width>0?params.width:base);
            float vCenter=pdfView==null?0:(pdfVertical.getScrollY()+pdfVertical.getHeight()*0.5f)/Math.max(1f,pdfView.getHeight());
            params.width=width;
            pdfVertical.setLayoutParams(params);
            pdfHorizontal.post(()->{
                pdfHorizontal.scrollTo(Math.max(0,Math.round(hCenter*width-base*0.5f)),0);
                if(pdfView!=null)pdfVertical.scrollTo(0,Math.max(0,Math.round(vCenter*pdfView.getHeight()-pdfVertical.getHeight()*0.5f)));
            });
        }
    }
    private void setPdfZoom(float target,boolean refreshBitmap){
        float next=Math.max(0.75f,Math.min(3.5f,target));
        if(Math.abs(next-pdfZoom)<0.002f)return;
        pdfZoom=next;
        updatePdfZoomLabel();
        updatePdfZoomWidth();
        if(refreshBitmap&&pdfRenderer!=null)showPage(currentPage,false);
    }
    private void goToPdfPage(){
        if(pdfRenderer==null){toast("Abra um PDF primeiro");return;}
        EditText input=new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setText(String.valueOf(currentPage+1));
        new AlertDialog.Builder(this).setTitle("Ir para página (1 a "+pdfRenderer.getPageCount()+")")
            .setView(input).setNegativeButton("Cancelar",null)
            .setPositiveButton("Ir",(d,w)->{
                try {
                    int page=Integer.parseInt(input.getText().toString());
                    if(page<1||page>pdfRenderer.getPageCount())throw new IllegalArgumentException();
                    showPage(page-1);
                }catch(Exception ex){toast("Número de página inválido");}
            }).show();
    }
    private void showPdfIndex(){
        if(pdfRenderer==null){toast("Abra um PDF primeiro");return;}
        if(pdfIndexLoading){toast("Carregando índice do PDF…");return;}
        if(pdfBookmarks.isEmpty()){
            new AlertDialog.Builder(this).setTitle("PDF sem sumário interno")
                .setMessage("Este PDF não possui marcadores de índice (bookmarks). Você ainda pode navegar pelo número da página.")
                .setNegativeButton("Fechar",null)
                .setPositiveButton("Ir para página",(d,w)->goToPdfPage()).show();
            return;
        }
        String[] items=new String[pdfBookmarks.size()];
        for(int i=0;i<items.length;i++){
            PdfOutlineReader.Bookmark bookmark=pdfBookmarks.get(i);
            StringBuilder line=new StringBuilder();
            for(int j=0;j<Math.min(8,bookmark.depth);j++)line.append("   ");
            line.append(bookmark.depth>0?"↳ ":"§ ").append(bookmark.title);
            if(bookmark.pageIndex>=0)line.append("  • p. ").append(bookmark.pageIndex+1);
            items[i]=line.toString();
        }
        new AlertDialog.Builder(this).setTitle("Índice / sumário do PDF")
            .setItems(items,(d,position)->{
                int page=pdfBookmarks.get(position).pageIndex;
                if(page>=0 && page<pdfRenderer.getPageCount())showPage(page);
                else toast("Este título não possui uma página vinculada");
            }).setNegativeButton("Fechar",null).show();
    }
    private void readPdfBookmarksAsync(Uri source){
        pdfIndexLoading=true;
        new Thread(()->{
            List<PdfOutlineReader.Bookmark> read=Collections.emptyList();
            try(InputStream stream=getContentResolver().openInputStream(source)){
                if(stream!=null)read=PdfOutlineReader.read(stream);
            }catch(Exception ex){android.util.Log.w("OfficeLivre", "Índice PDF indisponível",ex);}
            final List<PdfOutlineReader.Bookmark> results=read;
            runOnUiThread(()->{
                if(source.equals(pdfSource)){
                    pdfBookmarks.clear();pdfBookmarks.addAll(results);pdfIndexLoading=false;
                }
            });
        },"pdf-index-reader").start();
    }
    private void changePdfTool(PdfPageView.Tool tool){
        if(pdfView==null)return;
        pdfView.setTool(tool);
        toast(tool==PdfPageView.Tool.TEXT?"Toque no PDF para digitar texto":
            tool==PdfPageView.Tool.PEN?"Arraste o dedo para desenhar":"Arraste o dedo para destacar");
    }
    private void showPage(int index){showPage(index,index!=currentPage);}
    private void showPage(int index,boolean resetScroll){
        if(pdfRenderer==null || index<0||index>=pdfRenderer.getPageCount())return;
        currentPage=index;
        try(PdfRenderer.Page page=pdfRenderer.openPage(index)){
            double aspect=(double)page.getHeight()/Math.max(1,page.getWidth());
            int desired=(int)(page.getWidth()*2*Math.sqrt(pdfZoom));
            // Evita OutOfMemoryError em documentos de dimensões extremas.
            int maxWidth=Math.max(1,Math.min(2400,Math.min((int)(3500/Math.max(0.001,aspect)),
                (int)Math.sqrt(7_000_000/Math.max(0.001,aspect)))));
            int width=Math.max(1,Math.min(maxWidth,Math.max(600,desired)));
            int height=Math.max(1,(int)(aspect*width));
            Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            pdfView.update(bitmap,pdfNotes.computeIfAbsent(index,k->new ArrayList<>()),pdfStrokes.computeIfAbsent(index,k->new ArrayList<>()));
            pageLabel.setText("Página "+(index+1)+" de "+pdfRenderer.getPageCount());
            if(resetScroll){
                if(pdfVertical!=null)pdfVertical.post(()->pdfVertical.scrollTo(0,0));
                if(pdfHorizontal!=null)pdfHorizontal.post(()->pdfHorizontal.scrollTo(0,0));
            }
        }catch(Exception ex){error("Não foi possível mostrar a página",ex);}
    }
    private void chooseFile(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{DOCX,XLSX,"application/pdf","text/plain","text/csv","application/csv","application/octet-stream"});
        startActivityForResult(intent,OPEN_FILE);
    }
    private void saveAs(String type){
        exportType=type;
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        String extension=type.equals("docpdf")||type.equals("annotatedpdf")?"pdf":type;
        intent.setType(extension.equals("docx")?DOCX:extension.equals("xlsx")?XLSX:extension.equals("pdf")?"application/pdf":extension.equals("csv")?"text/csv":"text/plain");
        String base=filename.replaceAll("\\.(docx|xlsx|pdf|csv|txt)$","").replaceAll("[^\\p{L}0-9 _-]","_");
        intent.putExtra(Intent.EXTRA_TITLE,(base.isEmpty()?"arquivo":base)+"."+extension);
        startActivityForResult(intent,SAVE_FILE);
    }
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        if(req==OPEN_FILE)openFile(data.getData());
        else if(req==SAVE_FILE)writeOutput(data.getData());
    }
    private String getName(Uri uri){
        String name=null;
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst())name=c.getString(0);
        }catch(Exception ignored){}
        if(name==null||name.trim().isEmpty())name=uri.getLastPathSegment();
        return name==null?"arquivo":name;
    }
    private void openFile(Uri uri){
        try {
            String name=getName(uri);String lower=name.toLowerCase(Locale.ROOT);
            String mime=getContentResolver().getType(uri);
            filename=name;
            if(lower.endsWith(".pdf")||"application/pdf".equals(mime)){
                closePdf();pdfSource=uri;pdfFd=getContentResolver().openFileDescriptor(uri,"r");
                if(pdfFd==null)throw new IOException("Sem acesso ao PDF");
                pdfRenderer=new PdfRenderer(pdfFd);currentPage=0;pdfZoom=1f;
                pdfBookmarks.clear();pdfNotes.clear();pdfStrokes.clear();renderPdf();
                readPdfBookmarksAsync(uri);
            }else if(lower.endsWith(".docx")||DOCX.equals(mime)){
                try(InputStream in=getContentResolver().openInputStream(uri)){
                    OpenXml.WordDocument decoded=OpenXml.readDocx(in);renderDoc();
                    SpannableStringBuilder editable=new SpannableStringBuilder();
                    for(Object block:decoded.blocks){
                        if(editable.length()>0)editable.append("\n");
                        if(block instanceof OpenXml.Table){
                            OpenXml.Table table=(OpenXml.Table)block;
                            for(int r=0;r<table.rows.size();r++){
                                if(r>0)editable.append("\n");
                                editable.append(android.text.TextUtils.join("\t",table.rows.get(r)));
                            }
                        }else if(block instanceof OpenXml.Paragraph){
                            for(OpenXml.Run run:((OpenXml.Paragraph)block).runs){
                                int start=editable.length();editable.append(run.text);int end=editable.length();
                                if(end>start){
                                    if(run.bold)editable.setSpan(new StyleSpan(Typeface.BOLD),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    if(run.italic)editable.setSpan(new StyleSpan(Typeface.ITALIC),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    if(run.underline)editable.setSpan(new UnderlineSpan(),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    if(run.sizePt>0)editable.setSpan(new RelativeSizeSpan(run.sizePt/docFontSp),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                }
                            }
                        }
                    }
                    docInput.setText(editable);
                }
            }else if(lower.endsWith(".xlsx")||XLSX.equals(mime)){
                try(InputStream in=getContentResolver().openInputStream(uri)){book=OpenXml.readWorkbook(in);sheet=book.current();selectedCell="A1";renderSheet();}
            }else if(lower.endsWith(".csv")||"text/csv".equals(mime)){
                book=new WorkbookData();sheet=book.current();try(InputStream in=getContentResolver().openInputStream(uri)){sheet.readCsv(in);}selectedCell="A1";renderSheet();
            }else if(lower.endsWith(".txt")||"text/plain".equals(mime)){
                try(InputStream in=getContentResolver().openInputStream(uri)){
                    byte[] bytes=readLimited(in,5_000_000);renderDoc();docInput.setText(new String(bytes,StandardCharsets.UTF_8));
                }
            }else toast("Formato não suportado. Use DOCX, XLSX, CSV, TXT ou PDF.");
        }catch(Exception ex){error("Não foi possível abrir o arquivo",ex);}
    }
    private byte[] readLimited(InputStream in,int limit)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n,count=0;
        while((n=in.read(buf))!=-1){count+=n;if(count>limit)throw new IOException("Arquivo acima do limite");out.write(buf,0,n);}return out.toByteArray();
    }
    private void writeOutput(Uri uri){
        final String type=exportType;
        if("annotatedpdf".equals(type)){writePdfAnnotations(uri);return;}
        try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
            if(out==null)throw new IOException("Não foi possível escrever");
            switch(type){
                case "docx":OpenXml.writeDocx(out,documentData());break;
                case "txt":out.write(docInput.getText().toString().getBytes(StandardCharsets.UTF_8));break;
                case "xlsx":OpenXml.writeWorkbook(out,book);break;
                case "csv":sheet.writeCsv(out);break;
                case "docpdf":writeDocumentPdf(out);break;
                default:throw new IOException("Tipo desconhecido");
            }
            toast("Arquivo salvo com sucesso");
        }catch(Exception e){error("Erro ao salvar",e);}
    }
    private void writeDocumentPdf(OutputStream out)throws IOException{
        PdfDocument pdf=new PdfDocument();
        try{
            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.BLACK);paint.setTextSize(13);
            int index=1,y=50;PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,index).create());
            String[] lines=docInput.getText().toString().split("\\n",-1);
            for(String line:lines){
                if(line.isEmpty()){
                    y+=21;if(y>792){pdf.finishPage(page);page=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,++index).create());y=50;}
                    continue;
                }
                while(!line.isEmpty()){
                    int count=paint.breakText(line,true,515,null);
                    if(count<=0)break;
                    int breakAt=count;
                    if(count<line.length()){
                        int space=line.lastIndexOf(' ',count);
                        if(space>0)breakAt=space+1;
                    }
                    page.getCanvas().drawText(line.substring(0,breakAt),40,y,paint);
                    line=line.substring(breakAt);y+=21;
                    if(y>792){pdf.finishPage(page);page=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,++index).create());y=50;}
                }
            }
            pdf.finishPage(page);pdf.writeTo(out);
        }finally{pdf.close();}
    }
    private void writePdfAnnotations(Uri destination){
        if(pdfSource==null || pdfRenderer==null)return;
        final Uri source=pdfSource;
        final HashMap<Integer,List<PdfPageView.Note>> snapshot=new HashMap<>();
        for(Map.Entry<Integer,List<PdfPageView.Note>> e:pdfNotes.entrySet())
            snapshot.put(e.getKey(),new ArrayList<>(e.getValue()));
        final HashMap<Integer,List<PdfPageView.Stroke>> strokeSnapshot=new HashMap<>();
        for(Map.Entry<Integer,List<PdfPageView.Stroke>> e:pdfStrokes.entrySet()) {
            List<PdfPageView.Stroke> clones=new ArrayList<>();
            for(PdfPageView.Stroke stroke:e.getValue()){
                PdfPageView.Stroke copied=new PdfPageView.Stroke(stroke.highlighted);
                for(float[] point:stroke.points)copied.points.add(point.clone());
                clones.add(copied);
            }
            strokeSnapshot.put(e.getKey(),clones);
        }
        // Usamos outro renderer em background para não disputar páginas com a interface.
        new Thread(()->{
            try(ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(source,"r");
                PdfRenderer reader=new PdfRenderer(fd);
                OutputStream out=getContentResolver().openOutputStream(destination,"wt")){
                if(out==null)throw new IOException("Sem permissão de escrita");
                PdfDocument exported=new PdfDocument();
                try{
                    for(int i=0;i<reader.getPageCount();i++){
                        try(PdfRenderer.Page original=reader.openPage(i)){
                            int w=original.getWidth(),h=original.getHeight();
                            PdfDocument.Page dest=exported.startPage(new PdfDocument.PageInfo.Builder(w,h,i+1).create());
                            float scale=Math.min(2f,1280f/Math.max(w,h));
                            Bitmap bitmap=Bitmap.createBitmap(Math.max(1,(int)(w*scale)),Math.max(1,(int)(h*scale)),Bitmap.Config.ARGB_8888);
                            try{
                                bitmap.eraseColor(Color.WHITE);original.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                                Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
                                dest.getCanvas().drawBitmap(bitmap,null,new Rect(0,0,w,h),paint);
                                List<PdfPageView.Stroke> strokes=strokeSnapshot.get(i);
                                if(strokes!=null)for(PdfPageView.Stroke stroke:strokes)
                                    PdfPageView.drawStroke(dest.getCanvas(),stroke,new RectF(0,0,w,h),paint);
                                paint.reset();paint.setAntiAlias(true);
                                paint.setColor(Color.BLACK);paint.setTextSize(12f);
                                List<PdfPageView.Note> notes=snapshot.get(i);
                                if(notes!=null)for(PdfPageView.Note note:notes)
                                    PdfPageView.drawNote(dest.getCanvas(),note,new RectF(0,0,w,h),paint);
                            }finally{bitmap.recycle();}
                            exported.finishPage(dest);
                        }
                    }
                    exported.writeTo(out);
                }finally{exported.close();}
                runOnUiThread(()->toast("PDF anotado salvo. O conteúdo foi rasterizado."));
            }catch(Exception ex){runOnUiThread(()->error("Erro ao exportar PDF",ex));}
        },"PDF-Exporter").start();
    }
    private void closePdf(){
        if(pdfView!=null)pdfView.release();
        try{if(pdfRenderer!=null)pdfRenderer.close();}catch(Exception ignored){}
        try{if(pdfFd!=null)pdfFd.close();}catch(Exception ignored){}
        pdfRenderer=null;pdfFd=null;
        pdfSource=null;
        pdfBookmarks.clear();pdfIndexLoading=false;
    }
    private void toast(String t){Toast.makeText(this,t,Toast.LENGTH_LONG).show();}
    private void error(String title,Exception e){
        new AlertDialog.Builder(this).setTitle(title).setMessage(e.getMessage()==null?e.toString():e.getMessage()).setPositiveButton("OK",null).show();
    }
    @Override protected void onDestroy(){closePdf();super.onDestroy();}
}
