package com.koval.trainingplannerbackend.club.gazette.dto;

import java.util.List;

/**
 * Returned by GET /editions/{id}/posts. {@code posts} contains what the caller
 * is allowed to see (their own posts in DRAFT, or all posts in PUBLISHED), and
 * {@code othersDraftCount} surfaces the aggregate count of *other* members'
 * drafts so the UI can show "X teammates are also contributing" without
 * leaking content.
 */
public record ClubGazettePostsResponse(
        List<ClubGazettePostResponse> posts,
        long othersDraftCount
) {}
