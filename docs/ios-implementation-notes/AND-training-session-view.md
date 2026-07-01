# AND — Consistent Training Session screen for guardians

Notes from the iOS implementation (`TrainingSessionView`). One consistent screen a guardian uses to do (or repeat) a training session, linkable from multiple places.

## Cards

- **Android**: [Consistent Training Session screen for guardians](https://www.notion.so/360d8a41762281ecad49cb17b9aa5c97) — Backlog
- **iOS**: "Consistent Training Session view for guardians" — Done

## ⚠️ Sequencing — read before implementing

iOS is immediately following this with **"Step completion model: 3-day streak of perfect scores"** ([card](https://www.notion.so/360d8a41762281249f79fa4b0e24e3d4)). That card removes the advancement pointer (`current_item_id`), the advancement message, and the locked-step concept. The iOS `TrainingSessionView` as shipped *still* fires an advancement message on "Done"; that's removed in the next card. **Recommend Android lands both together**, or builds this one knowing the advancement bits are about to be torn out. The notes below describe `TrainingSessionView` as-shipped; ignore the advancement parts if you're doing both at once.

## What iOS built

### `TrainingSessionView` — a modal sheet

Input: `(planItemId, assignedPlan, isShared, viewModel)` plus an optional `onLogged` callback. It **self-loads** the step + behavior from the view model (`loadItems(for: planId)` if not already cached), so callers only need the step id + the assignment.

Layout (a `Form`):
1. Plan name / Behavior name / Step name (`LabeledContent` rows)
2. "Three D's" section — Distance / Duration / Distraction, read-only, from the step
3. "Training Timer" section — the existing timer component, re-homed here
4. **Train Now** button (bordered-prominent, full width)

Interaction:
- Before Train Now: only the above shows.
- Tap **Train Now** → `timer.start()`, AND a `hasStartedTraining` flag flips → the **reps 0–5 stepper** + a **Notes** field appear; the button label becomes **Done**.
- Tap **Done** → `createRecord(...)` with the step's Three D's (+ custom values), the score, notes, `plan_item_id`, and `petId` + `isShared` from the assignment → dismiss.
- (As shipped, iOS also runs `advanceCurrentStep` + shows an advancement alert when it's the current step — **this is removed in the follow-on card**, so don't port it.)

A `Cancel` toolbar item dismisses without logging.

### Why `isShared` is passed in (not read from the assignment)

The plan-detail screen has a live "Share sessions with trainer" toggle whose value can be ahead of the assignment snapshot the screen was opened with. So `TrainingSessionView` takes `isShared` explicitly; each caller passes the freshest value (the plan detail passes its live toggle state, the pet detail passes `assignment.isShared`).

### Entry points

**Plan detail screen** — tapping a step opens `TrainingSessionView(planItemId: step.id, assignedPlan:, isShared:, viewModel:)`. (iOS: replaces the old `TrainingRecordFormView` practice sheet.)

**Pet detail Training Sessions list** — tapping a session row opens a chooser (iOS used a `confirmationDialog`): **"View Session"** → the read-only past-session detail screen; **"Train Again"** → `TrainingSessionView` for that record's step. To resolve a tapped record back to its plan/assignment, iOS extended its per-session plan-context map with the owning `planId`, then looked up the `AssignedPlan` among the pet's assigned plans.

### Timer moved off the session form

The training timer was previously inside the session **form** (`TrainingRecordFormView`). It's removed from there — the form stays only for **editing** an existing record. The timer now lives solely on `TrainingSessionView`. The timer component itself (countdown with duration presets) is unchanged, just re-homed.

## File map

| iOS file | Change | Android equivalent |
|---|---|---|
| `Guardian/TrainingRecords/TrainingSessionView.swift` | **New** — the consistent training sheet | new `TrainingSessionScreen.kt` |
| `Guardian/TrainingRecords/TrainingRecordViewModel.swift` | `SessionPlanContext` gained `planId` | the pet-detail sessions view model |
| `Guardian/Plans/GuardianPlanDetailView.swift` | Step tap → `TrainingSessionView` instead of the old form sheet; removed the advancement alert | plan detail screen |
| `Guardian/Pets/PetDetailView.swift` | Session row tap → "View / Train Again" chooser → read-only detail OR `TrainingSessionView` | pet detail screen |
| `Guardian/TrainingRecords/TrainingRecordFormView.swift` | Removed the training timer; form is now edit-only | the session form |

## Manual test plan

1. Plan detail → tap a step → `TrainingSessionView` shows plan / behavior / step / Three D's / timer / **Train Now**.
2. Tap **Train Now** → timer starts, reps stepper + notes appear, button → **Done**.
3. Tap **Done** → a session is logged for that step; the sheet dismisses.
4. Pet detail → tap a session row → chooser appears: **View Session** → read-only detail; **Train Again** → `TrainingSessionView` for that step.
5. Edit a past session → the session form still works, but has **no timer**.

## Notes

- `TrainingSessionView` is self-contained: it creates the record itself so it behaves identically from every entry point. Keep that property on Android — don't push record-creation back onto the callers.
- The advancement message / `current_item_id` advancement is **going away** — see the follow-on card. Don't invest in porting it.
