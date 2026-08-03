/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone. If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.testing;

import baritone.Baritone;
import baritone.api.Settings;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.SettingsUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Writes a suite run up twice: markdown to read, JSON to parse.
 * <p>
 * Both land next to a {@code latest.*} copy that is overwritten every run, so that whatever reads
 * these does not have to guess at a filename. The timestamped copies are what makes two runs
 * comparable when a fix is supposed to have changed something.
 */
final class TestReport {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private TestReport() {}

    /**
     * @return The path of the timestamped markdown report
     */
    static Path write(Path directory, List<ScenarioResult> results, BetterBlockPos suiteOrigin,
                      boolean complete) throws IOException {
        Files.createDirectories(directory);
        ZonedDateTime now = ZonedDateTime.now();

        // An in-progress report only ever overwrites latest.*. It is rewritten after every scenario,
        // so keeping timestamped copies would leave several hundred files per run, and anything
        // reading these needs one path that always holds the newest state anyway.
        Path latestMarkdown = directory.resolve("latest.md");
        Files.write(latestMarkdown,
                renderMarkdown(results, suiteOrigin, now, complete).getBytes(StandardCharsets.UTF_8));
        Files.write(directory.resolve("latest.json"),
                renderJson(results, suiteOrigin, now, complete).getBytes(StandardCharsets.UTF_8));
        if (!complete) {
            return latestMarkdown;
        }

        String stamp = FILE_STAMP.format(now);
        Path markdown = directory.resolve("report-" + stamp + ".md");
        Files.copy(latestMarkdown, markdown, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(directory.resolve("latest.json"), directory.resolve("report-" + stamp + ".json"),
                StandardCopyOption.REPLACE_EXISTING);
        return markdown;
    }

    private static String version() {
        String version = TestReport.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }

    private static String renderMarkdown(List<ScenarioResult> results, BetterBlockPos suiteOrigin,
                                        ZonedDateTime now, boolean complete) {
        long failures = results.stream().filter(ScenarioResult::countsAsFailure).count();
        StringBuilder out = new StringBuilder();
        out.append("# Baritone test run\n\n");
        if (!complete) {
            out.append("> **In progress.** This run has not finished; results so far.\n\n");
        }
        out.append("- **When:** ").append(now).append('\n');
        out.append("- **Baritone:** ").append(version()).append('\n');
        out.append("- **Suite origin:** ").append(suiteOrigin).append('\n');
        out.append("- **Result:** ").append(results.size() - failures).append('/').append(results.size())
                .append(failures == 0 ? " passed\n" : " passed, **see failures below**\n");
        out.append('\n');

        out.append("| Scenario | Result | Ticks | Budget |\n|---|---|---:|---:|\n");
        for (ScenarioResult result : results) {
            out.append("| ").append(result.name)
                    .append(" | ").append(result.status)
                    .append(" | ").append(result.ticks)
                    .append(" | ").append(result.tickBudget)
                    .append(" |\n");
        }
        out.append('\n');

        for (ScenarioResult result : results) {
            out.append("## ").append(result.name).append(" -- ").append(result.status).append("\n\n");
            out.append(result.message).append("\n\n");
            if (!result.getNotes().isEmpty()) {
                out.append("Notes:\n\n");
                for (String note : result.getNotes()) {
                    out.append("- ").append(note).append('\n');
                }
                out.append('\n');
            }
        }

        // Settings that differ from default are the first thing to check when a run disagrees with
        // a previous one, and they are not otherwise recoverable from the report.
        out.append("## Modified settings at the end of the run\n\n```\n");
        for (Settings.Setting setting : SettingsUtil.modifiedSettings(Baritone.settings())) {
            out.append(SettingsUtil.settingToString(setting)).append('\n');
        }
        out.append("\n```\n");
        return out.toString();
    }

    private static String renderJson(List<ScenarioResult> results, BetterBlockPos suiteOrigin,
                                    ZonedDateTime now, boolean complete) {
        long failures = results.stream().filter(ScenarioResult::countsAsFailure).count();
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        out.append("  \"complete\": ").append(complete).append(",\n");
        out.append("  \"when\": \"").append(escape(now.toString())).append("\",\n");
        out.append("  \"version\": \"").append(escape(version())).append("\",\n");
        out.append("  \"suiteOrigin\": \"").append(escape(suiteOrigin.toString())).append("\",\n");
        out.append("  \"passed\": ").append(results.size() - failures).append(",\n");
        out.append("  \"total\": ").append(results.size()).append(",\n");
        out.append("  \"scenarios\": [\n");
        for (int i = 0; i < results.size(); i++) {
            ScenarioResult result = results.get(i);
            out.append("    {\n");
            out.append("      \"name\": \"").append(escape(result.name)).append("\",\n");
            out.append("      \"status\": \"").append(result.status).append("\",\n");
            out.append("      \"message\": \"").append(escape(result.message)).append("\",\n");
            out.append("      \"ticks\": ").append(result.ticks).append(",\n");
            out.append("      \"tickBudget\": ").append(result.tickBudget).append(",\n");
            out.append("      \"notes\": [");
            List<String> notes = result.getNotes();
            for (int n = 0; n < notes.size(); n++) {
                out.append(n == 0 ? "\n        \"" : ",\n        \"").append(escape(notes.get(n))).append('"');
            }
            out.append(notes.isEmpty() ? "]\n" : "\n      ]\n");
            out.append(i == results.size() - 1 ? "    }\n" : "    },\n");
        }
        out.append("  ]\n}\n");
        return out.toString();
    }

    private static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
