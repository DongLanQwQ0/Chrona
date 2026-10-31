package com.donglan.chrona.classroom;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Image;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.SourceSpan;
import org.commonmark.node.Text;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** CommonMark image references shared by validation, presentation filtering and export. */
public final class ClassroomMarkdownImages {
    private static final Parser PARSER=Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build();
    private ClassroomMarkdownImages() { }

    private static List<Image> images(String markdown){
        List<Image> images=new ArrayList<>();
        PARSER.parse(markdown).accept(new AbstractVisitor(){
            @Override public void visit(Image image){
                images.add(image);
                // Descendants contribute alt text, not additional rendered pictures.
            }
        });
        return images;
    }
    public static Set<String> inlineImages(String markdown)throws IOException{
        Set<String> result=new LinkedHashSet<>();
        for(Image image:images(markdown))result.add(ClassroomDocument.imageName(image.getDestination()));
        return result;
    }
    /** Edit image source spans only; preserve container markers, line endings and all other text. */
    public static String rewrite(String markdown,Set<String> rejected,boolean canonical)throws IOException{
        List<Replacement> replacements=new ArrayList<>();
        for(Image image:images(markdown)){
            String file=ClassroomDocument.imageName(image.getDestination());
            boolean remove=rejected.contains(file);
            if(!remove&&!canonical)continue;
            List<SourceSpan> spans=image.getSourceSpans();
            if(spans.isEmpty())throw new IOException("图片缺少 Markdown 源位置");
            String replacement=remove?"":"!["+alt(image)+"](images/"+file+")";
            for(int i=0;i<spans.size();i++){
                SourceSpan span=spans.get(i);int start=span.getInputIndex(),end=start+span.getLength();
                if(start<0||end<start||end>markdown.length())throw new IOException("图片 Markdown 源位置无效");
                replacements.add(new Replacement(start,end,i==0?replacement:""));
            }
        }
        replacements.sort(Comparator.comparingInt((Replacement row)->row.start).reversed());
        StringBuilder result=new StringBuilder(markdown);int boundary=markdown.length();
        for(Replacement row:replacements){
            if(row.end>boundary)throw new IOException("图片 Markdown 源位置重叠");
            result.replace(row.start,row.end,row.text);boundary=row.start;
        }
        return result.toString();
    }
    private static String alt(Image image){
        StringBuilder text=new StringBuilder();
        image.accept(new AbstractVisitor(){
            @Override public void visit(Text node){text.append(node.getLiteral());}
            @Override public void visit(Code node){text.append(node.getLiteral());}
            @Override public void visit(SoftLineBreak node){text.append('\n');}
            @Override public void visit(HardLineBreak node){text.append('\n');}
        });
        StringBuilder escaped=new StringBuilder();
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            // Entities keep an alt line break inside the image, including in list/quote containers.
            if(c=='\n')escaped.append("&#10;");
            else if(c=='\r')escaped.append("&#13;");
            else{
                if((c>='!'&&c<='/')||(c>=':'&&c<='@')||(c>='['&&c<='`')||(c>='{'&&c<='~'))escaped.append('\\');
                escaped.append(c);
            }
        }
        return escaped.toString();
    }
    private static final class Replacement{
        final int start,end;final String text;
        Replacement(int start,int end,String text){this.start=start;this.end=end;this.text=text;}
    }
}
