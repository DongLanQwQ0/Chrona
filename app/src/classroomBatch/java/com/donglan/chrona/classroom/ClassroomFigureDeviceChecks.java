package com.donglan.chrona.classroom;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import java.io.File;
import java.io.FileOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real Android bitmap decoding and bundled CommonMark runtime; synthetic images, zero requests. */
public final class ClassroomFigureDeviceChecks {
    private static int checks;
    private static void require(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    public static String run(Context app)throws Exception{
        checks=0;File dir=new File(app.getExternalFilesDir(null),"classroom-export-layout/figure-native");dir.mkdirs();
        File blank=new File(dir,"blank.jpg"),dense=new File(dir,"proof.jpg");
        try{
            Bitmap bitmap=Bitmap.createBitmap(1280,720,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);Paint paint=new Paint();
            canvas.drawColor(Color.BLACK);paint.setColor(Color.rgb(235,235,235));canvas.drawRect(160,0,1120,720,paint);
            paint.setColor(Color.BLUE);paint.setTextSize(34);canvas.drawText("证明",210,165,paint);
            try(var output=new FileOutputStream(blank)){bitmap.compress(Bitmap.CompressFormat.JPEG,92,output);}
            paint.setColor(Color.BLACK);paint.setStrokeWidth(4);
            for(int y=210;y<650;y+=16)for(int x=215;x<1040;x+=70)canvas.drawLine(x,y,x+45,y+7,paint);
            try(var output=new FileOutputStream(dense)){bitmap.compress(Bitmap.CompressFormat.JPEG,92,output);}bitmap.recycle();
            JSONObject low=ClassroomImages.quality(blank),normal=ClassroomImages.quality(dense);
            require("low".equals(low.getString("confidence")),"native near-empty title with letterbox is low confidence: "+low+"; proof: "+normal);
            require("normal".equals(normal.getString("confidence")),"native detailed proof retains normal confidence");
            require(normal.getDouble("score")>low.getDouble("score"),"native content density orders proof above empty frame");
            String text="Paragraph\n    ![拒绝图](images/image-48.jpg)\n\n> ```text\n> ![字面量](images/image-48.jpg)\n> ```\n\n![实际证明][p]\n\n[p]: images/image-61.jpg";
            JSONArray images=new JSONArray().put(new JSONObject().put("file","image-48.jpg").put("second",1).put("quality",low)).put(new JSONObject().put("file","image-61.jpg").put("second",2).put("quality",normal));
            JSONArray reviews=new JSONArray();for(String file:new String[]{"image-48.jpg","image-61.jpg"})reviews.put(new JSONObject().put("file",file).put("status","usable").put("confidence","high").put("visible","合成测试线条").put("reason","仅测试本地门禁"));
            JSONObject part=new JSONObject().put("overview","").put("notes",new JSONArray().put(new JSONObject().put("title","证明").put("text",text).put("images",new JSONArray().put("image-48.jpg").put("image-61.jpg")))).put("assignments",new JSONArray()).put("image_reviews",reviews);
            JSONArray checked=ClassroomFigureReview.apply(part,images,images);String result=part.getJSONArray("notes").getJSONObject(0).getString("text");
            require(!result.contains("![拒绝图]"),"native AST blocks visible indented continuation picture");
            require(result.contains("![字面量]"),"native AST preserves quoted code");
            require(ClassroomDocument.inlineImages(result).equals(java.util.Set.of("image-61.jpg")),"native reference-style and code share actual rendered image semantics");
            require(ClassroomDocument.exportMarkdown(result).contains("![实际证明](images/image-61.jpg)"),"native export resolves reference image canonically");
            require(!checked.getJSONObject(0).getBoolean("included"),"native local blank score caps overconfident verdict");
            return new JSONObject().put("assertions",checks).put("blank",low).put("proof",normal).put("networkCalls",0).toString();
        }finally{if(blank.exists()&&!blank.delete())throw new IllegalStateException("Cannot clean synthetic blank image");if(dense.exists()&&!dense.delete())throw new IllegalStateException("Cannot clean synthetic proof image");if(dir.exists()&&!dir.delete())throw new IllegalStateException("Cannot clean synthetic image directory");}
    }
}
