package com.koval.trainingplannerbackend.skills;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping
    public List<SkillSummary> list() {
        return skillService.listSkills();
    }

    @GetMapping("/koval-skills.zip")
    public void downloadAll(HttpServletResponse response) throws IOException {
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"koval-skills.zip\"");
        skillService.writeAllSkillsZip(response.getOutputStream());
        response.flushBuffer();
    }

    @GetMapping("/{skillName:[a-zA-Z0-9-]+}.zip")
    public ResponseEntity<byte[]> downloadOne(@PathVariable String skillName) {
        byte[] zip = skillService.getSkillZip(skillName);
        if (zip == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + skillName + ".zip\"")
                .body(zip);
    }
}
