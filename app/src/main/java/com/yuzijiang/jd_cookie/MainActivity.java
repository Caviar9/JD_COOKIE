package com.yuzijiang.jd_cookie;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


import androidx.appcompat.app.AppCompatActivity;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.Proxy;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final OkHttpClient client = new OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build();
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Pattern PT_KEY_PATTERN = Pattern.compile("(pt_key=)(.*?)(;)");
    private static final Pattern PT_PIN_PATTERN = Pattern.compile("(pt_pin=)(.*?)(;)");
    private static final String LOGIN_URL = "https://plogin.m.jd.com/login/login";
    private static final String PREFS_NAME = "server_settings";
    private static final String KEY_CLIENT_ID = "client_id";
    private static final String KEY_CLIENT_SECRET = "client_secret";
    private static final String KEY_IP = "ip";
    private static final String DEFAULT_CLIENT_ID = "";
    private static final String DEFAULT_CLIENT_SECRET = "";
    private static final String DEFAULT_IP = "";
    private static final int MENU_SETTINGS = 1;

    private String clientId;
    private String clientSecret;
    private String ip;
    private String token;
    private CookieManager cookieManager;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        loadServerSettings();

        WebView webview = findViewById(R.id.webview);
        WebSettings settings = webview.getSettings();
        settings.setJavaScriptEnabled(true); // 启用javascript
        settings.setDomStorageEnabled(true); // 支持HTML5中的一些控件标签
        settings.setBuiltInZoomControls(false); // 自选，非必要

        //处理http和https混合的问题
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        } else {
            settings.setMixedContentMode(WebSettings.LOAD_NORMAL);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // 允许javascript出错
            try {
                Method method = Class.forName("android.webkit.WebView").
                        getMethod("setWebContentsDebuggingEnabled", Boolean.TYPE);
                method.setAccessible(true);
                method.invoke(null, true);
            } catch (Exception e) {
                // do nothing
            }
        }

        if (hasValidServerSettings()) {
            client.newCall(new Request.Builder()
                            .url(buildAuthTokenUrl())
                            .get()
                            .build())
                    .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "提示：连接服务器失败", Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        String result = response.body().string();
                        JSONObject jsonObject = JSONObject.parseObject(result);
                        if (jsonObject.getInteger("code") == 200)
                            token = "Bearer " + jsonObject.getJSONObject("data").getString("token");
//                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "作者QQ：2741744509", Toast.LENGTH_LONG).show());
                    }
                    });
        }


        webview.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) { // 显示加载进度，自选
                //注意textView的视图层级应该在webView上，不然就被webView遮挡住了
                TextView progressTV = findViewById(R.id.progressTV);
                progressTV.setText(String.format(Locale.CHINA, "%d%%", progress));
                progressTV.setVisibility((progress > 0 && progress < 100) ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                super.onGeolocationPermissionsShowPrompt(origin, callback);
                callback.invoke(origin, true, false); // 页面有请求位置的时候需要
            }
        });


        webview.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("http://") || url.startsWith("https://")) { // 4.0以上必须要加
                    view.loadUrl(url);
                    return true;
                }
                return false;
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                //super.onReceivedSslError(view, handler, error)
                switch (error.getPrimaryError()) {
                    case SslError.SSL_INVALID: // 校验过程遇到了bug
                    case SslError.SSL_UNTRUSTED: // 证书有问题
                        handler.proceed();
                    default:
                        handler.cancel();
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                cookieManager = CookieManager.getInstance();
                String cookie = cookieManager.getCookie(url);

                if (cookie == null) {
                    super.onPageFinished(view, url);
                    return;
                }

                Matcher matcher1 = PT_KEY_PATTERN.matcher(cookie);
                Matcher matcher2 = PT_PIN_PATTERN.matcher(cookie);
                StringBuilder sb = new StringBuilder();
                if (matcher2.find())
                    sb.append("pt_pin=").append(matcher2.group(2)).append(";");
                if (matcher1.find())
                    sb.append("pt_key=").append(matcher1.group(2)).append(";");


                String finalStr = sb.toString();
                if (!sb.toString().isEmpty()) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("复制京东Cookie")
                            .setMessage(sb)
                            .setPositiveButton("复制", (dialog, which) -> {
                                copyStr(finalStr);
                                showShortToast("复制成功！");
                                clearCookiesAndReloadWebView();
                            })
                            .setNegativeButton("提交", (dialog, which) -> {
                                checkAndUpdateOrAddCookie(finalStr, getCookieValue(finalStr, PT_PIN_PATTERN));
                                clearCookiesAndReloadWebView();
                            }).create().show();
                }


                super.onPageFinished(view, url);
            }

            /**
             * 清除webview的cookie
             */
            private void clearCookiesAndReloadWebView() {
                cookieManager.removeAllCookies(null);
                cookieManager.flush();
                webview.loadUrl(LOGIN_URL);
            }

            /**
             * 提示
             */
            private void showShortToast(String message) {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }

            /**
             * 检查cookie是否存在, 存在就更新，不存在就添加
             */
            private void checkAndUpdateOrAddCookie(String cookieValue, String searchValue) {
                if (token == null || token.isEmpty()) {
                    showShortToast("Token 获取失败，请检查设置");
                    return;
                }
                if (searchValue == null || searchValue.isEmpty()) {
                    showShortToast("缺少 pt_pin");
                    return;
                }
                client.newCall(new Request.Builder()
                        .url(buildEnvsUrl(searchValue))
                        .get()
                        .addHeader("Authorization", token)
                        .build()).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        runOnUiThread(() -> showShortToast("查询失败！"));
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        try {
                            JSONArray data = JSONObject.parseObject(response.body().string()).getJSONArray("data");
                            if (!data.isEmpty())
                                updateCookie(data.getJSONObject(0), cookieValue);
                            else
                                addNewCookie(cookieValue, searchValue);
                        } catch (Exception e) {
                            runOnUiThread(() -> showShortToast("解析数据失败！"));
                        }
                    }
                });
            }

            /**
             * 更新cookie
             */
            private void updateCookie(JSONObject existingData, String newValue) {
                Integer id = existingData.getInteger("id");
                Map<String, Object> map = new HashMap<>();
                map.put("id", id);
                map.put("name", "JD_COOKIE");
                map.put("value", newValue);
                map.put("remarks", existingData.getString("remarks"));

                sendPutRequestToUpdateCookie(map);
            }

            private void sendPutRequestToUpdateCookie(Map<String, Object> map) {
                client.newCall(new Request.Builder()
                        .url(buildEnvsUrl())
                        .addHeader("Authorization", token)
                        .put(RequestBody.create(JSON, JSONObject.toJSONString(map)))
                        .build()).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        runOnUiThread(() -> showShortToast("更新失败！"));
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        try {
                            JSONObject jsonObject = JSONObject.parseObject(response.body().string());
                            if (jsonObject.getInteger("code") == 200) {
                                enableCookie(jsonObject.getJSONObject("data").getString("id"));
                            }
                        } catch (Exception e) {
                            runOnUiThread(() -> showShortToast("解析更新响应失败！"));
                        }
                    }
                });
            }

            /**
             * 启用cookie
             */
            private void enableCookie(String id) {
                client.newCall(new Request.Builder()
                        .url(buildEnvEnableUrl())
                        .addHeader("Authorization", token)
                        .put(RequestBody.create(JSON, createJsonArrayBody(id)))
                        .build()).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        runOnUiThread(() -> showShortToast("启用失败！"));
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        try {
                            if (JSONObject.parseObject(response.body().string()).getInteger("code") == 200) {
                                runOnUiThread(() -> showShortToast("更新成功！"));
                            }
                        } catch (Exception e) {
                            runOnUiThread(() -> showShortToast("解析启用响应失败！"));
                        }
                    }
                });
            }

            /**
             * 添加cookie
             */
            private void addNewCookie(String value, String remarks) {
                Map<String, Object> map = new HashMap<>();
                map.put("name", "JD_COOKIE");
                map.put("value", value);
                map.put("remarks", "软件" + remarks);

                sendPostRequestToAddCookie(map);
            }

            private void sendPostRequestToAddCookie(Map<String, Object> map) {
                client.newCall(new Request.Builder()
                        .url(buildEnvsUrl())
                        .addHeader("Authorization", token)
                        .post(RequestBody.create(JSON, createJsonArrayBody(map)))
                        .build()).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        runOnUiThread(() -> showShortToast("提交失败！"));
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        try {
                            if (JSONObject.parseObject(response.body().string()).getInteger("code") == 200) {
                                runOnUiThread(() -> showShortToast("提交成功！"));
                            }
                        } catch (Exception e) {
                            runOnUiThread(() -> showShortToast("解析提交响应失败！"));
                        }
                    }
                });
            }

            /**
             * 复制内容到剪贴板
             */
            private void copyStr(String copyStr) {
                try {
                    //获取剪贴板管理器
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    // 创建普通字符型ClipData
                    ClipData mClipData = ClipData.newPlainText("Label", copyStr);
                    // 将ClipData内容放到系统剪贴板里。
                    cm.setPrimaryClip(mClipData);
                } catch (Exception ignored) {
                }
            }

            private String getCookieValue(String cookie, Pattern pattern) {
                Matcher matcher = pattern.matcher(cookie);
                return matcher.find() ? matcher.group(2) : null;
            }
        });
        webview.loadUrl(LOGIN_URL);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_SETTINGS, 0, "设置")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_SETTINGS) {
            showSettingsDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadServerSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        clientId = prefs.getString(KEY_CLIENT_ID, DEFAULT_CLIENT_ID);
        clientSecret = prefs.getString(KEY_CLIENT_SECRET, DEFAULT_CLIENT_SECRET);
        ip = normalizeIp(prefs.getString(KEY_IP, DEFAULT_IP));
    }

    private void saveServerSettings(String newClientId, String newClientSecret, String newIp) {
        clientId = newClientId;
        clientSecret = newClientSecret;
        ip = normalizeIp(newIp);
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_CLIENT_ID, clientId)
                .putString(KEY_CLIENT_SECRET, clientSecret)
                .putString(KEY_IP, ip)
                .apply();
    }

    private String normalizeIp(String value) {
        String result = value.trim().replaceFirst("^https?://", "");
        if (result.endsWith("/open")) {
            result = result.substring(0, result.length() - "/open".length());
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private boolean hasValidServerSettings() {
        return !clientId.isEmpty() && !clientSecret.isEmpty() && !ip.isEmpty();
    }

    private HttpUrl.Builder apiUrlBuilder() {
        return new HttpUrl.Builder()
                .scheme("http")
                .host(getApiHost())
                .port(getApiPort())
                .addPathSegment("open");
    }

    private String getApiHost() {
        int portSeparatorIndex = ip.lastIndexOf(':');
        return portSeparatorIndex > -1 ? ip.substring(0, portSeparatorIndex) : ip;
    }

    private int getApiPort() {
        int portSeparatorIndex = ip.lastIndexOf(':');
        if (portSeparatorIndex == -1) {
            return 80;
        }
        try {
            return Integer.parseInt(ip.substring(portSeparatorIndex + 1));
        } catch (NumberFormatException e) {
            return 80;
        }
    }

    private HttpUrl buildAuthTokenUrl() {
        return apiUrlBuilder()
                .addPathSegment("auth")
                .addPathSegment("token")
                .addQueryParameter(KEY_CLIENT_ID, clientId)
                .addQueryParameter(KEY_CLIENT_SECRET, clientSecret)
                .build();
    }

    private HttpUrl buildEnvsUrl() {
        return apiUrlBuilder()
                .addPathSegment("envs")
                .build();
    }

    private HttpUrl buildEnvsUrl(String searchValue) {
        return apiUrlBuilder()
                .addPathSegment("envs")
                .addQueryParameter("searchValue", searchValue)
                .build();
    }

    private HttpUrl buildEnvEnableUrl() {
        return apiUrlBuilder()
                .addPathSegment("envs")
                .addPathSegment("enable")
                .build();
    }

    private String createJsonArrayBody(Object value) {
        JSONArray array = new JSONArray();
        array.add(value);
        return array.toJSONString();
    }

    private void showSettingsDialog() {
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        int itemMargin = (int) (12 * getResources().getDisplayMetrics().density);
        boolean hasSavedSettings = hasSavedServerSettings();
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(padding, padding / 2, padding, 0);

        EditText clientIdInput = createSettingsInput(hasSavedSettings ? clientId : "", false);
        EditText clientSecretInput = createSettingsInput(hasSavedSettings ? clientSecret : "", true);
        EditText ipInput = createSettingsInput(hasSavedSettings ? ip : "", false);

        layout.addView(createSettingsLabel("Client ID"));
        layout.addView(clientIdInput, createInputParams(itemMargin));
        layout.addView(createSettingsLabel("Client Secret"));
        layout.addView(clientSecretInput, createInputParams(itemMargin));
        layout.addView(createSettingsLabel("IP:端口"));
        layout.addView(ipInput, createInputParams(0));

        new AlertDialog.Builder(this)
                .setTitle("服务器设置")
                .setView(layout)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newClientId = clientIdInput.getText().toString().trim();
                    String newClientSecret = clientSecretInput.getText().toString().trim();
                    String newIp = ipInput.getText().toString().trim();
                    if (newClientId.isEmpty() || newClientSecret.isEmpty() || newIp.isEmpty()) {
                        Toast.makeText(MainActivity.this, "参数不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveServerSettings(newClientId, newClientSecret, newIp);
                    Toast.makeText(MainActivity.this, "设置已保存", Toast.LENGTH_SHORT).show();
                    recreate();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private EditText createSettingsInput(String text, boolean password) {
        EditText input = new EditText(this);
        input.setText(text);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setInputType(password
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        return input;
    }

    private boolean hasSavedServerSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.contains(KEY_CLIENT_ID)
                || prefs.contains(KEY_CLIENT_SECRET)
                || prefs.contains(KEY_IP);
    }

    private TextView createSettingsLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(14);
        label.setTextColor(0xFF333333);
        return label;
    }

    private LinearLayout.LayoutParams createInputParams(int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, bottomMargin);
        return params;
    }
}
