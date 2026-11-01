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
    interface Actions {
        void assignment(int index); void image(String name); Uri imageUri(String name);
        default void lesson(String key) { }
        default void image(String key,String name) { }
        default Uri imageUri(String key,String name) { return null; }
        default void readingGesture() { }
    }
    private final Activity activity;
    private final File directory;
    private final Set<String> images;
    private final String document;
    private final Actions actions;
    private final Map<String,File> lessonDirectories;
    private final Map<String,Set<String>> lessonImages=new HashMap<>();
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
        this(activity,directory,summary,null,Map.of(),false,actions);
    }
    ClassroomMarkdownView(Activity activity,JSONArray lessons,Map<String,File> directories,Actions actions)throws Exception {
        this(activity,null,null,lessons,directories,false,actions);
    }
    static ClassroomMarkdownView correctedTranscript(Activity activity,File directory,JSONObject summary,Actions actions)throws Exception {
        return new ClassroomMarkdownView(activity,directory,summary,null,Map.of(),true,actions);
    }
    @android.annotation.SuppressLint({"SetJavaScriptEnabled","AddJavascriptInterface"})
    private ClassroomMarkdownView(Activity activity,File directory,JSONObject summary,JSONArray lessons,Map<String,File> directories,boolean transcriptOnly,Actions actions)throws Exception {
        super(activity);this.activity=activity;this.directory=directory;this.actions=actions;
        lessonDirectories=Map.copyOf(directories);
        if(lessons==null){
            images=ClassroomDocument.referencedImages(summary);
            JSONArray names=new JSONArray();for(String name:images)names.put(name);
            JSONObject data=new JSONObject().put("images",names);
            if(transcriptOnly)data.put("transcript",ClassroomDocument.normalizeMarkdown(summary.optString("corrected_transcript")));
            else {
                JSONObject notes=new JSONObject(summary.toString());notes.remove("corrected_transcript");
                data.put("summary",ClassroomDocument.normalized(notes));
            }
            document=data.toString();
        }else{
            images=Set.of();JSONArray copy=new JSONArray();
            for(int i=0;i<lessons.length();i++){
                JSONObject row=lessons.getJSONObject(i);String key=row.getString("key");
                if(!key.matches("[0-9]+-[0-9]+")||!lessonDirectories.containsKey(key))throw new IOException("课堂来源无效");
                JSONObject value=row.getJSONObject("summary");
                if(!key.equals(ClassroomDocument.key(value.getJSONObject("lesson"))))throw new IOException("课堂来源不匹配");
                Set<String> referenced=ClassroomDocument.referencedImages(value);lessonImages.put(key,Set.copyOf(referenced));
                JSONArray names=new JSONArray();for(String name:referenced)names.put(name);
                copy.put(new JSONObject().put("key",key).put("label",row.getString("label"))
                        .put("summary",ClassroomDocument.normalized(value)).put("images",names));
            }
            document=new JSONObject().put("lessons",copy).toString();
        }
        setBackgroundColor(Color.TRANSPARENT);setVerticalScrollBarEnabled(false);setHorizontalScrollBarEnabled(false);
        getSettings().setJavaScriptEnabled(true);getSettings().setAllowFileAccess(false);getSettings().setAllowContentAccess(false);
        getSettings().setBlockNetworkLoads(true);getSettings().setDomStorageEnabled(false);getSettings().setTextZoom(100);
        getSettings().setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        setOnTouchListener((view,event)->{
            switch(event.getActionMasked()){
                case android.view.MotionEvent.ACTION_DOWN -> {actions.readingGesture();dragEligible=true;downX=event.getX();downY=event.getY();}
                case android.view.MotionEvent.ACTION_MOVE -> {int slop=android.view.ViewConfiguration.get(activity).getScaledTouchSlop();if(Math.hypot(event.getX()-downX,event.getY()-downY)>slop)dragEligible=false;}
                case android.view.MotionEvent.ACTION_POINTER_DOWN,android.view.MotionEvent.ACTION_UP,android.view.MotionEvent.ACTION_CANCEL -> dragEligible=false;
            }
            return false;
        });
        setOnKeyListener((view,key,event)->{if(event.getAction()==android.view.KeyEvent.ACTION_DOWN)actions.readingGesture();return false;});
        setOnLongClickListener(view->{
            if(disposed||!dragEligible)return false;
            HitTestResult hit=getHitTestResult();if(hit==null||hit.getType()!=HitTestResult.IMAGE_TYPE&&hit.getType()!=HitTestResult.SRC_IMAGE_ANCHOR_TYPE)return false;
            try{
                Uri source=Uri.parse(hit.getExtra());String path=source.getPath();
                if(!"https".equals(source.getScheme())||!"classroom.chrona.local".equals(source.getHost()))return false;
                Picture picture=picture(path);if(picture==null)return false;
                android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;
                String file=picture.file.getPath();android.graphics.BitmapFactory.decodeFile(file,options);
                options.inJustDecodeBounds=false;options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>640)options.inSampleSize*=2;
                android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeFile(file,options);
                boolean started=ImageDrag.start(activity,this,bitmap,picture.key==null?actions.imageUri(picture.name):actions.imageUri(picture.key,picture.name));dragEligible=false;
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
                    Picture picture=picture(path);
                    if(picture!=null)return new WebResourceResponse("image/jpeg",null,new FileInputStream(picture.file));
                    if(Arrays.asList("/reader.html","/reader.js","/markdown-it-14.1.0.min.js","/mathjax-tex-svg-3.2.2.js").contains(path))
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
                        case "lesson" -> {if(lessonDirectories.containsKey(parts.get(2)))actions.lesson(parts.get(2));}
                    }
                }catch(NumberFormatException ignored){}
                if(parts.size()==4&&parts.get(0).equals("action")&&parts.get(1).equals("image")
                        &&lessonImages.getOrDefault(parts.get(2),Set.of()).contains(parts.get(3)))actions.image(parts.get(2),parts.get(3));
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
    private record Picture(String key,String name,File file) { }
    private Picture picture(String path)throws IOException {
        if(path==null)return null;
        String key=null,name;File parent;
        if(path.startsWith("/images/")){
            name=ClassroomDocument.imageName(path.substring(8));if(!images.contains(name))return null;parent=directory;
        }else{
            String[] fields=path.split("/",-1);
            if(fields.length!=5||!fields[1].equals("lessons")||!fields[3].equals("images"))return null;
            key=fields[2];name=ClassroomDocument.imageName(fields[4]);
            if(!lessonImages.getOrDefault(key,Set.of()).contains(name))return null;parent=lessonDirectories.get(key);
        }
        File file=new File(parent,name);
        if(java.nio.file.Files.isSymbolicLink(file.toPath())||!file.getCanonicalFile().getParentFile().equals(parent.getCanonicalFile()))return null;
        return new Picture(key,name,file);
    }
    private static String hex(int color){return String.format(Locale.ROOT,"#%06x",color&0xffffff);}
    private static WebResourceResponse blocked(){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
    void onReady(Runnable action){readyAction=action;if(ready&&action!=null)action.run();}
    void dispose(){
        if(disposed)return;disposed=true;readyAction=null;removeCallbacks(readinessProbe);
        if(getParent() instanceof android.view.ViewGroup parent)parent.removeView(this);
        stopLoading();removeJavascriptInterface("ChronaSize");destroy();
    }
}
