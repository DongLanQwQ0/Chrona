package com.donglan.chrona;

import com.donglan.chrona.data.EventCandidate;
import java.util.List;

/** Regression checks for stable schedule IDs and the public Release metadata contract. */
public final class GuideReleaseCheck {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static EventCandidate candidate(long id) {
        return new EventCandidate(id, 7, "日程 " + id, null, null, "Asia/Shanghai", "", "", null, true);
    }

    private static void rejects(String json) throws Exception {
        try { GitHubRelease.parse(json); }
        catch (Exception expected) { return; }
        throw new AssertionError("Invalid metadata accepted: " + json);
    }

    public static void main(String[] args) throws Exception {
        List<EventCandidate> items = List.of(candidate(91), candidate(12), candidate(55));
        require(CandidateSelection.indexOf(items, 12, 0) == 1, "Second schedule must open itself");
        require(CandidateSelection.indexOf(items, 55, 0) == 2, "Last schedule must open itself");
        require(CandidateSelection.indexOf(items, 91, 2) == 0, "Target ID overrides saved fallback");
        require(CandidateSelection.indexOf(items, 333, 2) == 2, "Unknown/other-task ID preserves selection");
        require(CandidateSelection.indexOf(items, -1, 90) == 2, "Invalid selection clamps");
        require(CandidateSelection.indexOf(items, 0, -5) == 0, "Negative selection clamps");
        require(CandidateSelection.indexOf(List.of(), 12, 2) == 0, "Empty capture is safe");
        require(GitHubRelease.newer("v0.13.71", "0.13.70"), "Next patch");
        require(GitHubRelease.newer("0.13.100", "0.13.99"), "Numeric comparison");
        require(GitHubRelease.newer("1.0.0", "0.99.99"), "Next major");
        require(!GitHubRelease.newer("v0.13.71", "0.13.71"), "Equal versions");
        require(!GitHubRelease.newer("0.13.70", "0.13.71"), "Older release");
        for (String invalid : new String[]{"", "v1.0", "nightly", "1.2.3-beta", "1.2.3.4"}) {
            try { GitHubRelease.version(invalid); throw new AssertionError("Invalid tag accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        String page = "https://github.com/DongLanQwQ0/Chrona/releases/tag/v0.13.71";
        String apk = "https://github.com/DongLanQwQ0/Chrona/releases/download/v0.13.71/Chrona.apk";
        String base = "{\"tag_name\":\"v0.13.71\",\"html_url\":\"" + page + "\",\"body\":\"修复跳转\\n使用引导\"";
        String asset = "{\"name\":\"Chrona.apk\",\"state\":\"uploaded\",\"size\":123,\"browser_download_url\":\"" + apk + "\"}";
        GitHubRelease release = GitHubRelease.parse(base + ",\"assets\":[{\"name\":\"source.zip\"}," + asset + "]}");
        require(release.version.equals("0.13.71") && release.apkUrl.equals(apk), "APK asset selection");
        require(release.notes.contains("\n") && release.pageUrl.equals(page), "Notes and page");
        require(GitHubRelease.parse(base + "}").apkUrl.isEmpty(), "Missing APK still shows release");
        require(GitHubRelease.parse(base.replace("\"修复跳转\\n使用引导\"", "null") + "}").notes.isEmpty(), "Null notes");
        require(GitHubRelease.parse(base + ",\"assets\":[" + asset.replace("uploaded", "new") + "]}")
                .apkUrl.isEmpty(), "Incomplete upload ignored");
        require(GitHubRelease.parse(base + ",\"assets\":[" + asset.replace(apk, "https://evil.example/Chrona.apk") + "]}")
                .apkUrl.isEmpty(), "External asset ignored");
        rejects(base + ",\"draft\":true}");
        rejects(base + ",\"prerelease\":true}");
        rejects(base.replace(page, "https://github.com/another/repo/releases/tag/v0.13.71") + "}");
        rejects(base.replace("v0.13.71", "nightly") + "}");
        rejects("{}");
        rejects("invalid JSON");
        System.out.println("Guide/Release checks passed: stable candidate IDs, numeric versions, metadata and asset validation");
    }
}
