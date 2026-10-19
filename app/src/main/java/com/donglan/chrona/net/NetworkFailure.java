package com.donglan.chrona.net;

import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import javax.net.ssl.SSLException;

/** Safe diagnostics: never render exception messages, URLs, headers or response bodies. */
public final class NetworkFailure {
    private NetworkFailure() { }
    private interface Detail { }
    private static final class Failure extends IOException implements Detail {
        Failure(String message, Throwable cause) { super(message, cause); }
    }
    private static final class Timeout extends SocketTimeoutException implements Detail {
        Timeout(String message, Throwable cause) { super(message); initCause(cause); }
    }
    private static final class Interrupted extends InterruptedIOException implements Detail {
        Interrupted(String message, InterruptedIOException cause) {
            super(message); bytesTransferred = cause.bytesTransferred; initCause(cause);
        }
    }
    public static IOException transport(String stage, String host, IOException cause) {
        return context(stage + target(host) + "：" + describe(cause), cause);
    }
    /** Context must be application-owned copy, never server text or a URL. */
    public static IOException context(String message, IOException cause) {
        if (cause instanceof SocketTimeoutException) return new Timeout(message, cause);
        if (cause instanceof InterruptedIOException) return new Interrupted(message, (InterruptedIOException) cause);
        return new Failure(message, cause);
    }
    public static IOException http(String stage, String host, int code) {
        String reason;
        if (code == 407) reason = "代理要求身份认证，请检查代理账号或代理规则";
        else if (code == 429) reason = "请求过于频繁，请稍后重试";
        else if (code == 401 || code == 403) reason = "登录失效或访问权限不足，请重新登录并检查课程权限";
        else if (code >= 500) reason = "服务端处理失败，请稍后重试";
        else if (code >= 400) reason = "请求被拒绝或资源不可用，请刷新目录后重试";
        else reason = "返回了非预期状态，请稍后重试";
        return new Failure(stage + target(host) + "：HTTP " + code + " · " + reason, null);
    }
    public static boolean hasDetails(Throwable error) {
        for (Throwable item : chain(error)) if (item instanceof Detail) return true;
        return false;
    }
    public static String describe(Throwable error) {
        java.util.List<Throwable> chain = chain(error);
        for (Throwable item : chain) if (item instanceof Detail) return item.getMessage();
        for (Throwable item : chain) {
            String reason = null;
            if (item instanceof UnknownHostException) reason = "DNS 域名解析失败，请检查网络、DNS 及 VPN/代理规则";
            else if (item instanceof NoRouteToHostException) reason = "无可用网络路由，请检查网络连接及 VPN/代理规则";
            else if (item instanceof ConnectException) reason = "无法建立连接，请检查目标服务、网络及 VPN/代理规则";
            else if (item instanceof SSLException) reason = "TLS 安全连接失败，请检查设备时间、证书及网络拦截";
            else if (item instanceof SocketTimeoutException) reason = "连接或读取超时，请检查网络后重试";
            else if (item instanceof InterruptedIOException) reason = "请求已中断或超过请求时限";
            else if (item instanceof SocketException || item instanceof EOFException) reason = "连接中断，请检查网络稳定性及 VPN/代理规则后重试";
            if (reason != null) return reason + "（" + item.getClass().getSimpleName() + "）";
        }
        return "网络读写失败，请检查网络及 VPN/代理规则后重试（" +
                (error == null ? "Unknown" : error.getClass().getSimpleName()) + "）";
    }
    private static String target(String host) {
        return host != null && host.matches("[A-Za-z0-9.-]+") ? " · " + host : "";
    }
    private static java.util.List<Throwable> chain(Throwable error) {
        java.util.List<Throwable> items = new java.util.ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (error != null && seen.add(error)) { items.add(error); error = error.getCause(); }
        return items;
    }
}
