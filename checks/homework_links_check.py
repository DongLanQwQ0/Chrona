"""Execute per-assignment URL recovery without credentials, network or Android."""
from pathlib import Path
import os
import subprocess
root=Path(__file__).resolve().parents[1]
out=root/'build/homework-links-check'
out.mkdir(parents=True,exist_ok=True)
file=out/'HomeworkLinksCheck.java'
file.write_text('''package com.donglan.chrona;
public class HomeworkLinksCheck {
 static int checks;static void check(boolean x,String message){checks++;if(!x)throw new AssertionError(message);}
 public static void main(String[] args){
 String mobile="https://mcourses.zju.edu.cn/course/123/homework/456";
 String desktop="https://courses.zju.edu.cn/course/123/learning-activity#/456";
 HomeworkLinks old=HomeworkLinks.fromUrl("https://courses.zju.edu.cn/course/123/learning-activity/full-screen#/?activityId=456","123");
 check(mobile.equals(old.mobile)&&desktop.equals(old.desktop),"existing cached snapshot yields both deep links");
 check(desktop.equals(HomeworkLinks.fromUrl(desktop,"123").desktop),"desktop route retained");
 check(desktop.equals(HomeworkLinks.fromUrl(mobile,"123").desktop),"mobile route maps to same assignment");
 check(mobile.equals(HomeworkLinks.fromText("作业说明\\n\\n"+desktop).mobile),"portable synced description recovers assignment");
 for(String value:new String[]{"https://courses.zju.edu.cn/user/","https://mcourses.zju.edu.cn/","https://evil.test/course/123/learning-activity#/456","https://courses.zju.edu.cn@evil.test/course/123/learning-activity#/456","https://courses.zju.edu.cn:8080/course/123/learning-activity#/456","https://courses.zju.edu.cn/course/123/learning-activity#/nonsense","http://courses.zju.edu.cn/course/123/learning-activity#/456"})
 check(HomeworkLinks.fromUrl(value,null).mobile==null,"no homepage/foreign/unidentified destination: "+value);
 check(HomeworkLinks.fromUrl(desktop,"987").mobile==null,"mismatched snapshot course rejected");
 check(HomeworkLinks.fromText("课程123 作业456").mobile==null,"bare numbers never guessed as platform identity");
 System.out.println("PASS: "+checks+" production homework deep-link checks");
 }}''',encoding='utf-8')
java=Path(os.environ.get('JAVA_HOME','D:/Minecraft/java21'))/'bin'
subprocess.run([str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-d',str(out),str(root/'app/src/main/java/com/donglan/chrona/HomeworkLinks.java'),str(file)],check=True)
subprocess.run([str(java/'java.exe'),'-cp',str(out),'com.donglan.chrona.HomeworkLinksCheck'],check=True)
