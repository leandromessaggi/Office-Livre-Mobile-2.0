package com.officelivre.mobile;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;

/**
 * Leitor PDF com transformações na própria View, sem ScrollViews aninhados.
 * Suporta pinça, arrastar página, links internos e anotações normalizadas.
 */
public final class PdfPageView extends View {
    public enum Tool { TEXT, PEN, HIGHLIGHT }
    public static final class Note {
        public final float x, y;
        public final String text;
        public Note(float x, float y, String text) {this.x=x;this.y=y;this.text=text;}
    }
    public static final class Stroke {
        public final boolean highlighted;
        public final List<float[]> points=new ArrayList<>();
        public Stroke(boolean highlighted) {this.highlighted=highlighted;}
    }
    public interface OnPdfTap {void onTap(float x, float y);}
    public interface OnZoomGesture {
        void onScale(float factor, float focusX, float focusY);
        void onEnd();
    }
    private Bitmap bitmap;
    private List<Note> notes=Collections.emptyList();
    private List<Stroke> strokes=Collections.emptyList();
    private List<PdfOutlineReader.PageLink> links=Collections.emptyList();
    private OnPdfTap onPdfTap;
    private OnZoomGesture zoomGesture;
    private Tool tool=Tool.TEXT;
    private float zoom=1f, panX=0f, panY=0f;
    private float lastX, lastY, touchStartX, touchStartY;
    private boolean dragging=false, pinch=false;
    private Stroke drawing;
    private final float slop;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF pageRect=new RectF();
    private final ScaleGestureDetector scaleDetector;

    public PdfPageView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(230,235,242));
        slop=ViewConfiguration.get(context).getScaledTouchSlop();
        scaleDetector=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScaleBegin(ScaleGestureDetector d) {pinch=true;return true;}
            @Override public boolean onScale(ScaleGestureDetector d) {
                if(zoomGesture!=null)zoomGesture.onScale(d.getScaleFactor(),d.getFocusX(),d.getFocusY());
                else setZoom(zoom*d.getScaleFactor(),d.getFocusX(),d.getFocusY());
                return true;
            }
            @Override public void onScaleEnd(ScaleGestureDetector d) {
                if(zoomGesture!=null)zoomGesture.onEnd();
            }
        });
    }
    public void setOnPdfTap(OnPdfTap callback) {onPdfTap=callback;}
    public void setOnZoomGesture(OnZoomGesture callback) {zoomGesture=callback;}
    public void setTool(Tool t) {tool=t;drawing=null;invalidate();}
    public Tool getTool() {return tool;}
    public void setLinks(List<PdfOutlineReader.PageLink> pageLinks) {
        links=pageLinks==null?Collections.emptyList():pageLinks;
    }
    public int hitPageLink(float x,float y) {
        for(PdfOutlineReader.PageLink link:links) if(link.contains(x,y))return link.pageIndex;
        return -1;
    }
    public void update(Bitmap next,List<Note> texts,List<Stroke> lines) {
        if(bitmap!=null&&bitmap!=next&&!bitmap.isRecycled())bitmap.recycle();
        bitmap=next;
        notes=texts==null?Collections.emptyList():texts;
        strokes=lines==null?Collections.emptyList():lines;
        drawing=null;
        invalidate();
    }
    public float getZoom() {return zoom;}
    public void resetPan() {panX=panY=0;invalidate();}
    public void setZoom(float target) {setZoom(target,getWidth()/2f,getHeight()/2f);}
    public void setZoom(float target,float focusX,float focusY) {
        float next=Math.max(0.75f,Math.min(3.5f,target));
        if(Math.abs(next-zoom)<0.0001f)return;
        RectF old=new RectF();
        calculatePageRect(old);
        float rx=(focusX-old.left)/Math.max(1f,old.width());
        float ry=(focusY-old.top)/Math.max(1f,old.height());
        zoom=next;
        float w=getPageWidth(),h=getPageHeight();
        panX=(getWidth()-w)/2f + rx*w - focusX;
        panY=12f + ry*h - focusY;
        clampPan();
        invalidate();
    }
    private float getPageWidth() {return Math.max(1,getWidth()-24f)*zoom;}
    private float getPageHeight() {
        float ratio=bitmap==null?1.414f:(float)bitmap.getHeight()/Math.max(1,bitmap.getWidth());
        return getPageWidth()*ratio;
    }
    private void clampPan() {
        float width=getPageWidth();
        float maxHorizontal=Math.max(0,(width-getWidth()+24)/2f);
        panX=Math.max(-maxHorizontal,Math.min(maxHorizontal,panX));
        panY=Math.max(0,Math.min(Math.max(0,getPageHeight()+24-getHeight()),panY));
    }
    private void calculatePageRect(RectF out) {
        float w=getPageWidth(),h=getPageHeight();
        float left=(getWidth()-w)/2f-panX;
        float top=12f-panY;
        out.set(left,top,left+w,top+h);
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        super.onSizeChanged(w,h,oldw,oldh);clampPan();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(bitmap==null)return;
        calculatePageRect(pageRect);
        int save=canvas.save();
        canvas.clipRect(0,0,getWidth(),getHeight());
        paint.reset();paint.setAntiAlias(true);paint.setFilterBitmap(true);
        paint.setColor(Color.WHITE);canvas.drawRect(pageRect,paint);
        canvas.drawBitmap(bitmap,null,pageRect,paint);
        for(Stroke stroke:strokes)drawStroke(canvas,stroke,pageRect,paint);
        paint.reset();paint.setAntiAlias(true);paint.setColor(Color.BLACK);
        paint.setTextSize(16f*getResources().getDisplayMetrics().scaledDensity*zoom);
        for(Note note:notes)drawNote(canvas,note,pageRect,paint);
        canvas.restoreToCount(save);
    }
    public static void drawNote(Canvas canvas,Note note,RectF page,Paint p) {
        float x=page.left+note.x*page.width(),y=page.top+note.y*page.height();
        for(String line:note.text.split("\\n",-1)) {
            float available=Math.max(25,page.right-x-5);
            String part=line;
            while(!part.isEmpty()&&p.measureText(part)>available)part=part.substring(0,part.length()-1);
            canvas.drawText(part,x,y,p);y+=p.getTextSize()*1.28f;
            if(y>page.bottom)break;
        }
    }
    public static void drawStroke(Canvas canvas,Stroke stroke,RectF page,Paint p) {
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
        canvas.drawPath(path,p);p.setAlpha(255);p.setStyle(Paint.Style.FILL);
    }
    private void pushPoint(float x,float y) {
        if(drawing==null)return;
        calculatePageRect(pageRect);
        float rx=Math.max(0,Math.min(1,(x-pageRect.left)/pageRect.width()));
        float ry=Math.max(0,Math.min(1,(y-pageRect.top)/pageRect.height()));
        if(drawing.points.size()<20000)drawing.points.add(new float[]{rx,ry});
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN) {
            // O leitor recebe os dois dedos, mesmo que o usuário tenha iniciado arrastando.
            if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
            touchStartX=lastX=event.getX();touchStartY=lastY=event.getY();dragging=false;pinch=false;
            calculatePageRect(pageRect);
            if(bitmap!=null&&tool!=Tool.TEXT&&pageRect.contains(lastX,lastY)) {
                drawing=new Stroke(tool==Tool.HIGHLIGHT);strokes.add(drawing);
                pushPoint(lastX,lastY);invalidate();
            }
            return true;
        }
        if(event.getPointerCount()>1 || action==MotionEvent.ACTION_POINTER_DOWN ||
                action==MotionEvent.ACTION_POINTER_UP || pinch) {
            if(drawing!=null){strokes.remove(drawing);drawing=null;invalidate();}
            if(action==MotionEvent.ACTION_POINTER_UP) {
                lastX=event.getX(0);lastY=event.getY(0);dragging=true;
            }
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)pinch=false;
            return true;
        }
        if(action==MotionEvent.ACTION_MOVE) {
            float x=event.getX(),y=event.getY();
            if(drawing!=null){pushPoint(x,y);invalidate();}
            else {
                if(Math.abs(x-touchStartX)>slop||Math.abs(y-touchStartY)>slop)dragging=true;
                if(dragging){panX-=x-lastX;panY-=y-lastY;clampPan();invalidate();}
            }
            lastX=x;lastY=y;return true;
        }
        if(action==MotionEvent.ACTION_UP) {
            if(drawing!=null){pushPoint(event.getX(),event.getY());drawing=null;invalidate();}
            else if(!dragging && bitmap!=null) {
                calculatePageRect(pageRect);
                if(pageRect.contains(event.getX(),event.getY())&&onPdfTap!=null)
                    onPdfTap.onTap((event.getX()-pageRect.left)/pageRect.width(),
                            (event.getY()-pageRect.top)/pageRect.height());
            }
            pinch=false;performClick();return true;
        }
        if(action==MotionEvent.ACTION_CANCEL){drawing=null;pinch=false;return true;}
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    public void release(){if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();bitmap=null;drawing=null;}
}
