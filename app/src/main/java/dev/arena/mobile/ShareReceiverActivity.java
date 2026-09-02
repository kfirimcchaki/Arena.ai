package dev.arena.mobile;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Invisible share target. When you share a link/text to Arena AI from any app,
 * the content is forwarded to the main browser activity, which opens the URL
 * (or searches the text).
 */
public class ShareReceiverActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent src = getIntent();
        Intent target = new Intent(this, MainActivity.class);
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (src != null) {
            if (Intent.ACTION_SEND.equals(src.getAction())) {
                String text = src.getStringExtra(Intent.EXTRA_TEXT);
                target.putExtra(MainActivity.EXTRA_TEXT,
                        text == null ? "" : text);
            } else if (src.getData() != null) {
                target.putExtra(MainActivity.EXTRA_LOAD, src.getDataString());
            }
        }
        try {
            startActivity(target);
        } finally {
            finish();
        }
    }
}
