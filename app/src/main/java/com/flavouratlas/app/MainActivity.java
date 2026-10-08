package com.flavouratlas.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/**
 * Khmer Kreung: shows app/src/main/assets/index.html fully offline.
 * Never closes silently: any problem is shown on screen with its cause.
 */
public class MainActivity extends Activity {
    private static final int PICK_FILE = 1;
    private static final int SAVE_FILE = 2;
    private static final String HOST = "appassets.androidplatform.net";
    private static final String BASE = "https://" + HOST + "/assets/";

    private FrameLayout root;
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private String pendingSave;
    private int rendererRestarts = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        installCrashRecorder();

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#EEEFEA"));
        setContentView(root);

        // If the app crashed last time, show why instead of crashing again silently.
        SharedPreferences sp = getSharedPreferences("crash", MODE_PRIVATE);
        String last = sp.getString("last", null);
        if (last != null) {
            sp.edit().remove("last").commit();
            showError("Khmer Kreung closed unexpectedly last time. Details:", last);
            return;
        }

        try {
            createWebView();
            if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) loadApp();
        } catch (Throwable t) {
            showError("Khmer Kreung could not start. If this mentions WebView, update "
                    + "\"Android System WebView\" in the Play Store.", stack(t));
        }
    }

    /** Saves the cause of any crash so the next launch can show it. */
    private void installCrashRecorder() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                getSharedPreferences("crash", MODE_PRIVATE).edit().putString("last", stack(error)).commit();
            } catch (Throwable ignored) { }
            if (previous != null) previous.uncaughtException(thread, error);
            else System.exit(2);
        });
    }

    private void createWebView() {
        web = new WebView(this);
        root.removeAllViews();
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase();
                if (HOST.equals(u.getHost())) return serveAsset(u.getPath());
                // Block internet traffic only; data:, blob: and about: are the app's own content.
                if (scheme.equals("http") || scheme.equals("https")) {
                    return new WebResourceResponse("text/plain", "utf-8", 403, "Blocked", null,
                            new ByteArrayInputStream(new byte[0]));
                }
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                if ("http".equals(scheme) || "https".equals(scheme))
                    return !HOST.equals(request.getUrl().getHost());
                return false;
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                // The web engine crashed (often low memory). Rebuild it instead of closing the app.
                root.removeView(view);
                view.destroy();
                if (view == web) web = null;
                rendererRestarts++;
                if (rendererRestarts > 2) {
                    showError("The web engine keeps stopping. Update \"Android System WebView\" and "
                            + "\"Chrome\" in the Play Store, then restart your device.", "");
                } else {
                    Toast.makeText(MainActivity.this, "Reloading Khmer Kreung…", Toast.LENGTH_SHORT).show();
                    try { createWebView(); loadApp(); }
                    catch (Throwable t) { showError("Khmer Kreung could not restart.", stack(t)); }
                }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage m) {
                if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR)
                    Toast.makeText(MainActivity.this, "Page error: " + m.message()
                            + " (line " + m.lineNumber() + ")", Toast.LENGTH_LONG).show();
                return true;
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                try {
                    startActivityForResult(p.createIntent(), PICK_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
    }

    /** Serves files from inside the APK at the app's private address. */
    private WebResourceResponse serveAsset(String path) {
        if (path == null || !path.startsWith("/assets/")) return notFound();
        try {
            InputStream in = getAssets().open(path.substring(8));
            return new WebResourceResponse(mime(path), "utf-8", in);
        } catch (Exception e) {
            return notFound();
        }
    }

    private static WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not found", null,
                new ByteArrayInputStream(new byte[0]));
    }

    private static String mime(String p) {
        p = p.toLowerCase();
        if (p.endsWith(".html")) return "text/html";
        if (p.endsWith(".js")) return "application/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".json")) return "application/json";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }

    /** Reads index.html from inside the APK and shows it at a fixed private address. */
    private void loadApp() {
        String html;
        try (InputStream in = getAssets().open("index.html")) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            html = new String(buf.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            showError("index.html is missing. It must be at app/src/main/assets/index.html", stack(e));
            return;
        }
        // Same address every time, so saved notes and photos stay available after updates.
        web.loadDataWithBaseURL(BASE, html, "text/html", "UTF-8", null);
    }

    /** A plain native error screen with a Try again button. */
    private void showError(String title, String details) {
        if (web != null) {
            try { root.removeView(web); web.destroy(); } catch (Throwable ignored) { }
            web = null;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 96, 48, 48);
        box.setBackgroundColor(Color.WHITE);

        TextView head = new TextView(this);
        head.setText(title);
        head.setTextSize(18);
        head.setTextColor(Color.BLACK);
        box.addView(head);

        TextView body = new TextView(this);
        body.setText(details);
        body.setTextSize(13);
        body.setTextColor(Color.DKGRAY);
        body.setTextIsSelectable(true);
        body.setPadding(0, 32, 0, 32);
        box.addView(body);

        Button retry = new Button(this);
        retry.setText("Try again");
        retry.setOnClickListener(v -> recreate());
        box.addView(retry);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.WHITE);
        sv.addView(box);
        root.removeAllViews();
        root.addView(sv);
    }

    private static String stack(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    /** Lets the page save a backup file; the person chooses where. */
    class Bridge {
        @JavascriptInterface
        public void saveFile(final String name, final String content) {
            runOnUiThread(() -> {
                pendingSave = content;
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, name);
                try {
                    startActivityForResult(i, SAVE_FILE);
                } catch (Exception e) {
                    pendingSave = null;
                    Toast.makeText(MainActivity.this, "No file app available to save the backup",
                            Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                fileCallback = null;
            }
        } else if (requestCode == SAVE_FILE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingSave != null) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    out.write(pendingSave.getBytes(StandardCharsets.UTF_8));
                    Toast.makeText(this, "Backup saved", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Backup not saved: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
            pendingSave = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (web == null) { finish(); return; }
        // Back closes an open recipe or ingredient sheet first; otherwise leaves the app.
        web.evaluateJavascript(
            "(function(){var s=document.getElementById('sheet');"
          + "if(s&&s.classList.contains('open')&&typeof closeSheet==='function'){closeSheet();return 1}return 0})()",
            r -> { if (!"1".equals(r)) finish(); });
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (web != null) web.saveState(out);
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            try { web.destroy(); } catch (Throwable ignored) { }
            web = null;
        }
        super.onDestroy();
    }
}
