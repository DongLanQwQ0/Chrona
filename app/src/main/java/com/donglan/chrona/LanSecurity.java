package com.donglan.chrona;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** In-memory, per-server credentials; no secrets in preferences, URLs or logs. */
final class LanSecurity {
    private static final long WINDOW = 60_000L, SESSION_LIFETIME = 12 * 60 * 60_000L;
    private final LongSupplier clock;
    private final String authority;
    private final Map<String, Attempt> attempts = new HashMap<>();
    private final Map<String, Long> sessions = new HashMap<>();
    private String pairing = random(16), csrf = random(32);
    private long globalStart;
    private int globalCount;
    private boolean closed;
    private static final class Attempt { long start; int count; Attempt(long now) { start = now; } }
    LanSecurity(String authority) { this(authority, System::currentTimeMillis); }
    LanSecurity(String authority, LongSupplier clock) { this.authority = authority; this.clock = clock; }
    synchronized String pairing() { return pairing; }
    synchronized String csrf() { return csrf; }
    boolean host(String host) { return authority.equals(host); }
    boolean origin(String origin) { return ("http://" + authority).equals(origin); }
    synchronized String pair(String remote, String code) {
        long now = clock.getAsLong();
        if (closed) return null;
        attempts.entrySet().removeIf(entry -> now - entry.getValue().start >= WINDOW);
        sessions.entrySet().removeIf(entry -> now - entry.getValue() >= SESSION_LIFETIME);
        if (now - globalStart >= WINDOW) { globalStart = now; globalCount = 0; }
        if (++globalCount > 20 || (attempts.size() >= 128 && !attempts.containsKey(remote))) return null;
        Attempt attempt = attempts.computeIfAbsent(remote, key -> new Attempt(now));
        if (++attempt.count > 5 || !equal(pairing, code) || sessions.size() >= 16) return null;
        String token = random(32); sessions.put(token, now); return token;
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
    synchronized void close() { closed = true; sessions.clear(); attempts.clear(); pairing = ""; csrf = ""; }
    private static boolean equal(String a, String b) {
        return b != null && MessageDigest.isEqual(a.getBytes(java.nio.charset.StandardCharsets.UTF_8), b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static String random(int count) {
        byte[] bytes = new byte[count]; new SecureRandom().nextBytes(bytes);
        StringBuilder result = new StringBuilder(); for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
}
