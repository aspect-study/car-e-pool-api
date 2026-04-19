package com.carpool.repository;

import com.carpool.domain.entity.DriverNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DriverNoteRepository extends JpaRepository<DriverNote, Long> {

    /**
     * Get all notes for a user, sorted by most recently used.
     * Max 3 per user — no pagination needed.
     */
    @Query("""
        SELECT n FROM DriverNote n
        WHERE n.user.id = :userId
        ORDER BY n.lastUsedAt DESC
        """)
    List<DriverNote> findByUserIdOrderByLastUsedAtDesc(@Param("userId") Long userId);

    /**
     * Find existing note by hash — used for deduplication check.
     */
    @Query("""
        SELECT n FROM DriverNote n
        WHERE n.user.id = :userId
          AND n.contentHash = :hash
        """)
    Optional<DriverNote> findByUserIdAndContentHash(
            @Param("userId") Long userId,
            @Param("hash")   String hash);

    /**
     * Count notes per user — used to enforce max 3 limit.
     */
    @Query("""
        SELECT COUNT(n) FROM DriverNote n
        WHERE n.user.id = :userId
        """)
    long countByUserId(@Param("userId") Long userId);

    /**
     * Find least recently used note — used for LRU replacement
     * when user exceeds max 3 notes.
     */
    @Query("""
        SELECT n FROM DriverNote n
        WHERE n.user.id = :userId
        ORDER BY n.lastUsedAt ASC
        LIMIT 1
        """)
    Optional<DriverNote> findLeastRecentByUserId(@Param("userId") Long userId);
}