INSERT INTO vehicles (user_id, plate_number, model, color, seat_capacity, created_at, updated_at)
SELECT
    id,
    plate_number,
    car_model,
    car_color,
    4,
    COALESCE(created_at, NOW()),
    NOW()
FROM users
WHERE plate_number IS NOT NULL
  AND plate_number != ''
  AND car_model    IS NOT NULL
  AND car_model    != '';