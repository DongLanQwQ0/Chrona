"""Offline regression of real classroom/AI code; synthetic images and loopback HTTP only.

Android bitmap/atomic-file adapters run on the JVM. No downloads, credentials, real AI,
school account files or devices are accessed. Requires the existing JDK and JSON jar.
"""
from recurrence_dependencies import recurrence_java

from pathlib import Path
import argparse
import os
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--image-references-only', action='store_true')
parser.add_argument('--note-strength-only', action='store_true')
args = parser.parse_args()
src = root / 'app/src/main/java/com/donglan/chrona'
out = root / 'build/classroom-summary-check'
out.mkdir(parents=True, exist_ok=True)
fixtures = {
    'com/donglan/chrona/zju/ZjuClient.java': '''package com.donglan.chrona.zju;
public class ZjuClient {public static class SchoolException extends java.io.IOException {public SchoolException(String s){super(s);}}}''',
    'android/content/Context.java': '''package android.content;
public class Context {private final java.io.File root;public Context(java.io.File root){this.root=root;}
public java.io.File getNoBackupFilesDir(){return root;}}''',
    'android/util/AtomicFile.java': '''package android.util;
import java.io.*;import java.nio.file.*;
public class AtomicFile {private final File file,stage;public AtomicFile(File f){file=f;stage=new File(f+".new");}
public byte[] readFully()throws IOException{return Files.readAllBytes(file.toPath());}
public void delete(){file.delete();stage.delete();}
public FileOutputStream startWrite()throws IOException{return new FileOutputStream(stage);}
public void finishWrite(FileOutputStream s)throws IOException{s.close();Files.move(stage.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
public void failWrite(FileOutputStream s){try{s.close();Files.deleteIfExists(stage.toPath());}catch(IOException ignored){}}}''',
    'android/graphics/Bitmap.java': '''package android.graphics;
import java.awt.image.BufferedImage;import java.io.*;import javax.imageio.ImageIO;
public class Bitmap {final BufferedImage image;Bitmap(BufferedImage i){image=i;}
public static Bitmap createScaledBitmap(Bitmap b,int w,int h,boolean filter){BufferedImage i=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);var g=i.createGraphics();g.drawImage(b.image,0,0,w,h,null);g.dispose();return new Bitmap(i);}
public void getPixels(int[] p,int off,int stride,int x,int y,int w,int h){image.getRGB(x,y,w,h,p,off,stride);}
public void recycle(){}public enum CompressFormat{JPEG}
public boolean compress(CompressFormat f,int q,OutputStream out){try{return ImageIO.write(image,"jpg",out);}catch(IOException e){return false;}}}''',
    'android/graphics/BitmapFactory.java': '''package android.graphics;
import java.io.*;import javax.imageio.ImageIO;
public class BitmapFactory {public static class Options{public boolean inJustDecodeBounds;public int outWidth,outHeight,inSampleSize;}
public static Bitmap decodeFile(String p){return decodeFile(p,new Options());}
public static Bitmap decodeFile(String p,Options o){try{var i=ImageIO.read(new File(p));if(i==null)return null;o.outWidth=i.getWidth();o.outHeight=i.getHeight();return o.inJustDecodeBounds?null:new Bitmap(i);}catch(IOException e){return null;}}}''',
    'com/donglan/chrona/classroom/ClassroomClient.java': '''package com.donglan.chrona.classroom;
import java.util.*;import org.json.*;
public class ClassroomClient {public final Map<String,byte[]> images=new HashMap<>();public JSONObject value;public int downloads,materialReads;
public byte[] image(String url){downloads++;return images.get(url);}
public JSONObject materials(ClassroomLesson lesson){materialReads++;return value;}}''',
}
paths = []
for name, content in fixtures.items():
    path = out / 'stubs' / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding='utf-8')
    paths.append(path)
relative = ['classroom/ClassroomLesson.java', 'classroom/ClassroomStore.java', 'classroom/ClassroomNoteStrength.java', 'classroom/ClassroomImageQuality.java', 'classroom/ClassroomFigureReview.java',
            'classroom/ClassroomImages.java', 'classroom/ClassroomMaterialUpdates.java', 'classroom/ClassroomSummary.java', 'classroom/ClassroomConversation.java', 'classroom/ClassroomEnhancedSummary.java', 'classroom/ClassroomExport.java', 'classroom/ClassroomProgress.java',
            'ai/AiSettings.java', 'ai/ChatCompletionClient.java', 'ai/ParseResult.java', 'ai/SchedulePrompt.java',
            'ai/SemanticEventNormalizer.java', 'ai/TimeAssessment.java', 'ai/RequestOptions.java', 'ai/StreamChunkContent.java',
            'data/EventCandidate.java', 'data/EventCategory.java', 'data/EventTimeDefaults.java',
            'data/CandidateRules.java', 'calendar/AllDayDates.java', 'net/RequestControl.java']
paths += [src / name for name in relative]
paths.append(root / 'shared/src/main/java/com/donglan/chrona/classroom/ClassroomDocument.java')
paths.append(root / 'shared/src/main/java/com/donglan/chrona/classroom/ClassroomMarkdownImages.java')
paths.append(src / 'net/NetworkFailure.java')
paths += [root / 'checks/ClassroomSummaryCheck.java', root / 'checks/ClassroomFigureReviewCheck.java', root / 'checks/AiRequestCheck.java', root / 'checks/TimeAssessmentCheck.java']
jar = root / 'build/ai-checks/json.jar'
if not jar.is_file():
    raise RuntimeError('Existing build/ai-checks/json.jar is required; nothing will be downloaded')

def executable(name):
    suffix = '.exe' if os.name == 'nt' else ''
    home = os.environ.get('JAVA_HOME')
    candidate = Path(home) / 'bin' / (name + suffix) if home else None
    if candidate and candidate.is_file():
        return str(candidate)
    found = shutil.which(name)
    if not found:
        raise RuntimeError(f'Existing {name} required')
    return found

classes = out / 'classes'
classes.mkdir(exist_ok=True)
subprocess.run(recurrence_java([executable('javac'), '--release', '17', '-encoding', 'UTF-8', '-cp', str(jar),
                '-d', str(classes), *map(str, paths)]), check=True)
classpath = str(classes) + os.pathsep + str(jar)
subprocess.run(recurrence_java([executable('java'), '-cp', classpath, 'com.donglan.chrona.classroom.ClassroomFigureReviewCheck', '--cache', str(root / 'build/classroom-image-dedup/cache-rebuild-check')]), check=True)
if not args.image_references_only and not args.note_strength_only:
    subprocess.run(recurrence_java([executable('java'), '-cp', classpath, 'com.donglan.chrona.ai.AiRequestCheck']), check=True)
subprocess.run(recurrence_java([executable('java'), '-Djava.awt.headless=true', '-cp', classpath,
                'com.donglan.chrona.classroom.ClassroomSummaryCheck', str(out / 'fixtures'),
                *(['image-references'] if args.image_references_only else ['note-strength'] if args.note_strength_only else [])]), check=True)
