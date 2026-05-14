-- The original constraint UNIQUE(ride_id, rater_id) allowed only one rating row
-- per rater per ride. This works for passengers (one driver per ride = one row),
-- but breaks driver rating: a driver with 3 passengers needs 3 rows sharing the
-- same (ride_id, rater_id) but with different ratee_id values. The INSERT for
-- the second passenger fails with a duplicate key violation even though the
-- application-level check (existsByRideIdAndRaterIdAndRateeId) correctly passes.
--
-- Fix: widen the uniqueness to include ratee_id. This allows:
--   Driver → passenger1, Driver → passenger2  (different ratee_id, same rater)
-- And still prevents:
--   Driver → passenger1 (twice)               (all three columns identical)
--   Passenger → driver (twice)                (ride_id + rater_id already in, ratee_id same)

ALTER TABLE ride_ratings
    DROP INDEX uq_rating_ride_rater;

ALTER TABLE ride_ratings
    ADD CONSTRAINT uq_rating_ride_rater_ratee
        UNIQUE (ride_id, rater_id, ratee_id);