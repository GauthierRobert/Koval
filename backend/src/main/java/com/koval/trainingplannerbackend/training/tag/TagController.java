package com.koval.trainingplannerbackend.training.tag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
@CrossOrigin(origins = "*")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    /**
     * List all tags visible to the current user.
     */
    @GetMapping
    public ResponseEntity<List<Tag>> getTags(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String uid = userId != null ? userId : "anonymous";
        return ResponseEntity.ok(tagService.getVisibleTags(uid));
    }

    /**
     * Create a new tag.
     */
    @PostMapping
    public ResponseEntity<Tag> createTag(@RequestBody CreateTagRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String uid = userId != null ? userId : "anonymous";
        TagVisibility visibility = request.visibility() != null ? request.visibility() : TagVisibility.PUBLIC;
        Tag tag = tagService.getOrCreateTag(request.name(), visibility, uid);
        return ResponseEntity.ok(tag);
    }

    /**
     * Delete a tag. Only the creator can delete.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTag(@PathVariable String id,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String uid = userId != null ? userId : "anonymous";
        try {
            tagService.deleteTag(id, uid);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).build();
        }
    }

    record CreateTagRequest(String name, TagVisibility visibility) {}
}
