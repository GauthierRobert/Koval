package com.koval.trainingplannerbackend.skills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class SkillService {

    private static final Logger log = LoggerFactory.getLogger(SkillService.class);

    private static final String SKILLS_PATTERN = "classpath:/skills/*.md";
    private static final String NAME_KEY = "name:";
    private static final String DESC_KEY = "description:";

    /** Explicit list of skill files — used as fallback when classpath scanning
     *  returns nothing (e.g. GraalVM native image). Keep in sync with the
     *  skills/ directory and NativeImageHints. */
    private static final String[] KNOWN_SKILLS = {
            "koval-analyze-last-ride.md",
            "koval-athlete-onboarding.md",
            "koval-coach-onboarding.md",
            "koval-coach-weekly-review.md",
            "koval-create-workout.md",
            "koval-find-workout.md",
            "koval-form-check.md",
            "koval-plan-my-week.md",
            "koval-power-curve-report.md",
            "koval-prep-race.md",
            "koval-zone-setup.md"
    };

    private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    private volatile List<SkillSummary> cachedSummaries;
    private volatile Map<String, Resource> cachedResources;

    public List<SkillSummary> listSkills() {
        ensureLoaded();
        return cachedSummaries;
    }

    public Resource getSkillResource(String filename) {
        ensureLoaded();
        return cachedResources.get(filename);
    }

    public void writeZip(OutputStream out) throws IOException {
        ensureLoaded();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, Resource> entry : cachedResources.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                try (var in = entry.getValue().getInputStream()) {
                    in.transferTo(zip);
                }
                zip.closeEntry();
            }
        }
    }

    private synchronized void ensureLoaded() {
        if (cachedSummaries != null) return;

        List<SkillSummary> summaries = new ArrayList<>();
        Map<String, Resource> resources = new java.util.LinkedHashMap<>();

        // Try wildcard classpath scanning first (works on JVM, may fail in native image)
        try {
            Resource[] found = resolver.getResources(SKILLS_PATTERN);
            for (Resource res : found) {
                String filename = res.getFilename();
                if (filename == null || !filename.endsWith(".md")) continue;
                String content = new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                SkillSummary summary = parseFrontmatter(filename, content);
                summaries.add(summary);
                resources.put(filename, res);
            }
        } catch (IOException e) {
            log.warn("Wildcard classpath scanning for skills failed: {}", e.getMessage());
        }

        // Fallback: load each known skill individually (reliable in native images)
        if (summaries.isEmpty()) {
            log.info("Falling back to explicit skill file loading");
            for (String filename : KNOWN_SKILLS) {
                Resource res = new ClassPathResource("skills/" + filename);
                if (!res.exists()) continue;
                try {
                    String content = new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    summaries.add(parseFrontmatter(filename, content));
                    resources.put(filename, res);
                } catch (IOException e) {
                    log.warn("Failed to load skill {}: {}", filename, e.getMessage());
                }
            }
        }

        summaries.sort((a, b) -> a.filename().compareTo(b.filename()));
        this.cachedSummaries = Collections.unmodifiableList(summaries);
        this.cachedResources = Collections.unmodifiableMap(resources);
    }

    private SkillSummary parseFrontmatter(String filename, String content) {
        String name = filename.replace(".md", "");
        String description = "";

        if (!content.startsWith("---")) {
            return new SkillSummary(filename, name, description);
        }

        int end = content.indexOf("\n---", 3);
        if (end < 0) return new SkillSummary(filename, name, description);

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
                // YAML continuation line
                if (descBuilder.length() > 0) descBuilder.append(' ');
                descBuilder.append(line);
            } else if (line.contains(":")) {
                inDescription = false;
            }
        }

        description = descBuilder.toString().trim();
        return new SkillSummary(filename, name, description);
    }
}
