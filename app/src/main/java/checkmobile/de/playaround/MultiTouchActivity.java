package checkmobile.de.playaround;

public class MultiTouchActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TouchImageView img = new TouchImageView(this);
        img.setImageResource(R.drawable.img);
        img.setMaxZoom(4f);
        setContentView(img);
    }
}
