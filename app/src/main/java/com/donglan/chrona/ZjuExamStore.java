package com.donglan.chrona;

import android.content.Context;
import android.util.AtomicFile;
import com.donglan.chrona.zju.ZjuExam;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Re-fetchable, device-only exam cache. An empty successful query is a real snapshot. */
final class ZjuExamStore {
    private static final Object LOCK=new Object();
    private static volatile long revision;
    static long revision(){return revision;}
    private final AtomicFile file;
    ZjuExamStore(Context context){file=new AtomicFile(new File(context.getCacheDir(),"zju-exams.json"));}
    List<ZjuExam> load(String account)throws Exception{
        synchronized(LOCK){
            if(!file.getBaseFile().isFile()&&!new File(file.getBaseFile()+".bak").isFile())return null;
            JSONObject root=new JSONObject(new String(file.readFully(),StandardCharsets.UTF_8));
            if(!account.equals(root.getString("account")))return null;
            JSONArray rows=root.getJSONArray("exams");List<ZjuExam> result=new ArrayList<>();
            for(int i=0;i<rows.length();i++)result.add(ZjuExam.fromJson(rows.getJSONObject(i)));
            return List.copyOf(result);
        }
    }
    void save(String account,List<ZjuExam> exams)throws Exception{
        synchronized(LOCK){
            JSONArray rows=new JSONArray();for(ZjuExam exam:exams)rows.put(exam.json());
            byte[] bytes=new JSONObject().put("account",account).put("exams",rows).toString().getBytes(StandardCharsets.UTF_8);
            FileOutputStream out=file.startWrite();
            try{out.write(bytes);file.finishWrite(out);revision++;}catch(Exception failure){file.failWrite(out);throw failure;}
        }
    }
    void clear(){synchronized(LOCK){file.delete();revision++;}}
}
