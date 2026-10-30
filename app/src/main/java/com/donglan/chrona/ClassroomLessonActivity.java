package com.donglan.chrona;

import android.app.*;
import android.content.*;
import android.os.*;
import android.net.Uri;
import android.graphics.*;
import android.view.View;
import android.widget.*;
import com.donglan.chrona.classroom.*;
import com.donglan.chrona.ai.*;
import com.donglan.chrona.net.RequestControl;
import org.json.*;
import java.io.*;
import java.lang.ref.WeakReference;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Retained, account-bound single-lesson study workspace. */
public final class ClassroomLessonActivity extends Activity {
    private boolean firstDocumentPresentation;
    static final String EXTRA_LESSON = "classroom_lesson";
    static final String EXTRA_ACCOUNT = "classroom_account";
    private static final int EXPORT=2041;
    private Session s; private LinearLayout root,content,notes; private TextView status,overview,completion;
    private Button prepare,generate,cancel;private ImageButton export,delete;private int rendered,shownRevision=-1;private String detailKey="";
    private ProgressBar progressBar;
    private LinearLayout progressPanel;
    private TextView progressStage,progressCount,progressTime,progressDetail;
    private final Handler progressHandler=new Handler(Looper.getMainLooper());
    private boolean visible;
    private final Runnable progressTick=new Runnable(){@Override public void run(){updateProgress();if(visible)progressHandler.postDelayed(this,1000);}};
    private String shownRun="";
    private String shownParts="";
    private ZjuSettingsStore settings;
    private ClassroomMarkdownView documentView;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener preference=(p,k)->gate();
    static final class Session {
        final Context app;final String account;final ExecutorService worker=Executors.newSingleThreadExecutor();
        final android.content.SharedPreferences preferences;
        final android.content.SharedPreferences.OnSharedPreferenceChangeListener changes;
        final Handler main=new Handler(Looper.getMainLooper());
        WeakReference<ClassroomLessonActivity> owner=new WeakReference<>(null);
        volatile RequestControl control;volatile ClassroomClient client;volatile boolean busy,cancelRequested,deleting;
        volatile String message="";
        volatile ClassroomProgress progress;
        ClassroomNotifications notifications;
        volatile boolean notifyWork;
        boolean monitorOwned;
        Object batchOwner;
        boolean succeeded;
        boolean retryRequired;
        boolean summaryAuthorized;Boolean enhancedClassroom;
        Runnable completed;
        boolean updateQueued, detached;
        long startedAt;
        ClassroomLesson selected;File dir;JSONObject summary,materials,prepared;int imageLimit=ClassroomSummary.DEFAULT_IMAGES;
        ClassroomLesson pendingExport;Uri exportUri,shareUri;boolean localInitialized,exportInFlight;int revision;long ignoredSummaryUpdated=-1;
        Session(Context app,String account){this.app=app;this.account=account;preferences=new ZjuSettingsStore(app).prefs;changes=(p,k)->{if(!allowed()){cancel();if(notifications!=null)notifications.clear();}};preferences.registerOnSharedPreferenceChangeListener(changes);}
        boolean allowed(){ZjuSettingsStore p=new ZjuSettingsStore(app);return p.enabled()&&p.configured()&&account.equals(p.account());}
        void publish(){synchronized(this){if(updateQueued)return;updateQueued=true;}main.postDelayed(()->{synchronized(this){updateQueued=false;}ClassroomLessonActivity a=owner.get();if(a!=null&&!a.isDestroyed()&&allowed())a.update();},100);}
        void cancel(){cancelRequested=true;if(busy){progress=ClassroomProgress.waiting("正在停止","已完成内容会保留");publish();}RequestControl c=control;if(c!=null)c.cancel();ClassroomClient network=client;if(network!=null)network.cancel();}
        interface Work{void run()throws Exception;}
        void run(String stage,Work work){run(stage,false,work);}
        void run(String stage,boolean notices,Work work){if(busy)return;busy=true;succeeded=false;notifyWork=notices;cancelRequested=false;message="";startedAt=SystemClock.elapsedRealtime();progress=ClassroomProgress.waiting(stage,"");
            if(notices){if(notifications==null)notifications=new ClassroomNotifications(app,account,selected);notifications.progress(progress,true);}
            publish();Runnable execute=()->worker.execute(()->{
            boolean success=false;File leased=null;
            try{if(!allowed())return;if(dir!=null){ClassroomStore.begin(dir);leased=dir;}control=new RequestControl(60*60*1000L);if(cancelRequested)control.cancel();control.check();work.run();success=true;}
            catch(Exception e){message=ClassroomAutoSummary.failureDetail(e,cancelRequested||control!=null&&control.isCancelled());}
            finally{if(leased!=null)ClassroomStore.end(leased);if(client!=null){client.close();client=null;}if(control!=null){control.close();control=null;}if(exportInFlight){exportUri=null;exportInFlight=false;}boolean completed=success;main.post(()->{busy=false;succeeded=completed;
                if(notices&&notifications!=null){if(allowed())notifications.finished(message,completed);else notifications.clear();}notifyWork=false;if(notices)ClassroomWorkService.finished(this);publish();if(this.completed!=null)this.completed.run();disposeIfDetached();});}
        });
            if(notices){if(batchOwner!=null)ClassroomWorkService.submitBatch(batchOwner,this,execute);else if(monitorOwned)ClassroomWorkService.submitMonitor(this,execute);else ClassroomWorkService.submit(this,execute);}else execute.run();
        }
        void startFailed(){busy=false;succeeded=false;notifyWork=false;message="后台处理无法启动，请返回课堂重试";if(notifications!=null)notifications.finished(message,false);publish();if(completed!=null)completed.run();disposeIfDetached();}
        void disposeIfDetached(){if(!busy&&detached&&owner.get()==null){preferences.unregisterOnSharedPreferenceChangeListener(changes);worker.shutdown();}}
        ClassroomStore store()throws Exception{return new ClassroomStore(app,account);}
        ClassroomClient connect()throws Exception{if(!allowed())throw new IOException("学校账号已停用或更换");String[] pair=new ZjuSettingsStore(app).credentials();try{client=new ClassroomClient(pair[0],pair[1]);return client;}finally{Arrays.fill(pair,"");}}
        void prepareMaterials()throws Exception{prepared=ClassroomSummary.prepare(dir,selected,connect(),control,this::checkpoint);materials=ClassroomStore.read(new File(dir,"materials.json"));}
        void checkpoint(ClassroomProgress value){if(!allowed()){cancel();return;}progress=value;message=value.text();if(notifyWork&&notifications!=null)notifications.progress(value,false);try{if(dir!=null){JSONObject next=ClassroomStore.read(new File(dir,"summary.json"));if(next==null||next.optLong("updated")!=ignoredSummaryUpdated)summary=next;materials=ClassroomStore.read(new File(dir,"materials.json"));}}catch(Exception ignored){}publish();}
    }
    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this); super.onCreate(state); firstDocumentPresentation=state==null||state.getBoolean("first_document_presentation",false);settings=new ZjuSettingsStore(this);
        String notificationAccount=getIntent().getStringExtra(EXTRA_ACCOUNT);
        if(notificationAccount!=null&&!notificationAccount.equals(settings.account())){ClassroomNotifications.clearInactive(this);finish();return;}
        Object retained=getLastNonConfigurationInstance();
        ClassroomLesson incoming;
        try{incoming=ClassroomLesson.fromJson(new JSONObject(getIntent().getStringExtra(EXTRA_LESSON)));}
        catch(Exception error){Feedback.show(this,"课堂信息无效");finish();return;}
        Session running=ClassroomWorkService.find(settings.account(),incoming);
        s=running!=null?running:retained instanceof Session ? (Session)retained : new Session(getApplicationContext(),settings.account());
        s.detached=false;s.owner=new WeakReference<>(this); if(!gate())return;
        if(running==null&&!(retained instanceof Session)) {
            try {
                s.selected=ClassroomLesson.fromJson(new JSONObject(getIntent().getStringExtra(EXTRA_LESSON)));
                if(state!=null) {
                    String json=state.getString("export_lesson");
                    if(json!=null)s.pendingExport=ClassroomLesson.fromJson(new JSONObject(json));
                    String uri=state.getString("export_uri"); if(uri!=null)s.exportUri=Uri.parse(uri);
                    String share=state.getString("share_uri");if(share!=null)s.shareUri=Uri.parse(share);
                }
            } catch(Exception error) { Feedback.show(this,"课堂信息无效"); finish(); return; }
        }
        root=SettingsPageLayout.content(this);
        export=UiStyle.navigationIcon(this,R.drawable.ic_week_export,"导出课堂总结 ZIP",()->{if(gate())export();});
        HeaderActionGroup headerActions=new HeaderActionGroup(this,48);headerActions.addAction(export,48);
        headerActions.addAction(UiStyle.navigationIcon(this,R.drawable.ic_eye,"课堂监视",()->{if(gate())ClassroomMonitorUi.open(this,s.selected);}),48);
        delete=UiStyle.navigationIcon(this,R.drawable.ic_delete,"删除本地课堂总结",this::confirmDelete);headerActions.addAction(delete,48);headerActions.finish();
        SettingsPageLayout.header(this,root,"课堂总结",headerActions);
        status=text(root,"",14,false);
        progressPanel=new LinearLayout(this);progressPanel.setOrientation(LinearLayout.VERTICAL);
        progressPanel.setPadding(dp(16),dp(12),dp(16),dp(12));UiStyle.card(progressPanel);UiStyle.addSpaced(root,progressPanel,0,12);
        LinearLayout heading=new LinearLayout(this);heading.setGravity(android.view.Gravity.CENTER_VERTICAL);progressPanel.addView(heading);
        progressStage=new TextView(this);progressStage.setTextSize(16);progressStage.setTextColor(UiStyle.colors(this).text);progressStage.setMaxLines(2);progressStage.setGravity(android.view.Gravity.CENTER_VERTICAL);heading.addView(progressStage,new LinearLayout.LayoutParams(0,dp(48),1));
        progressCount=new TextView(this);progressCount.setTextSize(14);progressCount.setSingleLine(true);progressCount.setTextColor(UiStyle.colors(this).primary);progressCount.setTypeface(Typeface.MONOSPACE);progressCount.setGravity(android.view.Gravity.END|android.view.Gravity.CENTER_VERTICAL);heading.addView(progressCount,new LinearLayout.LayoutParams(dp(100),dp(48)));
        progressBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(UiStyle.colors(this).primary));
        progressBar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(UiStyle.colors(this).primary));
        progressBar.setContentDescription("课堂处理进度");progressPanel.addView(progressBar,new LinearLayout.LayoutParams(-1,dp(6)));
        LinearLayout detailRow=new LinearLayout(this);detailRow.setGravity(android.view.Gravity.CENTER_VERTICAL);progressPanel.addView(detailRow);
        progressDetail=new TextView(this);progressDetail.setTextSize(12);progressDetail.setTextColor(UiStyle.colors(this).text);progressDetail.setMaxLines(2);progressDetail.setGravity(android.view.Gravity.CENTER_VERTICAL);progressDetail.setEllipsize(android.text.TextUtils.TruncateAt.END);detailRow.addView(progressDetail,new LinearLayout.LayoutParams(0,dp(44),1));
        progressTime=new TextView(this);progressTime.setTextSize(12);progressTime.setTypeface(Typeface.MONOSPACE);progressTime.setTextColor(UiStyle.colors(this).text);progressTime.setGravity(android.view.Gravity.END|android.view.Gravity.CENTER_VERTICAL);detailRow.addView(progressTime,new LinearLayout.LayoutParams(dp(90),dp(44)));
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);root.addView(content);
        SettingsPageLayout.show(this,root,state==null);
        if(!s.localInitialized&&!s.busy)loadLocal();
        update();
    }
    @Override public Object onRetainNonConfigurationInstance(){if(s!=null&&s.owner.get()==this)s.owner.clear();return s;}
    @Override protected void onResume(){super.onResume();visible=true;if(s!=null){s.owner=new WeakReference<>(this);s.detached=false;}progressHandler.post(progressTick);if(settings!=null){settings.prefs.registerOnSharedPreferenceChangeListener(preference);if(gate())update();}}
    @Override protected void onPause(){visible=false;progressHandler.removeCallbacks(progressTick);super.onPause();if(settings!=null)settings.prefs.unregisterOnSharedPreferenceChangeListener(preference);}
    @Override protected void onDestroy(){if(documentView!=null)documentView.dispose();if(s!=null&&s.owner.get()==this){s.owner.clear();if(!isChangingConfigurations()){s.detached=true;s.disposeIfDetached();}}super.onDestroy();}
    private boolean gate(){if(s==null||s.allowed())return true;s.cancel();if(s.notifications!=null)s.notifications.clear();if(content!=null)content.removeAllViews();Feedback.show(this,"请启用折姜星辰并保存账号");finish();return false;}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==ClassroomNotifications.PERMISSION_REQUEST&&s!=null&&s.allowed()&&s.notifications!=null)s.notifications.repost();}
    private void loadLocal() {
        Session job=s;
        job.run("读取本节课堂",()->{
            job.message="正在读取本节课堂"; job.publish();
            job.dir=job.store().lesson(job.selected);
            job.summary=ClassroomStore.read(new File(job.dir,"summary.json"));
            job.materials=ClassroomStore.read(new File(job.dir,"materials.json"));
            JSONObject candidates=ClassroomStore.read(new File(job.dir,"candidates.json"));
            JSONObject automatic=ClassroomMonitorStore.auto(new ClassroomMonitorStore(job.app,job.account).read(),job.selected);
            if(automatic!=null)job.imageLimit=automatic.optInt("imageLimit",0);
            job.prepared=job.materials!=null&&candidates!=null
                    ? new JSONObject().put("segments",job.materials.getJSONArray("segments").length())
                        .put("raw",job.materials.optInt("raw_images"))
                        .put("candidates",candidates.getJSONArray("images").length()) : null;
            JSONObject run=ClassroomStore.read(new File(job.dir,"run.json"));
            if(run!=null&&run.optJSONObject("image_catalogue")!=null)job.imageLimit=Math.max(0,Math.min(ClassroomSummary.MAX_IMAGES,run.getJSONObject("image_catalogue").optInt("limit",job.imageLimit)));
            if(run!=null&&run.optJSONObject("execution")!=null)job.imageLimit=Math.max(0,Math.min(ClassroomSummary.MAX_IMAGES,run.getJSONObject("execution").optInt("imageLimit",job.imageLimit)));
            job.retryRequired=ClassroomAutoSummary.needsAuthorization(job.summary,run)||(job.summary==null||!job.summary.optBoolean("complete"))&&automatic!=null&&java.util.Set.of("failed","interrupted").contains(automatic.optString("state"));
            job.localInitialized=true; job.message=restoredFailure(job.summary,run);
        });
    }
    static String restoredFailure(JSONObject summary,JSONObject run){
        if(run==null)return "";
        JSONObject execution=run.optJSONObject("execution");
        if(execution!=null&&!"complete".equals(execution.optString("state")))return execution.optString("detail").isBlank()?"上次总结未完成，请手动授权继续":execution.optString("detail");
        if(run.has("final"))return "";
        JSONObject validation=run.optJSONObject("final_validation");if(validation==null)return "";
        String status=validation.optString("status");
        if(!status.equals("failed")&&!status.equals("cancelled"))return "";
        if(summary!=null&&!summary.optString("run").equals(run.optString("run")))return "";
        return validation.optString("error");
    }

    private void update() {
        if(content==null||!gate())return;
        if(s.localInitialized&&!s.busy&&!s.exportInFlight&&s.exportUri!=null){writeExport();return;}
        updateProgress();
        if(s.selected!=null) {
            if(detailKey.isEmpty()){detailKey=s.selected.courseId+"/"+s.selected.id;showDetail();}
            updateDetail();
        }
        if(visible&&!s.busy&&s.shareUri!=null){Uri uri=s.shareUri;s.shareUri=null;ExportShare.show(this,uri,"application/zip");}
    }
    private void updateProgress(){
        if(status==null||s==null)return;
        ClassroomProgress value=s.progress;
        String label=s.busy?"":s.message;
        setTextIfChanged(status,label);status.setVisibility(label.isEmpty()?View.GONE:View.VISIBLE);
        progressPanel.setVisibility(s.busy?View.VISIBLE:View.GONE);
        if(s.busy&&value!=null){setTextIfChanged(progressStage,value.stage);setTextIfChanged(progressCount,value.total>0?value.completed+"/"+value.total:"");setTextIfChanged(progressDetail,value.detail);setTextIfChanged(progressTime,ClassroomExport.time((SystemClock.elapsedRealtime()-s.startedAt)/1000.0));progressBar.setIndeterminate(value.total==0);if(value.total>0){progressBar.setMax(value.total);progressBar.setProgress(value.completed);}if(Build.VERSION.SDK_INT>=30)progressBar.setStateDescription(value.text());}
    }
    private static void setTextIfChanged(TextView view,String text){if(!text.contentEquals(view.getText()))view.setText(text);}
    private static String clock(ClassroomLesson l){return Instant.ofEpochMilli(l.startsAt).atZone(ClassroomLesson.ZONE).toLocalTime().toString();}

    private void showDetail(){content.removeAllViews();rendered=0;
        LinearLayout heading=new LinearLayout(this);heading.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView courseTitle=new TextView(this);courseTitle.setText(s.selected.course);courseTitle.setTextSize(21);UiStyle.title(courseTitle);
        heading.addView(courseTitle,new LinearLayout.LayoutParams(0,-2,1));
        content.addView(heading);text(content,s.selected.date()+" "+clock(s.selected)+" · "+s.selected.title,14,false);
        completion=text(content,"",15,false);LinearLayout actions=new LinearLayout(this);prepare=action(actions,"总结",this::prepare);generate=action(actions,"生成总结",()->{if(s.retryRequired||s.summary!=null)confirmGeneration();else prepare();});content.addView(actions);
        cancel=button(content,"停止",()->s.cancel(),false);
        overview=text(content,"",17,false);notes=new LinearLayout(this);notes.setOrientation(LinearLayout.VERTICAL);content.addView(notes);
    }
    private void updateDetail(){prepare.setEnabled(!s.busy&&s.selected.selectable(System.currentTimeMillis()));generate.setEnabled(!s.busy&&s.prepared!=null);generate.setText(s.summary==null?"生成总结":s.summary.optBoolean("complete")?"重新生成":"继续整理");export.setEnabled(!s.busy&&s.summary!=null);cancel.setVisibility(s.busy?View.VISIBLE:View.GONE);
        prepare.setVisibility(s.prepared==null?View.VISIBLE:View.GONE);generate.setVisibility(s.prepared!=null?View.VISIBLE:View.GONE);
        delete.setEnabled(!s.deleting&&s.localInitialized&&(s.summary!=null||s.materials!=null||s.busy));cancel.setVisibility(s.busy&&!s.deleting?View.VISIBLE:View.GONE);
        if(s.prepared!=null)completion.setText("字幕就绪 · "+s.prepared.optInt("segments")+" 条\n截图 "+s.prepared.optInt("raw")+" → "+s.prepared.optInt("candidates")+" 张");else completion.setText(s.selected.state(System.currentTimeMillis()));
        overview.setVisibility(View.GONE);
        JSONObject summary=s.summary;
        String next=summary==null?"":summary.toString();
        if(next.equals(shownParts)&&shownRevision==s.revision)return;
        shownParts=next;shownRevision=s.revision;
        if(documentView!=null){notes.removeView(documentView);documentView.dispose();documentView=null;}
        notes.removeAllViews();
        if(summary==null)return;
        try{
            documentView=new ClassroomMarkdownView(this,s.dir,summary,new ClassroomMarkdownView.Actions(){
                @Override public void assignment(int index){extractAssignment(index);}
                @Override public void image(String name){if(gate())AttachmentViewerActivity.openClassroomSummary(ClassroomLessonActivity.this,s.account,s.selected,name);}
                @Override public Uri imageUri(String name){return ImageTransferProvider.classroomSummary(ClassroomLessonActivity.this,s.account,s.selected,name);}
            });
            if(firstDocumentPresentation)documentView.setVisibility(View.INVISIBLE);
            notes.addView(documentView);
            ClassroomMarkdownView presented=documentView;
            presented.onReady(()->{
                if(documentView==presented&&firstDocumentPresentation&&!isFinishing()&&!isDestroyed()){
                    firstDocumentPresentation=false;presented.setVisibility(View.VISIBLE);UiMotion.enterPage(presented);
                }
            });
        }catch(Exception error){text(notes,"课堂正文无法显示："+error.getMessage(),16,false);}
    }
    private void extractAssignment(int index){
        if(!gate()||s.summary==null||index<0)return;
        JSONArray parts=s.summary.optJSONArray("parts");if(parts==null)return;
        for(int p=0;p<parts.length();p++){
            JSONArray tasks=parts.optJSONObject(p).optJSONArray("assignments");if(tasks==null)continue;
            if(index>=tasks.length()){index-=tasks.length();continue;}
            String classroomTime=Instant.ofEpochMilli(s.selected.startsAt).atZone(ClassroomLesson.ZONE).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            String prefill=s.selected.course+"\n课堂时间："+classroomTime+"（Asia/Shanghai）\n老师在这节课提出的安排："+tasks.optJSONObject(index).optString("text");
            UiDialogs.confirmDialog(this,"提取课堂安排",prefill,"继续",()->startActivity(new Intent(this,MainActivity.class).setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,prefill)));return;
        }
    }
    private void prepare(){if(s.retryRequired){confirmGeneration();return;}ClassroomNotifications.requestPermission(this);Session job=s;try{job.imageLimit=ClassroomAutoSummary.imageLimit(job.app,job.account);}catch(Exception failure){Feedback.show(this,"无法读取总结图片设置");return;}job.run("下载并总结课堂",true,()->ClassroomAutoSummary.downloadAndSummarize(job));}
    private void confirmGeneration(){Session job=s;job.run("检查 AI 服务配置",()->{AiSettingsStore store=new AiSettingsStore(job.app);AiSettings ai=ClassroomAutoSummary.settings.load(job.app);if(ai==null)throw new IOException("请先在 AI 设置中配置服务");boolean unsupported=store.isImageUnsupported(ai);job.message="";job.main.post(()->{ClassroomLessonActivity a=job.owner.get();if(a!=null&&job.allowed())a.generationDialog(ai,unsupported);});});}
    private void generationDialog(AiSettings ai,boolean unsupported){
        Dialog dialog=new Dialog(this);LinearLayout panel=UiDialogs.dialogPanel(this,"生成课堂总结");LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        text(body,ai.model+"\n"+ai.baseUrl,15,false);
        CheckBox upload=new CheckBox(this);upload.setText("上传图片 · 最多 "+ClassroomSummary.MAX_IMAGES+" 张");UiStyle.toggle(upload);upload.setChecked(!unsupported&&s.imageLimit>0);upload.setEnabled(!unsupported);body.addView(upload);
        if(unsupported)text(body,"当前模型仅支持文字",14,false);
        UiDialogs.dialogBody(panel,body);
        panel.addView(UiDialogs.actionRow(this,dialog,"上传并生成",()->{dialog.dismiss();startGeneration(ai,upload.isChecked()?ClassroomSummary.MAX_IMAGES:0);}));
        UiDialogs.showFloatingDialog(dialog,panel);
    }
    private void startGeneration(AiSettings ai,int limit){ClassroomNotifications.requestPermission(this);Session job=s;SchedulePreferences preferences=SchedulePreferences.load(this);String prompt=preferences.classroomPrompt;job.enhancedClassroom=preferences.enhancedClassroom;job.imageLimit=limit;job.run("准备整理课堂",true,()->{if(!new File(job.dir,"candidates.json").isFile())job.prepareMaterials();ClassroomAutoSummary.summarize(job,ai,prompt,true);});}
    private void confirmDelete(){
        if(!gate())return;
        if(s.busy||ClassroomLiveTranscripts.active(s.account,s.selected)){Feedback.show(this,"本节课堂正在处理，请先停止后再删除");return;}
        if(s.summary==null&&s.materials==null)return;
        UiDialogs.confirmDialog(this,"删除本地课堂资料","将删除本节已保存的总结、字幕和图片。学校原课堂及后续课堂监视保留。","删除",this::deleteLocal);
    }
    private void deleteLocal(){
        if(!gate()||s.busy){Feedback.show(this,"本节课堂正在处理，请先停止后再删除");return;}
        Session job=s;job.busy=true;job.deleting=true;job.message="";job.progress=ClassroomProgress.waiting("删除本地课堂","");job.startedAt=SystemClock.elapsedRealtime();update();
        job.worker.execute(()->{
            boolean removed=false;
            try{
                job.store().deleteLesson(job.selected,()->{
                    if(!job.allowed())throw new IOException("学校账号已停用或更换");
                    if(ClassroomLiveTranscripts.active(job.account,job.selected))throw new IOException("本节课堂正在处理，请先停止后再删除");
                    new ClassroomMonitorStore(job.app,job.account).deleted(job.selected);
                });removed=true;
            }catch(Exception failure){job.message=failure.getMessage()==null?"删除失败，请重试":failure.getMessage();}
            boolean success=removed;job.main.post(()->{job.busy=false;job.deleting=false;ClassroomLessonActivity a=job.owner.get();
                if(success){job.summary=null;job.materials=null;job.prepared=null;if(a!=null&&!a.isDestroyed()){Feedback.show(a,"本地课堂总结已删除");a.setResult(RESULT_OK);a.finish();}}
                else job.publish();job.disposeIfDetached();});
        });
    }
    private void export(){s.pendingExport=s.selected;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").putExtra(Intent.EXTRA_TITLE,"课堂总结-"+s.selected.date()+".zip");try{startActivityForResult(intent,EXPORT);}catch(Exception e){Feedback.show(this,"无法打开文件保存器");}}
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("first_document_presentation",firstDocumentPresentation);super.onSaveInstanceState(state);try{if(s.pendingExport!=null)state.putString("export_lesson",s.pendingExport.json().toString());if(s.exportUri!=null)state.putString("export_uri",s.exportUri.toString());if(s.shareUri!=null)state.putString("share_uri",s.shareUri.toString());}catch(Exception ignored){}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=EXPORT||result!=RESULT_OK||data==null||data.getData()==null||!gate())return;s.exportUri=data.getData();
        boolean read=(data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0,write=(data.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0;
        try{if(read&&write)getContentResolver().takePersistableUriPermission(s.exportUri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            else if(write)getContentResolver().takePersistableUriPermission(s.exportUri,Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            else if(read)getContentResolver().takePersistableUriPermission(s.exportUri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }catch(SecurityException ignored){}if(s.localInitialized&&!s.busy)writeExport();}
    private void writeExport(){Session job=s;if(!job.localInitialized||job.busy||job.exportInFlight)return;Uri uri=job.exportUri;ClassroomLesson lesson=job.pendingExport;if(lesson==null){job.exportUri=null;return;}job.exportInFlight=true;job.run("生成导出文件",()->{try{File dir=job.store().lesson(lesson);JSONObject summary=ClassroomStore.read(new File(dir,"summary.json"));if(summary==null)throw new IOException("总结缓存不存在");try(OutputStream out=job.app.getContentResolver().openOutputStream(uri,"w")){if(out==null)throw new IOException("无法写入文件");ClassroomExport.write(dir,summary,out,job::checkpoint);}job.message="ZIP 已导出";job.pendingExport=null;job.shareUri=uri;}finally{job.exportUri=null;job.exportInFlight=false;}});}
    private TextView text(LinearLayout parent,String value,int size,boolean title){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextIsSelectable(true);if(title)UiStyle.title(v);else v.setTextColor(UiStyle.colors(this).text);UiStyle.addSpaced(parent,v,6,10);return v;}
    private Button button(LinearLayout parent,String title,Runnable work,boolean primary){Button b=new Button(this);b.setText(title);b.setAllCaps(false);UiStyle.button(b,primary);b.setOnClickListener(v->{if(gate())work.run();});UiStyle.addSpaced(parent,b,4,6);return b;}
    private Button action(LinearLayout row,String title,Runnable work){Button b=new Button(this);b.setText(title);b.setTextSize(14);UiStyle.button(b,false);b.setOnClickListener(v->{if(gate())work.run();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(2),dp(4),dp(2),dp(6));row.addView(b,p);return b;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
