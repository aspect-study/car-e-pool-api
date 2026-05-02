package com.carpool.repository;

import com.carpool.domain.entity.HubAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface HubAliasRepository extends JpaRepository<HubAlias, Long> {

    /**
     * Find hub alias by exact match — case-insensitive.
     * Hub is eagerly fetched to avoid LazyInitializationException.
     */
    @Query("""
        SELECT a FROM HubAlias a
        JOIN FETCH a.hub h
        WHERE LOWER(a.alias) = LOWER(:alias)
        """)
    Optional<HubAlias> findByAliasIgnoreCase(@Param("alias") String alias);

}