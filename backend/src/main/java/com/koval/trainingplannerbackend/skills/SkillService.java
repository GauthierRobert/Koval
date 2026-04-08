package com.koval.trainingplannerbackend.skills;

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

    private static final String SKILLS_PATTERN = "classpath:/skills/*.md";
    private static final String NAME_KEY = "name:";
    private static final String DESC_KEY = "description:";

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
            throw new IllegalStateException("Failed to load skill resources", e);
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
