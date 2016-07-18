package checkmobile.de.playaround;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import com.pixplicity.sharp.OnSvgElementListener;
import com.pixplicity.sharp.Sharp;
import com.pixplicity.sharp.SharpDrawable;
import com.pixplicity.sharp.SharpPicture;
import com.pixplicity.sharp.SvgParseException;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private static final String DEBUG_TAG = "Gestures";
    private String TAG = this.getClass().getName();

    private Sharp mSvg;

    private CustomImageView imageView;

    private Float scale = 1f;

    private ScaleGestureDetector SGD;

    private GestureDetector GD ;

    private final GestureDetector.SimpleOnGestureListener mGestureListener = new GestureDetector.SimpleOnGestureListener() {

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            Log.e(TAG, "Scrolled "+distanceX+ " " + distanceY);
            imageView.setPan(-distanceX, -distanceY);
            return true;
        }


    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imageView = (CustomImageView) findViewById(R.id.imageView);
        mSvg = Sharp.loadResource(getResources(), R.raw.vectorpaint);
        SGD = new ScaleGestureDetector(this,new ScaleListener());
        GD = new GestureDetector(this, mGestureListener);
    }


    @Override
    public boolean onTouchEvent(MotionEvent event){
        SGD.onTouchEvent(event);
        GD.onTouchEvent(event);

        return super.onTouchEvent(event);
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scale *= detector.getScaleFactor();
            scale = Math.max(0.5f, Math.min(scale, 10f));
            imageView.setScale(scale);
            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
        }
    }
}
