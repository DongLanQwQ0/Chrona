package com.donglan.chrona;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.view.DragEvent;
import android.view.DragAndDropPermissions;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;

/** Separate-UID receiver, packaged only in androidTest. Never usable on a user's phone. */
public final class ImageDragReceiverActivity extends Activity {
    @Override public void onCreate(Bundle state){
        setTheme(android.R.style.Theme_Material_Light_Dialog_NoActionBar);
        super.onCreate(state);
        if(!android.os.Build.HARDWARE.contains("ranchu")||!getPackageName().endsWith(".test")){finish();return;}
        ResultReceiver reports=getIntent().getParcelableExtra("reports");
        if(reports==null){finish();return;}
        setFinishOnTouchOutside(false);
        // A real application window in a separate UID, with the source image still touchable.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        android.util.DisplayMetrics metrics=getResources().getDisplayMetrics();
        getWindow().setLayout(Math.round(metrics.widthPixels*.43f),Math.round(metrics.heightPixels*.20f));
        WindowManager.LayoutParams layout=getWindow().getAttributes();layout.gravity=Gravity.RIGHT|Gravity.BOTTOM;layout.y=Math.round(32*metrics.density);getWindow().setAttributes(layout);
        TextView target=new TextView(this);target.setGravity(android.view.Gravity.CENTER);target.setTextSize(22);target.setBackgroundColor(0xffe7eef3);target.setTextColor(0xff102c3e);
        Uri expected=Uri.parse(getIntent().getStringExtra("expected_uri"));String other=getIntent().getStringExtra("other_uri");
        byte[] expectedBytes=getIntent().getByteArrayExtra("expected_bytes");
        boolean beforeDenied=denied(expected);target.setText("DROP_READY beforeDenied="+beforeDenied);setContentView(target);
        target.post(()->{int[] xy=new int[2];target.getLocationOnScreen(xy);Bundle ready=new Bundle();ready.putBoolean("beforeDenied",beforeDenied);ready.putInt("uid",android.os.Process.myUid());ready.putInt("x",xy[0]+target.getWidth()/2);ready.putInt("y",xy[1]+target.getHeight()/2);ready.putInt("left",xy[0]);ready.putInt("top",xy[1]);ready.putInt("right",xy[0]+target.getWidth());ready.putInt("bottom",xy[1]+target.getHeight());reports.send(1,ready);});
        target.setOnDragListener((view,event)->{
            if(event.getAction()==DragEvent.ACTION_DRAG_STARTED){boolean image=event.getClipDescription()!=null&&event.getClipDescription().hasMimeType("image/*");Bundle started=new Bundle();started.putBoolean("image",image);reports.send(2,started);return image;}
            if(event.getAction()!=DragEvent.ACTION_DROP)return true;
            DragAndDropPermissions permission=requestDragAndDropPermissions(event);
            Bundle result=new Bundle();result.putBoolean("permission",permission!=null);result.putBoolean("beforeDenied",beforeDenied);result.putInt("uid",android.os.Process.myUid());
            try{
                Uri received=event.getClipData().getItemAt(0).getUri();result.putBoolean("exact",expected.equals(received));
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                try(InputStream input=getContentResolver().openInputStream(received)){if(input==null)throw new java.io.IOException();byte[] buffer=new byte[2048];int n;while((n=input.read(buffer))!=-1)bytes.write(buffer,0,n);}
                result.putString("mime",getContentResolver().getType(received));result.putInt("bytes",bytes.size());result.putBoolean("complete",expectedBytes!=null&&Arrays.equals(expectedBytes,bytes.toByteArray()));
                boolean writeDenied=false;try(var output=getContentResolver().openOutputStream(received,"w")){if(output==null)writeDenied=true;}catch(Exception failure){writeDenied=true;}
                result.putBoolean("writeDenied",writeDenied);result.putBoolean("otherDenied",other!=null&&denied(Uri.parse(other)));result.putBoolean("read",true);
                target.setText("DROP_OK "+result.getString("mime")+"\n"+bytes.size()+" bytes");
            }catch(Exception failure){result.putBoolean("read",false);result.putString("error",failure.getClass().getSimpleName());target.setText("DROP_BLOCKED "+failure.getClass().getSimpleName());}
            finally{
                if(permission!=null)permission.release();result.putBoolean("releasedDenied",denied(expected));reports.send(3,result);
                target.postOnAnimation(()->target.postOnAnimation(()->{reports.send(4,new Bundle());target.postDelayed(this::finish,1200);}));
            }
            return true;
        });
        new Handler(Looper.getMainLooper()).postDelayed(this::finish,25000);
    }
    private boolean denied(Uri uri){try(InputStream input=getContentResolver().openInputStream(uri)){return input==null;}catch(Exception blocked){return true;}}
}
