-- RLS cross-user access audit.
--
-- Verifies that the row-level security policies on `public.*` actually isolate
-- one guardian's data from another, and gate trainer access by the active
-- trainer_guardian_links row.
--
-- HOW TO RUN
--   1. Create three real accounts via the app (so auth.users + profiles + the
--      Postgres trigger all run normally):
--        - Guardian A    (role=guardian)
--        - Guardian B    (role=guardian)
--        - Trainer T     (role=trainer)
--   2. Link Trainer T -> Guardian A via the trainer's invite code flow.
--      Do NOT link Trainer T -> Guardian B.
--   3. Paste the three UUIDs into the `DECLARE` block below.
--   4. Run the whole script in the Supabase SQL editor. It opens a transaction,
--      seeds some test pets/records/resources/plans owned by each user (using
--      `SET LOCAL ROLE authenticated` to honor RLS during the seed), runs the
--      isolation tests, and ROLLBACKs at the end so nothing persists.
--   5. Review the final `audit_results` output — every row should have
--      pass = true. Any false row is an RLS leak worth fixing before launch.
--
-- TECHNIQUE
--   We impersonate via `SET LOCAL ROLE authenticated` + `SET LOCAL
--   request.jwt.claims TO '{"sub":"<uid>","role":"authenticated"}'`. That's
--   what supabase-js does on the wire, so testing this way exercises the
--   real RLS code path. Service role (the role running this script) bypasses
--   RLS, so we use it only for the fixture inserts that intentionally need to
--   sidestep policies — and we still seed each user's data while wearing
--   their hat where possible to surface INSERT-policy bugs.
--
-- LIMITATIONS
--   * Only tests core entities — pets, training_records, resources,
--     plan_assignments. Add more as new tables/policies appear.
--   * Doesn't cover storage (Storage RLS lives in `storage.objects` policies
--     and isn't exercised here).

begin;

create temp table audit_results (
  test         text not null,
  pass         boolean not null,
  detail       text,
  primary key (test)
);

do $audit$
declare
  -- ===== EDIT THESE THREE UUIDS BEFORE RUNNING =====
  guardian_a  uuid := '00000000-0000-0000-0000-000000000000';  -- Guardian A
  guardian_b  uuid := '00000000-0000-0000-0000-000000000000';  -- Guardian B
  trainer_t   uuid := '00000000-0000-0000-0000-000000000000';  -- Trainer T (linked to A only)
  -- ==================================================

  pet_a       uuid;
  pet_b       uuid;
  record_a_shared      uuid;
  record_a_private     uuid;
  record_b             uuid;
  resource_a           uuid;
  plan_t               uuid;
  observed             int;
begin
  if guardian_a = guardian_b or guardian_a = trainer_t or guardian_b = trainer_t
     or guardian_a = '00000000-0000-0000-0000-000000000000'::uuid then
    raise exception
      'Set the three test UUIDs at the top of the audit script first.';
  end if;

  ----------------------------------------------------------------------------
  -- SEED FIXTURES (each user creates their own data; failures here surface
  -- INSERT-policy bugs immediately).
  ----------------------------------------------------------------------------

  -- Guardian A creates a pet.
  set local role authenticated;
  perform set_config(
    'request.jwt.claims',
    json_build_object('sub', guardian_a::text, 'role', 'authenticated')::text,
    true
  );
  insert into public.pets (name, breed, guardian_id)
       values ('Rex',  'Mixed', guardian_a)
    returning id into pet_a;

  -- ...and a private + shared training record.
  insert into public.training_records (
    guardian_id, pet_id, recorded_at, score, status,
    distance, distraction, duration, is_shared
  ) values (
    guardian_a, pet_a, now(), 5, 'green',
    'arms_length', 'none', 'thirty_seconds', false
  ) returning id into record_a_private;

  insert into public.training_records (
    guardian_id, pet_id, recorded_at, score, status,
    distance, distraction, duration, is_shared
  ) values (
    guardian_a, pet_a, now(), 5, 'green',
    'arms_length', 'none', 'thirty_seconds', true
  ) returning id into record_a_shared;

  -- ...and a note resource.
  insert into public.resources (guardian_id, owner_id, added_by_id, kind, title, body)
       values (guardian_a, guardian_a, guardian_a, 'note', 'A note', 'body')
    returning id into resource_a;

  -- Guardian B creates a pet + a record (private).
  perform set_config(
    'request.jwt.claims',
    json_build_object('sub', guardian_b::text, 'role', 'authenticated')::text,
    true
  );
  insert into public.pets (name, breed, guardian_id)
       values ('Buddy', 'Labrador', guardian_b)
    returning id into pet_b;
  insert into public.training_records (
    guardian_id, pet_id, recorded_at, score, status,
    distance, distraction, duration, is_shared
  ) values (
    guardian_b, pet_b, now(), 3, 'yellow',
    'arms_length', 'none', 'thirty_seconds', true
  ) returning id into record_b;

  -- Trainer T creates a plan.
  perform set_config(
    'request.jwt.claims',
    json_build_object('sub', trainer_t::text, 'role', 'authenticated')::text,
    true
  );
  insert into public.training_plans (trainer_id, title, description)
       values (trainer_t, 'Recall basics', 'audit fixture')
    returning id into plan_t;

  ----------------------------------------------------------------------------
  -- TESTS — Guardian A's viewpoint
  ----------------------------------------------------------------------------

  perform set_config(
    'request.jwt.claims',
    json_build_object('sub', guardian_a::text, 'role', 'authenticated')::text,
    true
  );

  -- A reads own pet.
  select count(*) into observed from public.pets where id = pet_a;
  insert into audit_results values (
    'pets: A reads own pet',
    observed = 1,
    format('expected 1, got %s', observed)
  );

  -- A cannot read B's pet.
  select count(*) into observed from public.pets where id = pet_b;
  insert into audit_results values (
    'pets: A blocked from B pet',
    observed = 0,
    format('expected 0, got %s', observed)
  );

  -- A cannot insert a pet for B.
  begin
    insert into public.pets (name, breed, guardian_id) values ('Spy', 'X', guardian_b);
    insert into audit_results values (
      'pets: A blocked from inserting B pet',
      false, 'INSERT unexpectedly succeeded'
    );
  exception when others then
    insert into audit_results values (
      'pets: A blocked from inserting B pet',
      true, sqlerrm
    );
  end;

  -- A cannot delete B's pet.
  delete from public.pets where id = pet_b;
  get diagnostics observed = row_count;
  insert into audit_results values (
    'pets: A blocked from deleting B pet',
    observed = 0,
    format('rows deleted: %s', observed)
  );

  -- A reads own private record.
  select count(*) into observed from public.training_records where id = record_a_private;
  insert into audit_results values (
    'records: A reads own private record',
    observed = 1,
    format('expected 1, got %s', observed)
  );

  -- A cannot read B's record (even is_shared=true; A is not the trainer).
  select count(*) into observed from public.training_records where id = record_b;
  insert into audit_results values (
    'records: A blocked from B shared record',
    observed = 0,
    format('expected 0, got %s', observed)
  );

  -- A cannot read B's resource (no link, not owner/added_by).
  select count(*) into observed from public.resources where guardian_id = guardian_b;
  insert into audit_results values (
    'resources: A blocked from B resources',
    observed = 0,
    format('expected 0, got %s', observed)
  );

  ----------------------------------------------------------------------------
  -- TESTS — Trainer T's viewpoint
  ----------------------------------------------------------------------------

  perform set_config(
    'request.jwt.claims',
    json_build_object('sub', trainer_t::text, 'role', 'authenticated')::text,
    true
  );

  -- T reads A's pet (link is active).
  select count(*) into observed from public.pets where id = pet_a;
  insert into audit_results values (
    'pets: T reads linked guardian (A) pet',
    observed = 1,
    format('expected 1, got %s', observed)
  );

  -- T cannot read B's pet (no link).
  select count(*) into observed from public.pets where id = pet_b;
  insert into audit_results values (
    'pets: T blocked from unlinked guardian (B) pet',
    observed = 0,
    format('expected 0, got %s', observed)
  );

  -- T cannot insert a pet for A (no INSERT policy for trainers).
  begin
    insert into public.pets (name, breed, guardian_id) values ('Trainer-Spy', 'X', guardian_a);
    insert into audit_results values (
      'pets: T blocked from inserting pet for A',
      false, 'INSERT unexpectedly succeeded'
    );
  exception when others then
    insert into audit_results values (
      'pets: T blocked from inserting pet for A',
      true, sqlerrm
    );
  end;

  -- T cannot delete A's pet (no DELETE policy for trainers).
  delete from public.pets where id = pet_a;
  get diagnostics observed = row_count;
  insert into audit_results values (
    'pets: T blocked from deleting A pet',
    observed = 0,
    format('rows deleted: %s', observed)
  );

  -- T reads A's shared record.
  select count(*) into observed from public.training_records where id = record_a_shared;
  insert into audit_results values (
    'records: T reads A shared record',
    observed = 1,
    format('expected 1, got %s', observed)
  );

  -- T does NOT read A's private record.
  select count(*) into observed from public.training_records where id = record_a_private;
  insert into audit_results values (
    'records: T blocked from A private record',
    observed = 0,
    format('expected 0, got %s', observed)
  );

  -- T does NOT read B's record (no link, regardless of is_shared).
  select count(*) into observed from public.training_records where id = record_b;
  insert into audit_results values (
    'records: T blocked from unlinked B record',
    observed = 0,
    format('expected 0, got %s', observed)
  );

  -- T can read A's resources (link is active).
  select count(*) into observed from public.resources where id = resource_a;
  insert into audit_results values (
    'resources: T reads linked A resource',
    observed = 1,
    format('expected 1, got %s', observed)
  );

  -- T can INSERT a resource for A (linked).
  begin
    insert into public.resources (guardian_id, owner_id, added_by_id, kind, title, body)
         values (guardian_a, trainer_t, trainer_t, 'note', 'T-for-A', 'ok');
    insert into audit_results values (
      'resources: T inserts for linked A',
      true, 'insert allowed'
    );
  exception when others then
    insert into audit_results values (
      'resources: T inserts for linked A',
      false, sqlerrm
    );
  end;

  -- T cannot INSERT a resource for B (unlinked).
  begin
    insert into public.resources (guardian_id, owner_id, added_by_id, kind, title, body)
         values (guardian_b, trainer_t, trainer_t, 'note', 'T-for-B', 'leak?');
    insert into audit_results values (
      'resources: T blocked from inserting for unlinked B',
      false, 'INSERT unexpectedly succeeded'
    );
  exception when others then
    insert into audit_results values (
      'resources: T blocked from inserting for unlinked B',
      true, sqlerrm
    );
  end;

  -- T can assign their own plan to A (linked).
  begin
    insert into public.plan_assignments (plan_id, trainer_id, guardian_id, is_shared)
         values (plan_t, trainer_t, guardian_a, false);
    insert into audit_results values (
      'plan_assignments: T assigns own plan to linked A',
      true, 'insert allowed'
    );
  exception when others then
    insert into audit_results values (
      'plan_assignments: T assigns own plan to linked A',
      false, sqlerrm
    );
  end;

  -- T cannot assign their own plan to B (unlinked).
  begin
    insert into public.plan_assignments (plan_id, trainer_id, guardian_id, is_shared)
         values (plan_t, trainer_t, guardian_b, false);
    insert into audit_results values (
      'plan_assignments: T blocked from assigning to unlinked B',
      false, 'INSERT unexpectedly succeeded'
    );
  exception when others then
    insert into audit_results values (
      'plan_assignments: T blocked from assigning to unlinked B',
      true, sqlerrm
    );
  end;

  ----------------------------------------------------------------------------
  -- TESTS — Guardian A self-assigns own plan (Phase 9D)
  ----------------------------------------------------------------------------

  perform set_config(
    'request.jwt.claims',
    json_build_object('sub', guardian_a::text, 'role', 'authenticated')::text,
    true
  );

  -- A creates an own plan (trainer_id = self).
  declare own_plan uuid;
  begin
    insert into public.training_plans (trainer_id, title, description)
         values (guardian_a, 'A own plan', 'audit')
      returning id into own_plan;
    insert into audit_results values (
      'plan_assignments: A creates own plan',
      own_plan is not null, 'insert allowed'
    );
  exception when others then
    insert into audit_results values (
      'plan_assignments: A creates own plan',
      false, sqlerrm
    );
  end;

  -- A self-assigns it.
  begin
    insert into public.plan_assignments (plan_id, trainer_id, guardian_id, is_shared)
    select id, guardian_a, guardian_a, false
      from public.training_plans
     where trainer_id = guardian_a and title = 'A own plan'
     limit 1;
    insert into audit_results values (
      'plan_assignments: A self-assigns own plan',
      true, 'insert allowed'
    );
  exception when others then
    insert into audit_results values (
      'plan_assignments: A self-assigns own plan',
      false, sqlerrm
    );
  end;

  -- A cannot self-assign a plan they don't own (use Trainer T's plan_t).
  begin
    insert into public.plan_assignments (plan_id, trainer_id, guardian_id, is_shared)
         values (plan_t, guardian_a, guardian_a, false);
    insert into audit_results values (
      'plan_assignments: A blocked from self-assigning a plan they do not own',
      false, 'INSERT unexpectedly succeeded'
    );
  exception when others then
    insert into audit_results values (
      'plan_assignments: A blocked from self-assigning a plan they do not own',
      true, sqlerrm
    );
  end;

  reset role;
end
$audit$;

-- Final report. Everything should be pass = true.
select test, pass, detail
  from audit_results
 order by pass, test;

rollback;
