package com.officelivre.mobile;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;

/** Visualizador simples de PDF com texto e desenhos posicionados proporcionalmente. */
public final class PdfPageView extends View {
    public enum Tool { TEXT, PEN, HIGHLIGHT }
    public static final class Note {
        public final float x,y;public final String text;
        public Note(float x,float y,String text){this.x=x;this.y=y;this.text=text;}
    }
    public static final class Stroke {
        public final boolean highlighted;
        /** Cada ponto contém x e y normalizados entre 0 e 1. */
        public final List<float[]> points=new ArrayList<>();
        public Stroke(boolean highlighted){this.highlighted=highlighted;}
    }
    public interface OnPdfTap {void onTap(float x,float y);}
    public interface OnZoomGesture {
        void onScale(float factor);
        void onEnd();
    }
    private Bitmap bitmap;
    private List<Note> notes=Collections.emptyList();
    private List<Stroke> strokes=Collections.emptyList();
    private OnPdfTap onPdfTap;
    private Tool tool=Tool.TEXT;
    private final ScaleGestureDetector pinchDetector;
    private OnZoomGesture zoomGesture;
    private boolean pinching;
    private Stroke drawing;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF pageRect=new RectF();

    public PdfPageView(Context ctx){
        super(ctx);setBackgroundColor(Color.rgb(230,235,242));
        pinchDetector=new ScaleGestureDetector(ctx,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScaleBegin(ScaleGestureDetector detector){pinching=true;return true;}
            @Override public boolean onScale(ScaleGestureDetector detector){
                if(zoomGesture!=null)zoomGesture.onScale(detector.getScaleFactor());
                return true;
            }
            @Override public void onScaleEnd(ScaleGestureDetector detector){
                if(zoomGesture!=null)zoomGesture.onEnd();
            }
        });
    }
    public void setOnZoomGesture(OnZoomGesture zoom){zoomGesture=zoom;}
    public void update(Bitmap next,List<Note> texts,List<Stroke> lines){
        if(bitmap!=null&&bitmap!=next&&!bitmap.isRecycled())bitmap.recycle();
        bitmap=next;notes=texts;strokes=lines;drawing=null;requestLayout();invalidate();
    }
    public void setTool(Tool next){tool=next;drawing=null;invalidate();}
    public Tool getTool(){return tool;}
    public void setOnPdfTap(OnPdfTap callback){onPdfTap=callback;}
    @Override protected void onMeasure(int widthMeasureSpec,int heightMeasureSpec){
        int width=MeasureSpec.getSize(widthMeasureSpec);
        float ratio=bitmap==null?1.4f:(float)bitmap.getHeight()/Math.max(1,bitmap.getWidth());
        setMeasuredDimension(width,Math.max(240,(int)(width*ratio)+24));
    }
    private void measurePage(){
        float padding=12f,available=Math.max(1,getWidth()-2*padding);
        float height=bitmap==null?available*1.4f:available*bitmap.getHeight()/Math.max(1,bitmap.getWidth());
        pageRect.set(padding,padding,padding+available,padding+height);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);measurePage();
        if(bitmap==null)return;
        paint.reset();paint.setAntiAlias(true);paint.setFilterBitmap(true);
        paint.setColor(Color.WHITE);canvas.drawRect(pageRect,paint);
        canvas.drawBitmap(bitmap,null,pageRect,paint);
        for(Stroke stroke:strokes)drawStroke(canvas,stroke,pageRect,paint);
        paint.reset();paint.setAntiAlias(true);paint.setColor(Color.BLACK);
        paint.setTextSize(16f*getResources().getDisplayMetrics().scaledDensity);
        for(Note note:notes)drawNote(canvas,note,pageRect,paint);
    }
    public static void drawNote(Canvas canvas,Note note,RectF page,Paint p){
        float x=page.left+note.x*page.width(),y=page.top+note.y*page.height();
        String[] lines=note.text.split("\\n",-1);
        for(String line:lines){
            float available=Math.max(25,page.right-x-5);
            String part=line;
            while(!part.isEmpty()&&p.measureText(part)>available)part=part.substring(0,part.length()-1);
            canvas.drawText(part,x,y,p);y+=p.getTextSize()*1.28f;
            if(y>page.bottom)break;
        }
    }
    public static void drawStroke(Canvas canvas,Stroke stroke,RectF page,Paint p){
        if(stroke.points.isEmpty())return;
        p.reset();p.setAntiAlias(true);p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(stroke.highlighted?Color.rgb(255,218,18):Color.rgb(30,80,180));
        p.setAlpha(stroke.highlighted?95:255);
        p.setStrokeWidth(Math.max(1,page.width()*(stroke.highlighted?0.028f:0.004f)));
        Path path=new Path();boolean first=true;
        for(float[] xy:stroke.points){
            float x=page.left+xy[0]*page.width(),y=page.top+xy[1]*page.height();
            if(first){path.moveTo(x,y);first=false;}else path.lineTo(x,y);
        }
        if(stroke.points.size()==1){float[] xy=stroke.points.get(0);path.lineTo(page.left+xy[0]*page.width()+0.1f,page.top+xy[1]*page.height()+0.1f);}
        canvas.drawPath(path,p);
        p.setAlpha(255);p.setStyle(Paint.Style.FILL);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        pinchDetector.onTouchEvent(event);
        measurePage();
        float x=event.getX(),y=event.getY();
        int kind=event.getActionMasked();
        if(event.getPointerCount()>1 || kind==MotionEvent.ACTION_POINTER_DOWN ||
                kind==MotionEvent.ACTION_POINTER_UP || pinching){
            if(drawing!=null){strokes.remove(drawing);drawing=null;invalidate();}
            if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
            if(kind==MotionEvent.ACTION_UP || kind==MotionEvent.ACTION_CANCEL)pinching=false;
            return true;
        }
        if(kind==MotionEvent.ACTION_DOWN){
            if(bitmap==null||!pageRect.contains(x,y))return false;
            if(tool!=Tool.TEXT){
                getParent().requestDisallowInterceptTouchEvent(true);
                drawing=new Stroke(tool==Tool.HIGHLIGHT);strokes.add(drawing);
                pushPoint(x,y);invalidate();
            }
            return true;
        }
        if(kind==MotionEvent.ACTION_MOVE){
            if(drawing!=null){pushPoint(x,y);invalidate();}return true;
        }
        if(kind==MotionEvent.ACTION_UP){
            if(drawing!=null){pushPoint(x,y);drawing=null;invalidate();}
            else if(tool==Tool.TEXT&&pageRect.contains(x,y)&&onPdfTap!=null)
                onPdfTap.onTap((x-pageRect.left)/pageRect.width(),(y-pageRect.top)/pageRect.height());
            performClick();return true;
        }
        if(kind==MotionEvent.ACTION_CANCEL){drawing=null;return true;}
        return true;
    }
    private void pushPoint(float x,float y){
        if(drawing==null)return;
        float rx=Math.max(0,Math.min(1,(x-pageRect.left)/pageRect.width()));
        float ry=Math.max(0,Math.min(1,(y-pageRect.top)/pageRect.height()));
        if(drawing.points.size()<20000)drawing.points.add(new float[]{rx,ry});
    }
    @Override public boolean performClick(){super.performClick();return true;}
    public void release(){if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();bitmap=null;drawing=null;}
}
