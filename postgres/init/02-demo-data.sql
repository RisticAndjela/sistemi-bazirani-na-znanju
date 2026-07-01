INSERT INTO patient_cases (
    child_id,
    age_in_months,
    rr1,
    spo21,
    chest1,
    grunting1,
    apnea1,
    cyanosis1,
    rr2,
    spo22,
    chest2,
    grunting2,
    apnea2,
    cyanosis2,
    intake_percent,
    poor_feeding
)
SELECT *
FROM (
    VALUES
        (1, 10, 54, 95, TRUE, FALSE, FALSE, FALSE, 58, 94, TRUE, TRUE, FALSE, FALSE, 60, TRUE),
        (2, 24, 40, 97, FALSE, FALSE, FALSE, FALSE, 38, 97, FALSE, FALSE, FALSE, FALSE, 90, FALSE),
        (3, 6, 62, 89, TRUE, TRUE, TRUE, FALSE, 68, 88, TRUE, TRUE, FALSE, TRUE, 45, TRUE),
        (4, 18, 44, 95, FALSE, TRUE, FALSE, FALSE, 46, 94, TRUE, TRUE, FALSE, FALSE, 65, TRUE)
) AS demo_cases(
    child_id,
    age_in_months,
    rr1,
    spo21,
    chest1,
    grunting1,
    apnea1,
    cyanosis1,
    rr2,
    spo22,
    chest2,
    grunting2,
    apnea2,
    cyanosis2,
    intake_percent,
    poor_feeding
)
WHERE NOT EXISTS (
    SELECT 1
    FROM patient_cases
);
