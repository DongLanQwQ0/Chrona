package com.donglan.chrona;

import android.app.Activity;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.donglan.chrona.classroom.*;
import org.json.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/** Dedicated monitor workspace; the service owns the connection when this screen is closed. */
public final class ClassroomTranscriptActivity extends Activity {
    private ClassroomLesson lesson;private String account;
    private LinearLayout rows;private ScrollView scroll;private ImageButton latest;private TextView status;private FrameLayout readingBody;
    private CheckBox repeat;private ImageButton action;private boolean binding;private int mode;
    private ScrollView slidesScroll;private LinearLayout slides;private String pictureSignature="";
    private TextView slidesLabel;private DockNavigationLayout sections;
    private final List<TextView> paragraphs=new ArrayList<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService reader=Executors.newSingleThreadExecutor();
    private boolean active,reading,follow=true,touching,scrollingMotion,pendingLatest=true;private int generation,pendingRestore=-1;private String signature="";
    private ViewTreeObserver.OnPreDrawListener restore;
    private final Runnable poll=new Runnable(){public void run(){if(!active)return;read();main.postDelayed(this,250);}};
    static void open(Activity activity,String account,ClassroomLesson lesson)throws Exception {
        if(activity instanceof ClassroomTranscriptActivity&&((ClassroomTranscriptActivity)activity).showing(account,lesson)){((ClassroomTranscriptActivity)activity).signature="";return;}
        activity.startActivity(new Intent(activity,ClassroomTranscriptActivity.class).putExtra("account",account).putExtra("lesson",lesson.json().toString()));
    }
    private boolean showing(String owner,ClassroomLesson selected){return owner.equals(account)&&ClassroomMonitorStore.key(selected).equals(ClassroomMonitorStore.key(lesson));}
    @Override protected void onCreate(Bundle state){
        ThemeStore.apply(this);super.onCreate(state);
        try{account=getIntent().getStringExtra("account");lesson=ClassroomLesson.fromJson(new JSONObject(getIntent().getStringExtra("lesson")));if(!ClassroomMonitor.allowed(this,account)){finish();return;}}
        catch(Exception failure){finish();return;}
        if(state!=null){follow=state.getBoolean("follow",true);mode=state.getInt("mode",0);pictureIndex=state.getInt("picture",0);pendingLatest=false;pendingRestore=state.getInt("scroll",0);}
        LinearLayout root=SettingsPageLayout.content(this);
        root.setClipChildren(false);root.setClipToPadding(false);
        action=UiStyle.navigationIcon(this,R.drawable.ic_play,"开始监视",this::toggleMonitoring);
        SettingsPageLayout.header(this,root,"课堂监视",action);
        LinearLayout information=new LinearLayout(this);information.setGravity(Gravity.TOP);
        LinearLayout description=new LinearLayout(this);description.setOrientation(LinearLayout.VERTICAL);
        TextView title=new TextView(this);title.setText(lesson.course);title.setTextSize(16);UiStyle.title(title);UiStyle.addSpaced(description,title,0,4);
        TextView when=new TextView(this);when.setText(lesson.date()+" "+ClassroomMonitorUi.clock(lesson)+(lesson.room.isEmpty()?"":" · "+lesson.room));when.setTextSize(13);UiStyle.muted(when);description.addView(when);
        information.addView(description,new LinearLayout.LayoutParams(0,-2,1));
        repeat=new CheckBox(this);repeat.setText("后续课程");repeat.setContentDescription("监视后续课程");repeat.setTextSize(15);repeat.setMinHeight(dp(48));UiStyle.toggle(repeat);
        LinearLayout.LayoutParams scopeParams=new LinearLayout.LayoutParams(-2,-2);scopeParams.leftMargin=dp(8);information.addView(repeat,scopeParams);UiStyle.addSpaced(root,information,0,4);
        refreshScope();if(state!=null&&!monitoring())repeat.setChecked(state.getBoolean("repeat",false));
        repeat.setOnCheckedChangeListener((button,checked)->{if(binding||!monitoring())return;try{ClassroomMonitor.start(this,lesson,checked);signature="";}catch(Exception failure){Feedback.show(this,"无法更改监视范围");refreshScope();}});
        status=new TextView(this);status.setTextSize(13);UiStyle.muted(status);status.setText("未开始监视");UiStyle.addSpaced(root,status,0,4);
        sections=new DockNavigationLayout(this,this::setMode,()->{});sections.setPadding(dp(6),dp(6),dp(6),dp(6));
        for(int i=0;i<2;i++){final int index=i;TextView item=new TextView(this);item.setText(i==0?"字幕":"PPT");item.setTextSize(15);item.setGravity(Gravity.CENTER);UiStyle.title(item);item.setOnClickListener(v->setMode(index));sections.addItem(item,new LinearLayout.LayoutParams(0,dp(48),1));if(i==1)slidesLabel=item;}
        FrameLayout dockHost=sections.motionHost();
        FrameLayout body=new FrameLayout(this);body.setClipChildren(false);body.setClipToPadding(false);
        FrameLayout transitionHost=new FrameLayout(this);readingBody=new FrameLayout(this);transitionHost.addView(readingBody,new FrameLayout.LayoutParams(-1,-1));body.addView(transitionHost,new FrameLayout.LayoutParams(-1,-1));
        scroll=new ScrollView(this){@Override public void computeScroll(){scrollingMotion=true;try{super.computeScroll();}finally{scrollingMotion=false;}}
            @Override public boolean dispatchTouchEvent(MotionEvent event){
            int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN){touching=true;pendingLatest=false;pendingRestore=getScrollY();follow=false;updateFollow();}
            boolean handled=super.dispatchTouchEvent(event);
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){touching=false;follow=atBottom();updateFollow();}
            return handled;
        }};scroll.setVerticalScrollBarEnabled(false);scroll.setClipToPadding(true);
        rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);scroll.addView(rows);
        readingBody.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        slidesScroll=new ScrollView(this);slidesScroll.setVerticalScrollBarEnabled(false);slides=new LinearLayout(this);slides.setOrientation(LinearLayout.VERTICAL);slidesScroll.addView(slides);readingBody.addView(slidesScroll,new FrameLayout.LayoutParams(-1,-1));
        latest=UiStyle.navigationIcon(this,R.drawable.ic_arrow_down,"回到最新",()->{pendingRestore=-1;pendingLatest=true;positionCaptions();});
        FrameLayout.LayoutParams jump=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.BOTTOM|Gravity.END);body.addView(latest,jump);
        InteractionLayers.pin(latest);
        body.addView(dockHost,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));InteractionLayers.pin(dockHost);
        dockHost.addOnLayoutChangeListener((view,l,t,r,b,oldL,oldT,oldR,oldB)->{
            int clearance=view.getHeight();
            if(rows.getPaddingBottom()!=clearance+dp(64)){rows.setPadding(0,0,0,clearance+dp(64));slides.setPadding(0,0,0,clearance+dp(12));
                FrameLayout.LayoutParams position=(FrameLayout.LayoutParams)latest.getLayoutParams();position.bottomMargin=clearance+dp(8);latest.setLayoutParams(position);}
        });
        scroll.setOnScrollChangeListener((view,x,y,oldX,oldY)->{if((touching||scrollingMotion)&&pendingRestore>=0)pendingRestore=y;follow=atBottom();updateFollow();});
        root.addView(body,new LinearLayout.LayoutParams(-1,0,1));SettingsPageLayout.showFixed(this,root);setMode(mode);
    }
    private boolean atBottom(){return scroll.getScrollY()>=Math.max(0,rows.getHeight()-scroll.getHeight()-dp(48));}
    private void updateFollow(){if(latest!=null)latest.setVisibility(mode!=0||follow?View.GONE:View.VISIBLE);}
    private void setMode(int selected){int next=selected==1?1:0;boolean changed=mode!=next;if(changed&&mode==0&&scroll!=null&&!pendingLatest&&pendingRestore<0)pendingRestore=scroll.getScrollY();mode=next;
        Runnable switchBody=()->{scroll.setVisibility(mode==0?View.VISIBLE:View.GONE);slidesScroll.setVisibility(mode==1?View.VISIBLE:View.GONE);if(mode==0)positionCaptions();};
        if(scroll!=null){if(changed&&active&&readingBody.isLaidOut())UiMotion.swap(readingBody,switchBody);else switchBody.run();}
        if(sections!=null)sections.finishPageSelection(mode);updateFollow();
    }
    private boolean monitoring(){try{return ClassroomMonitorStore.ruleFor(new ClassroomMonitorStore(this,account).read(),lesson)!=null;}catch(Exception ignored){return false;}}
    private void refreshScope(){try{JSONObject rule=ClassroomMonitorStore.ruleFor(new ClassroomMonitorStore(this,account).read(),lesson);binding=true;if(rule!=null)repeat.setChecked(rule.getBoolean("repeat"));binding=false;boolean watching=rule!=null;action.setImageResource(watching?R.drawable.ic_circle_stop:R.drawable.ic_play);action.setContentDescription(watching?"停止监视":"开始监视");action.setTooltipText(watching?"停止监视":"开始监视");}catch(Exception ignored){binding=false;}}
    private void toggleMonitoring(){
        if(!ClassroomMonitor.allowed(this,account)){finish();return;}
        try{if(monitoring()){new ClassroomMonitorStore(this,account).stop(lesson);ClassroomMonitor.changed(this);}else{ClassroomNotifications.requestPermission(this);ClassroomMonitor.start(this,lesson,repeat.isChecked());}refreshScope();signature="";read();}
        catch(Exception failure){Feedback.show(this,"无法更新课堂监视");}
    }
    private void read(){
        if(reading)return;if(!ClassroomMonitor.allowed(this,account)){finish();return;}
        reading=true;int token=generation;
        reader.execute(()->{
            try{
                ClassroomStore store=new ClassroomStore(getApplicationContext(),account);File local=store.localLesson(lesson),saved=store.lesson(lesson);
                StringBuilder stamp=new StringBuilder();for(File file:new File[]{new File(local,"live-transcript.json"),new File(local,"live-current.json"),new File(local,"live-status.json"),new File(saved,"materials.json"),new File(local,"monitor-materials.json"),new File(local,"monitor-candidates.json"),new File(local,"candidates.json")})stamp.append(file.lastModified()).append(':').append(file.length()).append('/');
                boolean watching=ClassroomMonitorUi.watching(getApplicationContext(),lesson);
                JSONObject monitor=new ClassroomMonitorStore(getApplicationContext(),account).read();
                String version=stamp.toString()+watching+":"+monitor.optJSONArray("rules")+":"+(lesson.startsAt<=System.currentTimeMillis());
                if(version.equals(signature))return;
                JSONObject complete=ClassroomStore.read(new File(saved,"materials.json"));JSONArray segments;
                boolean finalTranscript=complete!=null&&!complete.optBoolean("monitoring")&&complete.optJSONArray("segments")!=null&&complete.getJSONArray("segments").length()>0;
                if(finalTranscript)segments=complete.getJSONArray("segments");
                else{segments=ClassroomTranscript.snapshot(ClassroomLiveTranscripts.read(local));if(segments.length()==0){JSONObject observed=ClassroomStore.read(new File(local,"monitor-materials.json"));if(observed!=null)segments=observed.getJSONArray("segments");}}
                JSONObject connection=ClassroomStore.read(new File(local,"live-status.json"));
                String phase=finalTranscript?"完整字幕":connection==null?"连接中":connection.optString("state","连接中");
                if(!finalTranscript&&!watching)phase=segments.length()>0?"已保存":"未开始监视";
                else if(lesson.startsAt>System.currentTimeMillis())phase="等待上课";
                if(!finalTranscript&&connection!=null&&"实时连接".equals(phase)&&System.currentTimeMillis()-connection.optLong("at")>70_000)phase="正在恢复连接";
                JSONObject prepared=ClassroomStore.read(new File(local,"monitor-candidates.json")),ordinary=ClassroomStore.read(new File(local,"candidates.json"));if(prepared==null||ordinary!=null&&ordinary.optLong("updated")>prepared.optLong("updated"))prepared=ordinary;
                JSONArray pictures=prepared==null?new JSONArray():prepared.getJSONArray("images");
                JSONArray incoming=segments;String label=phase+" · "+segments.length()+" 条字幕 · "+pictures.length()+" 张PPT";
                main.post(()->{if(!active||generation!=token||!ClassroomMonitor.allowed(this,account))return;signature=version;refreshScope();render(incoming,label);renderSlides(local,pictures);});
            }catch(Exception failure){main.post(()->{if(active&&generation==token)status.setText("读取失败，正在重试");});}
            finally{main.post(()->reading=false);}
        });
    }
    private int pictureIndex,slideGeneration;
    private JSONArray shownPictures=new JSONArray();private File pictureDirectory;
    private ImageView slideImage;private FrameLayout slideFrame;private DateIntervalBar slidesBar;private ImageButton previousSlide,nextSlide;private String decodedRaw="";
    private final CalendarSwipe imageSwipe=new CalendarSwipe();
    private void renderSlides(File directory,JSONArray pictures){
        String next=pictures.toString();if(next.equals(pictureSignature))return;
        String current=shownPictures.optJSONObject(pictureIndex)==null?"":shownPictures.optJSONObject(pictureIndex).optString("raw");
        boolean last=shownPictures.length()>0&&pictureIndex==shownPictures.length()-1;
        pictureSignature=next;shownPictures=pictures;pictureDirectory=directory;
        if(pictures.length()==0){slides.removeAllViews();slidesBar=null;slideImage=null;decodedRaw="";TextView empty=new TextView(this);empty.setText("暂无PPT");empty.setTextSize(16);UiStyle.muted(empty);UiStyle.addSpaced(slides,empty,8,0);return;}
        if(last)pictureIndex=pictures.length()-1;else for(int i=0;i<pictures.length();i++)if(current.equals(pictures.optJSONObject(i).optString("raw")))pictureIndex=i;
        pictureIndex=Math.max(0,Math.min(pictureIndex,pictures.length()-1));
        if(slidesBar==null){
            slides.removeAllViews();previousSlide=nextSlide=null;slidesBar=new DateIntervalBar(this,this::pickSlide,this::shiftSlide);slidesBar.setContentDescription("PPT，左右滑动切换，点击选择图片");UiStyle.addSpaced(slides,slidesBar,0,8);
            for(int i=0;i<slidesBar.getChildCount();i++)if(slidesBar.getChildAt(i) instanceof ImageButton arrow){if(previousSlide==null){previousSlide=arrow;arrow.setContentDescription("上一张PPT");}else{nextSlide=arrow;arrow.setContentDescription("下一张PPT");}}
            slideFrame=new FrameLayout(this);slideFrame.setClipToOutline(true);slideFrame.setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View view,android.graphics.Outline outline){outline.setRoundRect(0,0,view.getWidth(),view.getHeight(),dp(UiStyle.radius(ClassroomTranscriptActivity.this,UiStyle.RADIUS_CARD)));}});slideFrame.setBackgroundColor(UiStyle.colors(this).surfaceAlt);slides.addView(slideFrame,new LinearLayout.LayoutParams(-1,dp(280)));
            UiStyle.registerDynamicSurface(slideFrame,()->{slideFrame.setBackgroundColor(UiStyle.colors(this).surfaceAlt);slideFrame.invalidateOutline();if(slideImage!=null)slideImage.invalidateOutline();});
            slideImage=new ImageView(this);slideImage.setAdjustViewBounds(true);slideImage.setScaleType(ImageView.ScaleType.FIT_CENTER);slideFrame.addView(slideImage,new FrameLayout.LayoutParams(-1,-1));
            slideImage.setClipToOutline(true);slideImage.setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View view,android.graphics.Outline outline){
                android.graphics.drawable.Drawable drawable=((ImageView)view).getDrawable();if(drawable==null||drawable.getIntrinsicWidth()<=0||drawable.getIntrinsicHeight()<=0){outline.setEmpty();return;}
                float fit=Math.min(view.getWidth()/(float)drawable.getIntrinsicWidth(),view.getHeight()/(float)drawable.getIntrinsicHeight());int width=Math.round(drawable.getIntrinsicWidth()*fit),height=Math.round(drawable.getIntrinsicHeight()*fit),left=(view.getWidth()-width)/2,top=(view.getHeight()-height)/2;
                outline.setRoundRect(left,top,left+width,top+height,dp(UiStyle.radius(ClassroomTranscriptActivity.this,UiStyle.RADIUS_CARD)));
            }});
            slideImage.setOnClickListener(v->openSlide());
            slideImage.setOnTouchListener(new View.OnTouchListener(){float x,y;final int slop=ViewConfiguration.get(ClassroomTranscriptActivity.this).getScaledTouchSlop();
                public boolean onTouch(View view,MotionEvent event){int action=event.getActionMasked();
                    if(action==MotionEvent.ACTION_DOWN){x=event.getX();y=event.getY();imageSwipe.begin(x,y,true);return true;}
                    if(action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_CANCEL)imageSwipe.cancel();
                    if(action==MotionEvent.ACTION_MOVE&&imageSwipe.move(event.getX(),event.getY(),slop,false))view.getParent().requestDisallowInterceptTouchEvent(true);
                    if(action==MotionEvent.ACTION_UP){boolean claimed=imageSwipe.claimed();int direction=imageSwipe.finish(event.getX(),event.getY(),Math.max(dp(48),view.getWidth()*.15f));view.getParent().requestDisallowInterceptTouchEvent(false);if(direction!=0)shiftSlide(direction);else if(!claimed&&Math.abs(event.getX()-x)<=slop&&Math.abs(event.getY()-y)<=slop)view.performClick();}
                    if(action==MotionEvent.ACTION_CANCEL)view.getParent().requestDisallowInterceptTouchEvent(false);return true;
                }
            });
        }
        showSlide();
    }
    private void pickSlide(){String[] labels=new String[shownPictures.length()];for(int i=0;i<labels.length;i++)labels[i]=(i+1)+" · "+ClassroomExport.time(shownPictures.optJSONObject(i).optDouble("second"));UiDialogs.choiceDialog(this,"PPT",labels,pictureIndex,i->{pictureIndex=i;showSlide();});}
    private boolean shiftSlide(int direction){int next=pictureIndex+direction;if(next<0||next>=shownPictures.length())return false;pictureIndex=next;showSlide();return true;}
    private void openSlide(){if(slideImage==null||slideImage.getDrawable()==null||!ClassroomMonitor.allowed(this,account))return;JSONObject picture=shownPictures.optJSONObject(pictureIndex);if(picture==null)return;AttachmentViewerActivity.openClassroom(this,account,lesson,picture.optString("raw"));}
    private void showSlide(){
        if(shownPictures.length()==0||slideImage==null)return;pictureIndex=Math.max(0,Math.min(pictureIndex,shownPictures.length()-1));JSONObject picture=shownPictures.optJSONObject(pictureIndex);
        slidesBar.setText((pictureIndex+1)+" / "+shownPictures.length()+" · "+ClassroomExport.time(picture.optDouble("second")));
        previousSlide.setEnabled(pictureIndex>0);nextSlide.setEnabled(pictureIndex+1<shownPictures.length());
        slideImage.setContentDescription("PPT "+(pictureIndex+1)+"，点击全屏查看并保存");
        String name=picture.optString("raw");if(!name.matches("raw-[0-9]+\\.img"))return;File file=new File(pictureDirectory,name);int token=++slideGeneration;ImageView target=slideImage;String key=file.getPath();if(key.equals(decodedRaw)&&target.getDrawable()!=null)return;
        reader.execute(()->{android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(file.getPath(),options);options.inSampleSize=Math.max(1,Math.max(options.outWidth,options.outHeight)/1200);options.inJustDecodeBounds=false;android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeFile(file.getPath(),options);main.post(()->{if(token==slideGeneration&&target.isAttachedToWindow()&&ClassroomMonitor.allowed(this,account)){if(bitmap!=null){decodedRaw=key;UiMotion.swap(target,()->{target.setImageBitmap(bitmap);target.invalidateOutline();});}else{target.setImageDrawable(null);target.setContentDescription("PPT缓存暂不可用");}}else if(bitmap!=null)bitmap.recycle();});});
    }
    private void render(JSONArray segments,String label){
        status.setText(label);
        try{
            if(!pendingLatest&&pendingRestore<0)pendingRestore=scroll.getScrollY();
            for(int i=0;i<segments.length();i++){
                if(i==paragraphs.size()){TextView text=new TextView(this);text.setTextSize(16);text.setTextIsSelectable(true);text.setTextColor(UiStyle.colors(this).text);text.setLineSpacing(dp(3),1f);UiStyle.addSpaced(rows,text,0,12);paragraphs.add(text);}
                TextView text=paragraphs.get(i);String value=segments.getJSONObject(i).getString("text");if(!value.contentEquals(text.getText()))text.setText(value);
            }
            while(paragraphs.size()>segments.length())rows.removeView(paragraphs.remove(paragraphs.size()-1));
            if(segments.length()>0)positionCaptions();updateFollow();
        }catch(Exception failure){status.setText("字幕格式无法读取");}
    }
    private void positionCaptions(){
        if(restore!=null||mode!=0||rows.getChildCount()==0||(!pendingLatest&&pendingRestore<0))return;
        restore=()->{if(rows.isLayoutRequested())return true;scroll.getViewTreeObserver().removeOnPreDrawListener(restore);restore=null;
            int y=pendingLatest?Math.max(0,rows.getHeight()-scroll.getHeight()):Math.max(0,pendingRestore);pendingLatest=false;pendingRestore=-1;scroll.scrollTo(0,y);follow=atBottom();updateFollow();return true;
        };scroll.getViewTreeObserver().addOnPreDrawListener(restore);scroll.invalidate();
    }
    @Override protected void onResume(){super.onResume();if(lesson==null)return;if(!ClassroomMonitor.allowed(this,account)){finish();return;}active=true;generation++;signature="";main.post(poll);}
    @Override protected void onPause(){active=false;generation++;main.removeCallbacks(poll);imageSwipe.cancel();if(readingBody!=null)UiMotion.finishSwap(readingBody);if(slideImage!=null)UiMotion.finishSwap(slideImage);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putBoolean("follow",follow);state.putInt("mode",mode);state.putInt("picture",pictureIndex);if(repeat!=null)state.putBoolean("repeat",repeat.isChecked());if(scroll!=null)state.putInt("scroll",pendingRestore>=0?pendingRestore:scroll.getScrollY());}
    @Override protected void onDestroy(){if(restore!=null&&scroll!=null)scroll.getViewTreeObserver().removeOnPreDrawListener(restore);reader.shutdownNow();super.onDestroy();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
