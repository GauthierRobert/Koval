package com.koval.trainingplannerbackend.skills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class SkillService {

    private static final Logger log = LoggerFactory.getLogger(SkillService.class);

    private static final String SKILL_ENTRY_PATTERN = "classpath:/skills/*/SKILL.md";
    private static final String SKILL_ASSETS_PATTERN = "classpath:/skills/%s/**";
    private static final String NAME_KEY = "name:";
    private static final String DESC_KEY = "description:";

    /** Explicit list of skill folder names — used as fallback when classpath scanning
     *  returns nothing (e.g. GraalVM native image). Keep in sync with the skills/
     *  directory and NativeImageHints. Optional resources/ files for a skill must also
     *  be listed in KNOWN_SKILL_ASSETS. */
    private static final String[] KNOWN_SKILLS = {
            "koval-analyze-last-ride",
            "koval-athlete-onboarding",
            "koval-coach-onboarding",
            "koval-coach-weekly-review",
            "koval-create-workout",
            "koval-find-workout",
            "koval-form-check",
            "koval-plan-my-week",
            "koval-power-curve-report",
            "koval-prep-race",
            "koval-zone-setup"
    };

    private static final Map<String, String[]> KNOWN_SKILL_ASSETS = Map.of(
            "koval-athlete-onboarding", new String[]{"resources/athlete-profile.template.md"},
            "koval-coach-onboarding", new String[]{"resources/coach-profile.template.md"}
    );

    private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    private volatile List<SkillSummary> cachedSummaries;
    private volatile Map<String, List<SkillFile>> cachedFiles;

    public List<SkillSummary> listSkills() {
        ensureLoaded();
        return cachedSummaries;
    }

    /** Returns a zip of the named skill (folder layout: {@code <skillName>/SKILL.md} + resources),
     *  or {@code null} when the skill does not exist. */
    public byte[] getSkillZip(String skillName) {
        ensureLoaded();
        List<SkillFile> files = cachedFiles.get(skillName);
        if (files == null) return null;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeFilesAsZip(out, files);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build zip for skill " + skillName, e);
        }
    }

    public void writeAllSkillsZip(OutputStream out) throws IOException {
        ensureLoaded();
        List<SkillFile> all = cachedFiles.values().stream()
                .flatMap(Collection::stream)
                .toList();
        writeFilesAsZip(out, all);
    }

    private void writeFilesAsZip(OutputStream out, List<SkillFile> files) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (SkillFile file : files) {
                zip.putNextEntry(new ZipEntry(file.entryName()));
                try (var in = file.resource().getInputStream()) {
                    in.transferTo(zip);
                }
                zip.closeEntry();
            }
        }
    }

    private synchronized void ensureLoaded() {
        if (cachedSummaries != null) return;

        List<SkillSummary> summaries = new ArrayList<>();
        Map<String, List<SkillFile>> filesBySkill = new LinkedHashMap<>();

        // Wildcard scanning first (JVM). May fail in native image.
        try {
            Resource[] skillMds = resolver.getResources(SKILL_ENTRY_PATTERN);
            for (Resource md : skillMds) {
                String skillName = extractSkillNameFromUri(md);
                if (skillName == null) continue;
                try {
                    SkillSummary summary = buildSummary(skillName, md);
                    List<SkillFile> files = collectSkillFilesByScan(skillName);
                    summaries.add(summary);
                    filesBySkill.put(skillName, files);
                } catch (IOException e) {
                    log.warn("Failed to load skill {}: {}", skillName, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Wildcard classpath scanning for skills failed: {}", e.getMessage());
        }

        // Fallback: explicit per-skill loading (reliable in native image).
        if (summaries.isEmpty()) {
            log.info("Falling back to explicit skill file loading");
            for (String skillName : KNOWN_SKILLS) {
                Resource md = new ClassPathResource("skills/" + skillName + "/SKILL.md");
                if (!md.exists()) continue;
                try {
                    summaries.add(buildSummary(skillName, md));
                    filesBySkill.put(skillName, collectSkillFilesFromKnown(skillName, md));
                } catch (IOException e) {
                    log.warn("Failed to load skill {}: {}", skillName, e.getMessage());
                }
            }
        }

        summaries.sort((a, b) -> a.filename().compareTo(b.filename()));
        this.cachedSummaries = Collections.unmodifiableList(summaries);
        this.cachedFiles = Collections.unmodifiableMap(filesBySkill);
    }

    private SkillSummary buildSummary(String skillName, Resource skillMd) throws IOException {
        String content = new String(skillMd.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return parseFrontmatter(skillName, content);
    }

    private List<SkillFile> collectSkillFilesByScan(String skillName) throws IOException {
        List<SkillFile> out = new ArrayList<>();
        Resource[] assets = resolver.getResources(String.format(SKILL_ASSETS_PATTERN, skillName));
        String prefix = "/skills/" + skillName + "/";
        for (Resource asset : assets) {
            if (!asset.isReadable()) continue;
            String uri = asset.getURI().toString();
            int idx = uri.indexOf(prefix);
            if (idx < 0) continue;
            String rel = uri.substring(idx + prefix.length());
            if (rel.isEmpty() || rel.endsWith("/")) continue;
            out.add(new SkillFile(skillName + "/" + rel, asset));
        }
        out.sort(Comparator.comparing(SkillFile::entryName));
        return out;
    }

    private List<SkillFile> collectSkillFilesFromKnown(String skillName, Resource skillMd) {
        String[] extra = KNOWN_SKILL_ASSETS.getOrDefault(skillName, new String[0]);
        Stream<SkillFile> entry = Stream.of(new SkillFile(skillName + "/SKILL.md", skillMd));
        Stream<SkillFile> assets = Arrays.stream(extra)
                .map(rel -> new SkillFile(skillName + "/" + rel,
                        new ClassPathResource("skills/" + skillName + "/" + rel)))
                .filter(sf -> sf.resource().exists());
        return Stream.concat(entry, assets).toList();
    }

    private String extractSkillNameFromUri(Resource skillMd) {
        try {
            String uri = skillMd.getURI().toString();
            int end = uri.lastIndexOf("/SKILL.md");
            if (end < 0) return null;
            int start = uri.lastIndexOf('/', end - 1);
            if (start < 0) return null;
            return uri.substring(start + 1, end);
        } catch (IOException e) {
            return null;
        }
    }

    private SkillSummary parseFrontmatter(String skillName, String content) {
        String name = skillName;
        String description = "";

        if (!content.startsWith("---")) {
            return new SkillSummary(skillName, name, description);
        }

        int end = content.indexOf("\n---", 3);
        if (end < 0) return new SkillSummary(skillName, name, description);

        String frontmatter = content.substring(3, end);
        StringBuilder descBuilder = new StringBuilder();
        boolean inDescription = false;

        for (String rawLine : frontmatter.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith(NAME_KEY)) {
                inDescription = false;
                name = line.substring(NAME_KEY.length()).trim();
            } else if (line.startsWith(DESC_KEY)) {
                inDescription = true;
                String first = line.substring(DESC_KEY.length()).trim();
                if (!first.isEmpty()) descBuilder.append(first);
            } else if (inDescription && rawLine.startsWith(" ")) {
                if (descBuilder.length() > 0) descBuilder.append(' ');
                descBuilder.append(line);
            } else if (line.contains(":")) {
                inDescription = false;
            }
        }

        description = descBuilder.toString().trim();
        return new SkillSummary(skillName, name, description);
    }

    private record SkillFile(String entryName, Resource resource) {}
}
