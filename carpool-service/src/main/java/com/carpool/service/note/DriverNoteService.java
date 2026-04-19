package com.carpool.service.note;

import com.carpool.common.exception.UserNotFoundException;
import com.carpool.domain.entity.DriverNote;
import com.carpool.domain.entity.User;
import com.carpool.repository.DriverNoteRepository;
import com.carpool.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverNoteService {

    private static final int MAX_NOTES_PER_USER = 3;

    private final DriverNoteRepository noteRepository;
    private final UserRepository       userRepository;

    /**
     * Get all saved notes for a driver, sorted by most recently used.
     */
    @Transactional(readOnly = true)
    public List<DriverNote> getNotes(Long userId) {
        return noteRepository.findByUserIdOrderByLastUsedAtDesc(userId);
    }

    /**
     * Get a single note by ID — used for preview step.
     */
    @Transactional(readOnly = true)
    public DriverNote getById(Long noteId) {
        return noteRepository.findById(noteId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Note not found: " + noteId));
    }

    /**
     * Save or update a note:
     * - If same content already exists → update lastUsedAt + usedCount
     * - If new content + user has < 3 notes → insert
     * - If new content + user has 3 notes → replace LRU note
     */
    @Transactional
    public DriverNote saveOrUpdate(Long userId, String content) {
        String normalized = normalize(content);
        String hash       = hash(normalized);

        // Check for duplicate
        return noteRepository.findByUserIdAndContentHash(userId, hash)
                .map(existing -> {
                    existing.setUsedCount(existing.getUsedCount() + 1);
                    existing.setLastUsedAt(Instant.now());
                    log.debug("DriverNoteService: updated existing note id={} userId={}",
                            existing.getId(), userId);
                    return noteRepository.save(existing);
                })
                .orElseGet(() -> {
                    // Enforce max 3 — replace LRU if needed
                    long count = noteRepository.countByUserId(userId);
                    if (count >= MAX_NOTES_PER_USER) {
                        noteRepository.findLeastRecentByUserId(userId)
                                .ifPresent(lru -> {
                                    log.debug("DriverNoteService: replacing LRU note id={} userId={}",
                                            lru.getId(), userId);
                                    noteRepository.delete(lru);
                                });
                    }

                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new UserNotFoundException(userId));

                    DriverNote note = DriverNote.builder()
                            .user(user)
                            .content(content.trim())
                            .contentHash(hash)
                            .usedCount(1)
                            .lastUsedAt(Instant.now())
                            .build();

                    log.debug("DriverNoteService: saved new note userId={} content='{}'",
                            userId, content);
                    return noteRepository.save(note);
                });
    }

    /**
     * Mark a saved note as used — update lastUsedAt + usedCount.
     * Called when driver selects an existing note.
     */
    @Transactional
    public DriverNote markUsed(Long noteId) {
        DriverNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Note not found: " + noteId));
        note.setUsedCount(note.getUsedCount() + 1);
        note.setLastUsedAt(Instant.now());
        return noteRepository.save(note);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Normalize content for hashing:
     * trim + lowercase + collapse multiple spaces.
     */
    private String normalize(String content) {
        return content.trim()
                .toLowerCase()
                .replaceAll("\\s+", " ");
    }

    /**
     * SHA-256 hash of normalized content.
     * Returns 64-char hex string.
     */
    private String hash(String normalized) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(
                    normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}