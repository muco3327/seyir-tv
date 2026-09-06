package tv.seyir.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.Executors;

public final class AppUpdater {
    public static final String GITHUB_OWNER = "muco3327";
    public static final String GITHUB_REPO = "seyir-tv";
    public static final String VERSION_URL = "https://raw.githubusercontent.com/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/main/version.json";
    public static final String RELEASES_URL = "https://github.com/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases";

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static boolean isDownloading = false;

    private AppUpdater() {}

    public static void check(Activity activity, boolean manual) {
        if (activity == null || activity.isFinishing()) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                URL url = new URL(VERSION_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "SeyirTV-Updater");
                conn.setUseCaches(false);

                if (conn.getResponseCode() != 200) {
                    throw new Exception("HTTP " + conn.getResponseCode());
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();
                conn.disconnect();

                JSONObject json = new JSONObject(sb.toString());
                int remoteCode = json.optInt("versionCode", 0);
                String remoteName = json.optString("versionName", "Bilinmeyen");
                String changelog = json.optString("changelog", "Performans ve hata duzeltmeleri.");
                String apkUrl = json.optString("apkUrl", RELEASES_URL + "/latest/download/Seyir-TV.apk");

                PackageManager pm = activity.getPackageManager();
                PackageInfo pInfo = pm.getPackageInfo(activity.getPackageName(), 0);
                int currentCode = pInfo.versionCode;
                String currentName = pInfo.versionName;

                mainHandler.post(() -> {
                    if (activity.isFinishing()) return;
                    if (remoteCode > currentCode) {
                        showUpdateDialog(activity, remoteName, changelog, apkUrl);
                    } else if (manual) {
                        Toast.makeText(activity, "Seyir TV guncel (v" + currentName + ")", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (activity.isFinishing()) return;
                    if (manual) {
                        Toast.makeText(activity, "Guncelleme denetlenemedi: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private static void showUpdateDialog(Activity activity, String versionName, String changelog, String apkUrl) {
        new AlertDialog.Builder(activity)
                .setTitle("🚀 Yeni Surum Bulundu (v" + versionName + ")")
                .setMessage(changelog + "\n\nOtomatik indirilip guncellensin mi?")
                .setPositiveButton("Simdi Guncelle", (dialog, which) -> startDownload(activity, apkUrl, versionName))
                .setNegativeButton("Daha Sonra", null)
                .show();
    }

    private static void startDownload(Activity activity, String apkUrl, String versionName) {
        if (isDownloading) {
            Toast.makeText(activity, "Indirme islemi zaten devam ediyor…", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(activity)
                        .setTitle("Yukleme Izni Gerekli")
                        .setMessage("Seyir TV'yi guncelleyebilmek icin 'Bilinmeyen uygulamalari yukle' iznini acmaniz gerekiyor.")
                        .setPositiveButton("Ayarlari Ac", (d, w) -> {
                            try {
                                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                                activity.startActivity(intent);
                            } catch (Exception e) {
                                activity.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
                            }
                        })
                        .setNegativeButton("Iptal", null)
                        .show();
                return;
            }
        }

        isDownloading = true;

        LinearLayout root = Ui.column(activity);
        root.setBackgroundColor(Ui.BG);
        int pad = Ui.dp(activity, 20);
        root.setPadding(pad, pad, pad, pad);

        TextView title = Ui.text(activity, "Seyir TV v" + versionName + " Indiriliyor…", 16, Ui.WHITE);
        Ui.bold(title);
        root.addView(title);
        Ui.gap(root, 14);

        ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(false);
        bar.setMax(100);
        bar.setProgress(0);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 16)));
        Ui.gap(root, 10);

        TextView status = Ui.text(activity, "Baglaniyor…", 13, Ui.MUTED);
        root.addView(status);

        AlertDialog progressDialog = new AlertDialog.Builder(activity)
                .setView(root)
                .setCancelable(false)
                .setNegativeButton("Iptal", (d, w) -> isDownloading = false)
                .create();
        progressDialog.show();

        Executors.newSingleThreadExecutor().execute(() -> {
            File outputFile = new File(activity.getCacheDir(), "Seyir-TV-update.apk");
            if (outputFile.exists()) outputFile.delete();

            try {
                String currentUrl = apkUrl;
                HttpURLConnection conn = null;
                int redirects = 0;

                while (redirects < 6) {
                    URL u = new URL(currentUrl);
                    conn = (HttpURLConnection) u.openConnection();
                    conn.setInstanceFollowRedirects(true);
                    conn.setRequestProperty("User-Agent", "SeyirTV-Updater");
                    conn.connect();
                    int code = conn.getResponseCode();
                    if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == 307 || code == 308) {
                        String loc = conn.getHeaderField("Location");
                        if (loc != null) {
                            currentUrl = loc;
                            redirects++;
                            continue;
                        }
                    }
                    break;
                }

                if (conn == null || conn.getResponseCode() != 200) {
                    throw new Exception("Sunucu yanit vermedi: HTTP " + (conn != null ? conn.getResponseCode() : -1));
                }

                int contentLength = conn.getContentLength();
                InputStream in = conn.getInputStream();
                FileOutputStream out = new FileOutputStream(outputFile);

                byte[] buffer = new byte[8192];
                long totalRead = 0;
                int read;

                while ((read = in.read(buffer)) != -1) {
                    if (!isDownloading) {
                        out.close();
                        in.close();
                        if (outputFile.exists()) outputFile.delete();
                        return;
                    }
                    out.write(buffer, 0, read);
                    totalRead += read;
                    if (contentLength > 0) {
                        int percent = (int) ((totalRead * 100) / contentLength);
                        float readMb = totalRead / (1024f * 1024f);
                        float totalMb = contentLength / (1024f * 1024f);
                        mainHandler.post(() -> {
                            bar.setProgress(percent);
                            status.setText(String.format(Locale.getDefault(), "%%%d (%.1f MB / %.1f MB)", percent, readMb, totalMb));
                        });
                    }
                }

                out.flush();
                out.close();
                in.close();
                conn.disconnect();

                isDownloading = false;
                mainHandler.post(() -> {
                    if (progressDialog.isShowing()) progressDialog.dismiss();
                    installApk(activity, outputFile);
                });

            } catch (Exception e) {
                isDownloading = false;
                if (outputFile.exists()) outputFile.delete();
                mainHandler.post(() -> {
                    if (progressDialog.isShowing()) progressDialog.dismiss();
                    Toast.makeText(activity, "Indirme hatasi: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private static void installApk(Context context, File apkFile) {
        if (!apkFile.exists()) return;
        try {
            Uri apkUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".provider",
                    apkFile
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Yukleyici baslatilamadi: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}