package checkmobile.de.playaround;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Region;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageView;

import com.pixplicity.sharp.Line;
import com.pixplicity.sharp.OnSvgElementListener;
import com.pixplicity.sharp.Sharp;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class SimpleImageView extends ImageView {

    // Remember some things for zooming
    PointF last = new PointF();
    PointF start = new PointF();
    float minScale = 0.8f;
    float maxScale = 3f;
    float[] m;

    // We can be in one of these 3 states
    static final int NONE = 0;
    static final int DRAG = 1;
    static final int ZOOM = 2;
    int mode = NONE;

    // canvas translation
    private float translateX;
    private float translateY;

    // zoom scale factor
    float saveScale = 1f;

    // Distance to seperate drag and click.
    static final int CLICK = 10;

    // Points where we start zoom. Global coordinates.
    private float startZoomX;
    private float startZoomY;

    // Point between panning fingers. Global coordinates.
    private float focusX;
    private float focusY;

    // Size of view widget in pixels.
    int viewWidth, viewHeight;

    private Sharp mSvg;

    // SVG bounds
    //private Rect rectSvg;

    private float maxX;
    private float maxY;
    private float minX;
    private float minY;

    // All svg pathes cache
    private HashMap<String, PathInfo> cache = new HashMap<String, PathInfo>();
    private TreeMap<String, PathInfo> sorted_map;

    // Store screen width and height.
    int oldMeasuredWidth, oldMeasuredHeight;

    ScaleGestureDetector mScaleDetector;

    Context context;

    // Bitmap used to cache picture of svg.
    private Bitmap bitmap;

    private PointF clickPoint;

    public SimpleImageView(Context context) {
        super(context);
        sharedConstructing(context);
    }

    public SimpleImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        sharedConstructing(context);
    }

    public SimpleImageView(Context context, AttributeSet attrs, int defStyleAttr) {

        super(context, attrs, defStyleAttr);
        sharedConstructing(context);
    }

    private void sharedConstructing(Context context) {

        ValueComparator bvc = new ValueComparator(cache);
        sorted_map = new TreeMap<>(bvc);

        super.setClickable(true);
        this.context = context;
        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());

        setOnTouchListener(new OnTouchListener() {
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
                            last.set(curr.x, curr.y);

                            translateX -= deltaX;
                            translateY -= deltaY;

                            invalidate();
                        }

                        break;

                    case MotionEvent.ACTION_UP:
                        mode = NONE;
                        int xDiff = (int) Math.abs(curr.x - start.x);
                        int yDiff = (int) Math.abs(curr.y - start.y);
                        if (xDiff < CLICK && yDiff < CLICK) {
                            performClick();
                            clickPoint = curr;
                        }
                        break;

                    case MotionEvent.ACTION_POINTER_UP:

                        mode = NONE;
                        break;
                }
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

            startZoomX = (startZoomX) / saveScale - ((-translateX) / saveScale);
            startZoomY = (startZoomY) / saveScale - ((-translateY) / saveScale);

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


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        viewWidth = MeasureSpec.getSize(widthMeasureSpec);
        viewHeight = MeasureSpec.getSize(heightMeasureSpec);



        //
        // Rescales image on rotation
        //
        if (oldMeasuredHeight == viewWidth && oldMeasuredHeight == viewHeight || viewWidth == 0 || viewHeight == 0) {
            return;
        }

        oldMeasuredHeight = viewHeight;
        oldMeasuredWidth = viewWidth;

        if (saveScale == 1) {
            //Fit to screen.
            float scale;

            float scaleX = (float) viewWidth / (float) maxX;
            float scaleY = (float) viewHeight / (float) maxY;
            saveScale = Math.min(scaleX, scaleY);

            // We need to draw on screen without scaling.
            Matrix scaleMatrix = new Matrix();
            scaleMatrix.setScale(saveScale, saveScale);

            for(PathInfo pi: cache.values()) {
                pi.path.transform(scaleMatrix);
                pi.recalculate();
            }

            maxX *= saveScale;
            maxY *= saveScale;

            saveScale = 1;

            bitmap = Bitmap.createBitmap((int) maxX, (int) maxY, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawEverything(canvas);
        }

    }

    private void drawEverything(Canvas canvas) {

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

            // Draw contour of fillable paht, coz it has no
            if (paint.getStyle() == Paint.Style.FILL) {
                canvas.drawPath(pi.path, contour);
            }
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

                if(!id.contains("cm:")) {
                    continue;
                }

                if(pi.region.contains((int)x,(int)y)) {
                    selected = pi;
                }
            }

            if(selected != null) {
                Paint select = new Paint(Paint.ANTI_ALIAS_FLAG);
                select.setStyle(Paint.Style.FILL);
                select.setStrokeWidth(0.5f);
                select.setColor(Color.RED);
                select.setAlpha(50);

                //selected.path.close();

                canvas.drawPath(selected.path, select);
            }

        }


    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // translate in local coordinates
        // focus in terms of local coordinates
        float x = (focusX) / saveScale - ((-translateX) / saveScale);
        float y = (focusY) / saveScale - ((-translateY) / saveScale);

        // translate in local coordinates
        float tx = (-translateX) / saveScale;
        float ty = (-translateY) / saveScale;

        // Important not to refresh picture while zooming.
        if (clickPoint != null & mode != ZOOM) {
            bitmap = Bitmap.createBitmap((int) maxX, (int) maxY, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bitmap);
            drawEverything(c);
        }

        canvas.save();

        //We're going to scale the X and Y coordinates by the same amount
        canvas.scale(saveScale, saveScale, 0, 0);

        //We need to divide by the scale factor here, otherwise we end up with excessive panning based on our zoom level
        //because the translation amount also gets scaled according to how much we've zoomed into the canvas.
        if(mode==ZOOM) {
            if ((x*x < 0.1 && y*y < 0.1)
                    || (startZoomX*startZoomX < 0.1 && startZoomY*startZoomX < 0.1)
                    || (focusX*focusX < 0.1 && focusY*focusY < 0.1) ) {
                canvas.translate(tx , ty );
            } else {
                canvas.translate(tx + (x - startZoomX), ty + (y - startZoomY));
                translateX = -(tx + (x - startZoomX)) * saveScale;
                translateY = -(ty + (y - startZoomY)) * saveScale;
            }
        } else {
            canvas.translate(tx , ty );
        }

        // It's important to clear old values! It blinks otherwise
        focusY = focusX = 0f;

        canvas.drawBitmap(bitmap, 0f, 0f, new Paint(Paint.ANTI_ALIAS_FLAG));
        canvas.restore();

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


    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        // car3 is working
        mSvg = Sharp.loadResource(getResources(), R.raw.car_rawww);

        mSvg.setOnElementListener(new OnSvgElementListener() {
            @Override
            public void onSvgStart(@NonNull Canvas canvas,
                                   @Nullable RectF bounds) {

            }

            @Override
            public void onSvgEnd(@NonNull Canvas canvas,
                                 @Nullable RectF bounds) {

                Log.e("MEASURE", "minX:"+minX+" maxX:"+maxX+" minY:" +minY+" maxY:"+maxY);

                float xtransform = 0 - minX;
                float ytransform = 0 - minY;

                Matrix moveMatrix = new Matrix();
                moveMatrix.setTranslate(xtransform, ytransform);

                for(PathInfo pi: cache.values()) {
                    pi.path.transform(moveMatrix);
                    pi.recalculate();
                }

                maxX += xtransform;
                maxY += ytransform;

                sorted_map.putAll(cache);
            }

            @Override
            public <T> T onSvgElement(@Nullable String id,
                                      @NonNull T element,
                                      @Nullable RectF elementBounds,
                                      @NonNull Canvas canvas,
                                      @Nullable RectF canvasBounds,
                                      @Nullable Paint paint) {
                if (element instanceof Path) {
                    Path p = (Path) element;
                    collectPath(p, id, paint);
                } else if(element instanceof Line) {
                    Line l = (Line) element;
                    Path p = new Path();
                    p.moveTo(l.left, l.top);
                    p.lineTo(l.right, l.bottom);
                    collectPath(p, id, paint);
                } else if (element instanceof RectF) {
                    RectF r = (RectF) element;
                    Path p = new Path();
                    p.addRect(r, Path.Direction.CW);
                    collectPath(p, id, paint);
                } else {
                    Log.e("CLASS", element.getClass().getName());
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

        mSvg.getSharpPicture();
    }

    public PathInfo collectPath(Path p, String id) {
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.BLACK);
        return collectPath(p,id,paint);
    }

    public PathInfo collectPath(Path p, String id, Paint paint) {
        RectF rectF = new RectF();
        Region region = new Region();
        if (!cache.containsKey(id)) {
            p.computeBounds(rectF, true);

            if (rectF.right > maxX) {
                maxX = rectF.right;
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

            return cache.put(id, pi);
        } else {
            return null;
        }
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
