package com.donglan.chrona;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** In-memory, per-server credentials; no secrets in preferences, URLs or logs. */
final class LanSecurity {
    private static final long WINDOW = 60_000L, SESSION_LIFETIME = 12 * 60 * 60_000L;
    private static final long PAIRING_LIFETIME = 10 * 60_000L;
    static final long APPROVAL_LIFETIME = 2 * 60_000L;
    private static final int MAX_APPROVALS = 8, MAX_SESSIONS = 16;
    private final LongSupplier clock;
    private final String authority;
    private final long pairingIssued;
    private final Map<String, Attempt> attempts = new HashMap<>();
    private final Map<String, Long> sessions = new HashMap<>();
    private final Map<String, Approval> approvals = new LinkedHashMap<>();
    private String pairing = pairingCode(new SecureRandom()), csrf = random(32);
    private long globalStart;
    private int globalCount;
    private boolean closed;
    private static final class Attempt { long start; int count; Attempt(long now) { start = now; } }
    static final class Approval {
        final String id, remote, device, verification;
        final long expiresAt;
        private String state = "pending";
        Approval(String id, String remote, String device, String verification, long expiresAt) {
            this.id = id; this.remote = remote; this.device = device;
            this.verification = verification; this.expiresAt = expiresAt;
        }
    }
    static final class ApprovalResult {
        final String state, token;
        ApprovalResult(String state, String token) { this.state = state; this.token = token; }
    }
    LanSecurity(String authority) { this(authority, System::currentTimeMillis); }
    LanSecurity(String authority, LongSupplier clock) { this.authority = authority; this.clock = clock; pairingIssued = clock.getAsLong(); }
    synchronized String pairing() { return clock.getAsLong() - pairingIssued < PAIRING_LIFETIME ? pairing : ""; }
    synchronized String csrf() { return csrf; }
    boolean host(String host) { return authority.equals(host); }
    boolean origin(String origin) { return ("http://" + authority).equals(origin); }
    synchronized String pair(String remote, String code) {
        long now = clock.getAsLong();
        if (closed || now - pairingIssued >= PAIRING_LIFETIME) return null;
        if (!attempt(remote, now) || !equal(pairing, code) || sessions.size() >= MAX_SESSIONS) return null;
        return newSession(now);
    }
    private boolean attempt(String remote, long now) {
        attempts.entrySet().removeIf(entry -> now - entry.getValue().start >= WINDOW);
        sessions.entrySet().removeIf(entry -> now - entry.getValue() >= SESSION_LIFETIME);
        if (now - globalStart >= WINDOW) { globalStart = now; globalCount = 0; }
        if (++globalCount > 20 || (attempts.size() >= 128 && !attempts.containsKey(remote))) return false;
        Attempt attempt = attempts.computeIfAbsent(remote, key -> new Attempt(now));
        return ++attempt.count <= 5;
    }
    private String newSession(long now) {
        String token = random(32); sessions.put(token, now); return token;
    }
    /** Only this high-entropy handle, not the visible comparison digits, can claim approval. */
    synchronized Approval requestApproval(String remote, String userAgent) {
        long now = clock.getAsLong(); expireApprovals(now);
        if (closed || !attempt(remote, now) || sessions.size() >= MAX_SESSIONS
                || approvals.size() >= MAX_APPROVALS) return null;
        for (Approval pending : approvals.values())
            if (pending.remote.equals(remote) && pending.state.equals("pending")) return null;
        String verification = String.format(java.util.Locale.ROOT, "%04d", new SecureRandom().nextInt(10_000));
        Approval request = new Approval(random(32), remote, device(userAgent), verification, now + APPROVAL_LIFETIME);
        approvals.put(request.id, request); return request;
    }
    synchronized Approval pendingApproval() {
        expireApprovals(clock.getAsLong());
        if (!closed) for (Approval request : approvals.values()) if (request.state.equals("pending")) return request;
        return null;
    }
    synchronized boolean decideApproval(String id, boolean allow) {
        expireApprovals(clock.getAsLong());
        Approval request = approvals.get(id);
        if (closed || request == null || !request.state.equals("pending")) return false;
        request.state = allow ? "approved" : "denied"; return true;
    }
    synchronized ApprovalResult pollApproval(String remote, String id) {
        long now = clock.getAsLong(); expireApprovals(now);
        Approval request = approvals.get(id);
        if (closed || request == null || !request.remote.equals(remote)) return new ApprovalResult("expired", null);
        if (request.state.equals("pending")) return new ApprovalResult("pending", null);
        approvals.remove(id);
        if (request.state.equals("denied")) return new ApprovalResult("denied", null);
        sessions.entrySet().removeIf(entry -> now - entry.getValue() >= SESSION_LIFETIME);
        return sessions.size() >= MAX_SESSIONS ? new ApprovalResult("full", null)
                : new ApprovalResult("approved", newSession(now));
    }
    synchronized boolean cancelApproval(String remote, String id) {
        Approval request = approvals.get(id);
        if (closed || request == null || !request.remote.equals(remote)) return false;
        approvals.remove(id); return true;
    }
    private void expireApprovals(long now) { approvals.entrySet().removeIf(entry -> now >= entry.getValue().expiresAt); }
    private static String device(String agent) {
        String value = agent == null ? "" : agent;
        String browser = value.contains("Edg/") || value.contains("EdgA/") || value.contains("EdgiOS/") ? "Microsoft Edge"
                : value.contains("Firefox/") || value.contains("FxiOS/") ? "Firefox"
                : value.contains("OPR/") ? "Opera"
                : value.contains("Chrome/") || value.contains("CriOS/") ? "Chrome"
                : value.contains("Safari/") ? "Safari" : "浏览器";
        String system = value.contains("Windows") ? "Windows" : value.contains("Android") ? "Android"
                : value.contains("iPhone") || value.contains("iPad") ? "iOS"
                : value.contains("Macintosh") ? "macOS" : value.contains("Linux") ? "Linux" : "";
        return browser + (system.isEmpty() ? "" : " · " + system);
    }
    synchronized boolean authenticated(String cookie) {
        if (closed || cookie == null) return false;
        for (String part : cookie.split(";")) {
            String item = part.trim();
            if (!item.startsWith("chrona_session=")) continue;
            String token = item.substring(15);
            Long issued = sessions.get(token);
            return issued != null && clock.getAsLong() - issued < SESSION_LIFETIME;
        }
        return false;
    }
    synchronized boolean write(String origin, String supplied) { return !closed && origin(origin) && equal(csrf, supplied); }
    synchronized void logout(String cookie) {
        if (cookie != null) for (String part : cookie.split(";")) {
            String item = part.trim(); if (item.startsWith("chrona_session=")) sessions.remove(item.substring(15));
        }
    }
    synchronized void close() { closed = true; sessions.clear(); attempts.clear(); approvals.clear(); pairing = ""; csrf = ""; }
    private static boolean equal(String a, String b) {
        return b != null && MessageDigest.isEqual(a.getBytes(java.nio.charset.StandardCharsets.UTF_8), b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static String random(int count) {
        byte[] bytes = new byte[count]; new SecureRandom().nextBytes(bytes);
        StringBuilder result = new StringBuilder(); for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
    static String pairingCode(SecureRandom random) {
        return String.format(java.util.Locale.ROOT, "%06d", random.nextInt(1_000_000));
    }
}
