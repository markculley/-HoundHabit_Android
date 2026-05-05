# Use Cases — Pet Management

## 1. Create Pet (with optional photo)

```mermaid
sequenceDiagram
    actor G as Guardian
    participant App as Hound Habit App
    participant DB as Supabase (pets table)
    participant S as Supabase Storage (pet-photos)

    G->>App: Tap + on Pets tab
    App->>G: Present Add Pet sheet
    G->>App: Enter name and breed
    G->>App: (Optional) Pick photo from library
    G->>App: Tap Add

    App->>DB: INSERT into pets (name, breed, guardian_id)
    DB-->>App: Return pet record with ID

    alt Photo selected
        App->>S: Upload photo to {userId}/{petId}/photo.jpg
        S-->>App: Upload confirmed
        App->>DB: UPDATE pets SET photo_url = public URL
        DB-->>App: Return updated pet record
    end

    App->>G: Dismiss sheet, pet appears in list
```

## 2. Edit Pet (update name, breed, or photo)

```mermaid
sequenceDiagram
    actor G as Guardian
    participant App as Hound Habit App
    participant DB as Supabase (pets table)
    participant S as Supabase Storage (pet-photos)

    G->>App: Tap pet in list → Pet Detail screen
    G->>App: Tap Edit (top right)
    App->>G: Present Edit Pet sheet (pre-filled)

    G->>App: Update name / breed
    G->>App: (Optional) Pick new photo from library
    G->>App: Tap Save

    alt New photo selected
        App->>S: Upload photo to {userId}/{petId}/photo.jpg (upsert)
        S-->>App: Upload confirmed
        Note over App: photo_url set to new public URL
    end

    App->>DB: UPDATE pets SET name, breed, photo_url WHERE id = petId
    DB-->>App: Return updated pet record

    App->>G: Dismiss sheet, Pet Detail refreshes
```

## 3. Delete Pet

```mermaid
sequenceDiagram
    actor G as Guardian
    participant App as Hound Habit App
    participant DB as Supabase (pets table)

    alt From Pet List (swipe)
        G->>App: Swipe left on pet row
        App->>G: Show Delete button
        G->>App: Tap Delete
    else From Pet Detail
        G->>App: Tap Delete Pet (bottom of screen)
        App->>G: Show confirmation alert
        G->>App: Confirm deletion
    end

    App->>DB: DELETE FROM pets WHERE id = petId
    DB-->>App: Deletion confirmed

    App->>G: Pet removed from list / pop back to list
```

## Notes

### Cache-busting `photo_url` — iOS vs. Android

Both clients write the bare public URL to `pets.photo_url`, but they handle cache-busting differently in the rendered UI:

- **iOS** sometimes persists a `?v=<timestamp>` query string baked directly into `photo_url` (e.g. row `8e59b3e5-…`'s URL ends in `?v=1776878153`).
- **Android** stores the bare URL in `photo_url` and only appends `?t=<updated_at.epochSeconds>` at render time (in `PetAvatar` / `PetRow` / `PetDetailScreen`).

Both approaches achieve the same goal: bypassing CDN caching after a re-upload. The values are functionally equivalent — but if any code path ever string-compares `photo_url`s across the two writers (e.g. dedupe logic, sync, analytics joins), strip query params first or it will mis-flag iOS-written rows as different.

