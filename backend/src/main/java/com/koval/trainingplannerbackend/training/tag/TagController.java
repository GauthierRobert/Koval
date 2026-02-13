package com.koval.trainingplannerbackend.training.tag;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
@CrossOrigin(origins = "*")
public class TagController {

    private final TagService tagService;
    private final UserRepository userRepository;

    public TagController(TagService tagService, UserRepository userRepository) {
        this.tagService = tagService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<Tag>> getTags() {
        String userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.ok(List.of());
        }
        if (user.isCoach()) {
            return ResponseEntity.ok(tagService.getTagsForCoach(userId));
        } else {
            return ResponseEntity.ok(tagService.getTagsForAthlete(userId));
        }
    }

    @PostMapping
    public ResponseEntity<Tag> createTag(@RequestBody CreateTagRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.isCoach()) {
            return ResponseEntity.status(403).build();
        }
        Tag tag = tagService.getOrCreateTag(request.name(), userId);
        return ResponseEntity.ok(tag);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTag(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.isCoach()) {
            return ResponseEntity.status(403).build();
        }
        try {
            tagService.deleteTag(id, userId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).build();
        }
    }

    record CreateTagRequest(String name) {}
}
