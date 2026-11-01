package com.donglan.chrona.classroom;

import org.json.*;
import java.util.Set;
import java.util.regex.*;

/** User-facing projection only. Exact provider replies remain in conversation history. */
public final class ClassroomJsonFailure {
    private static final String INVALID="AI 返回的 JSON 结构或字段不完整，请手动继续以完整重新生成当前阶段";
    private static final Set<String> FIELDS=Set.of("overview","notes","affairs","assignments","title","text","sources","images","evidence",
            "image_assessment","image_requests","decision","reason","start","end","image_reviews","file","status","confidence","visible",
            "sections","collection","refs","groups");
    private static final Pattern LOCATION=Pattern.compile("(?s).*\\bat (?:character|index) \\d+ (?:of|in) .*");
    private static final Pattern POSITION=Pattern.compile("\\bat (?:character|index) (\\d{1,10})\\b|\\bat (\\d{1,10}) \\[character ");
    private static final Pattern MISSING=Pattern.compile("^No value for ([A-Za-z_][A-Za-z_0-9]*)$");
    private static final Pattern OBJECT_FIELD=Pattern.compile("(?s)^JSONObject\\[\"([^\"]+)\"\\] (?:not found\\.?|is not a (\\w+).*)$");
    private static final Pattern CONVERSION=Pattern.compile("(?s)^Value .* cannot be converted to (\\w+).*$");
    private static final Pattern CONVERSION_FIELD=Pattern.compile("(?s)^Value .* at ([A-Za-z_][A-Za-z_0-9]*) of type .* cannot be converted to (\\w+).*$");
    private static final Pattern ROOT_CONVERSION=Pattern.compile("(?s)^Value .* of type (org\\.json\\.JSONArray|java\\.lang\\.String) cannot be converted to JSONObject$");
    private static final Pattern MEMBER_CONVERSION=Pattern.compile("(?s)^Value .* at (?:\\d+|[A-Za-z_][A-Za-z_0-9]*) of type .* cannot be converted to .*$");
    private ClassroomJsonFailure() { }
    public static String detail(Exception error) {
        String projected=diagnostic(error.getMessage());
        return projected!=null?projected:error instanceof JSONException?INVALID:stored(error.getMessage());
    }
    public static String stored(String error) {
        if(error==null)return "";
        String projected=diagnostic(error);
        return projected!=null?projected:error;
    }
    public static boolean isJsonFailure(String detail) {
        if(detail==null)return false;
        return detail.equals(INVALID)||diagnostic(detail)!=null||detail.startsWith("JSON 字段缺失：")
                ||detail.startsWith("JSON 字段类型错误：")||detail.startsWith("JSON 语法错误：");
    }
    private static String diagnostic(String message) {
        if(message==null)return null;
        String text=message.trim();boolean named=text.startsWith("org.json.JSONException:");
        if(named)text=text.substring("org.json.JSONException:".length()).trim();
        Matcher missing=MISSING.matcher(text);if(missing.matches())return FIELDS.contains(missing.group(1))?missing(missing.group(1)):INVALID;
        Matcher field=OBJECT_FIELD.matcher(text);
        if(field.matches())return FIELDS.contains(field.group(1))?(field.group(2)==null?missing(field.group(1)):type(field.group(1),knownType(field.group(2)))):INVALID;
        Matcher conversion=CONVERSION_FIELD.matcher(text);
        if(conversion.matches())return FIELDS.contains(conversion.group(1))?type(conversion.group(1),knownType(conversion.group(2))):INVALID;
        String reason=null;
        if(text.startsWith("Unterminated object"))reason="对象未闭合";
        else if(text.startsWith("Unterminated array"))reason="数组未闭合";
        else if(text.startsWith("Unterminated string"))reason="字符串未闭合";
        else if(text.startsWith("Invalid escape")||text.startsWith("Illegal escape"))reason="字符串转义无效";
        else if(text.startsWith("Expected ':'"))reason="缺少冒号分隔符";
        else if(text.startsWith("Expected a ',' or '}'"))reason="对象缺少逗号或右花括号";
        else if(text.startsWith("Expected a ',' or ']'"))reason="数组缺少逗号或右方括号";
        else if(text.startsWith("Expected literal value")||text.startsWith("Missing value"))reason="缺少字段值";
        else if(text.startsWith("End of input"))reason="输入意外结束";
        else if(text.startsWith("A JSONObject text must begin with '{'"))reason="顶层必须是对象";
        if(reason!=null){Matcher position=POSITION.matcher(text);return "JSON 语法错误："+reason+(position.find()?"（字符 "+(position.group(1)!=null?position.group(1):position.group(2))+"）":"");}
        if(text.equals("image_assessment.reason必须是字符串"))return type("image_assessment.reason","字符串");
        if(text.equals("image_requests必须是数组"))return type("image_requests","数组");
        if(text.startsWith("image_reviews缺少实际图片核对："))return "JSON 字段缺失：image_reviews 中的实际图片核对";
        if(named||LOCATION.matcher(text).matches()||CONVERSION.matcher(text).matches()||text.startsWith("No value for ")||text.startsWith("JSONObject[")||text.startsWith("JSONArray["))return INVALID;
        return null;
    }
    private static String knownType(String type) {
        return switch(type){case "JSONArray"->"数组";case "JSONObject"->"对象";case "String","string"->"字符串";
            case "Integer","Long","Double","Number","int","long","double"->"数字";case "Boolean","boolean"->"布尔值";default->null;};
    }
    private static String missing(String path){return "JSON 字段缺失："+path;}
    private static String type(String path,String expected){return expected==null?INVALID:"JSON 字段类型错误："+path+" 应为"+expected;}

    /** Recheck a saved failed reply; never validates its sources, image content or facts. */
    public static String responseDetail(String raw,String stage,JSONObject run) {
        if(stage==null||!(stage.equals("first")||stage.startsWith("first-")||stage.startsWith("draft-")
                ||stage.equals("final")||stage.startsWith("final-")||stage.equals("edit-plan")||stage.startsWith("edit-plan-")
                ||stage.equals("edit-assignments")||stage.startsWith("edit-assignments-")||stage.startsWith("edit-note-")))return "";
        JSONObject value;
        try {value=ClassroomJsonObject.parseObject(raw==null?"":raw);}
        catch(JSONException invalid){return parsedFailure(invalid);}
        try {
            int affairs=run==null?0:run.optInt("affairs_schema"),figures=run==null?0:run.optInt("figure_policy");
            if(stage.equals("edit-plan")||stage.startsWith("edit-plan-")){
                required(value,"overview",String.class,"");JSONArray sections=array(value,"sections","");
                for(int i=0;i<sections.length();i++){String path="sections["+i+"]";JSONObject row=object(sections,i,path);required(row,"title",String.class,path);if(affairs>0)required(row,"collection",String.class,path);items(array(row,"refs",path),String.class,path+".refs");}
            }else if(stage.equals("edit-assignments")||stage.startsWith("edit-assignments-")){
                JSONArray groups=array(value,"groups","");for(int i=0;i<groups.length();i++){String path="groups["+i+"]";Object group=groups.opt(i);if(!(group instanceof JSONArray))throw new FieldProblem(type(path,"数组"));items((JSONArray)group,String.class,path);}
            }else if(stage.equals("first")||stage.startsWith("first-")||stage.startsWith("draft-")||stage.equals("final")||stage.startsWith("final-")||stage.startsWith("edit-note-")){
                required(value,"overview",String.class,"");notes(value,"notes");if(affairs>0||value.has("affairs"))notes(value,"affairs");
                JSONArray tasks=array(value,"assignments","");boolean evidence=stage.startsWith("draft-")||stage.matches("final-\\d+(?:-.*)?");
                for(int i=0;i<tasks.length();i++){String path="assignments["+i+"]";JSONObject task=object(tasks,i,path);required(task,"text",String.class,path);items(array(task,"sources",path),Integer.class,path+".sources");if(evidence)required(task,"evidence",String.class,path);
                    if(task.has("images")){JSONArray images=array(task,"images",path);for(int n=0;n<images.length();n++)if(!(images.opt(n) instanceof String))throw new FieldProblem(type(path+".images["+n+"]","字符串"));}}
                if(stage.equals("first")||stage.startsWith("first-")||stage.startsWith("draft-")){
                    JSONObject assessment=(JSONObject)required(value,"image_assessment",JSONObject.class,"");required(assessment,"decision",String.class,"image_assessment");strictString(assessment,"reason","image_assessment");
                    JSONArray ranges=array(value,"image_requests","");for(int i=0;i<ranges.length();i++){String path="image_requests["+i+"]";JSONObject range=object(ranges,i,path);required(range,"start",Number.class,path);required(range,"end",Number.class,path);
                        if("request".equals(assessment.optString("decision")))strictString(range,"reason",path);}
                }
                if(figures>0&&(stage.equals("final")||stage.startsWith("final-"))){JSONArray reviews=array(value,"image_reviews","");
                    for(int i=0;i<reviews.length();i++){String path="image_reviews["+i+"]";JSONObject row=object(reviews,i,path);for(String field:new String[]{"file","status","confidence","visible","reason"})required(row,field,String.class,path);}}
            }
        }catch(FieldProblem invalid){return invalid.getMessage();}
        return "";
    }
    /** Only the shared parser's catch may classify an Android root-value conversion. */
    static String parsedFailure(JSONException invalid) {
        String message=invalid.getMessage();
        if(message!=null&&!MEMBER_CONVERSION.matcher(message).matches()){
            Matcher root=ROOT_CONVERSION.matcher(message);
            if(root.matches())return "JSON 语法错误：顶层必须是对象（实际为"+(root.group(1).equals("org.json.JSONArray")?"数组":"字符串")+"）";
        }
        return detail(invalid);
    }
    private static void notes(JSONObject value,String field){JSONArray notes=array(value,field,"");for(int i=0;i<notes.length();i++){String path=field+"["+i+"]";JSONObject row=object(notes,i,path);required(row,"title",String.class,path);required(row,"text",String.class,path);items(array(row,"sources",path),Integer.class,path+".sources");items(array(row,"images",path),String.class,path+".images");}}
    private static JSONArray array(JSONObject value,String field,String path){return (JSONArray)required(value,field,JSONArray.class,path);}
    private static Object required(JSONObject value,String field,Class<?> expected,String parent){String path=parent.isEmpty()?field:parent+"."+field;
        if(!value.has(field))throw new FieldProblem(missing(path));Object result=value.opt(field);
        if(expected==String.class){try{return value.getString(field);}catch(JSONException invalid){throw new FieldProblem(type(path,"字符串"));}}
        if(!expected.isInstance(result))throw new FieldProblem(type(path,label(expected)));return result;}
    private static void strictString(JSONObject value,String field,String path){required(value,field,String.class,path);if(!(value.opt(field) instanceof String))throw new FieldProblem(type(path+"."+field,"字符串"));}
    private static JSONObject object(JSONArray rows,int index,String path){Object row=rows.opt(index);if(!(row instanceof JSONObject))throw new FieldProblem(type(path,"对象"));return (JSONObject)row;}
    private static void items(JSONArray rows,Class<?> expected,String path){for(int i=0;i<rows.length();i++){
        if(expected==String.class){try{rows.getString(i);continue;}catch(JSONException invalid){throw new FieldProblem(type(path+"["+i+"]","字符串"));}}
        if(!expected.isInstance(rows.opt(i)))throw new FieldProblem(type(path+"["+i+"]",label(expected)));}}
    private static String label(Class<?> type){return type==JSONArray.class?"数组":type==JSONObject.class?"对象":type==String.class?"字符串":"数字";}
    private static final class FieldProblem extends RuntimeException {FieldProblem(String safe){super(safe);}}
}
