import com.donglan.chrona.net.NetworkFailure;
import java.io.*;
import java.net.*;
import javax.net.ssl.SSLHandshakeException;

public final class NetworkFailureCheck {
    static int checks;
    static void check(boolean value,String label) { checks++;if(!value)throw new AssertionError(label); }
    public static void main(String[] args) {
        IOException[] failures={new UnknownHostException("SECRET"),new NoRouteToHostException("SECRET"),new ConnectException("SECRET"),
                new SSLHandshakeException("SECRET"),new SocketTimeoutException("SECRET"),new SocketException("SECRET"),new EOFException("SECRET")};
        String[] labels={"DNS","路由","连接","TLS","超时","中断","中断"};
        for(int i=0;i<failures.length;i++) {
            IOException result=NetworkFailure.transport("读取课堂截图","video.cmc.zju.edu.cn",failures[i]);
            String message=NetworkFailure.describe(result);
            check(result.getCause()==failures[i],"original cause");
            check(message.contains(labels[i])&&message.contains("读取课堂截图")&&message.contains("video.cmc.zju.edu.cn"),"classification and context");
            check(message.contains(failures[i].getClass().getSimpleName())&&!message.contains("SECRET"),"safe exception type");
            check(!(failures[i] instanceof SocketTimeoutException)||result instanceof SocketTimeoutException,"timeout semantic type");
        }
        IOException nested=new IOException("SECRET",new UnknownHostException("SECRET"));
        check(NetworkFailure.describe(nested).contains("DNS"),"cause chain classification");
        InterruptedIOException interrupted=new InterruptedIOException("SECRET");
        IOException wrapped=NetworkFailure.transport("等待响应","host",interrupted);
        check(wrapped instanceof InterruptedIOException&&wrapped.getCause()==interrupted&&!wrapped.getMessage().contains("SECRET"),"interruption preserved safely");
        for(int status:new int[]{401,403,404,407,429,500,503}) {
            String text=NetworkFailure.describe(NetworkFailure.http("读取学校数据","classroom.zju.edu.cn",status));
            check(text.contains("HTTP "+status),"HTTP status retained");
            if(status==407)check(text.contains("代理要求身份认证"),"explicit proxy diagnosis");
            if(status==429)check(text.contains("过于频繁"),"rate limiting");
            if(status>=500)check(text.contains("服务端"),"server error");
        }
        check(!NetworkFailure.describe(NetworkFailure.transport("下载","https://secret/path?key=SECRET",new IOException("SECRET"))).contains("SECRET"),"reject non-host target");
        IOException timed=NetworkFailure.context("第 66/130 张截图："+NetworkFailure.describe(failures[4]),failures[4]);
        check(timed instanceof SocketTimeoutException&&timed.getCause()==failures[4]&&NetworkFailure.describe(timed).contains("第 66/130"),"indexed timeout retains cause");
        check(!NetworkFailure.hasDetails(nested)&&NetworkFailure.hasDetails(timed),"safe diagnostics discriminator");
        System.out.println("Network failure checks: "+checks+" passed");
    }
}
