package checkmobile.de.playaround;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.pixplicity.sharp.OnSvgElementListener;
import com.pixplicity.sharp.Sharp;

public class MultiTouchActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TouchImageView img = new TouchImageView(this);
        img.setMaxZoom(4f);

        Sharp mSvg = Sharp.loadResource(getResources(), R.raw.car3);

        img.setImageDrawable(mSvg.getSharpPicture().createDrawable(img, 600));

        setContentView(img);
    }
}
