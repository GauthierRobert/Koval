package com.koval.trainingplannerbackend;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Per-cache Caffeine specs. {@link MongoConfig} enables caching; this binds
 * named caches to bounded Caffeine instances so heap usage is capped and
 * stale entries expire automatically. Without this, Spring falls back to an
 * unbounded {@code ConcurrentMapCacheManager}.
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                // Per-race cached document — high read fan-out, evicted on race mutation.
                build("races", 5_000, Duration.ofHours(1)),
                // Aggregated facets — recomputed by allEntries evict on race mutation; TTL bounds the rebuild storm.
                build("raceSportFacets", 16, Duration.ofMinutes(15)),
                build("raceCountryFacets", 64, Duration.ofMinutes(15)),
                // Parsed GPX routes — keyed per (raceId, discipline). Big payloads, modest count.
                build("raceRoutes", 1_000, Duration.ofHours(2)),
                // Power curves derived from FIT samples — heavy compute, per-session immutable post-ingest.
                build("sessionPowerCurves", 10_000, Duration.ofHours(6)),
                // User → club list. Cheap to recompute on miss; short TTL avoids stale roles.
                build("userClubs", 50_000, Duration.ofMinutes(10)),
                // Invite codes for a club. Mutates on redeem; short TTL is a safety net.
                build("clubInviteCodes", 5_000, Duration.ofMinutes(10)),
                // Goals for an athlete; evicted on goal mutation.
                build("athleteGoals", 50_000, Duration.ofMinutes(15)),
                // Coach-defined zone systems; evicted on system mutation.
                build("coachZoneSystems", 5_000, Duration.ofMinutes(30))
        ));
        return manager;
    }

    private static CaffeineCache build(String name, long maxSize, Duration ttl) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttl)
                .recordStats()
                .build());
    }
}
