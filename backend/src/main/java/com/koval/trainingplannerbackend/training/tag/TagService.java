package com.koval.trainingplannerbackend.training.tag;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TagService {

    private final TagRepository tagRepository;

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    /**
     * Get or create a tag for a specific coach.
     */
    public Tag getOrCreateTag(String name, String coachId) {
        String normalized = name.toLowerCase().trim();
        Optional<Tag> existing = tagRepository.findByCoachIdAndName(coachId, normalized);
        if (existing.isPresent()) {
            return existing.get();
        }

        Tag tag = new Tag();
        tag.setName(normalized);
        tag.setCoachId(coachId);
        tag.setCreatedAt(LocalDateTime.now());
        return tagRepository.save(tag);
    }

    /**
     * Get all tags for a specific coach.
     */
    public List<Tag> getTagsForCoach(String coachId) {
        return tagRepository.findByCoachId(coachId);
    }

    /**
     * Get all tags that contain this athlete.
     */
    public List<Tag> getTagsForAthlete(String athleteId) {
        return tagRepository.findByAthleteIdsContaining(athleteId);
    }

    /**
     * Add an athlete to a tag.
     */
    public Tag addAthleteToTag(String tagId, String athleteId) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new IllegalArgumentException("Tag not found: " + tagId));
        if (!tag.getAthleteIds().contains(athleteId)) {
            tag.getAthleteIds().add(athleteId);
            tagRepository.save(tag);
        }
        return tag;
    }

    /**
     * Remove an athlete from a tag.
     */
    public Tag removeAthleteFromTag(String tagId, String athleteId) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new IllegalArgumentException("Tag not found: " + tagId));
        tag.getAthleteIds().remove(athleteId);
        return tagRepository.save(tag);
    }

    /**
     * Remove an athlete from all tags owned by a specific coach.
     */
    public void removeAthleteFromAllCoachTags(String coachId, String athleteId) {
        List<Tag> coachTags = tagRepository.findByCoachId(coachId);
        for (Tag tag : coachTags) {
            if (tag.getAthleteIds().remove(athleteId)) {
                tagRepository.save(tag);
            }
        }
    }

    /**
     * Get all unique athlete IDs across all tags for a coach.
     */
    public List<String> getAthleteIdsForCoach(String coachId) {
        List<Tag> tags = tagRepository.findByCoachId(coachId);
        Set<String> athleteIds = new LinkedHashSet<>();
        for (Tag tag : tags) {
            athleteIds.addAll(tag.getAthleteIds());
        }
        return new ArrayList<>(athleteIds);
    }

    /**
     * Check if an athlete has any coach (is in any tag).
     */
    public boolean athleteHasCoach(String athleteId) {
        return tagRepository.existsByAthleteIdsContaining(athleteId);
    }

    /**
     * Get all unique coach IDs for an athlete.
     */
    public List<String> getCoachIdsForAthlete(String athleteId) {
        List<Tag> tags = tagRepository.findByAthleteIdsContaining(athleteId);
        return tags.stream()
                .map(Tag::getCoachId)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Delete a tag. Only the coach who owns it can delete.
     */
    public void deleteTag(String tagId, String coachId) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new IllegalArgumentException("Tag not found: " + tagId));
        if (!coachId.equals(tag.getCoachId())) {
            throw new IllegalArgumentException("Only the tag owner can delete this tag");
        }
        tagRepository.deleteById(tagId);
    }

    /**
     * Get a tag by ID.
     */
    public Tag getTagById(String tagId) {
        return tagRepository.findById(tagId)
                .orElseThrow(() -> new IllegalArgumentException("Tag not found: " + tagId));
    }

    /**
     * Get tags by a list of IDs.
     */
    public List<Tag> getTagsByIds(List<String> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return List.of();
        return tagRepository.findByIdIn(tagIds);
    }
}
