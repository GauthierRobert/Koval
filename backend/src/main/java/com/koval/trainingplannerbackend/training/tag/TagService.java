package com.koval.trainingplannerbackend.training.tag;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class TagService {

    private final TagRepository tagRepository;

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    /**
     * Get or create a tag. If the tag already exists, return it.
     * If not, create a new one with the given visibility.
     */
    public Tag getOrCreateTag(String name, TagVisibility visibility, String userId) {
        String normalized = name.toLowerCase().trim();
        Optional<Tag> existing = tagRepository.findByName(normalized);
        if (existing.isPresent()) {
            return existing.get();
        }

        Tag tag = new Tag();
        tag.setName(normalized);
        tag.setDisplayName(name.trim());
        tag.setVisibility(visibility);
        tag.setCreatedBy(userId);
        tag.setCreatedAt(LocalDateTime.now());
        return tagRepository.save(tag);
    }

    /**
     * Get all tags visible to a user: all PUBLIC tags + the user's own PRIVATE tags.
     */
    public List<Tag> getVisibleTags(String userId) {
        Set<Tag> result = new LinkedHashSet<>();
        result.addAll(tagRepository.findByVisibility(TagVisibility.PUBLIC));
        result.addAll(tagRepository.findByCreatedBy(userId));
        return new ArrayList<>(result);
    }

    /**
     * Check if a tag is visible to a user.
     */
    public boolean isTagVisibleToUser(Tag tag, String userId) {
        if (tag.getVisibility() == TagVisibility.PUBLIC) {
            return true;
        }
        return userId.equals(tag.getCreatedBy());
    }

    /**
     * Check if a tag name corresponds to a public tag.
     */
    public boolean isTagPublic(String tagName) {
        String normalized = tagName.toLowerCase().trim();
        Optional<Tag> tag = tagRepository.findByName(normalized);
        return tag.map(t -> t.getVisibility() == TagVisibility.PUBLIC).orElse(true);
    }

    /**
     * Delete a tag. Only the creator can delete it.
     */
    public void deleteTag(String tagId, String userId) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new IllegalArgumentException("Tag not found: " + tagId));
        if (!userId.equals(tag.getCreatedBy())) {
            throw new IllegalArgumentException("Only the tag creator can delete this tag");
        }
        tagRepository.deleteById(tagId);
    }

    /**
     * Auto-register tags from a training. Called when creating a training
     * to ensure all tag names have corresponding Tag documents.
     */
    public void registerTags(List<String> tagNames, String userId) {
        if (tagNames == null) return;
        for (String name : tagNames) {
            getOrCreateTag(name, TagVisibility.PUBLIC, userId);
        }
    }

    /**
     * Get a tag by ID.
     */
    public Tag getTagById(String tagId) {
        return tagRepository.findById(tagId)
                .orElseThrow(() -> new IllegalArgumentException("Tag not found: " + tagId));
    }
}
