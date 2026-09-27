package org.lizebi.bibleotetela;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.net.Uri;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

public class MainActivity extends Activity {
    private File bookRoot;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView bar = new TextView(this);
        bar.setText("Bible Otetela");
        bar.setTextSize(20);
        bar.setTextColor(Color.WHITE);
        bar.setGravity(17);
        bar.setPadding(12, 10, 12, 10);
        bar.setBackgroundColor(Color.BLACK);
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(52)));

        WebView web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(false);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        if (Build.VERSION.SDK_INT >= 21) s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new WebViewClient());
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        try {
            bookRoot = new File(getFilesDir(), "book");
            File first = prepareBook(bookRoot);
            web.loadUrl(Uri.fromFile(first).toString());
        } catch (Exception e) {
            web.loadDataWithBaseURL(null,
                "<html><body style='padding:24px'><h2>Impossible d'ouvrir la Bible</h2><p>" +
                escape(e.getMessage()) + "</p></body></html>", "text/html", "UTF-8", null);
        }
    }

    private File prepareBook(File root) throws Exception {
        File marker = new File(root, ".ready");
        if (!marker.exists()) {
            deleteRec(root);
            root.mkdirs();
            File source = new File(getFilesDir(), "source.zip");
            copyAsset("bible_source.zip", source);
            File outer = new File(getFilesDir(), "outer");
            deleteRec(outer); outer.mkdirs();
            unzipSafe(source, outer);
            File epub = findFile(outer, ".epub");
            if (epub == null) throw new IOException("Aucun EPUB trouvé dans l'archive source.");
            File epubDir = new File(root, "epub");
            epubDir.mkdirs();
            unzipSafe(epub, epubDir);
            marker.createNewFile();
        }
        File first = findFirstReadable(root);
        if (first == null) throw new IOException("Aucune page XHTML/HTML trouvée dans l'EPUB.");
        return first;
    }

    private File findFirstReadable(File dir) {
        File[] fs = dir.listFiles();
        if (fs == null) return null;
        Arrays.sort(fs, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File f : fs) {
            if (f.isDirectory()) { File x = findFirstReadable(f); if (x != null) return x; }
            else {
                String n = f.getName().toLowerCase(Locale.ROOT);
                if ((n.endsWith(".xhtml") || n.endsWith(".html") || n.endsWith(".htm")) && !n.contains("nav")) return f;
            }
        }
        return null;
    }

    private File findFile(File dir, String suffix) {
        File[] fs = dir.listFiles();
        if (fs == null) return null;
        for (File f : fs) {
            if (f.isDirectory()) { File x = findFile(f, suffix); if (x != null) return x; }
            else if (f.getName().toLowerCase(Locale.ROOT).endsWith(suffix)) return f;
        }
        return null;
    }

    private void copyAsset(String name, File out) throws IOException {
        try (InputStream in = getAssets().open(name); OutputStream os = new FileOutputStream(out)) {
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) >= 0) os.write(b, 0, n);
        }
    }

    private void unzipSafe(File zip, File dest) throws IOException {
        String base = dest.getCanonicalPath() + File.separator;
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry e; byte[] buf = new byte[8192];
            while ((e = zis.getNextEntry()) != null) {
                File out = new File(dest, e.getName());
                if (!out.getCanonicalPath().startsWith(base)) throw new IOException("Entrée ZIP non sûre");
                if (e.isDirectory()) { out.mkdirs(); continue; }
                File p = out.getParentFile(); if (p != null) p.mkdirs();
                try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                    int n; while ((n = zis.read(buf)) > 0) os.write(buf, 0, n);
                }
            }
        }
    }

    private void deleteRec(File f) {
        if (f == null || !f.exists()) return;
        File[] fs = f.listFiles();
        if (fs != null) for (File x : fs) deleteRec(x);
        f.delete();
    }

    private String escape(String x) {
        if (x == null) return "Erreur inconnue";
        return x.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
}
