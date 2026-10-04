package com.donglan.chrona.sync;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A bounded exchange. Callers persist the returned state before applying it to local UI data. */
public final class SyncExchange {
    private static final long MAX_ALL_HEAD_BYTES = 128L * 1024 * 1024;
    private SyncExchange() { }

    public static SyncState exchange(SyncState local, File blobDirectory, WebDavClient client) throws IOException {
        try {
            if (!blobDirectory.isDirectory() && !blobDirectory.mkdirs()) throw new IOException("无法创建同步附件目录");
            SyncState merged = local.copy();
            client.ensureRoot();
            Map<String, String> heads = client.listHeads();
            String ownName = local.deviceId + ".json", ownEtag = null;
            Set<String> remoteBlobs = new HashSet<>(), validated = new HashSet<>();
            Map<String, Long> sizes = new HashMap<>();
            long total = 0;
            validate(merged, validated, sizes);
            for (String filename : heads.keySet()) {
                WebDavClient.Head downloaded = client.readHead(filename);
                total += downloaded.bytes.length;
                if (total > MAX_ALL_HEAD_BYTES) throw new IOException("云端同步记录总量过大");
                String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(downloaded.bytes)).toString();
                SyncState remote = SyncState.fromJson(new JSONObject(text));
                if (!filename.equals(remote.deviceId + ".json")) throw new IOException("云端设备身份不一致");
                validate(remote, validated, sizes);
                remoteBlobs.addAll(attachments(remote).keySet());
                merged.merge(remote);
                if (ownName.equals(filename)) ownEtag = downloaded.etag;
            }
            Map<String, Long> needed = attachments(merged);
            for (Map.Entry<String, Long> item : needed.entrySet()) {
                String sha = item.getKey();
                File file = new File(blobDirectory, sha);
                if (!file.isFile()) {
                    if (!remoteBlobs.contains(sha)) throw new IOException("本机附件缺失，请先恢复附件后同步");
                    client.getBlob(sha, file);
                }
                if (file.length() != item.getValue() || file.length() > TaskCodec.MAX_ATTACHMENT_BYTES
                        || !sha.equals(SyncState.sha256(Files.readAllBytes(file.toPath()))))
                    throw new IOException("附件校验失败，同步未应用");
                // A published head promises its blobs were stored first. Avoid re-uploading every file on each poll.
                if (!remoteBlobs.contains(sha)) client.putBlob(sha, file);
            }
            byte[] body = merged.toJson().toString().getBytes(StandardCharsets.UTF_8);
            if (body.length > WebDavClient.HEAD_LIMIT) throw new IOException("同步索引超过 32 MB，请减少记录或附件元数据");
            client.writeHead(ownName, body, ownEtag);
            return merged;
        } catch (IOException exception) { throw exception; }
        catch (Exception exception) { throw new IOException("同步内容无效，原有数据已保留", exception); }
    }

    private static void validate(SyncState state, Set<String> validated, Map<String, Long> sizes) throws IOException {
        try {
            for (Map.Entry<String, List<SyncState.Version>> entry : state.records().entrySet()) {
                if (!entry.getKey().matches("task_[0-9a-fA-F-]{36}|term_[0-9]{4}_[0-3]"))
                    throw new IOException("云端记录身份不受支持");
                for (SyncState.Version version : entry.getValue()) if (!version.deleted) {
                    String fingerprint = SyncState.sha256(SyncState.canonical(version.payload).getBytes(StandardCharsets.UTF_8));
                    TaskCodec.validateId(entry.getKey(), version.payload);
                    if (validated.add(fingerprint)) TaskCodec.validate(version.payload);
                }
            }
            for (Map.Entry<String, Long> attachment : attachments(state).entrySet()) {
                Long existing = sizes.putIfAbsent(attachment.getKey(), attachment.getValue());
                if (existing != null && !existing.equals(attachment.getValue())) throw new IOException("附件长度记录不一致");
            }
        } catch (IOException exception) { throw exception; }
        catch (Exception exception) { throw new IOException("同步记录校验失败", exception); }
    }

    public static Map<String, Long> attachments(SyncState state) throws IOException {
        try {
            Map<String, Long> result = new HashMap<>();
            for (List<SyncState.Version> versions : state.records().values()) for (SyncState.Version version : versions) {
                if (version.deleted || !"task".equals(version.payload.optString("kind"))) continue;
                JSONArray files = version.payload.getJSONArray("attachments");
                for (int i = 0; i < files.length(); i++) {
                    JSONObject file = files.getJSONObject(i);
                    String sha = file.getString("sha"); long size = file.getLong("size");
                    if (!sha.matches("[0-9a-f]{64}") || size < 0 || size > TaskCodec.MAX_ATTACHMENT_BYTES)
                        throw new IOException("附件元数据无效");
                    Long previous = result.putIfAbsent(sha, size);
                    if (previous != null && previous != size) throw new IOException("附件大小不一致");
                }
            }
            return result;
        } catch (IOException exception) { throw exception; }
        catch (Exception exception) { throw new IOException("附件列表无效", exception); }
    }
}
