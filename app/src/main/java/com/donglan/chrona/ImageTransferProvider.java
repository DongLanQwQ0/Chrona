package com.donglan.chrona;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import com.donglan.chrona.classroom.ClassroomLesson;
import com.donglan.chrona.classroom.ClassroomStore;
import com.donglan.chrona.image.ImageStore;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/** Private image source; Android grants only the exact image explicitly dragged by the user. */
public final class ImageTransferProvider extends ContentProvider {
    static Uri attachment(Context app,String name){
        if(!ImageStore.isStoredName(name))throw new IllegalArgumentException("图片附件无效");
        return base(app).appendPath("attachment").appendPath(name).build();
    }
    static Uri classroom(Context app,String account,ClassroomLesson lesson,String raw){
        return base(app).appendPath("classroom").appendPath(account).appendPath(lesson.courseId).appendPath(lesson.id).appendPath(raw).build();
    }
    private static Uri.Builder base(Context app){return new Uri.Builder().scheme("content").authority(app.getPackageName()+".images");}
    @Override public boolean onCreate(){return true;}
    private File resolve(Uri uri)throws FileNotFoundException {
        try{
            Context app=getContext();if(app==null||!"content".equals(uri.getScheme())||!(app.getPackageName()+".images").equals(uri.getAuthority()))throw new FileNotFoundException();
            List<String> parts=uri.getPathSegments();File file;
            if(parts.size()==2&&"attachment".equals(parts.get(0)))file=new ImageStore(app).fileFor(parts.get(1));
            else if(parts.size()==5&&"classroom".equals(parts.get(0))&&ClassroomMonitor.allowed(app,parts.get(1)))
                file=new ClassroomStore(app,parts.get(1)).localImage(parts.get(2),parts.get(3),parts.get(4));
            else throw new FileNotFoundException();
            if(!file.isFile())throw new FileNotFoundException();return file;
        }catch(Exception unavailable){throw new FileNotFoundException("图片已不可用");}
    }
    private String mime(File file)throws FileNotFoundException {
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0||bounds.outMimeType==null||!bounds.outMimeType.startsWith("image/"))throw new FileNotFoundException("图片格式无效");
        return bounds.outMimeType;
    }
    @Override public String getType(Uri uri){try{return mime(resolve(uri));}catch(FileNotFoundException unavailable){return null;}}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {
        if(!"r".equals(mode))throw new FileNotFoundException("图片只允许读取");
        File file=resolve(uri);mime(file);
        return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] arguments,String order){
        try{
            File file=resolve(uri);String type=mime(file),extension=android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(type);
            if(extension==null)throw new FileNotFoundException("图片格式无效");
            String name="classroom".equals(uri.getPathSegments().get(0))?"课堂PPT-"+file.getName().replace(".img","")+"."+extension:file.getName();
            String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
            MatrixCursor result=new MatrixCursor(columns);Object[] values=new Object[columns.length];
            for(int i=0;i<columns.length;i++)values[i]=OpenableColumns.DISPLAY_NAME.equals(columns[i])?name:OpenableColumns.SIZE.equals(columns[i])?file.length():null;
            result.addRow(values);return result;
        }catch(FileNotFoundException unavailable){return null;}
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("Read only");}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] arguments){throw new UnsupportedOperationException("Read only");}
    @Override public int delete(Uri uri,String selection,String[] arguments){throw new UnsupportedOperationException("Read only");}
}
