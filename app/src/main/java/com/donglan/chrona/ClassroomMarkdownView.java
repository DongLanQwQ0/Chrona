package com.donglan.chrona;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.webkit.*;
import android.widget.LinearLayout;
import com.donglan.chrona.classroom.ClassroomDocument;
import org.json.*;
import java.io.*;
import java.util.*;

/** A local-only document renderer; native code owns every document action. */
final class ClassroomMarkdownView extends WebView {
    private static final String ORIGIN="https://classroom.chrona.local/";
    interface Actions { void assignment(int index); void image(String name); Uri imageUri(String name); }
    private final Activity activity;
    private final File directory;
    private final Set<String> images;
    private final String document;
    private final Actions actions;
    private boolean disposed;
    private boolean ready;
    private Runnable readyAction;
    private final Runnable readinessProbe=()->{
        if(disposed||ready)return;
        evaluateJavascript("JSON.stringify({state:document.readyState,renderer:typeof renderClassroom,math:typeof MathJax,stage:window.classroomRenderStage||'',children:document.querySelector('main')?.childElementCount||0,formulas:document.querySelectorAll('[data-formula]').length,rendered:document.querySelectorAll('mjx-container').length})",
                value->android.util.Log.w("ChronaReader","readiness "+value));
    };
    private boolean dragEligible;private float downX,downY;
    @android.annotation.SuppressLint({"SetJavaScriptEnabled","AddJavascriptInterface"})
    ClassroomMarkdownView(Activity activity,File directory,JSONObject summary,Actions actions) throws Exception {
        super(activity);this.activity=activity;this.directory=directory;this.actions=actions;
        images=ClassroomDocument.referencedImages(summary);
        JSONArray names=new JSONArray();for(String name:images)names.put(name);
        document=new JSONObject().put("summary",ClassroomDocument.normalized(summary)).put("images",names).toString();
        setBackgroundColor(Color.TRANSPARENT);setVerticalScrollBarEnabled(false);setHorizontalScrollBarEnabled(false);
        getSettings().setJavaScriptEnabled(true);getSettings().setAllowFileAccess(false);getSettings().setAllowContentAccess(false);
        getSettings().setBlockNetworkLoads(true);getSettings().setDomStorageEnabled(false);getSettings().setTextZoom(100);
        getSettings().setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        setOnTouchListener((view,event)->{
            switch(event.getActionMasked()){
                case android.view.MotionEvent.ACTION_DOWN -> {dragEligible=true;downX=event.getX();downY=event.getY();}
                case android.view.MotionEvent.ACTION_MOVE -> {int slop=android.view.ViewConfiguration.get(activity).getScaledTouchSlop();if(Math.hypot(event.getX()-downX,event.getY()-downY)>slop)dragEligible=false;}
                case android.view.MotionEvent.ACTION_POINTER_DOWN,android.view.MotionEvent.ACTION_UP,android.view.MotionEvent.ACTION_CANCEL -> dragEligible=false;
            }
            return false;
        });
        setOnLongClickListener(view->{
            if(disposed||!dragEligible)return false;
            HitTestResult hit=getHitTestResult();if(hit==null||hit.getType()!=HitTestResult.IMAGE_TYPE&&hit.getType()!=HitTestResult.SRC_IMAGE_ANCHOR_TYPE)return false;
            try{
                Uri source=Uri.parse(hit.getExtra());String path=source.getPath();
                if(!"classroom.chrona.local".equals(source.getHost())||path==null||!path.startsWith("/images/"))return false;
                String name=ClassroomDocument.imageName(path.substring(8));if(!images.contains(name))return false;
                android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;
                String file=new File(directory,name).getPath();android.graphics.BitmapFactory.decodeFile(file,options);
                options.inJustDecodeBounds=false;options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>640)options.inSampleSize*=2;
                android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeFile(file,options);
                boolean started=ImageDrag.start(activity,this,bitmap,actions.imageUri(name));dragEligible=false;
                if(started){long now=android.os.SystemClock.uptimeMillis();android.view.MotionEvent cancel=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_CANCEL,0,0,0);try{onTouchEvent(cancel);}finally{cancel.recycle();}}
                if(!started&&bitmap!=null)bitmap.recycle();return started;
            }catch(Exception unavailable){return false;}
        });
        addJavascriptInterface(new Object(){
        @JavascriptInterface public void failure(String type,String location){post(()->{
            if(disposed)return;
            String safeType=Set.of("Error","TypeError","SyntaxError","ReferenceError","RangeError").contains(type)?type:"Error";
            String safeLocation=location!=null&&location.matches("reader\\.js:[0-9]+(?::[0-9]+)?")?location:"render-entry";
            android.util.Log.e("ChronaReader",safeType+" at "+safeLocation);
        });}
        @JavascriptInterface public void ready(){post(()->{
            if(disposed)return;
            postVisualStateCallback(0,new VisualStateCallback(){@Override public void onComplete(long requestId){
                if(disposed)return;
                removeCallbacks(readinessProbe);
                ready=true;if(readyAction!=null)readyAction.run();
            }});
        });}
        @JavascriptInterface public void height(int cssPixels){
            if(cssPixels<0)return;
            post(()->{if(disposed)return;int height=Math.round(cssPixels*getResources().getDisplayMetrics().density);
                android.view.ViewGroup.LayoutParams p=getLayoutParams();if(p!=null&&p.height!=height){p.height=height;setLayoutParams(p);}});
        }},"ChronaSize");
        setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                Uri uri=request.getUrl();
                if(!"https".equals(uri.getScheme())||!"classroom.chrona.local".equals(uri.getHost()))return blocked();
                String path=uri.getPath();
                try{
                    if(path!=null&&path.startsWith("/images/")){
                        String name=ClassroomDocument.imageName(path.substring(8));
                        if(images.contains(name))return new WebResourceResponse("image/jpeg",null,new FileInputStream(new File(directory,name)));
                    } else if(Arrays.asList("/reader.html","/reader.js","/markdown-it-14.1.0.min.js","/mathjax-tex-svg-3.2.2.js").contains(path))
                        return new WebResourceResponse(path.endsWith(".html")?"text/html":"application/javascript","UTF-8",activity.getAssets().open("classroom"+path));
                }catch(Exception ignored){}
                return blocked();
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                if(!request.isForMainFrame())return true;
                Uri uri=request.getUrl();if(!"classroom.chrona.local".equals(uri.getHost()))return true;
                List<String> parts=uri.getPathSegments();
                if(parts.size()==3&&parts.get(0).equals("action"))try{
                    switch(parts.get(1)){
                        case "task" -> actions.assignment(Integer.parseInt(parts.get(2)));
                        case "image" -> {if(images.contains(parts.get(2)))actions.image(parts.get(2));}
                    }
                }catch(NumberFormatException ignored){}
                return true;
            }
            @Override public void onPageFinished(WebView view,String url){
                if(disposed||!url.equals(ORIGIN+"reader.html"))return;
                try{
                    UiStyle.Palette palette=UiStyle.colors(activity);
                    JSONObject theme=new JSONObject().put("text",hex(palette.text)).put("muted",hex(palette.muted))
                            .put("link",ThemeStore.dark(activity)?"#80baff":"#1468c8").put("line",hex(palette.outline))
                            .put("fontScale",getResources().getConfiguration().fontScale)
                            .put("viewportHeight",getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density);
                    String report="e=>{const at=String(e?.stack||'').match(/reader\\.js:[0-9]+(?::[0-9]+)?/);ChronaSize.failure(String(e?.name||'Error'),at?at[0]:'render-entry');}";
                    evaluateJavascript("(()=>{try{Promise.resolve(renderClassroom(JSON.parse("+JSONObject.quote(document)+"),"+theme+")).catch("+report+");}catch(e){("+report+")(e);}})()",null);
                    removeCallbacks(readinessProbe);postDelayed(readinessProbe,10000);
                }catch(JSONException ignored){}
            }
        });
        setLayoutParams(new LinearLayout.LayoutParams(-1,1));
        loadUrl(ORIGIN+"reader.html");
    }
    private static String hex(int color){return String.format(Locale.ROOT,"#%06x",color&0xffffff);}
    private static WebResourceResponse blocked(){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
    void onReady(Runnable action){readyAction=action;if(ready&&action!=null)action.run();}
    void dispose(){disposed=true;readyAction=null;removeCallbacks(readinessProbe);stopLoading();removeJavascriptInterface("ChronaSize");destroy();}
}
