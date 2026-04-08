package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.auth.UserRole;
import com.koval.trainingplannerbackend.config.audit.AuditLog;
import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.training.group.Group;
import com.koval.trainingplannerbackend.training.group.GroupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Bulk athlete import from CSV files. Expected columns:
 * email, firstName?, lastName?, groupName?
 * Header row is optional (auto-detected if first row's first cell equals "email").
 */
@Service
public class AthleteImportService {

    private static final Logger log = LoggerFactory.getLogger(AthleteImportService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UserRepository userRepository;
    private final GroupService groupService;

    public AthleteImportService(UserRepository userRepository, GroupService groupService) {
        this.userRepository = userRepository;
        this.groupService = groupService;
    }

    public record ImportRowError(int row, String email, String reason) {}

    public record ImportResult(int processed, int added, int skipped, List<ImportRowError> errors) {}

    @AuditLog(action = "IMPORT_ATHLETES")
    public ImportResult importFromCsv(MultipartFile file, String coachId) {
        User coach = userRepository.findById(coachId)
                .orElseThrow(() -> new ResourceNotFoundException("User", coachId));
        if (coach.getRole() != UserRole.COACH) {
            throw new ForbiddenOperationException("Only coaches can import athletes");
        }

        List<ImportRowError> errors = new ArrayList<>();
        int processed = 0;
        int added = 0;
        int skipped = 0;
        Map<String, Group> groupCache = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int rowNum = 0;
            boolean headerSkipped = false;
            while ((line = reader.readLine()) != null) {
                rowNum++;
                // Strip UTF-8 BOM on first line
                if (rowNum == 1 && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                if (line.isBlank()) continue;

                List<String> cells = parseCsvLine(line);
                if (cells.isEmpty()) continue;

                // Auto-detect header
                if (!headerSkipped && rowNum == 1
                        && "email".equalsIgnoreCase(cells.get(0).trim())) {
                    headerSkipped = true;
                    continue;
                }

                processed++;
                String email = cells.get(0).trim().toLowerCase();
                String groupName = cells.size() >= 4 ? cells.get(3).trim() : "";

                if (!EMAIL_PATTERN.matcher(email).matches()) {
                    errors.add(new ImportRowError(rowNum, email, "Invalid email format"));
                    skipped++;
                    continue;
                }

                Optional<User> userOpt = userRepository.findByEmail(email);
                if (userOpt.isEmpty()) {
                    errors.add(new ImportRowError(rowNum, email, "No user with that email"));
                    skipped++;
                    continue;
                }

                String athleteId = userOpt.get().getId();
                try {
                    if (!groupName.isBlank()) {
                        Group group = groupCache.computeIfAbsent(
                                groupName, name -> groupService.getOrCreateGroup(name, coachId, 0));
                        groupService.addAthleteToGroup(group.getId(), athleteId);
                    } else {
                        // No group specified — add to default "Imported" group
                        Group group = groupCache.computeIfAbsent(
                                "Imported", name -> groupService.getOrCreateGroup(name, coachId, 0));
                        groupService.addAthleteToGroup(group.getId(), athleteId);
                    }
                    added++;
                } catch (Exception e) {
                    errors.add(new ImportRowError(rowNum, email, "Failed to add to group: " + e.getMessage()));
                    skipped++;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read CSV file: " + e.getMessage(), e);
        }

        log.info("Athlete CSV import for coach {}: processed={}, added={}, skipped={}, errors={}",
                coachId, processed, added, skipped, errors.size());
        return new ImportResult(processed, added, skipped, errors);
    }

    /**
     * RFC 4180 minimal CSV line parser. Handles quoted fields with embedded commas
     * and escaped quotes ("").
     */
    private List<String> parseCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == ',') {
                    cells.add(current.toString());
                    current.setLength(0);
                } else if (c == '"' && current.length() == 0) {
                    inQuotes = true;
                } else {
                    current.append(c);
                }
            }
        }
        cells.add(current.toString());
        return cells;
    }
}
