package com.donglan.chrona;

import java.util.concurrent.atomic.AtomicLong;

public final class LanSecurityCheck {
    private static int checks;
    private static void check(boolean result,String message){checks++;if(!result)throw new AssertionError(message);}
    public static void main(String[] arguments) throws Exception {
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
        approvals();
        System.out.println("LAN security: "+checks+" checks passed");
    }
    private static void approvals() throws Exception {
        AtomicLong time = new AtomicLong(1_000_000);
        LanSecurity security = new LanSecurity("192.168.1.8:8765", time::get);
        LanSecurity.Approval request = security.requestApproval("computer", "Mozilla/5.0 (Windows NT 10.0) Chrome/150.0 Edg/150.0");
        check(request != null && request.id.matches("[0-9a-f]{64}") && request.verification.matches("[0-9]{4}"), "independent high-entropy handle and visible comparison digits");
        check(request.device.equals("Microsoft Edge · Windows"), "bounded browser identity from reported agent");
        check(!security.authenticated("chrona_session=" + request.id), "pending handle is not a data session");
        check(security.pendingApproval() == request, "phone sees pending request");
        check(security.requestApproval("computer", "other") == null, "same source cannot stack pending popups");
        check(security.pollApproval("other", request.id).state.equals("expired") && !security.cancelApproval("other", request.id), "other remote cannot claim or cancel");
        check(security.pollApproval("computer", request.id).state.equals("pending"), "polling cannot grant access");
        check(security.decideApproval(request.id, true) && security.pendingApproval() == null, "native approval removes request from prompt queue");
        check(!security.decideApproval(request.id, false), "decision is immutable");
        LanSecurity.ApprovalResult result = security.pollApproval("computer", request.id);
        check(result.state.equals("approved") && security.authenticated("chrona_session=" + result.token), "approved owner claims normal session");
        check(security.pollApproval("computer", request.id).state.equals("expired"), "approved handle is consumed once");
        request = security.requestApproval("denied", "<script>untrusted</script>");
        check(request.device.equals("浏览器"), "raw user agent never enters native identity");
        check(security.decideApproval(request.id, false) && security.pollApproval("denied", request.id).state.equals("denied"), "denial is explicit and issues no cookie");
        request = security.requestApproval("cancelled", "");
        check(security.decideApproval(request.id, true) && security.cancelApproval("cancelled", request.id), "browser cancel revokes approved but unclaimed grant");
        check(security.pollApproval("cancelled", request.id).token == null, "cancelled grant cannot be claimed");
        request = security.requestApproval("expires", ""); time.addAndGet(LanSecurity.APPROVAL_LIFETIME);
        check(security.pendingApproval() == null && !security.decideApproval(request.id, true), "exact deadline expires native consent");
        check(security.pollApproval("expires", request.id).state.equals("expired"), "expired handle stays unauthenticated");
        time.addAndGet(10 * 60_000L);
        check(security.pairing().isEmpty(), "legacy six-digit code has expired");
        request = security.requestApproval("fresh", "Safari/1");
        check(request != null && security.decideApproval(request.id, true), "native consent remains usable after code expiry");
        final LanSecurity shared = security; final String id = request.id;
        java.util.concurrent.ExecutorService workers = java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            java.util.List<java.util.concurrent.Future<LanSecurity.ApprovalResult>> claims = new java.util.ArrayList<>();
            for (int i=0; i<30; i++) claims.add(workers.submit(() -> shared.pollApproval("fresh", id)));
            int granted=0; for (var claim:claims) if (claim.get().token != null) granted++;
            check(granted == 1, "concurrent claims create exactly one session");
        } finally { workers.shutdownNow(); }
        request = security.requestApproval("closed", ""); security.close();
        check(security.pendingApproval() == null && !security.decideApproval(request.id, true)
                && security.pollApproval("closed", request.id).token == null && security.requestApproval("closed", "") == null, "service stop revokes pending and approved access");
        security = new LanSecurity("192.168.1.8:8765", time::get);
        for (int i=0;i<8;i++) check(security.requestApproval("waiting-"+i, "") != null, "bounded queue accepts request " + i);
        check(security.requestApproval("overflow", "") == null, "pending storage is bounded");
        time.addAndGet(LanSecurity.APPROVAL_LIFETIME);
        check(security.requestApproval("after-expiry", "") != null, "expired requests release capacity");
        security = new LanSecurity("192.168.1.8:8765", time::get);
        for (int i=0;i<5;i++) { request=security.requestApproval("limited", ""); check(request != null, "allowed source attempt " + i); security.cancelApproval("limited", request.id); }
        check(security.requestApproval("limited", "") == null && security.pair("limited", security.pairing()) == null, "approval and manual pairing share attempt cap");
        security = new LanSecurity("192.168.1.8:8765", time::get);
        for (int i=0;i<16;i++) { request=security.requestApproval("session-"+i, ""); security.decideApproval(request.id, true); check(security.pollApproval("session-"+i, request.id).token != null, "normal session capacity " + i); }
        check(security.requestApproval("session-overflow", "") == null, "approval respects existing session cap");
    }
}
