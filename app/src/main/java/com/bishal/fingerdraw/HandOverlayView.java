package com.bishal.fingerdraw;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.mediapipe.formats.proto.LandmarkProto;
import com.google.mediapipe.solutions.hands.HandLandmark;
import com.google.mediapipe.solutions.hands.HandsResult;

import java.util.ArrayList;
import java.util.List;
public class HandOverlayView extends View {

    private static float INITIAL_LINE_WIDTH = 10.0f;  // Initial width
    private float currentLineWidth = INITIAL_LINE_WIDTH;  // Current line width, can be changed dynamically
    private static int LINE_COLOR = Color.RED;
    private static int LINE_COLOR2 = Color.BLUE;

    private final Paint paint = new Paint();
    private final Path path = new Path();
    private final List<Point> points = new ArrayList<>();
    private boolean isDrawing = false;
    private final Path path2 = new Path();
    private final List<Point> points2 = new ArrayList<>();
    private boolean isDrawing2 = false;
    private final Paint paint2 = new Paint();


    public HandOverlayView(Context context) {
        super(context);
        init();
    }

    public HandOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HandOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint.setColor(LINE_COLOR);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(INITIAL_LINE_WIDTH);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
         paint2.setColor(LINE_COLOR2);
        paint2.setStyle(Paint.Style.STROKE);
        paint2.setStrokeWidth(INITIAL_LINE_WIDTH);
        paint2.setStrokeJoin(Paint.Join.ROUND);
        paint2.setStrokeCap(Paint.Cap.ROUND);;
        setBackgroundColor(Color.TRANSPARENT);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawPath(path, paint);
        canvas.drawPath(path2,paint2);
    }

    public void updateLandmarks(HandsResult handsResult) {
        if (handsResult != null && !handsResult.multiHandLandmarks().isEmpty()) {

            LandmarkProto.NormalizedLandmark indexFingerDIP = handsResult.multiHandLandmarks()
                    .get(0)
                    .getLandmarkList()
                    .get(HandLandmark.INDEX_FINGER_TIP);
            LandmarkProto.NormalizedLandmark thumb = handsResult.multiHandLandmarks()
                    .get(0)
                    .getLandmarkList()
                    .get(HandLandmark.THUMB_TIP);

            if (indexFingerDIP != null && thumb != null) {
                float x = indexFingerDIP.getX() * getWidth();
                float y = indexFingerDIP.getY() * getHeight();
                points.add(new Point(x, y));

                float x2 = thumb.getX() * getWidth();
                float y2 = thumb.getY() * getHeight();
                points2.add(new Point(x2, y2));

                if (!isDrawing) {
                    path.moveTo(x, y);
                    isDrawing = true;
                    path2.moveTo(x2, y2);
                } else {
                    paint.setStrokeWidth(currentLineWidth);
                    path.lineTo(x, y);
                    path2.lineTo(x2, y2);
                }

                postInvalidate();
            } else {
                isDrawing = false;
            }
        }
    }

    public void changeBrushSize(float size) {
        currentLineWidth = size;
    }

    public void changeColor(int color) {
        LINE_COLOR = color;
        paint.setColor(LINE_COLOR);
    }

    public void clearPaths() {
        path.reset();
        points.clear();
        path2.reset();
        points2.clear();
        postInvalidate();
    }

    private static class Point {
        final float x;
        final float y;

        Point(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
}
