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
    interface Actions { void assignment(int index); void image(String name); }
    private final Activity activity;
    private final File directory;
    private final Set<String> images;
    private final String document;
    private final Actions actions;
    private boolean disposed;
    @android.annotation.SuppressLint({"SetJavaScriptEnabled","AddJavascriptInterface"})
    ClassroomMarkdownView(Activity activity,File directory,JSONObject summary,Actions actions) throws Exception {
        super(activity);this.activity=activity;this.directory=directory;this.actions=actions;
        images=ClassroomDocument.referencedImages(summary);
        JSONArray names=new JSONArray();for(String name:images)names.put(name);
        document=new JSONObject().put("summary",summary).put("images",names).toString();
        setBackgroundColor(Color.TRANSPARENT);setVerticalScrollBarEnabled(false);setHorizontalScrollBarEnabled(false);
        getSettings().setJavaScriptEnabled(true);getSettings().setAllowFileAccess(false);getSettings().setAllowContentAccess(false);
        getSettings().setBlockNetworkLoads(true);getSettings().setDomStorageEnabled(false);getSettings().setTextZoom(100);
        getSettings().setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        addJavascriptInterface(new Object(){@JavascriptInterface public void height(int cssPixels){
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
                            .put("fontScale",getResources().getConfiguration().fontScale);
                    evaluateJavascript("renderClassroom(JSON.parse("+JSONObject.quote(document)+"),"+theme+")",null);
                }catch(JSONException ignored){}
            }
        });
        setLayoutParams(new LinearLayout.LayoutParams(-1,1));
        loadUrl(ORIGIN+"reader.html");
    }
    private static String hex(int color){return String.format(Locale.ROOT,"#%06x",color&0xffffff);}
    private static WebResourceResponse blocked(){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
    void dispose(){disposed=true;stopLoading();removeJavascriptInterface("ChronaSize");destroy();}
}
