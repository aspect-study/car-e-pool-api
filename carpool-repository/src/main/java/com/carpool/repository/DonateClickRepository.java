package com.carpool.repository;

import com.carpool.domain.entity.DonateClick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DonateClickRepository extends JpaRepository<DonateClick, Long> {

    /**
     * Total taps of a donate channel button (includes repeat taps by the same user).
     */
    long countByChannel(String channel);

    /**
     * Distinct users who tapped a donate channel button at least once —
     * the "how many people are curious" number, as opposed to raw tap count.
     */
    @Query("SELECT COUNT(DISTINCT d.user.id) FROM DonateClick d WHERE d.channel = :channel")
    long countDistinctUsersByChannel(@Param("channel") String channel);
}
