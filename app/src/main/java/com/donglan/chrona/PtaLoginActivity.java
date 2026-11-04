package com.donglan.chrona;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.*;
import android.widget.LinearLayout;
import android.widget.FrameLayout;

/** Official interactive login; no JS bridge, credential logging or captcha automation. */
public final class PtaLoginActivity extends Activity {
    private WebView web;
    private boolean checking;
    private volatile boolean finished;
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable harvest=new Runnable(){public void run(){verify();if(!finished)handler.postDelayed(this,2500);}};
    @Override protected void onCreate(Bundle saved){
        ThemeStore.apply(this);super.onCreate(saved);
        LinearLayout root=SettingsPageLayout.content(this);
        root.setPadding(root.getPaddingLeft(),root.getPaddingTop(),root.getPaddingRight(),Math.round(8*getResources().getDisplayMetrics().density));
        SettingsPageLayout.header(this,root,"登录 PTA",UiStyle.navigationIcon(this,R.drawable.ic_check,"完成登录",()->verify(true)));
        web=new WebView(this);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);CookieManager.getInstance().setAcceptCookie(true);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !"https".equals(request.getUrl().getScheme());}
            @Override public void onPageFinished(WebView view,String url){verify();}
        });
        FrameLayout website=new FrameLayout(this);UiSurfaces.clipRounded(website,UiStyle.RADIUS_CARD);
        website.addView(web,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout.LayoutParams viewport=new LinearLayout.LayoutParams(-1,0,1);viewport.topMargin=Math.round(8*getResources().getDisplayMetrics().density);
        root.addView(website,viewport);SettingsPageLayout.showFixed(this,root);
        web.loadUrl("https://pintia.cn/auth/login");
    }
    private void verify(){
        verify(false);
    }
    private void verify(boolean explicit){
        if(checking||finished){if(explicit)Feedback.show(this,"正在验证 PTA 登录");return;}
        String cookies=CookieManager.getInstance().getCookie("https://pintia.cn");String value=null;
        if(cookies!=null)for(String part:cookies.split(";")){String p=part.trim();if(p.startsWith("PTASession="))value=p.substring(11);}
        if(value==null||value.isBlank()){if(explicit)Feedback.show(this,"请先在网页完成登录");return;}
        String session=value;checking=true;
        ZjuSync.WORKER.execute(()->{
            boolean success=false;String error=null;
            try(PtaClient client=new PtaClient(session)){
                PtaClient.User user=client.whoAmI();
                synchronized(AndroidSync.LOCK){
                    if(finished)return;
                    PtaSettingsStore settings=new PtaSettingsStore(this);settings.save(session,user.id(),user.nickname());
                    new ZjuHomeworkStore(this,2).configure(settings.account(),true);settings.prefs.edit().putBoolean("auth_expired",false).apply();
                }
                success=true;
            }catch(Exception failure){error=failure instanceof PtaClient.AuthExpired?"请先在网页完成登录":"PTA 验证或保存失败，请检查网络后重试";}
            boolean accepted=success;String message=error;runOnUiThread(()->{checking=false;if(isDestroyed())return;if(accepted){finished=true;clearBrowserSession();ZjuSyncJobService.schedule(this);ZjuSync.requestPta(this,null);finish();}else if(explicit)Feedback.show(this,message);});
        });
    }
    @Override protected void onResume(){super.onResume();handler.post(harvest);}
    @Override protected void onPause(){handler.removeCallbacks(harvest);super.onPause();}
    @Override protected void onDestroy(){finished=true;handler.removeCallbacks(harvest);if(web!=null){web.stopLoading();web.destroy();}super.onDestroy();}
    static void clearBrowserSession(){
        CookieManager manager=CookieManager.getInstance();
        for(String host:new String[]{"pintia.cn","passport.pintia.cn"}){
            manager.setCookie("https://"+host,"PTASession=; Max-Age=0; Path=/; Secure; HttpOnly");
            manager.setCookie("https://"+host,"PTASession=; Max-Age=0; Path=/; Domain=.pintia.cn; Secure; HttpOnly");
        }
        manager.flush();
    }
}
