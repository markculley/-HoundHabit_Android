# Phase 11: Trainer Add Resources to Guardian

## Overview

Trainers can add resources (notes, URLs, photos) directly to a linked guardian's resource library. The guardian sees trainer-added resources alongside their own in the Resources tab.

---

## UC-11.1: Trainer Adds a Resource to a Guardian

**Actor:** Trainer  
**Precondition:** Trainer is authenticated and has at least one linked guardian.

### Flow

```
Trainer                   App                        Supabase
  │                         │                            │
  │  Guardians tab →        │                            │
  │  tap guardian name      │                            │
  │────────────────────────▶│                            │
  │  GuardianDetailView     │                            │
  │◀────────────────────────│                            │
  │                         │                            │
  │  Tap + (toolbar)        │                            │
  │────────────────────────▶│                            │
  │  TrainerAddResourceView │                            │
  │  sheet presented        │                            │
  │◀────────────────────────│                            │
  │                         │                            │
  │  Select kind, fill in   │                            │
  │  title + content        │                            │
  │  Tap Save               │                            │
  │────────────────────────▶│                            │
  │                         │  INSERT resources          │
  │                         │  owner_id = guardian_id    │
  │                         │  added_by_id = trainer_id  │
  │                         │  guardian_id = guardian_id │
  │                         │───────────────────────────▶│
  │                         │◀───────────────────────────│
  │  Sheet dismissed        │                            │
  │◀────────────────────────│                            │
```

### Resource Kinds

| Kind | Content |
|------|---------|
| Note | Free-text body |
| URL | Web link + optional notes |
| Photo | Image upload + optional notes |

---

## Architecture

### RLS Policies

**`resources` table INSERT** (`materials_insert_trainer`):
```sql
added_by_id = auth.uid()
AND EXISTS (
  SELECT 1 FROM trainer_guardian_links
  WHERE trainer_id = auth.uid()
    AND guardian_id = resources.guardian_id
    AND status = 'active'
)
```

**`resources` storage bucket INSERT** (`resources_storage_insert`):
```sql
bucket_id = 'resources' AND (
  foldername(name)[1] = auth.uid()::text
  OR EXISTS (
    SELECT 1 FROM trainer_guardian_links
    WHERE trainer_id = auth.uid()
      AND guardian_id::text = foldername(name)[1]
      AND status = 'active'
  )
)
```

The storage policy was extended in Phase 11 to allow trainers to upload photos to a linked guardian's folder (`{guardianId}/{resourceId}.jpg`).

### `ResourceService.createResourceForGuardian`

Separate method from the guardian's `createResource` — sets `owner_id` and `guardian_id` to the target guardian, `added_by_id` to the trainer.

### Guardian Visibility

The guardian's `ResourceListView` queries `resources` filtered by `guardian_id = auth.uid()`, which returns both self-added and trainer-added resources. No changes needed on the guardian side.

---

## Test Flow

1. Sign in as Trainer → tap a linked guardian → tap **+**
2. Add a **Note**: enter title + note text → Save → sheet dismisses
3. Add a **URL**: enter title + URL → Save → sheet dismisses
4. Add a **Photo**: enter title + pick photo → Save → sheet dismisses
5. Sign out → sign in as Guardian → Settings → Resources → all three resources appear
6. Verify trainer-added resources are not deletable by... (deletion is `added_by_id = auth.uid() OR guardian_id = auth.uid()` — both trainer and guardian can delete)

---

## Edge Cases

| Scenario | Behaviour |
|----------|-----------|
| Trainer not linked to guardian | RLS blocks INSERT — error shown |
| Photo upload to guardian's storage folder | Allowed via updated storage policy for linked trainers |
| Guardian deletes trainer-added resource | Permitted — `guardian_id = auth.uid()` satisfies DELETE policy |
| Trainer deletes their own added resource | Permitted — `added_by_id = auth.uid()` satisfies DELETE policy |

---

## Notes

### Known bug — orphaned storage objects on delete (iOS + Android)

When a Photo-kind resource is deleted, **the storage object is left behind**. Both clients call `ResourceService.deleteResource(id)` which only removes the `resources` row; neither client calls `supabase.storage.from("resources").remove(...)`. The storage path (`{guardianId}/{resourceId}.jpg`) becomes orphaned: no DB row references it, RLS still forbids new clients from accessing it, but it occupies bucket storage forever.

**Confirmed empirically in Phase 6 testing** (Android). User created a Photo resource, then deleted it from the detail screen. Result:

- `public.resources` — row gone
- `storage.objects` (bucket `resources`) — `{userId}/{resourceId}.jpg` still present, ~1 MB

The same shape almost certainly applies to **pet-photos** on `Pet` deletion (`PetService.deletePet` only removes the row), and to any future buckets that store per-row content.

**Recommended fix (single-stroke, both clients):** add a Postgres trigger on `resources` AFTER DELETE that calls `storage.delete_object('resources', OLD.guardian_id || '/' || OLD.id || '.jpg')` (and an analogous trigger for `pets` → `pet-photos`). Server-side cleanup means clients don't need to coordinate the two deletes (which would also race on a flaky network and leave inconsistent state).

Until then, neither client surfaces this to the user — the file is invisible from the UI but consumes storage quota. Worth a one-time `storage.objects` sweep before launch.
