package io.kuenzler.whatsappwebtogo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.ConsoleMessage;
import android.webkit.DownloadListener;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

public class WebviewActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final String CHROME_TEMPLATE = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/%s Safari/537.36";
    private static final String CHROME_VERSION_DEFAULT = "131.0.0.0";
    public static final String FIREFOX_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0";
    public static final String EDGE_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0";
    private static String USER_AGENT = String.format(CHROME_TEMPLATE, CHROME_VERSION_DEFAULT);

    private static final String STORAGE_PERMISSION = Manifest.permission.WRITE_EXTERNAL_STORAGE; // "android.permission.WRITE_EXTERNAL_STORAGE"
    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA; // "android.permission.CAMERA"
    private static final String AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO; // "android.permission.RECORD_AUDIO"
    private static final String[] VIDEO_PERMISSION = {CAMERA_PERMISSION, AUDIO_PERMISSION};

    private static final String WHATSAPP_HOMEPAGE_URL = "https://www.whatsapp.com/";

    private static final String WHATSAPP_WEB_BASE_URL = "web.whatsapp.com";
    private static final String WORLD_ICON = "\uD83C\uDF10";
    private static final String WHATSAPP_WEB_URL = "https://" + WHATSAPP_WEB_BASE_URL
            + "/" + WORLD_ICON + "/"
            + Locale.getDefault().getLanguage();

    private static final int FILECHOOSER_RESULTCODE        = 200;
    private static final int CAMERA_PERMISSION_RESULTCODE  = 201;
    private static final int AUDIO_PERMISSION_RESULTCODE   = 202;
    private static final int VIDEO_PERMISSION_RESULTCODE   = 203;
    private static final int STORAGE_PERMISSION_RESULTCODE = 204;

    public static final String DEBUG_TAG = "WAWEBTOGO";

    private final Activity activity = this;

    private SharedPreferences mSharedPrefs;

    private WebView mWebView;
    private ViewGroup mMainView;

    private long mLastBackClick = 0;

    boolean mKeyboardEnabled = false;
    boolean mDarkMode;

    private ValueCallback<Uri[]> mUploadMessage;
    private PermissionRequest mCurrentPermissionRequest;
    private String mCurrentDownloadRequest = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name);
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            getWindow().setStatusBarColor(Color.WHITE);
        }

        mSharedPrefs = this.getSharedPreferences(this.getPackageName(), Context.MODE_PRIVATE);
        mDarkMode = mSharedPrefs.getBoolean("darkMode", false);
        initUserAgent();

        mMainView = findViewById(R.id.layout);

        // webview stuff

        mWebView = findViewById(R.id.webview);
        try {
            // In virtualized / cloud emulator containers without DRI rendernode access,
            // hardware acceleration can cause Mesa EGL driver warnings/crashes.
            // Using View.LAYER_TYPE_HARDWARE or fallback gracefully:
            mWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        } catch (Exception e) {
            Log.w(DEBUG_TAG, "Failed setting layer type, falling back to software", e);
            mWebView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mWebView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        mWebView.getSettings().setUserAgentString(USER_AGENT);

        mWebView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                mCurrentDownloadRequest = url;
                if(checkPermission(STORAGE_PERMISSION)) {
                    mWebView.loadUrl(BlobDownloader.getBase64StringFromBlobUrl(url));
                    triggerDownload();
                } else {
                    requestPermission(STORAGE_PERMISSION);
                }
            }
        });
        mWebView.addJavascriptInterface(new BlobDownloader(getApplicationContext()), BlobDownloader.JsInstance);

        mWebView.getSettings().setJavaScriptEnabled(true); //for wa web
        mWebView.getSettings().setAllowContentAccess(true); // for camera
        mWebView.getSettings().setAllowFileAccess(true);
        mWebView.getSettings().setAllowFileAccessFromFileURLs(true);
        mWebView.getSettings().setAllowUniversalAccessFromFileURLs(true);
        mWebView.getSettings().setMediaPlaybackRequiresUserGesture(false); //for audio messages

        mWebView.getSettings().setDomStorageEnabled(true); //for html5 app
        mWebView.getSettings().setDatabaseEnabled(true);
        mWebView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);

        mWebView.getSettings().setLoadWithOverviewMode(true);
        mWebView.getSettings().setUseWideViewPort(true);

        mWebView.getSettings().setSupportZoom(true);
        mWebView.getSettings().setBuiltInZoomControls(true);
        mWebView.getSettings().setDisplayZoomControls(false);

        mWebView.getSettings().setSaveFormData(true);
        mWebView.getSettings().setLoadsImagesAutomatically(true);
        mWebView.getSettings().setBlockNetworkImage(false);
        mWebView.getSettings().setBlockNetworkLoads(false);
        mWebView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        mWebView.getSettings().setNeedInitialFocus(false);
        mWebView.getSettings().setGeolocationEnabled(true);
        mWebView.getSettings().setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);

        mWebView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        mWebView.setScrollbarFadingEnabled(true);

        mWebView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onCreateWindow(WebView view, boolean dialog, boolean userGesture, Message resultMsg) {
                Toast.makeText(getApplicationContext(), "OnCreateWindow", Toast.LENGTH_LONG).show();
                return true;
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                if (request.getResources()[0].equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                    if (ContextCompat.checkSelfPermission(activity, CAMERA_PERMISSION) == PackageManager.PERMISSION_DENIED
                            && ContextCompat.checkSelfPermission(activity, AUDIO_PERMISSION) == PackageManager.PERMISSION_DENIED) {
                        ActivityCompat.requestPermissions(activity, VIDEO_PERMISSION, VIDEO_PERMISSION_RESULTCODE);
                        mCurrentPermissionRequest = request;
                    } else if (ContextCompat.checkSelfPermission(activity, CAMERA_PERMISSION) == PackageManager.PERMISSION_DENIED) {
                        ActivityCompat.requestPermissions(activity, new String[]{CAMERA_PERMISSION}, CAMERA_PERMISSION_RESULTCODE);
                        mCurrentPermissionRequest = request;
                    } else if (ContextCompat.checkSelfPermission(activity, AUDIO_PERMISSION) == PackageManager.PERMISSION_DENIED) {
                        ActivityCompat.requestPermissions(activity, new String[]{AUDIO_PERMISSION}, AUDIO_PERMISSION_RESULTCODE);
                        mCurrentPermissionRequest = request;
                    } else {
                        request.grant(request.getResources());
                    }
                } else if (request.getResources()[0].equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                    if (ContextCompat.checkSelfPermission(activity, AUDIO_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(request.getResources());
                    } else {
                        ActivityCompat.requestPermissions(activity, new String[]{AUDIO_PERMISSION}, AUDIO_PERMISSION_RESULTCODE);
                        mCurrentPermissionRequest = request;
                    }
                } else {
                    try {
                        request.grant(request.getResources());
                    } catch (RuntimeException e) {
                        Log.d(DEBUG_TAG, "Granting permissions failed", e);
                    }
                }
            }

            public boolean onConsoleMessage(ConsoleMessage cm) {
                Log.d(DEBUG_TAG, "WebView console message: " + cm.message());
                return super.onConsoleMessage(cm);
            }

            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                mUploadMessage = filePathCallback;
                Intent chooserIntent = fileChooserParams.createIntent();
                WebviewActivity.this.startActivityForResult(chooserIntent, FILECHOOSER_RESULTCODE);
                return true;
            }
        });

        mWebView.setWebViewClient(new WebViewClient() {

            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                injectCompatibilityFixes(view);
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                super.onPageCommitVisible(view, url);
                injectCompatibilityFixes(view);
                setContentSize(view);
                if (mDarkMode) {
                    addDarkMode(view);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectCompatibilityFixes(view);
                view.scrollTo(0, 0);

                setContentSize(view);
                if (mDarkMode) {
                    addDarkMode(view);
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrlLoading(view, Uri.parse(url));
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrlLoading(view, request.getUrl());
            }

            private boolean handleUrlLoading(WebView view, Uri url) {
                if (url == null) return false;
                String urlStr = url.toString();
                Log.d(DEBUG_TAG, "handleUrlLoading: " + urlStr);

                if (urlStr.equals(WHATSAPP_HOMEPAGE_URL) || urlStr.contains("whatsapp.com/android") || urlStr.contains("whatsapp.com/download")){
                    showToast(R.string.reload_required);
                    loadWhatsapp();
                    return true;
                } else if (url.getHost() != null && (url.getHost().equals(WHATSAPP_WEB_BASE_URL) || url.getHost().endsWith("whatsapp.com") || url.getHost().endsWith("whatsapp.net"))) {
                    return false;
                } else {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, url);
                        startActivity(intent);
                    } catch (Exception e) {
                        Log.e(DEBUG_TAG, "Error opening external url", e);
                    }
                    return true;
                }
            }

            @RequiresApi(api = Build.VERSION_CODES.M)
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                String msg = String.format("Error: %s - %s", error.getErrorCode(), error.getDescription());
                Log.d(DEBUG_TAG, msg);
            }

            @SuppressWarnings("deprecation")
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                String msg = String.format("Error: %s - %s", errorCode, description);
                Log.d(DEBUG_TAG, msg);
            }

            public void onUnhandledKeyEvent(WebView view, KeyEvent event) {
                Log.d(DEBUG_TAG, "Unhandled key event: " + event.toString());
            }
        });

        if (savedInstanceState == null) {
            loadWhatsapp();
        } else {
            Log.d(DEBUG_TAG, "savedInstanceState is present");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mWebView.onResume();

        mKeyboardEnabled = mSharedPrefs.getBoolean("keyboardEnabled", true);
        setAppbarEnabled(mSharedPrefs.getBoolean("appbarEnabled", false));

        setKeyboardEnabled(mKeyboardEnabled);

        showIntroInfo();
        showVersionInfo();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mWebView.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action_bar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.toggle_keyboard) {
            toggleKeyboard();
        } else if (id == R.id.scroll_left) {
            showToast(R.string.appbar_button_scroll_left);
            runOnUiThread(() -> mWebView.scrollTo(0, 0));
        } else if (id == R.id.scroll_right) {
            showToast(R.string.appbar_title_scroll_right);
            runOnUiThread(() -> mWebView.scrollTo(2000, 0));
        }
        return true;
    }

    private boolean checkPermission(String permission) {
        return PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(activity, permission);
    }

    private void requestPermission(String permission) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
            ActivityCompat.requestPermissions(activity, new String[]{permission}, STORAGE_PERMISSION_RESULTCODE);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case VIDEO_PERMISSION_RESULTCODE:
                if (permissions.length == 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                        grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    try {
                        mCurrentPermissionRequest.grant(mCurrentPermissionRequest.getResources());
                    } catch (RuntimeException e) {
                        Log.e(DEBUG_TAG, "Granting permissions failed", e);
                    }
                } else {
                    showSnackbar(R.string.permission_denied_video);
                    mCurrentPermissionRequest.deny();
                }
                break;
            case CAMERA_PERMISSION_RESULTCODE:
            case AUDIO_PERMISSION_RESULTCODE:
                //same same
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    try {
                        mCurrentPermissionRequest.grant(mCurrentPermissionRequest.getResources());
                    } catch (RuntimeException e) {
                        Log.e(DEBUG_TAG, "Granting permissions failed", e);
                    }
                } else {
                    showSnackbar(R.string.permission_denied_cam +
                            (requestCode == CAMERA_PERMISSION_RESULTCODE ? R.string.camera : R.string.microphone));
                    mCurrentPermissionRequest.deny();
                }
                break;
            case STORAGE_PERMISSION_RESULTCODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    triggerDownload();
                } else {
                    showToast(R.string.permission_denied_download);
                    mCurrentDownloadRequest = null;
                }
                break;
            default:
                Log.d(DEBUG_TAG, "Got permission result with unknown request code " +
                        requestCode + " - " + Arrays.asList(permissions).toString());
        }
        mCurrentPermissionRequest = null;
    }

    private void triggerDownload(){
        if(null != mCurrentDownloadRequest) {
            mWebView.loadUrl(BlobDownloader.getBase64StringFromBlobUrl(mCurrentDownloadRequest));
        }
        mCurrentDownloadRequest = null;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mWebView.saveState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mWebView.restoreState(savedInstanceState);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case FILECHOOSER_RESULTCODE:
                if (resultCode == RESULT_CANCELED || data.getData() == null) {
                    mUploadMessage.onReceiveValue(null);
                } else {
                    Uri result = data.getData();
                    Uri[] results = new Uri[1];
                    results[0] = result;
                    mUploadMessage.onReceiveValue(results);
                }
                break;
            default:
                Log.d(DEBUG_TAG, "Got activity result with unknown request code " +
                        requestCode + " - " + data.toString());
        }
    }

    private void showPopupDialog(String message) {
        final SpannableString msg = new SpannableString(message);
        Linkify.addLinks(msg, Linkify.WEB_URLS|Linkify.EMAIL_ADDRESSES);

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(msg)
                .setCancelable(false)
                .setPositiveButton(R.string.ok_button, null);
        AlertDialog alert = builder.create();
        alert.show();
        ((TextView) alert.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void showPopupDialog(int resId) {
       showPopupDialog(getString(resId));
    }

    private void showToast(int resId) {
        this.runOnUiThread(() -> Toast.makeText(this, getString(resId), Toast.LENGTH_LONG).show());
    }

    private void showSnackbar(int resId) {
        this.runOnUiThread(() -> {
            final Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), getString(resId), 900);
            snackbar.setAction(getString(R.string.dismiss), (View view) -> snackbar.dismiss());
            snackbar.setActionTextColor(Color.parseColor("#075E54"));
            snackbar.show();
        });
    }

    private void toggleKeyboard() {
        setKeyboardEnabled(!mKeyboardEnabled);
    }

    private void setKeyboardEnabled(final boolean enable) {
        mKeyboardEnabled = enable;
        InputMethodManager inputMethodManager = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (enable && mMainView.getDescendantFocusability() == ViewGroup.FOCUS_BLOCK_DESCENDANTS) {
            mMainView.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            showSnackbar(R.string.unblock_keyboard);
            //inputMethodManager.showSoftInputFromInputMethod(activity.getCurrentFocus().getWindowToken(), 0);
        } else if (!enable) {
            mMainView.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            mWebView.getRootView().requestFocus();
            showSnackbar(R.string.block_keyboard);
            inputMethodManager.hideSoftInputFromWindow(activity.getCurrentFocus().getWindowToken(), 0);
        }
        mSharedPrefs.edit().putBoolean("keyboardEnabled", enable).apply();
    }

    private void setAppbarEnabled(boolean enable) {
        ActionBar actionBar= getSupportActionBar();
        if (actionBar != null) {
            if (enable) {
                actionBar.show();
            } else {
                actionBar.hide();
            }
            mSharedPrefs.edit().putBoolean("appbarEnabled", enable).apply();
        }
    }

    private void toggleDarkMode() {
        boolean currentState = mSharedPrefs.getBoolean("darkMode", false);
        mSharedPrefs.edit().putBoolean("darkMode", !currentState).apply();

        Log.d(DEBUG_TAG, "Dark Mode Enabled: " + !currentState);
        recreate();
    }

    private void showVersionInfo() {
        int lastShownVersionCode = 0;
        int currentVersionCode = 0;
        try {
            PackageInfo pInfo = this.getPackageManager().getPackageInfo(getPackageName(), 0);
            currentVersionCode = pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(DEBUG_TAG, "Error checking versioncode", e);
            return;
        }
        lastShownVersionCode = mSharedPrefs.getInt("lastShownVersionCode", 0);
        if (lastShownVersionCode == 0) {
            mSharedPrefs.edit().putInt("lastShownVersionCode", currentVersionCode).apply();
            return;
        }
        if (lastShownVersionCode < currentVersionCode) {
            showPopupDialog(R.string.versionInfoText);
        } else {
            return;
        }
        mSharedPrefs.edit().putInt("lastShownVersionCode", currentVersionCode).apply();
    }

    private void showIntroInfo() {
        if (!mSharedPrefs.getBoolean("introShown", false)) {
            showPopupDialog(R.string.introInfoText);
        } else {
            return;
        }
        mSharedPrefs.edit().putBoolean("introShown", true).apply();
    }

    private void showAbout() {
        showPopupDialog(R.string.aboutText);
    }

    @Override
    public void onBackPressed() {
        //close drawer if open and impl. press back again to leave
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer != null && drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
            return;
        }

        mWebView.evaluateJavascript("(function(){ return (window.waMobileGoBack && window.waMobileGoBack()) ? 'true' : 'false'; })();", value -> {
            boolean handled = value != null && value.contains("true");
            if (!handled) {
                if (System.currentTimeMillis() - mLastBackClick < 1200) {
                    finishAffinity();
                } else {
                    mWebView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE));
                    showToast(R.string.navigation_drawer_close_confirm);
                    mLastBackClick = System.currentTimeMillis();
                }
            }
        });
    }

    private void loadWhatsapp() {
        initUserAgent();
        mWebView.loadUrl(WHATSAPP_WEB_URL);
    }

    public void addDarkMode(final WebView mWebView) {
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().setStatusBarColor(Color.BLACK);

        Objects.requireNonNull(getSupportActionBar()).setBackgroundDrawable(new ColorDrawable(Color.BLACK));

        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(mWebView.getSettings(), WebSettingsCompat.FORCE_DARK_ON);
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                WebSettingsCompat.setForceDarkStrategy(mWebView.getSettings(), WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING);
            }
        } else {
            mWebView.loadUrl("javascript:(" +
                    "function(){ " +
                    "try {  document.body.classList.add('dark') } catch(err) { }" +
                    "})()");
        }
    }

    public void setContentSize(final WebView mWebView){
        String js = "javascript:(function() {" +
                "  try {" +
                "    var isLanding = !!(document.querySelector('.landing-wrapper, [data-testid=\"landing-wrapper\"], div[data-testid=\"qrcode\"], .landing-window, .landing-box') ||" +
                "                       document.querySelector('canvas[aria-label*=\"Scan\"], canvas[aria-label*=\"QR\"], [data-ref]'));" +
                "    if (isLanding && !document.getElementById('side')) {" +
                "      return;" +
                "    }" +
                "    var meta = document.querySelector('meta[name=\"viewport\"]');" +
                "    if (!meta) {" +
                "      meta = document.createElement('meta');" +
                "      meta.name = 'viewport';" +
                "      document.head.appendChild(meta);" +
                "    }" +
                "    meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover';" +
                "    var style = document.getElementById('wa-mobile-css');" +
                "    if (!style) {" +
                "      style = document.createElement('style');" +
                "      style.id = 'wa-mobile-css';" +
                "      style.type = 'text/css';" +
                "      document.head.appendChild(style);" +
                "    }" +
                "    var css = 'html, body { width: 100% !important; height: 100% !important; min-width: 0 !important; max-width: 100vw !important; margin: 0 !important; padding: 0 !important; overflow-x: hidden !important; -webkit-tap-highlight-color: transparent !important; font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, \"Noto Sans Arabic\", Helvetica, Arial, sans-serif !important; } ' +" +
                "      '#app, #app > div, .app-wrapper-web { width: 100vw !important; height: 100% !important; min-width: 0 !important; max-width: 100vw !important; top: 0 !important; left: 0 !important; right: 0 !important; bottom: 0 !important; margin: 0 !important; border-radius: 0 !important; } ' +" +
                "      '.landing-wrapper, [data-testid=\"landing-wrapper\"], .landing-window, .landing-box { width: 100% !important; max-width: 100vw !important; min-width: 0 !important; box-sizing: border-box !important; padding: 12px !important; margin: 0 auto !important; display: flex !important; flex-direction: column !important; align-items: center !important; } ' +" +
                "      'div[data-testid=\"qrcode\"], [data-testid=\"qrcode\"] { margin: 12px auto !important; display: flex !important; justify-content: center !important; max-width: 85vw !important; } ' +" +
                "      'div[data-testid=\"qrcode\"] canvas, div[data-testid=\"qrcode\"] img { max-width: 100% !important; height: auto !important; } ' +" +
                "      '#main [data-testid=\"intro-title\"], #main [data-asset-intro-image-light], #main [data-asset-intro-image-dark], #main [data-testid=\"intro-text\"], #main [data-testid=\"intro-md-beta-logo-dark\"], #main [data-testid=\"intro-md-beta-logo-light\"] { display: none !important; } ' +" +
                "      '[data-testid=\"status-v3-unread\"], [data-testid=\"status-v3\"], [data-testid=\"status-outline\"], [data-icon=\"status-v3\"], [data-icon=\"status-outline\"], span[data-icon=\"status-v3\"], span[data-icon=\"status-outline\"], button[aria-label*=\"Status\"], button[aria-label*=\"الحالة\"], button[aria-label*=\"حالة\"], button[aria-label*=\"ستوري\"], button[title*=\"Status\"], button[title*=\"الحالة\"], [data-testid=\"newsletter-icon-unread\"], [data-testid=\"newsletter-icon\"], [data-icon=\"newsletter-outline\"], span[data-icon=\"newsletter-outline\"], button[aria-label*=\"Channels\"], button[aria-label*=\"القنوات\"], button[aria-label*=\"قنوات\"], [data-testid=\"community-icon\"], button[aria-label*=\"Communities\"], button[aria-label*=\"المجتمعات\"], button[aria-label*=\"مجتمعات\"], [data-testid=\"status-feed\"], div[aria-label*=\"Status\"] { display: none !important; width: 0 !important; min-width: 0 !important; max-width: 0 !important; height: 0 !important; min-height: 0 !important; visibility: hidden !important; pointer-events: none !important; opacity: 0 !important; margin: 0 !important; padding: 0 !important; } ' +" +
                "      '#side { background: #ffffff !important; } ' +" +
                "      'body.dark #side { background: #111b21 !important; } ' +" +
                "      '#side header { background: #ffffff !important; height: 60px !important; min-height: 60px !important; box-shadow: 0 1px 3px rgba(11,20,26,0.06) !important; border-bottom: 1px solid rgba(11,20,26,0.06) !important; padding: 0 12px !important; display: flex !important; align-items: center !important; justify-content: space-between !important; } ' +" +
                "      'body.dark #side header { background: #111b21 !important; border-bottom: 1px solid rgba(255,255,255,0.06) !important; } ' +" +
                "      '#side header img, #side header [data-testid=\"avatar-element\"], #side header [data-testid=\"cell-frame-icon\"] > div { border-radius: 50% !important; width: 40px !important; height: 40px !important; } ' +" +
                "      '#side [data-testid=\"chat-list-search\"], #side ._ak96, #side ._ai05, #side ._ai08, #side [data-testid=\"search-input-container\"] { background: #f0f2f5 !important; border-radius: 24px !important; border: none !important; padding: 2px 14px !important; } ' +" +
                "      'body.dark #side [data-testid=\"chat-list-search\"], body.dark #side ._ak96, body.dark #side ._ai05, body.dark #side ._ai08 { background: #202c33 !important; } ' +" +
                "      '#side [data-testid=\"cell-frame-container\"], #side div[role=\"listitem\"], #side div._ak72, #side div._ak73 { border-bottom: 1px solid rgba(11,20,26,0.05) !important; padding-top: 6px !important; padding-bottom: 6px !important; } ' +" +
                "      'body.dark #side [data-testid=\"cell-frame-container\"], body.dark #side div[role=\"listitem\"] { border-bottom: 1px solid rgba(255,255,255,0.05) !important; } ' +" +
                "      '#side [data-testid=\"cell-frame-icon\"] img, #side [data-testid=\"avatar-element\"], #side [data-testid=\"cell-frame-icon\"] > div { border-radius: 50% !important; box-shadow: 0 1px 2px rgba(11,20,26,0.08) !important; } ' +" +
                "      '#side [data-testid=\"cell-frame-title\"], #side [data-testid=\"cell-frame-title\"] span { font-size: 16px !important; font-weight: 600 !important; color: #111b21 !important; } ' +" +
                "      'body.dark #side [data-testid=\"cell-frame-title\"], body.dark #side [data-testid=\"cell-frame-title\"] span { color: #e9edef !important; } ' +" +
                "      'span[data-testid=\"icon-unread-count\"], span._ak8q, span._al_h, [data-testid=\"unread-count\"], span[aria-label*=\"unread\"] { background: #007aff !important; color: #ffffff !important; border-radius: 12px !important; min-width: 20px !important; height: 20px !important; line-height: 20px !important; font-size: 11.5px !important; font-weight: 700 !important; display: inline-flex !important; align-items: center !important; justify-content: center !important; padding: 0 5px !important; box-shadow: 0 1px 3px rgba(0,122,255,0.3) !important; } ' +" +
                "      'body.wa-view-main .wa-hide-on-chat { display: none !important; width: 0 !important; min-width: 0 !important; max-width: 0 !important; flex: 0 0 0 !important; } ' +" +
                "      'body.wa-view-main .wa-main-col { display: flex !important; width: 100vw !important; min-width: 100vw !important; max-width: 100vw !important; flex: 1 1 100vw !important; height: 100% !important; } ' +" +
                "      'body.wa-view-main #main { display: flex !important; flex-direction: column !important; width: 100vw !important; min-width: 100vw !important; max-width: 100vw !important; flex: 1 1 100vw !important; height: 100% !important; } ' +" +
                "      'body.wa-view-side .wa-main-col { display: none !important; width: 0 !important; min-width: 0 !important; max-width: 0 !important; flex: 0 0 0 !important; } ' +" +
                "      'body.wa-view-side #main { display: none !important; } ' +" +
                "      'body.wa-view-side .wa-side-col { display: flex !important; width: 100% !important; min-width: 0 !important; max-width: 100vw !important; flex: 1 1 auto !important; height: 100% !important; } ' +" +
                "      'body.wa-view-side #side { display: flex !important; width: 100% !important; min-width: 0 !important; max-width: 100% !important; height: 100% !important; flex: 1 1 auto !important; } ' +" +
                "      '#main { background: #efeae2 !important; } ' +" +
                "      'body.dark #main { background: #0c1317 !important; } ' +" +
                "      '#main header { width: 100% !important; max-width: 100vw !important; min-width: 0 !important; box-sizing: border-box !important; padding: 4px 8px !important; display: flex !important; align-items: center !important; overflow: hidden !important; height: 60px !important; min-height: 60px !important; z-index: 10 !important; background: #ffffff !important; border-bottom: 1px solid rgba(11,20,26,0.08) !important; box-shadow: 0 1px 3px rgba(11,20,26,0.05) !important; } ' +" +
                "      'body.dark #main header { background: #202c33 !important; border-bottom: 1px solid rgba(255,255,255,0.06) !important; box-shadow: 0 1px 3px rgba(0,0,0,0.3) !important; } ' +" +
                "      '#wa-mobile-back-btn { background: transparent; border: none; min-width: 40px; min-height: 44px; padding: 8px 4px 8px 6px; margin: 0 2px; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; color: #111b21; outline: none; -webkit-tap-highlight-color: transparent; } ' +" +
                "      'body.dark #wa-mobile-back-btn { color: #e9edef; } ' +" +
                "      '#wa-mobile-back-btn svg { fill: currentColor; width: 26px; height: 26px; } ' +" +
                "      '#main header [data-testid=\"conversation-info-header-chat-title\"], #main header span[title] { font-size: 16px !important; font-weight: 600 !important; color: #111b21 !important; } ' +" +
                "      'body.dark #main header [data-testid=\"conversation-info-header-chat-title\"], body.dark #main header span[title] { color: #e9edef !important; } ' +" +
                "      '#main header img, #main header [data-testid=\"avatar-element\"] { border-radius: 50% !important; width: 38px !important; height: 38px !important; } ' +" +
                "      '#main footer { z-index: 100 !important; } ' +" +
                "      '#main [data-testid=\"conversation-panel-messages\"], #main [data-testid=\"conversation-panel-wrapper\"], #main ._amjz, #main ._aigv, #main ._ak2o { width: 100% !important; max-width: 860px !important; margin: 0 auto !important; box-sizing: border-box !important; } ';" +
                "    style.textContent = css;" +
                "    function isChatActive() {" +
                "      if (window.__waForceSideUntil && Date.now() < window.__waForceSideUntil) return false;" +
                "      var main = document.getElementById('main');" +
                "      if (!main) return false;" +
                "      var isIntro = main.querySelector('[data-testid=\"intro-title\"]') || main.querySelector('[data-asset-intro-image-light]') || main.querySelector('[data-asset-intro-image-dark]') || main.querySelector('[data-testid=\"intro-text\"]');" +
                "      if (isIntro) return false;" +
                "      var header = main.querySelector('header');" +
                "      var footer = main.querySelector('footer');" +
                "      var compose = main.querySelector('[contenteditable=\"true\"]');" +
                "      return !!(header && (footer || compose));" +
                "    }" +
                "    window.waMobileGoBack = function() {" +
                "      window.__waForceSideUntil = Date.now() + 700;" +
                "      try {" +
                "        var escDown = new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', keyCode: 27, which: 27, bubbles: true });" +
                "        document.dispatchEvent(escDown);" +
                "        var escUp = new KeyboardEvent('keyup', { key: 'Escape', code: 'Escape', keyCode: 27, which: 27, bubbles: true });" +
                "        document.dispatchEvent(escUp);" +
                "      } catch(e) {}" +
                "      document.body.classList.remove('wa-view-main');" +
                "      document.body.classList.add('wa-view-side');" +
                "      syncMobileView();" +
                "      return true;" +
                "    };" +
                "    function fixComposeAndMessages() {" +
                "      var hideSelectors = ['[data-testid=\"status-v3-unread\"]', '[data-testid=\"status-v3\"]', '[data-testid=\"status-outline\"]', '[data-icon=\"status-v3\"]', '[data-icon=\"status-outline\"]', 'span[data-icon=\"status-v3\"]', 'span[data-icon=\"status-outline\"]', 'button[aria-label*=\"Status\"]', 'button[aria-label*=\"الحالة\"]', 'button[aria-label*=\"حالة\"]', 'button[aria-label*=\"ستوري\"]', 'button[title*=\"Status\"]', 'button[title*=\"الحالة\"]', '[data-testid=\"newsletter-icon-unread\"]', '[data-testid=\"newsletter-icon\"]', '[data-icon=\"newsletter-outline\"]', 'span[data-icon=\"newsletter-outline\"]', 'button[aria-label*=\"Channels\"]', 'button[aria-label*=\"القنوات\"]', 'button[aria-label*=\"قنوات\"]', '[data-testid=\"community-icon\"]', 'button[aria-label*=\"Communities\"]', 'button[aria-label*=\"المجتمعات\"]', 'button[aria-label*=\"مجتمعات\"]'];" +
                "      hideSelectors.forEach(function(sel) {" +
                "        var els = document.querySelectorAll(sel);" +
                "        for (var c = 0; c < els.length; c++) {" +
                "          els[c].style.setProperty('display', 'none', 'important');" +
                "          els[c].style.setProperty('width', '0px', 'important');" +
                "          els[c].style.setProperty('min-width', '0px', 'important');" +
                "          els[c].style.setProperty('max-width', '0px', 'important');" +
                "          els[c].style.setProperty('padding', '0px', 'important');" +
                "          els[c].style.setProperty('margin', '0px', 'important');" +
                "          els[c].style.setProperty('visibility', 'hidden', 'important');" +
                "          els[c].style.setProperty('pointer-events', 'none', 'important');" +
                "          if (els[c].parentElement && (els[c].parentElement.children.length === 1 || els[c].parentElement.tagName === 'SPAN')) {" +
                "            els[c].parentElement.style.setProperty('display', 'none', 'important');" +
                "          }" +
                "        }" +
                "      });" +
                "      var main = document.getElementById('main');" +
                "      if (main) {" +
                "        var panel = main.querySelector('[data-testid=\"conversation-panel-messages\"]') ||" +
                "                    main.querySelector('._amjz') ||" +
                "                    main.querySelector('._aigv');" +
                "        if (panel) {" +
                "          panel.style.setProperty('box-sizing', 'border-box', 'important');" +
                "          panel.style.setProperty('max-width', '860px', 'important');" +
                "          panel.style.setProperty('margin', '0 auto', 'important');" +
                "          panel.style.setProperty('width', '100%', 'important');" +
                "        }" +
                "      }" +
                "    }" +
                "    function syncMobileView() {" +
                "      var isLanding = !!(document.querySelector('.landing-wrapper, [data-testid=\"landing-wrapper\"], div[data-testid=\"qrcode\"], .landing-window, .landing-box') ||" +
                "                         document.querySelector('canvas[aria-label*=\"Scan\"], canvas[aria-label*=\"QR\"], [data-ref]'));" +
                "      if (isLanding && !document.getElementById('side')) return;" +
                "      var side = document.getElementById('side');" +
                "      var main = document.getElementById('main');" +
                "      if (!side && !main) return;" +
                "      var isChat = isChatActive();" +
                "      var sideCol = null;" +
                "      var mainCol = null;" +
                "      var container = null;" +
                "      if (side) {" +
                "        var cur = side;" +
                "        while (cur.parentElement && cur.parentElement.id !== 'app' && !cur.parentElement.classList.contains('app-wrapper-web') && cur.parentElement !== document.body) {" +
                "          if (main && cur.parentElement.contains(main)) {" +
                "            container = cur.parentElement;" +
                "            break;" +
                "          }" +
                "          if (cur.parentElement.children.length > 1) {" +
                "            container = cur.parentElement;" +
                "          }" +
                "          cur = cur.parentElement;" +
                "        }" +
                "        if (!container && side.parentElement) container = side.parentElement.parentElement || side.parentElement;" +
                "      }" +
                "      if (container) {" +
                "        for (var i = 0; i < container.children.length; i++) {" +
                "          var child = container.children[i];" +
                "          if (side && child.contains(side)) sideCol = child;" +
                "          if (main && child.contains(main)) mainCol = child;" +
                "        }" +
                "      }" +
                "      if (isChat) {" +
                "        document.body.classList.remove('wa-view-side');" +
                "        document.body.classList.add('wa-view-main');" +
                "        if (container) {" +
                "          for (var j = 0; j < container.children.length; j++) {" +
                "            var c = container.children[j];" +
                "            if (mainCol && c === mainCol) {" +
                "              c.classList.remove('wa-hide-on-chat');" +
                "              c.classList.add('wa-main-col');" +
                "              c.style.setProperty('display', 'flex', 'important');" +
                "              c.style.setProperty('width', '100vw', 'important');" +
                "              c.style.setProperty('min-width', '100vw', 'important');" +
                "              c.style.setProperty('max-width', '100vw', 'important');" +
                "              c.style.setProperty('flex', '1 1 100vw', 'important');" +
                "              c.style.setProperty('height', '100%', 'important');" +
                "            } else {" +
                "              c.classList.add('wa-hide-on-chat');" +
                "              c.classList.remove('wa-main-col');" +
                "              c.style.setProperty('display', 'none', 'important');" +
                "              c.style.setProperty('width', '0', 'important');" +
                "              c.style.setProperty('min-width', '0', 'important');" +
                "              c.style.setProperty('max-width', '0', 'important');" +
                "              c.style.setProperty('flex', '0 0 0', 'important');" +
                "            }" +
                "          }" +
                "        }" +
                "        if (main) {" +
                "          main.style.setProperty('display', 'flex', 'important');" +
                "          main.style.setProperty('width', '100vw', 'important');" +
                "          main.style.setProperty('min-width', '100vw', 'important');" +
                "          main.style.setProperty('max-width', '100vw', 'important');" +
                "          main.style.setProperty('flex', '1 1 100vw', 'important');" +
                "          main.style.setProperty('height', '100%', 'important');" +
                "          var header = main.querySelector('header');" +
                "          if (header && !document.getElementById('wa-mobile-back-btn')) {" +
                "            var isRtl = document.dir === 'rtl' || document.documentElement.dir === 'rtl' || (window.getComputedStyle && window.getComputedStyle(document.body).direction === 'rtl');" +
                "            var btn = document.createElement('button');" +
                "            btn.id = 'wa-mobile-back-btn';" +
                "            btn.setAttribute('aria-label', 'Back');" +
                "            var svgPath = isRtl ? '<path d=\"M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z\"></path>' : '<path d=\"M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z\"></path>';" +
                "            btn.innerHTML = '<svg viewBox=\"0 0 24 24\">' + svgPath + '</svg>';" +
                "            btn.onclick = function(e) {" +
                "              e.stopPropagation();" +
                "              e.preventDefault();" +
                "              window.waMobileGoBack();" +
                "            };" +
                "            header.insertBefore(btn, header.firstChild);" +
                "          }" +
                "        }" +
                "        fixComposeAndMessages();" +
                "      } else {" +
                "        document.body.classList.remove('wa-view-main');" +
                "        document.body.classList.add('wa-view-side');" +
                "        if (container) {" +
                "          for (var k = 0; k < container.children.length; k++) {" +
                "            var col = container.children[k];" +
                "            if (mainCol && col === mainCol) {" +
                "              col.style.setProperty('display', 'none', 'important');" +
                "              col.style.setProperty('width', '0', 'important');" +
                "              col.style.setProperty('min-width', '0', 'important');" +
                "              col.style.setProperty('max-width', '0', 'important');" +
                "              col.style.setProperty('flex', '0 0 0', 'important');" +
                "            } else if (sideCol && col === sideCol) {" +
                "              col.classList.remove('wa-hide-on-chat');" +
                "              col.classList.add('wa-side-col');" +
                "              col.style.setProperty('display', 'flex', 'important');" +
                "              col.style.setProperty('width', '100%', 'important');" +
                "              col.style.setProperty('min-width', '0', 'important');" +
                "              col.style.setProperty('max-width', '100vw', 'important');" +
                "              col.style.setProperty('flex', '1 1 auto', 'important');" +
                "              col.style.setProperty('height', '100%', 'important');" +
                "            } else {" +
                "              col.classList.remove('wa-hide-on-chat');" +
                "              col.style.removeProperty('display');" +
                "              col.style.removeProperty('width');" +
                "              col.style.removeProperty('min-width');" +
                "              col.style.removeProperty('max-width');" +
                "              col.style.removeProperty('flex');" +
                "            }" +
                "          }" +
                "        }" +
                "        if (side) {" +
                "          side.style.setProperty('display', 'flex', 'important');" +
                "          side.style.setProperty('width', '100%', 'important');" +
                "          side.style.setProperty('min-width', '0', 'important');" +
                "          side.style.setProperty('max-width', '100%', 'important');" +
                "          side.style.setProperty('height', '100%', 'important');" +
                "          side.style.setProperty('flex', '1 1 auto', 'important');" +
                "        }" +
                "        if (main) {" +
                "          main.style.setProperty('display', 'none', 'important');" +
                "        }" +
                "        fixComposeAndMessages();" +
                "      }" +
                "    }" +
                "    if (!window.__waMobileControllerInit) {" +
                "      window.__waMobileControllerInit = true;" +
                "      setInterval(syncMobileView, 200);" +
                "      document.addEventListener('click', function(e) {" +
                "        var s = document.getElementById('side');" +
                "        if (s && s.contains(e.target)) { window.__waForceSideUntil = 0; }" +
                "        setTimeout(syncMobileView, 30);" +
                "        setTimeout(syncMobileView, 150);" +
                "        setTimeout(syncMobileView, 350);" +
                "      }, true);" +
                "      window.addEventListener('resize', syncMobileView);" +
                "      window.addEventListener('orientationchange', function() { setTimeout(syncMobileView, 200); });" +
                "      try {" +
                "        var observer = new MutationObserver(syncMobileView);" +
                "        observer.observe(document.body, { childList: true, subtree: true });" +
                "      } catch(e) {}" +
                "    }" +
                "    syncMobileView();" +
                "  } catch(err) {}" +
                "})();";
        mWebView.loadUrl(js);
    }

    public void logout(){
        new AlertDialog.Builder(this)
                .setTitle(R.string.logout_title)
                .setMessage(R.string.logout_message)
                .setPositiveButton(R.string.yes_button, (dialog, which) -> {
                    showSnackbar(R.string.logging_out);
                    mWebView.loadUrl("javascript:localStorage.clear()");
                    WebStorage.getInstance().deleteAllData();
                    loadWhatsapp();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.no_button, (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void initUserAgent() {
        if (mSharedPrefs == null) {
            mSharedPrefs = this.getSharedPreferences(this.getPackageName(), Context.MODE_PRIVATE);
        }
        String choice = mSharedPrefs.getString("user_agent_choice", "chrome");
        if ("firefox".equals(choice)) {
            USER_AGENT = FIREFOX_UA;
        } else if ("edge".equals(choice)) {
            USER_AGENT = EDGE_UA;
        } else {
            String chromeVersion = CHROME_VERSION_DEFAULT;
            try {
                PackageInfo webviewPackageInfo = WebViewCompat.getCurrentWebViewPackage(getApplicationContext());
                if (webviewPackageInfo != null && webviewPackageInfo.versionName != null && !webviewPackageInfo.versionName.isEmpty()) {
                    String[] parts = webviewPackageInfo.versionName.split("\\.");
                    if (parts.length > 0) {
                        int major = Integer.parseInt(parts[0]);
                        // WhatsApp Web blocks Chrome if the major version is outdated (< 115).
                        // On Android 5 and older systems, the WebView is version 37-53 (or max 95),
                        // which directly triggers WhatsApp Web's "Update Google Chrome" blocking screen.
                        // Only use local webview version if it is modern (>= 115); otherwise keep the modern desktop fallback!
                        if (major >= 115) {
                            chromeVersion = webviewPackageInfo.versionName;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(DEBUG_TAG, "Error evaluating webview package info", e);
            }
            USER_AGENT = String.format(CHROME_TEMPLATE, chromeVersion);
        }
        if (mWebView != null) {
            mWebView.getSettings().setUserAgentString(USER_AGENT);
        }
    }

    public void injectCompatibilityFixes(WebView view) {
        if (view == null) return;
        String polyfill = "javascript:(function(){" +
                "  try {" +
                "    if (typeof globalThis === 'undefined') { window.globalThis = window; }" +
                "    if (typeof queueMicrotask === 'undefined') { window.queueMicrotask = function(fn){ Promise.resolve().then(fn); }; }" +
                "    if (typeof Object.fromEntries === 'undefined') { Object.fromEntries = function(entries){ var obj = {}; for (var pair of entries) { obj[pair[0]] = pair[1]; } return obj; }; }" +
                "    try { Object.defineProperty(navigator, 'platform', { get: function(){ return 'Win32'; } }); } catch(e){}" +
                "  } catch(e) {}" +
                "})();";
        view.loadUrl(polyfill);
    }

    private void showBrowserAgentDialog() {
        final String[] options = new String[]{
                getString(R.string.browser_chrome),
                getString(R.string.browser_firefox),
                getString(R.string.browser_edge)
        };
        String currentChoice = mSharedPrefs.getString("user_agent_choice", "chrome");
        int checkedItem = 0;
        if ("firefox".equals(currentChoice)) {
            checkedItem = 1;
        } else if ("edge".equals(currentChoice)) {
            checkedItem = 2;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title_choose_browser)
                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                    String selected = "chrome";
                    if (which == 1) {
                        selected = "firefox";
                    } else if (which == 2) {
                        selected = "edge";
                    }
                    mSharedPrefs.edit().putString("user_agent_choice", selected).apply();
                    initUserAgent();
                    loadWhatsapp();
                    dialog.dismiss();
                    showSnackbar(R.string.reloading);
                })
                .setNegativeButton(R.string.dismiss, null)
                .show();
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.appbar_hide) {
            if (getSupportActionBar().isShowing()) {
                showSnackbar(R.string.appbar_hiding);
                setAppbarEnabled(false);
            } else {
                setAppbarEnabled(true);
            }
        } else if (id == R.id.nav_logout) {
           logout();
        } else if (id == R.id.nav_new) {
            //showToast("nav_new");
        } else if (id == R.id.nav_browser_agent) {
            showBrowserAgentDialog();
        } else if (id == R.id.nav_settings) {
            //showToast("nav_settings");
        } else if (id == R.id.nav_about) {
            showAbout();
        } else if (id == R.id.nav_reload) {
            showSnackbar(R.string.reloading);
            loadWhatsapp();
        } else if (id == R.id.nav_dark_mode) {
            toggleDarkMode();
        } else if (id == R.id.nav_keyboard) {
            toggleKeyboard();
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

}
