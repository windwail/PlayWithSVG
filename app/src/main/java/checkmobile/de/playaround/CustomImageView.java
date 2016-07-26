package checkmobile.de.playaround;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import com.pixplicity.sharp.OnSvgElementListener;
import com.pixplicity.sharp.Sharp;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by icetusk on 19.05.16.
 */
public class CustomImageView extends ImageView {

    private String TAG = this.getClass().getName();

    private Sharp mSvg;
    private Sharp mSvg2;

    private Path p;

    private CustomImageView imageView;

    private Point touched = new Point();

    private String selectedId;

    private ScaleGestureDetector sgd;

    private Float scale = 1f;

    private Matrix matrix = new Matrix();


    private HashMap<String, PathInfo> cache = new HashMap<String, PathInfo>();

    private TreeMap<String, PathInfo> sorted_map;

    private float maxX;

    private float maxY;

    private float minX;

    private float minY;

    private int initialWidth;

    private int initialHeight;

    private float translateX;

    private float translateY;

    private float focusX;

    private float focusY;

    private float startZoomX;

    private float startZoomY;


    Rect rect;

    private GestureDetector GD;

    // We can be in one of these 3 states
    static final int NONE = 0;
    static final int DRAG = 1;
    static final int ZOOM = 2;
    int mode = NONE;

    // Remember some things for zooming
    PointF last = new PointF();
    PointF start = new PointF();
    float minScale = -3f;
    float maxScale = 3f;
    float[] m;

    int viewWidth, viewHeight;
    static final int CLICK = 6;
    float saveScale = 1f;
    protected float origWidth, origHeight;
    int oldMeasuredWidth, oldMeasuredHeight;

    ScaleGestureDetector mScaleDetector;

    Context context;

    private Bitmap bitmap;

    private PointF clickPoint;

    public CustomImageView(Context context) {
        super(context);
        init(context);
    }

    public CustomImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CustomImageView(Context context, AttributeSet attrs, int defStyleAttr) {

        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        ValueComparator bvc = new ValueComparator(cache);
        sorted_map = new TreeMap<>(bvc);
        sharedConstructing(context);
    }

    class ValueComparator implements Comparator<String> {
        Map<String, PathInfo> base;

        public ValueComparator(Map<String, PathInfo> base) {
            this.base = base;
        }

        // Note: this comparator imposes orderings that are inconsistent with
        // equals.
        public int compare(String a, String b) {
            PathInfo pia = base.get(a);
            PathInfo pib = base.get(b);

            float areaA = pia.rectf.height() * pia.rectf.width();
            float areaB = pib.rectf.height() * pib.rectf.width();

            if (areaA >= areaB) {
                return -1;
            } else {
                return 1;
            } // returning 0 would merge keys
        }
    }

    private void sharedConstructing(Context context) {
        super.setClickable(true);
        this.context = context;
        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        matrix = new Matrix();
        m = new float[9];
        //setImageMatrix(matrix);
        //setScaleType(ScaleType.MATRIX);

        setOnTouchListener(new OnTouchListener() {

            private Matrix panMatrix = new Matrix();

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mScaleDetector.onTouchEvent(event);
                PointF curr = new PointF(event.getX(), event.getY());


                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        last.set(curr);
                        start.set(last);
                        mode = DRAG;
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (mode == DRAG) {
                            float deltaX = curr.x - last.x;
                            float deltaY = curr.y - last.y;
                            float fixTransX = getFixDragTrans(deltaX, viewWidth, origWidth * saveScale);
                            float fixTransY = getFixDragTrans(deltaY, viewHeight, origHeight * saveScale);

                            matrix.postTranslate(fixTransX, fixTransY);
                            fixTrans();
                            last.set(curr.x, curr.y);

                            panMatrix.postTranslate(fixTransX, fixTransY);

                            translateX -= deltaX;
                            translateY -= deltaY;
                            Log.e("MOVE", translateX + " " + translateY);

                            /*
                            for(String id: cache.keySet()) {
                                PathInfo pi = cache.get(id);

                                if(pi.zoomedPath == null ) {
                                    pi.zoomedPath = new Path(pi.path);
                                }
                                //pi.zoomedPath.rMoveTo(fixTransX, fixTransY);
                                //pi.zoomedPath.offset(fixTransX, fixTransY);
                                //pi.recalculate();
                            }*/
                            invalidate();
                        }

                        break;

                    case MotionEvent.ACTION_UP:
                        mode = NONE;
                        int xDiff = (int) Math.abs(curr.x - start.x);
                        int yDiff = (int) Math.abs(curr.y - start.y);
                        if (xDiff < CLICK && yDiff < CLICK) {
                            performClick();
                            Log.e("CLICK", "CLICK");

                            clickPoint = curr;

                        }
                        break;

                    case MotionEvent.ACTION_POINTER_UP:
                        mode = NONE;
                        break;
                }

                //setImageMatrix(matrix);
                invalidate();
                return true; // indicate event was handled
            }

        });
    }


    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mode = ZOOM;

            startZoomX = detector.getFocusX();
            startZoomY = detector.getFocusY();

            startZoomX = startZoomX / saveScale - ((-translateX) / saveScale);
            startZoomY = startZoomY / saveScale - ((-translateY) / saveScale);

            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float mScaleFactor = detector.getScaleFactor();
            focusX = detector.getFocusX();
            focusY = detector.getFocusY();

            saveScale *= mScaleFactor;

            if (saveScale > maxScale) {
                saveScale = maxScale;
            } else if (saveScale < minScale) {
                saveScale = minScale;
            }

            return true;
        }
    }

    void fixTrans() {
        matrix.getValues(m);
        float transX = m[Matrix.MTRANS_X];
        float transY = m[Matrix.MTRANS_Y];

        float fixTransX = getFixTrans(transX, viewWidth, origWidth * saveScale);
        float fixTransY = getFixTrans(transY, viewHeight, origHeight * saveScale);

        if (fixTransX != 0 || fixTransY != 0)
            matrix.postTranslate(fixTransX, fixTransY);
    }

    float getFixTrans(float trans, float viewSize, float contentSize) {
        float minTrans, maxTrans;

        if (contentSize <= viewSize) {
            minTrans = 0;
            maxTrans = viewSize - contentSize;
        } else {
            minTrans = viewSize - contentSize;
            maxTrans = 0;
        }

        if (trans < minTrans)
            return -trans + minTrans;
        if (trans > maxTrans)
            return -trans + maxTrans;
        return 0;
    }

    float getFixDragTrans(float delta, float viewSize, float contentSize) {
        if (contentSize <= viewSize) {
            return 0;
        }
        return delta;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        viewWidth = MeasureSpec.getSize(widthMeasureSpec);
        viewHeight = MeasureSpec.getSize(heightMeasureSpec);

        //
        // Rescales image on rotation
        //
        if (oldMeasuredHeight == viewWidth && oldMeasuredHeight == viewHeight
                || viewWidth == 0 || viewHeight == 0)
            return;
        oldMeasuredHeight = viewHeight;
        oldMeasuredWidth = viewWidth;

        if (saveScale == 1) {
            //Fit to screen.
            float scale;

            float scaleX = (float) viewWidth / (float) maxX;
            float scaleY = (float) viewHeight / (float) maxY;
            saveScale = Math.min(scaleX, scaleY);

            origWidth = viewWidth;
            origHeight = viewHeight;

            // We need to draw on screen without scaling.
            Matrix scaleMatrix = new Matrix();
            scaleMatrix.setScale(saveScale, saveScale);

            for(PathInfo pi: cache.values()) {
                pi.path.transform(scaleMatrix);
            }

            maxX *= saveScale;
            maxY *= saveScale;

            saveScale = 1;

            bitmap = Bitmap.createBitmap((int) maxX, (int) maxY, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawEverything(canvas);


        }

    }


    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mSvg = Sharp.loadResource(getResources(), R.raw.car3);

        mSvg.setOnElementListener(new OnSvgElementListener() {
            @Override
            public void onSvgStart(@NonNull Canvas canvas,
                                   @Nullable RectF bounds) {

            }

            @Override
            public void onSvgEnd(@NonNull Canvas canvas,
                                 @Nullable RectF bounds) {

                rect = new Rect((int) bounds.left, (int) bounds.right, (int) bounds.top, (int) bounds.bottom);

                Paint paint = new Paint();
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(10);
                paint.setColor(Color.WHITE);

                sorted_map.putAll(cache);


            }

            @Override
            public <T> T onSvgElement(@Nullable String id,
                                      @NonNull T element,
                                      @Nullable RectF elementBounds,
                                      @NonNull Canvas canvas,
                                      @Nullable RectF canvasBounds,
                                      @Nullable Paint paint) {

                Log.e("PRINT", id);

                p = (Path) element;
                if (p instanceof Path) {

                    RectF rectF = new RectF();
                    Region region = new Region();
                    if (!cache.containsKey(id)) {
                        p.computeBounds(rectF, true);

                        if (rectF.right > maxX) {
                            maxX = rectF.right;
                            Log.e("MAX", "" + rectF.right);
                        }

                        if (rectF.bottom > maxY) {
                            maxY = rectF.bottom;
                        }

                        if (rectF.left < minX) {
                            minX = rectF.left;
                        }

                        if (rectF.top < minY) {
                            minY = rectF.top;
                        }

                        region.setPath(p, new Region((int) rectF.left, (int) rectF.top, (int) rectF.right, (int) rectF.bottom));
                        PathMeasure pm = new PathMeasure(p, false);

                        PathInfo pi = new PathInfo(region, rectF, pm, p);
                        pi.color = paint.getColor();
                        pi.style = paint.getStyle();
                        pi.strokeWidth = paint.getStrokeWidth();
                        pi.id = id;

                        cache.put(id, pi);
                    }
                }
                return element;
            }

            @Override
            public <T> void onSvgElementDrawn(@Nullable String id,
                                              @NonNull T element,
                                              @NonNull Canvas canvas,
                                              @Nullable Paint paint) {

            }

        });

        try {
            mSvg.getSharpPicture();
        } catch (Exception ex) {

        }
    }

    public void incScale() {
        saveScale += 0.2f;
        invalidate();
    }

    public void decScale() {
        saveScale -= 0.2f;
        invalidate();
    }

    boolean drawed = false;

    private void drawEverything(Canvas canvas) {
         /*
        if(touched != null) {
            Paint p = new Paint();
            p.setStrokeWidth(4f);
            p.setColor(Color.WHITE);
            canvas.drawPoint(touched.x, touched.y, p);
        }
        */

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Paint contour = new Paint(Paint.ANTI_ALIAS_FLAG);
        contour.setStyle(Paint.Style.STROKE);
        contour.setStrokeWidth(0.5f);
        contour.setColor(Color.BLACK);

        for (String id : sorted_map.keySet()) {
            PathInfo pi = cache.get(id);


            paint.setStyle(pi.style);
            paint.setColor(pi.color);

            switch (paint.getStyle()) {
                case FILL:
                    paint.setStrokeWidth(0.5f);
                    break;
                case STROKE:
                case FILL_AND_STROKE:
                    paint.setStrokeWidth(pi.strokeWidth);
                    break;
            }

            canvas.drawPath(pi.path, paint);

            if (paint.getStyle() == Paint.Style.FILL) {
                canvas.drawPath(pi.path, contour);
            }


            /*if(pi.selected) {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawPath(pi.zoomedPath, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(Color.GRAY);
                canvas.drawPath(pi.zoomedPath, paint);
                paint.setColor(Color.WHITE);

            }*/
        }


        if(clickPoint != null) {

            float x = clickPoint.x / saveScale - ((-translateX) / saveScale);
            float y = clickPoint.y / saveScale - ((-translateY) / saveScale);

            float size = 20 ;

            //canvas.drawRect(x - size, y - size,  x + size, y + size, new Paint(Paint.ANTI_ALIAS_FLAG));

            Paint damage = new Paint(Paint.ANTI_ALIAS_FLAG);
            damage.setStyle(Paint.Style.STROKE);
            damage.setStrokeWidth(10f);
            damage.setColor(Color.RED);


            canvas.drawLine(x - size, y - size,  x + size, y + size, damage);
            canvas.drawLine(x + size, y - size,  x - size, y + size, damage);


            clickPoint = null;


            PathInfo selected = null;

            for (String id : sorted_map.keySet()) {
                PathInfo pi = cache.get(id);
                if(pi.region.contains((int)x,(int)y)) {
                    Log.e("IN", pi.id);
                    if(selected != null) {
                        if(selected.rectf.width()*selected.rectf.height() > pi.rectf.width()*pi.rectf.height()) {
                            selected = pi;
                            continue;
                        }
                    } else {
                        selected = pi;
                    }
                }
            }

            if(selected != null) {
                Paint select = new Paint(Paint.ANTI_ALIAS_FLAG);
                select.setStyle(Paint.Style.FILL);
                select.setStrokeWidth(0.5f);
                select.setColor(Color.RED);
                select.setAlpha(50);

                selected.path.close();

                canvas.drawPath(selected.path, select);
            }

        }


    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (clickPoint != null) {
            bitmap = Bitmap.createBitmap((int) maxX, (int) maxY, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bitmap);
            drawEverything(c);
        }


        canvas.save();



        //We're going to scale the X and Y coordinates by the same amount
        canvas.scale(saveScale, saveScale, 0, 0);

        //We need to divide by the scale factor here, otherwise we end up with excessive panning based on our zoom level
        //because the translation amount also gets scaled according to how much we've zoomed into the canvas.

        canvas.translate((-translateX) / saveScale, (-translateY) / saveScale);

        float x = (focusX) / saveScale - ((-translateX) / saveScale);
        float y = (focusY) / saveScale - ((-translateY) / saveScale);





        if(mode==ZOOM) {
            canvas.translate((x - startZoomX) * saveScale, (y - startZoomY) * saveScale);
            Log.e("xy", (x-startZoomX) + " " + (y-startZoomY) + " " + (mode==ZOOM));
        }






        canvas.drawBitmap(bitmap, 0f, 0f, new Paint(Paint.ANTI_ALIAS_FLAG));


        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStrokeWidth(30f);
        p.setColor(Color.RED);

        canvas.drawPoint(startZoomX, startZoomY, p);

        p.setColor(Color.GREEN);
        canvas.drawPoint(x, y, p);



        canvas.restore();


    }

    public static class PathInfo {
        public Region region;
        public RectF rectf;
        public PathMeasure pathMeasure;
        public Path path;
        public Path bounds;
        public boolean selected;
        public String id;

        public int color;
        public float strokeWidth;
        public Paint.Style style;

        public PathInfo(Region region, RectF rectf, PathMeasure pathMeasure, Path path) {
            this.region = region;
            this.rectf = rectf;
            this.pathMeasure = pathMeasure;
            this.path = path;
            recalculate();
        }

        public void recalculate() {
            rectf = new RectF();
            path.computeBounds(rectf, true);
            region = new Region();
            region.setPath(path, new Region((int) rectf.left, (int) rectf.top, (int) rectf.right, (int) rectf.bottom));
        }


    }

}
