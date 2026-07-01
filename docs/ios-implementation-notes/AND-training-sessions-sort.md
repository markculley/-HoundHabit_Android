# AND — Add sort to Training Sessions in PetDetailScreen

Notes from the iOS implementation (card **IOS-32**). Pure client-side sorting of the Pet detail Training Sessions list — no backend, no schema, no new queries.

## Cards

- **Android**: [Add sort to Training Sessions in PetDetailScreen](https://www.notion.so/360d8a41762281359827ca1be3a69271) — Backlog
- **iOS**: [IOS-32](https://www.notion.so/360d8a41762280eb945df9eca23d3aa1) — Done

## Depends on

The "Training Sessions row redesign" card (iOS IOS-31 / `AND-training-sessions-row.md`). That card introduces the per-session plan context (behavior + step). IOS-32 just extends that context with sort positions and adds the sort UI. Do the row card first, or both together.

## What iOS did

### 1. Extended the per-session plan context with sort orders

IOS-31 resolved a `behaviorName` + `stepTitle` per `planItemId`. IOS-32 widened that into a struct that also carries the sort positions:

```swift
struct SessionPlanContext: Equatable {
    let behaviorName: String
    let behaviorSortOrder: Int   // behavior's position in its plan
    let stepTitle: String
    let stepSortOrder: Int       // step's position within the behavior
}

// TrainingRecordViewModel
var planContext: [UUID: SessionPlanContext] = [:]   // keyed by planItemId
```

Built from the same two bulk queries IOS-31 already runs (`fetchItems(ids:)` then `fetchBehaviors(ids:)`) — no extra round-trips. `Behavior` and `TrainingPlanItem` both already carry `sortOrder`, so it's just a matter of keeping them instead of discarding them after reading the name.

### 2. The three sort modes

```swift
enum SessionSort: String, CaseIterable, Identifiable {
    case behavior          = "Behavior"
    case behaviorStep      = "Behavior → Step"
    case behaviorStepDate  = "Behavior → Step → DateTime"
    var id: String { rawValue }
}
```

Each mode adds another sort key; the unspecified tail falls back to newest-first. Default is `.behaviorStepDate`.

| Mode | Sort keys |
|---|---|
| Behavior | behaviorSortOrder, behaviorName, then recordedAt desc |
| Behavior → Step | + stepSortOrder, then recordedAt desc |
| Behavior → Step → DateTime (default) | + recordedAt desc (explicit) |

### 3. The comparator

`PetDetailView.sortedRecords` is a computed property over `trainingVM.records`:

```swift
private var sortedRecords: [TrainingRecord] {
    func ctx(_ r: TrainingRecord) -> SessionPlanContext? {
        r.planItemId.flatMap { trainingVM.planContext[$0] }
    }
    return trainingVM.records.sorted { a, b in
        let ca = ctx(a), cb = ctx(b)

        let aBehaviorOrder = ca?.behaviorSortOrder ?? Int.max
        let bBehaviorOrder = cb?.behaviorSortOrder ?? Int.max
        if aBehaviorOrder != bBehaviorOrder { return aBehaviorOrder < bBehaviorOrder }

        let aBehaviorName = ca?.behaviorName ?? ""
        let bBehaviorName = cb?.behaviorName ?? ""
        if aBehaviorName != bBehaviorName {
            return aBehaviorName.localizedCaseInsensitiveCompare(bBehaviorName) == .orderedAscending
        }

        if sessionSort != .behavior {
            let aStepOrder = ca?.stepSortOrder ?? Int.max
            let bStepOrder = cb?.stepSortOrder ?? Int.max
            if aStepOrder != bStepOrder { return aStepOrder < bStepOrder }
        }

        return a.recordedAt > b.recordedAt   // newest-first; explicit in the deepest mode, implicit tiebreaker otherwise
    }
}
```

Note the `behaviorName` tiebreaker after `behaviorSortOrder` — a pet assigned **multiple plans** can have behaviors from different plans sharing a `sortOrder`, so name keeps the order deterministic. `Int.max` defaults make plan-less records (shouldn't exist post-IOS-31, but defensive) sort to the bottom.

Kotlin: `records.sortedWith(comparator)` where the comparator chains `compareBy { ... }` / `thenBy { ... }`, built conditionally on the selected mode. Or a `when (sessionSort)` returning the right `Comparator`.

### 4. The sort control

A `Menu` wrapping a `Picker` bound to `sessionSort` — the `Picker` gives the active option an automatic checkmark. Placed on the trailing side of the Training Sessions header, shown only when the list is non-empty.

```swift
HStack {
    Text("Training Sessions").font(.title2).bold()
    Spacer()
    if !trainingVM.records.isEmpty {
        Menu {
            Picker("Sort", selection: $sessionSort) {
                ForEach(SessionSort.allCases) { Text($0.rawValue).tag($0) }
            }
        } label: {
            Label("Sort", systemImage: "arrow.up.arrow.down").font(.subheadline)
        }
    }
}
```

Compose: a trailing `IconButton` (`Icons.AutoMirrored.Filled.Sort` or `Icons.Default.SwapVert`) in the header `Row` that opens a `DropdownMenu` of three `DropdownMenuItem`s, each showing a leading check `Icon` when it's the selected mode. Hold the selected mode in screen state (`rememberSaveable`), and the sorted list is just `derivedStateOf { records.sortedWith(comparatorFor(mode)) }`.

## Manual test plan

Best tested with a pet that has multiple behaviors and several sessions across different steps.

1. Training Sessions header shows a Sort control (only when there's ≥1 session).
2. Tapping it shows three options with a checkmark on the active one; default is **Behavior → Step → DateTime**.
3. **Behavior → Step → DateTime**: sessions group by behavior (plan order), then step (plan order within behavior), then newest-first.
4. **Behavior → Step**: same, but within a step the order is just newest-first (visually similar — recordedAt is the implicit tiebreaker).
5. **Behavior**: grouped by behavior only; within a behavior, sessions of different steps interleave by date (newest-first).
6. Switching modes re-orders the list immediately with no spinner / no re-fetch.

## Notes

- **No new queries**: sorting is a pure in-memory comparator over the already-loaded list. The plan context was already fetched for the row redesign.
- **Multi-plan pets**: the `behaviorName` tiebreaker after `behaviorSortOrder` keeps cross-plan ordering deterministic. The user accepted that behaviors from different plans sharing a sort position will interleave — that's an acknowledged edge case, not a bug.
- **No section headers**: the list stays flat — each row already shows its Behavior + Step, so a flat sorted list reads clearly. Section headers were explicitly out of scope.
