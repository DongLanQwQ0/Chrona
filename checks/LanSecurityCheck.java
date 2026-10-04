package com.donglan.chrona;

import java.util.concurrent.atomic.AtomicLong;

public final class LanSecurityCheck {
    private static int checks;
    private static void check(boolean result,String message){checks++;if(!result)throw new AssertionError(message);}
    public static void main(String[] arguments){
        AtomicLong time=new AtomicLong(1_000_000);
        LanSecurity security=new LanSecurity("192.168.1.8:8765",time::get);
        check(security.pairing().matches("[0-9]{6}"),"six decimal digits");
        check(LanSecurity.pairingCode(new java.security.SecureRandom() {
            @Override public int nextInt(int bound) { return 7; }
        }).equals("000007"),"leading zero retained");
        check(security.csrf().matches("[0-9a-f]{64}"),"independent 256-bit write token");
        check(security.host("192.168.1.8:8765"),"exact bound authority");
        for(String bad:new String[]{"evil.com:8765","192.168.1.8","192.168.1.8:8765.evil","127.0.0.1:8765"})check(!security.host(bad),"DNS rebinding host");
        check(!security.origin("https://192.168.1.8:8765"),"exact HTTP origin");
        check(!security.authenticated(null),"unauthenticated data rejected");
        check(security.pair("computer","wrong")==null,"wrong pair");
        String token=security.pair("computer",security.pairing());check(token!=null&&token.length()==64,"256-bit session");
        String cookie="other=test; chrona_session="+token;
        check(security.authenticated(cookie),"HttpOnly session parser");
        check(!security.authenticated("chrona_session="+token+"suffix"),"token equality");
        check(security.write("http://192.168.1.8:8765",security.csrf()),"authenticated same-origin write");
        check(!security.write("http://evil.com",security.csrf()),"cross-origin POST rejected");
        check(!security.write(null,security.csrf()),"missing origin rejected");
        check(!security.write("http://192.168.1.8:8765","wrong"),"missing CSRF rejected");
        security.logout(cookie);check(!security.authenticated(cookie),"logout revokes token");
        for(int i=0;i<4;i++)security.pair("computer","wrong");check(security.pair("computer",security.pairing())==null,"per-peer attempt cap");
        time.addAndGet(60_001);token=security.pair("computer",security.pairing());check(token!=null,"limiter resets");
        for(int i=0;i<30;i++)security.pair("peer-"+i,"wrong");check(security.pair("new-peer",security.pairing())==null,"global attempt cap");
        String originalCode=security.pairing();
        time.addAndGet(10*60_000L);
        check(security.pair("fresh-peer",originalCode)==null&&security.pairing().isEmpty(),"pairing expires after ten minutes");
        check(security.authenticated("chrona_session="+token),"pairing expiry preserves established session");
        time.addAndGet(12*60*60_000L);check(!security.authenticated("chrona_session="+token),"session expires");
        security=new LanSecurity("192.168.1.8:8765",time::get);
        token=security.pair("computer",security.pairing());check(token!=null,"new server session");security.close();
        check(!security.authenticated("chrona_session="+token),"service stop revokes all sessions");
        check(security.pair("computer",security.pairing())==null,"closed service cannot pair");
        check(!security.write("http://192.168.1.8:8765",security.csrf()),"closed write rejected");
        System.out.println("LAN security: "+checks+" checks passed");
    }
}
