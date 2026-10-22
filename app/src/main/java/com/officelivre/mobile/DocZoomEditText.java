package com.officelivre.mobile;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.EditText;

/** Editor de texto com gesto de pinça que altera apenas a apresentação (não o DOCX salvo). */
public final class DocZoomEditText extends EditText {
    public interface ZoomListener { void onZoom(float factor); }
    private final ScaleGestureDetector detector;
    private ZoomListener zoomListener;
    private boolean pinchInProgress;

    public DocZoomEditText(Context context) { this(context, null); }
    public DocZoomEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        detector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector gesture) {
                pinchInProgress = true;
                return true;
            }
            @Override public boolean onScale(ScaleGestureDetector gesture) {
                if (zoomListener != null) zoomListener.onZoom(gesture.getScaleFactor());
                return true;
            }
        });
    }
    public void setZoomListener(ZoomListener listener) { zoomListener = listener; }

    @Override public boolean onTouchEvent(MotionEvent event) {
        detector.onTouchEvent(event);
        int action = event.getActionMasked();
        if (event.getPointerCount() > 1 || action == MotionEvent.ACTION_POINTER_DOWN ||
                action == MotionEvent.ACTION_POINTER_UP || pinchInProgress) {
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) pinchInProgress = false;
            return true;
        }
        return super.onTouchEvent(event);
    }
}
