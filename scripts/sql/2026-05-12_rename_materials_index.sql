-- Rename a legacy index that still carried the old `materials_*` prefix from
-- before the table was renamed to `resources`. Now matches every other index
-- + constraint on this table.
--
-- All five Phase 12 plan-mandated indexes already exist:
--   training_records(guardian_id)       -> idx_training_records_guardian_id
--   training_records(pet_id)            -> idx_training_records_pet_id
--   training_records(recorded_at DESC)  -> idx_training_records_recorded_at
--   resources(guardian_id)              -> idx_resources_guardian_id (renamed below)
--   badges(user_id)                     -> idx_badges_user_id

alter index public.idx_materials_guardian_id rename to idx_resources_guardian_id;
