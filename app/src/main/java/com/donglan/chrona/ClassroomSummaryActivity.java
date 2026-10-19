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
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.*;

/** Retained, account-bound single-lesson study workspace. */
public final class ClassroomSummaryActivity extends Activity {
    private static final int EXPORT=2041;
    private static final ExecutorService PICTURES=Executors.newFixedThreadPool(2);
    private Session s; private LinearLayout root,content,notes; private TextView status,overview,completion;
    private Button prepare,generate,export,cancel,browseRefresh,browseCancel;private int rendered,shownRevision=-1;private String detailKey="";
    private LinearLayout browseBody,listBody;private DockNavigationLayout navigation;
    private int shownMode=-1,listCount;private List<ClassroomLesson> shownCatalog;private List<JSONObject> shownHistory;private LocalDate shownWeek;private String shownRun="";
    private ZjuSettingsStore settings;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener preference=(p,k)->gate();
    private static final class Session {
        final Context app;final String account;final ExecutorService worker=Executors.newSingleThreadExecutor();
        final android.content.SharedPreferences preferences;
        final android.content.SharedPreferences.OnSharedPreferenceChangeListener changes;
        final Handler main=new Handler(Looper.getMainLooper());
        WeakReference<ClassroomSummaryActivity> owner=new WeakReference<>(null);
        volatile RequestControl control;volatile ClassroomClient client;volatile boolean busy,cancelRequested;
        volatile String message="";List<ClassroomLesson> catalog=new ArrayList<>();List<JSONObject> history=new ArrayList<>();
        ClassroomLesson selected;File dir;JSONObject summary,materials,prepared;int mode,page=50,imageLimit=20;
        LocalDate week=LocalDate.now(ClassroomLesson.ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        ClassroomLesson pendingExport;Uri exportUri;boolean localInitialized,exportInFlight;int revision;long ignoredSummaryUpdated=-1;
        Session(Context app,String account){this.app=app;this.account=account;preferences=new ZjuSettingsStore(app).prefs;changes=(p,k)->{if(!allowed())cancel();};preferences.registerOnSharedPreferenceChangeListener(changes);}
        boolean allowed(){ZjuSettingsStore p=new ZjuSettingsStore(app);return p.enabled()&&p.configured()&&account.equals(p.account());}
        void publish(){main.post(()->{ClassroomSummaryActivity a=owner.get();if(a!=null&&!a.isDestroyed()&&allowed())a.update();});}
        void cancel(){cancelRequested=true;RequestControl c=control;if(c!=null)c.cancel();ClassroomClient network=client;if(network!=null)network.cancel();}
        interface Work{void run()throws Exception;}
        void run(Work work){if(busy)return;busy=true;cancelRequested=false;message="处理中";publish();worker.execute(()->{
            try{if(!allowed())return;control=new RequestControl(60*60*1000L);if(cancelRequested)control.cancel();control.check();work.run();}
            catch(Exception e){message=control!=null&&control.isCancelled()?"已停止，已完成内容已保存":(e.getMessage()==null?"操作失败，请重试":e.getMessage());}
            finally{if(client!=null){client.close();client=null;}if(control!=null){control.close();control=null;}if(exportInFlight){exportUri=null;exportInFlight=false;}busy=false;publish();}
        });}
        ClassroomStore store()throws Exception{return new ClassroomStore(app,account);}
        ClassroomClient connect()throws Exception{if(!allowed())throw new IOException("学校账号已停用或更换");String[] pair=new ZjuSettingsStore(app).credentials();try{client=new ClassroomClient(pair[0],pair[1]);return client;}finally{Arrays.fill(pair,"");}}
        void checkpoint(String text){if(!allowed()){cancel();return;}message=text;try{if(dir!=null){JSONObject next=ClassroomStore.read(new File(dir,"summary.json"));if(next==null||next.optLong("updated")!=ignoredSummaryUpdated)summary=next;materials=ClassroomStore.read(new File(dir,"materials.json"));}}catch(Exception ignored){}publish();}
    }
    @Override protected void onCreate(Bundle state){ThemeStore.apply(this);super.onCreate(state);settings=new ZjuSettingsStore(this);
        Object retained=getLastNonConfigurationInstance();s=retained instanceof Session?(Session)retained:new Session(getApplicationContext(),settings.account());
        s.owner=new WeakReference<>(this);if(!gate())return;
        if(!(retained instanceof Session)&&state!=null){String json=state.getString("export_lesson");try{if(json!=null)s.pendingExport=ClassroomLesson.fromJson(new JSONObject(json));String uri=state.getString("export_uri");if(uri!=null)s.exportUri=Uri.parse(uri);}catch(Exception ignored){}}
        root=SettingsPageLayout.content(this);SettingsPageLayout.header(this,root,"课堂总结");
        status=text(root,"",14,false);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);root.addView(content);
        SettingsPageLayout.show(this,root,state==null);
        if(!s.localInitialized&&!s.busy)loadLocal();
        update();
    }
    @Override public Object onRetainNonConfigurationInstance(){s.owner.clear();return s;}
    @Override protected void onResume(){super.onResume();if(settings!=null){settings.prefs.registerOnSharedPreferenceChangeListener(preference);gate();}}
    @Override protected void onPause(){super.onPause();if(settings!=null)settings.prefs.unregisterOnSharedPreferenceChangeListener(preference);}
    @Override protected void onDestroy(){if(s!=null&&isFinishing()){s.cancel();s.preferences.unregisterOnSharedPreferenceChangeListener(s.changes);s.owner.clear();s.worker.shutdown();}super.onDestroy();}
    private boolean gate(){if(s==null||s.allowed())return true;s.cancel();if(content!=null)content.removeAllViews();Feedback.show(this,"请启用折姜星辰并保存账号");finish();return false;}
    private void loadLocal(){Session job=s;job.run(()->{ClassroomStore store;
        try{store=job.store();job.catalog=store.catalog();job.history=store.history();job.message="";}
        finally{job.localInitialized=true;}
        job.publish();if(job.catalog.isEmpty()&&job.exportUri==null){job.message="正在刷新课堂";job.publish();List<ClassroomLesson> list=job.connect().lessons();job.control.check();if(job.allowed()){store.catalog(list);job.catalog=list;job.message="";}}});}
    private void refresh(){Session job=s;job.run(()->{job.message="正在刷新课堂";job.publish();List<ClassroomLesson> list=job.connect().lessons();job.control.check();if(!job.allowed())return;job.store().catalog(list);job.catalog=list;job.message="";});}
    private void update(){if(content==null||!gate())return;if(!s.busy&&!s.localInitialized){loadLocal();return;}if(s.localInitialized&&!s.busy&&!s.exportInFlight&&s.exportUri!=null){writeExport();return;}status.setText(s.message);status.setVisibility(s.message.isEmpty()?View.GONE:View.VISIBLE);
        if(s.selected!=null){String key=s.selected.courseId+"/"+s.selected.id;if(!detailKey.equals(key)){detailKey=key;showDetail();}updateDetail();return;}
        if(navigation==null||!detailKey.isEmpty()){detailKey="";content.removeAllViews();shownMode=-1;
            navigation=new DockNavigationLayout(this,index->{s.mode=index;s.page=50;update();},()->{});
            String[] tabs={"课表","课堂","历史"};for(String tab:tabs){TextView v=new TextView(this);v.setText(tab);v.setTextSize(15);v.setGravity(android.view.Gravity.CENTER);v.setMinHeight(dp(48));UiStyle.title(v);navigation.addItem(v,new LinearLayout.LayoutParams(0,-2,1));}navigation.setSelectedIndex(s.mode);content.addView(navigation.motionHost());
            browseBody=new LinearLayout(this);browseBody.setOrientation(LinearLayout.VERTICAL);content.addView(browseBody);browseRefresh=button(content,"刷新课堂",this::refresh,false);browseCancel=button(content,"停止",()->s.cancel(),false);
        }
        browseRefresh.setEnabled(!s.busy);browseCancel.setVisibility(s.busy?View.VISIBLE:View.GONE);
        boolean changed=shownMode!=s.mode||shownCatalog!=s.catalog||shownHistory!=s.history||!s.week.equals(shownWeek);
        if(changed){browseBody.removeAllViews();listCount=0;listBody=null;shownMode=s.mode;shownCatalog=s.catalog;shownHistory=s.history;shownWeek=s.week;
            if(s.mode==0){button(browseBody,s.week.toString(),()->new DatePickerDialog(this,(v,y,m,d)->{s.week=LocalDate.of(y,m+1,d).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));update();},s.week.getYear(),s.week.getMonthValue()-1,s.week.getDayOfMonth()).show(),false);LinearLayout nav=new LinearLayout(this);action(nav,"上一周",()->{s.week=s.week.minusWeeks(1);update();});action(nav,"下一周",()->{s.week=s.week.plusWeeks(1);update();});browseBody.addView(nav);browseBody.addView(new ClassroomLessonPickerView(this,s.catalog,s.week,this::open));}
            else if(s.mode==1){listBody=new LinearLayout(this);listBody.setOrientation(LinearLayout.VERTICAL);browseBody.addView(listBody);button(browseBody,"加载更多",()->{s.page+=50;update();},false);}
            else{for(JSONObject h:s.history)try{ClassroomLesson l=ClassroomLesson.fromJson(h.getJSONObject("lesson"));button(browseBody,l.course+"\n"+l.date()+" · "+(h.optBoolean("complete")?"已完成":"未完成"),()->open(l),false);}catch(Exception ignored){}if(s.history.isEmpty())text(browseBody,"暂无历史总结",16,false);}
        }
        if(s.mode==1){List<ClassroomLesson> recent=new ArrayList<>();for(ClassroomLesson l:s.catalog)if(l.startsAt<=System.currentTimeMillis())recent.add(l);recent.sort((a,b)->Long.compare(b.startsAt,a.startsAt));for(int i=listCount;i<Math.min(s.page,recent.size());i++){ClassroomLesson l=recent.get(i);Button b=button(listBody,l.date()+" "+clock(l)+" · "+l.state(System.currentTimeMillis())+"\n"+l.course+" · "+l.title,()->open(l),false);b.setEnabled(l.selectable(System.currentTimeMillis()));listCount++;}browseBody.getChildAt(1).setVisibility(listCount<recent.size()?View.VISIBLE:View.GONE);if(changed&&recent.isEmpty())text(listBody,"暂无课堂",16,false);}
    }
    private static String clock(ClassroomLesson l){return Instant.ofEpochMilli(l.startsAt).atZone(ClassroomLesson.ZONE).toLocalTime().toString();}
    private void open(ClassroomLesson lesson){if(s.busy)return;Session job=s;job.run(()->{job.selected=lesson;job.dir=job.store().lesson(lesson);job.summary=ClassroomStore.read(new File(job.dir,"summary.json"));job.materials=ClassroomStore.read(new File(job.dir,"materials.json"));JSONObject candidates=ClassroomStore.read(new File(job.dir,"candidates.json"));job.prepared=job.materials!=null&&candidates!=null?new JSONObject().put("segments",job.materials.getJSONArray("segments").length()).put("raw",job.materials.optInt("raw_images")).put("candidates",candidates.getJSONArray("images").length()):null;job.message="";});}
    private void showDetail(){content.removeAllViews();rendered=0;button(content,"返回课堂",()->{if(s.busy){UiStyle.confirmDialog(this,"停止整理？","已完成内容会保留。","停止",()->s.cancel());return;}s.selected=null;loadLocal();update();},false);
        text(content,s.selected.course,21,true);text(content,s.selected.date()+" "+clock(s.selected)+" · "+s.selected.title,14,false);
        completion=text(content,"",15,false);LinearLayout actions=new LinearLayout(this);prepare=action(actions,"获取素材",this::prepare);generate=action(actions,"生成总结",this::confirmGeneration);content.addView(actions);
        export=button(content,"导出 ZIP",this::export,false);cancel=button(content,"停止",()->s.cancel(),false);
        overview=text(content,"",17,false);notes=new LinearLayout(this);notes.setOrientation(LinearLayout.VERTICAL);content.addView(notes);
    }
    private void updateDetail(){prepare.setEnabled(!s.busy&&s.selected.selectable(System.currentTimeMillis()));generate.setEnabled(!s.busy&&s.prepared!=null);generate.setText(s.summary!=null&&!s.summary.optBoolean("complete")?"继续整理":"生成总结");export.setEnabled(!s.busy&&s.summary!=null);cancel.setVisibility(s.busy?View.VISIBLE:View.GONE);
        if(s.prepared!=null)completion.setText("字幕就绪 · "+s.prepared.optInt("segments")+" 条\n截图 "+s.prepared.optInt("raw")+" → "+s.prepared.optInt("candidates")+" 张");else completion.setText(s.selected.state(System.currentTimeMillis()));
        if(shownRevision!=s.revision){notes.removeAllViews();rendered=0;overview.setText("");shownRevision=s.revision;}
        JSONObject summary=s.summary;if(summary==null)return;if(!shownRun.equals(summary.optString("run"))){notes.removeAllViews();rendered=0;shownRun=summary.optString("run");}JSONArray parts=summary.optJSONArray("parts");if(parts==null)return;
        overview.setText((summary.optBoolean("complete")?"重点概览":"未完成 · "+parts.length()+" / "+summary.optInt("total_parts")+" 段")+"\n"+summary.optString("overview")+"\n未分析图片 "+Math.max(0,summary.optInt("candidate_images")-summary.optJSONArray("images").length())+" 张");
        if(parts.length()<rendered){notes.removeAllViews();rendered=0;}
        while(rendered<parts.length()){JSONObject part=parts.optJSONObject(rendered++);if(part==null)continue;if(!summary.optBoolean("complete"))text(notes,part.optString("overview"),16,true);JSONArray items=part.optJSONArray("notes");if(items!=null)for(int i=0;i<items.length();i++){JSONObject n=items.optJSONObject(i);if(n==null)continue;text(notes,n.optString("title"),19,true);text(notes,n.optString("text"),16,false);sources(notes,n.optJSONArray("sources"));JSONArray images=n.optJSONArray("images");if(images!=null)for(int p=0;p<images.length();p++)image(notes,images.optString(p));}
            JSONArray tasks=part.optJSONArray("assignments");if(tasks!=null)for(int i=0;i<tasks.length();i++){JSONObject task=tasks.optJSONObject(i);if(task==null)continue;text(notes,"课堂安排",18,true);text(notes,task.optString("text"),16,false);sources(notes,task.optJSONArray("sources"));String taskText=task.optString("text");ClassroomLesson lesson=s.selected;String classroomTime=Instant.ofEpochMilli(lesson.startsAt).atZone(ClassroomLesson.ZONE).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));String prefill=lesson.course+"\n课堂时间："+classroomTime+"（Asia/Shanghai）\n老师在这节课提出的安排："+taskText;button(notes,"提取为日程",()->UiStyle.confirmDialog(this,"提取课堂安排",prefill,"继续",()->startActivity(new Intent(this,MainActivity.class).setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,prefill))),false);}}
    }
    private void prepare(){Session job=s;job.run(()->{job.prepared=ClassroomSummary.prepare(job.dir,job.selected,job.connect(),job.control,job::checkpoint);job.materials=ClassroomStore.read(new File(job.dir,"materials.json"));job.message="素材已就绪";});}
    private void confirmGeneration(){Session job=s;job.run(()->{AiSettingsStore store=new AiSettingsStore(job.app);AiSettings ai=store.load();if(ai==null)throw new IOException("请先在 AI 设置中配置服务");boolean unsupported=store.isImageUnsupported(ai);job.message="";job.main.post(()->{ClassroomSummaryActivity a=job.owner.get();if(a!=null&&job.allowed())a.generationDialog(ai,unsupported);});});}
    private void generationDialog(AiSettings ai,boolean unsupported){Dialog dialog=new Dialog(this);LinearLayout panel=UiStyle.dialogPanel(this,"生成课堂总结");LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);text(body,"将本节字幕及最多指定数量的截图上传至\n"+ai.baseUrl+"\n"+ai.model,15,false);EditText limit=new EditText(this);limit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);limit.setText(String.valueOf(unsupported?0:s.imageLimit));limit.setHint("截图上限 0–100");UiStyle.input(limit);body.addView(limit);if(unsupported){text(body,"当前模型仅支持文字",14,false);limit.setEnabled(false);}
        button(body,"上传并生成",()->{int count;try{count=Integer.parseInt(limit.getText().toString());}catch(Exception e){Feedback.show(this,"请输入 0 至 100");return;}if(count<0||count>100){Feedback.show(this,"请输入 0 至 100");return;}dialog.dismiss();startGeneration(ai,count);},true);
        if(!unsupported)button(body,"仅上传字幕",()->{dialog.dismiss();startGeneration(ai,0);},false);button(body,"取消",dialog::dismiss,false);UiStyle.dialogBody(panel,body);UiStyle.showFloatingDialog(dialog,panel);if(dialog.getWindow()!=null)dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
    private void startGeneration(AiSettings ai,int limit){Session job=s;s.imageLimit=limit;job.run(()->{JSONObject prior=ClassroomStore.read(new File(job.dir,"run.json"));String config=ai.baseUrl+"\n"+ai.model+"\n"+ai.reasoningEffort+"\n"+limit+"\n1";if(prior==null||!config.equals(prior.optString("config"))){job.ignoredSummaryUpdated=job.summary==null?-1:job.summary.optLong("updated");job.summary=null;job.revision++;job.publish();}
            try{job.summary=ClassroomSummary.generate(job.dir,job.selected,ai,limit,job.control,job::checkpoint);}
            catch(ChatCompletionClient.RequestException e){if(limit>0&&e.rejectsImage()){new AiSettingsStore(job.app).markImageUnsupported(ai);throw new IOException("当前 AI 服务拒绝图片。可再次选择生成总结，以纯文字模式上传字幕。",e);}throw e;}
            job.ignoredSummaryUpdated=-1;job.message="总结已完成";job.history=job.store().history();});}
    private void sources(LinearLayout parent,JSONArray refs){if(refs==null||s.materials==null)return;JSONArray segments=s.materials.optJSONArray("segments");HorizontalScrollView scroll=new HorizontalScrollView(this);LinearLayout row=new LinearLayout(this);scroll.addView(row);for(int i=0;i<refs.length();i++){JSONObject source=segments==null?null:segments.optJSONObject(refs.optInt(i,-1));if(source==null)continue;TextView link=new TextView(this);link.setText(ClassroomExport.time(source.optDouble("start"))+"  ·  ");link.setTextSize(14);link.setTextColor(UiStyle.colors(this).primary);link.setMinHeight(dp(48));link.setGravity(android.view.Gravity.CENTER_VERTICAL);link.setContentDescription("字幕来源 "+ClassroomExport.time(source.optDouble("start")));link.setOnClickListener(v->{if(gate())showSource(source);});row.addView(link);}parent.addView(scroll);}
    private void showSource(JSONObject source){Dialog dialog=new Dialog(this);LinearLayout panel=UiStyle.dialogPanel(this,ClassroomExport.time(source.optDouble("start"))+" — "+ClassroomExport.time(source.optDouble("end")));LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);text(body,source.optString("text"),16,false);JSONArray images=s.summary==null?null:s.summary.optJSONArray("images");if(images!=null)for(int i=0;i<images.length();i++){JSONObject pic=images.optJSONObject(i);if(pic!=null&&Math.abs(pic.optDouble("second")-source.optDouble("start"))<90)image(body,pic.optString("file"));}UiStyle.dialogBody(panel,body);button(panel,"关闭",dialog::dismiss,false);UiStyle.showFloatingDialog(dialog,panel);}
    private void image(LinearLayout parent,String name){try{ClassroomExport.safeImage(name);}catch(Exception e){return;}File file=new File(s.dir,name);ImageView view=new ImageView(this);view.setAdjustViewBounds(true);view.setContentDescription("课堂截图，点击放大");parent.addView(view,new LinearLayout.LayoutParams(-1,dp(190)));WeakReference<ImageView> target=new WeakReference<>(view);Session job=s;PICTURES.execute(()->{BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),bounds);BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=Math.max(1,Math.max(bounds.outWidth,bounds.outHeight)/1200);Bitmap bitmap=BitmapFactory.decodeFile(file.getPath(),options);job.main.post(()->{ImageView v=target.get();if(v!=null&&job.allowed()&&v.isAttachedToWindow()){if(bitmap!=null)v.setImageBitmap(bitmap);else v.setContentDescription("截图读取失败");}else if(bitmap!=null)bitmap.recycle();});});view.setOnClickListener(v->{Dialog dialog=new Dialog(this);LinearLayout panel=UiStyle.dialogPanel(this,"课堂截图");ImageView large=new ImageView(this);large.setAdjustViewBounds(true);large.setImageDrawable(view.getDrawable());panel.addView(large,new LinearLayout.LayoutParams(-1,-2));button(panel,"关闭",dialog::dismiss,false);UiStyle.showFloatingDialog(dialog,panel);});}
    private void export(){s.pendingExport=s.selected;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").putExtra(Intent.EXTRA_TITLE,"课堂总结-"+s.selected.date()+".zip");try{startActivityForResult(intent,EXPORT);}catch(Exception e){Feedback.show(this,"无法打开文件保存器");}}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);try{if(s.pendingExport!=null)state.putString("export_lesson",s.pendingExport.json().toString());if(s.exportUri!=null)state.putString("export_uri",s.exportUri.toString());}catch(Exception ignored){}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=EXPORT||result!=RESULT_OK||data==null||data.getData()==null||!gate())return;s.exportUri=data.getData();
        boolean read=(data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0,write=(data.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0;
        try{if(read&&write)getContentResolver().takePersistableUriPermission(s.exportUri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            else if(write)getContentResolver().takePersistableUriPermission(s.exportUri,Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            else if(read)getContentResolver().takePersistableUriPermission(s.exportUri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }catch(SecurityException ignored){}if(s.localInitialized&&!s.busy)writeExport();}
    private void writeExport(){Session job=s;if(!job.localInitialized||job.busy||job.exportInFlight)return;Uri uri=job.exportUri;ClassroomLesson lesson=job.pendingExport;if(lesson==null){job.exportUri=null;return;}job.exportInFlight=true;job.run(()->{try{File dir=job.store().lesson(lesson);JSONObject summary=ClassroomStore.read(new File(dir,"summary.json"));if(summary==null)throw new IOException("总结缓存不存在");try(OutputStream out=job.app.getContentResolver().openOutputStream(uri,"w")){if(out==null)throw new IOException("无法写入文件");ClassroomExport.write(dir,summary,out);}job.message="ZIP 已导出";job.pendingExport=null;}finally{job.exportUri=null;job.exportInFlight=false;}});}
    private TextView text(LinearLayout parent,String value,int size,boolean title){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextIsSelectable(true);if(title)UiStyle.title(v);else v.setTextColor(UiStyle.colors(this).text);UiStyle.addSpaced(parent,v,6,10);return v;}
    private Button button(LinearLayout parent,String title,Runnable work,boolean primary){Button b=new Button(this);b.setText(title);b.setAllCaps(false);UiStyle.button(b,primary);UiStyle.choice(b,primary,8);b.setOnClickListener(v->{if(gate())work.run();});UiStyle.addSpaced(parent,b,4,6);return b;}
    private Button action(LinearLayout row,String title,Runnable work){Button b=new Button(this);b.setText(title);b.setTextSize(14);UiStyle.button(b,false);UiStyle.choice(b,false,8);b.setOnClickListener(v->{if(gate())work.run();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(2),dp(4),dp(2),dp(6));row.addView(b,p);return b;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
