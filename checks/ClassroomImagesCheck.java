import com.donglan.chrona.classroom.*;
import com.donglan.chrona.net.RequestControl;
import java.io.*;
import java.nio.file.*;
import org.json.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public final class ClassroomImagesCheck {
    static int checks;
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    public static void main(String[] args)throws Exception {
        File dir=Files.createTempDirectory("chrona-images-").toFile();
        try(RequestControl control=new RequestControl(60000)) {
            ByteArrayOutputStream output=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(48,48,BufferedImage.TYPE_INT_RGB),"png",output);
            ClassroomClient client=new ClassroomClient();client.bytes=output.toByteArray();
            JSONObject materials=new JSONObject().put("images",new JSONArray().put(new JSONObject().put("url","fixture").put("second",0)));
            File raw=new File(dir,"raw-0.img");Files.writeString(raw.toPath(),"broken");
            ClassroomImages.prepare(dir,materials,client,control,p->{});
            check(client.downloads==1&&ImageIO.read(raw)!=null,"corrupt cache replaced once");
            ClassroomImages.prepare(dir,materials,client,control,p->{});
            check(client.downloads==1,"valid cache reused");
            raw.delete();client.bytes="broken".getBytes();
            try { ClassroomImages.prepare(dir,materials,client,control,p->{});throw new AssertionError("bad download"); }
            catch(IOException e){check(e.getMessage().contains("第 1/1 张截图"),"safe indexed decode failure");}
            check(!raw.exists()&&!new File(dir,"raw-0.img.validating").exists(),"bad download not cached");
            client.bytes=output.toByteArray();ClassroomImages.prepare(dir,materials,client,control,p->{});
            check(ImageIO.read(raw)!=null,"retry can recover");
            materials.getJSONArray("images").put(new JSONObject().put("url","fixture2").put("second",1));client.bytes="broken".getBytes();
            try { ClassroomImages.prepare(dir,materials,client,control,p->{});throw new AssertionError("second bad image"); }
            catch(IOException e){check(e.getMessage().contains("第 2/2 张截图"),"failed index exact");}
            check(raw.exists()&&!new File(dir,"raw-1.img").exists(),"successful cache preserved");
            client.failure=new java.net.SocketTimeoutException("sensitive URL");
            try { ClassroomImages.prepare(dir,materials,client,control,p->{});throw new AssertionError("timeout"); }
            catch(java.net.SocketTimeoutException e){check(e.getMessage().contains("第 2/2 张截图")&&!e.getMessage().contains("sensitive"),"timeout retains type and safe index");}
            client.failure=new IOException("sensitive URL");
            try { ClassroomImages.prepare(dir,materials,client,control,p->{});throw new AssertionError("network"); }
            catch(IOException e){check(!e.getMessage().contains("sensitive"),"network message sanitized");}
            control.cancel();int downloads=client.downloads;
            try { ClassroomImages.prepare(dir,materials,client,control,p->{});throw new AssertionError("cancel"); }
            catch(InterruptedIOException e){check(downloads==client.downloads,"cancel performs no download");}
        } finally {for(File f:dir.listFiles())f.delete();dir.delete();}
        System.out.println("Classroom image checks: "+checks+" passed");
    }
}
