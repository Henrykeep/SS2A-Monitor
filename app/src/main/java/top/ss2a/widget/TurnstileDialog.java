package top.ss2a.widget;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class TurnstileDialog {
    public interface Callback {
        void onVerified(String token);
        void onCancelled(String reason);
    }

    public static void show(final Activity activity, final String siteKey, final Callback callback) {
        if (activity == null || activity.isFinishing()) {
            if (callback != null) callback.onCancelled("页面已关闭");
            return;
        }
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#12141C"));
        root.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14));

        TextView title = new TextView(activity);
        title.setText("请完成人机验证");
        title.setTextColor(Color.parseColor("#F8FAFC"));
        title.setTextSize(16);
        title.setPadding(0, 0, 0, dp(activity, 6));
        root.addView(title);

        TextView hint = new TextView(activity);
        hint.setText("站点已开启 Cloudflare Turnstile，勾选后会自动继续登录。");
        hint.setTextColor(Color.parseColor("#94A3B8"));
        hint.setTextSize(12);
        hint.setPadding(0, 0, 0, dp(activity, 10));
        root.addView(hint);

        final ProgressBar progress = new ProgressBar(activity);
        root.addView(progress, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final WebView webView = new WebView(activity);
        LinearLayout.LayoutParams webLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 140));
        webLp.topMargin = dp(activity, 8);
        root.addView(webView, webLp);

        TextView cancel = new TextView(activity);
        cancel.setText("取消");
        cancel.setTextColor(Color.parseColor("#F87171"));
        cancel.setTextSize(14);
        cancel.setPadding(0, dp(activity, 12), 0, 0);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        root.addView(cancel);

        dialog.setContentView(root);
        dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(android.content.DialogInterface d) {
                try {
                    webView.stopLoading();
                    webView.destroy();
                } catch (Exception ignored) {}
            }
        });

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }
        webView.setBackgroundColor(Color.parseColor("#12141C"));
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
            }
        });
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void onToken(final String token) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (dialog.isShowing()) dialog.dismiss();
                        if (callback != null) callback.onVerified(token);
                    }
                });
            }

            @JavascriptInterface
            public void onFail(final String reason) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (dialog.isShowing()) dialog.dismiss();
                        if (callback != null) callback.onCancelled(reason != null ? reason : "验证失败");
                    }
                });
            }
        }, "SS2ABridge");

        String safeKey = siteKey == null ? "" : siteKey.replace("'", "");
        String html = "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<script src='https://challenges.cloudflare.com/turnstile/v0/api.js' async defer></script>"
                + "<style>html,body{margin:0;background:#12141C;color:#fff;}#box{min-height:70px;}</style>"
                + "</head><body><div id='box'></div><script>"
                + "function boot(){if(!window.turnstile){setTimeout(boot,200);return;}"
                + "turnstile.render('#box',{sitekey:'" + safeKey + "',theme:'dark',callback:function(t){SS2ABridge.onToken(t);},'error-callback':function(){SS2ABridge.onFail('Turnstile 加载失败');},'expired-callback':function(){SS2ABridge.onFail('Turnstile 已过期，请重试');}});}"
                + "boot();</script></body></html>";
        webView.loadDataWithBaseURL("https://ss2a.top/", html, "text/html", "UTF-8", null);

        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private static int dp(Activity activity, int value) {
        float density = activity.getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }
}
