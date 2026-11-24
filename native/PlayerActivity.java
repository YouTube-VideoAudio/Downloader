package com.videoaudio.downloader;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

public class PlayerActivity extends Activity {
    private VideoView vv;
    private int pos = 0;
    private Uri uri;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String u = getIntent().getStringExtra("uri");
        String title = getIntent().getStringExtra("title");
        if (u == null) { finish(); return; }
        uri = Uri.parse(u);
        if (b != null) pos = b.getInt("pos", 0);
        float d = getResources().getDisplayMetrics().density;
        int p = (int) (14 * d);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        vv = new VideoView(this);
        root.addView(vv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        TextView t = new TextView(this);
        t.setText(title == null ? "" : title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(15);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setBackgroundColor(0x66000000);
        t.setPadding(p, p, p * 4, p);
        root.addView(t, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        TextView x = new TextView(this);
        x.setText("\u2715");
        x.setTextColor(Color.WHITE);
        x.setTextSize(20);
        x.setPadding(p, p, p, p);
        x.setOnClickListener(v -> finish());
        root.addView(x, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END));

        setContentView(root);

        MediaController mc = new MediaController(this);
        mc.setAnchorView(vv);
        vv.setMediaController(mc);
        vv.setVideoURI(uri);
        vv.setOnPreparedListener(mp -> {
            if (pos > 0) vv.seekTo(pos);
            vv.start();
        });
        vv.setOnErrorListener((mp, what, extra) -> {
            openExternal();
            return true;
        });
    }

    private void openExternal() {
        try {
            Intent v = new Intent(Intent.ACTION_VIEW);
            String type = getContentResolver().getType(uri);
            v.setDataAndType(uri, type == null ? "video/*" : type);
            v.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(v, "Open with"));
        } catch (Exception e) {
            Toast.makeText(this, "Cannot play this file.", Toast.LENGTH_LONG).show();
        }
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (vv != null) out.putInt("pos", vv.getCurrentPosition());
    }

    @Override
    protected void onPause() {
        if (vv != null) {
            pos = vv.getCurrentPosition();
            vv.pause();
        }
        super.onPause();
    }
}
