# ReadFree v2.0 — Implementation Audit

> Compared every spec line in `readfree_v2_plan.md` against the current codebase.
> Status: ✅ Done | ⚠️ Partial | ❌ Missing

---

## § 2.1 — Reading (Core WebView)

| Spec | Status | Gap |
|------|--------|-----|
| Medium URLs proxied via Freedium | ✅ | |
| Mirror failover (preferred → default → error panel) | ✅ | |
| Mirror settings in BottomSheet | ✅ | |
| SSL warning dialog | ✅ | |
| "Open in Browser" via Chrome Custom Tab | ✅ | |

---

## § 2.2 — Universal Library (Save Any URL)

| Spec | Status | Gap |
|------|--------|-----|
| Any URL can be saved | ✅ | |
| Medium URLs proxied, non-Medium direct | ✅ | |
| Library layer works identically for both | ✅ | |

---

## § 2.3 — Save Flow

| Spec | Status | Gap |
|------|--------|-----|
| Share sheet → reader loads immediately | ✅ | |
| Non-blocking save banner auto-dismisses 5s | ✅ | |
| Banner: "Add to library? [Save] [✕]" | ✅ | |
| Banner: "Already in library [Edit] [✕]" if saved | ❌ | Banner still shows for OFFLINE-ONLY saves; "Already in library" does not appear correctly when article was downloaded but not saved via SaveSheet — the `suppressBanner` mechanism is incomplete |
| 🔖 button always available in bottom bar | ✅ | |
| [Read] from home paste input | ✅ | |
| [+ Save] from home opens SaveBottomSheet without loading article | ❌ | "Save" button was removed; only "Read" remains |
| Title fallback: hostname + path if WebView.getTitle() null | ⚠️ | Uses "Untitled Article" instead of `Uri.parse(url).host + path` |
| "Save & Close" saves and calls finish() | ✅ | Fixed in earlier session |

---

## § 2.4 — Lists (Folders)

| Spec | Status | Gap |
|------|--------|-----|
| User creates lists with name, emoji, color | ✅ | |
| One article can belong to multiple lists (many-to-many) | ✅ | Schema correct |
| "All" system list (every article) | ✅ | |
| "Offline" system list (articles with offline file) | ⚠️ | Chip exists but **the offline filter produces no results** — the DAO query uses `offlineFilePath IS NOT NULL` but most downloads write the path as a string — need to verify the DB actually has the value |
| "Unsorted" virtual filter (no list assignment) | ✅ | |
| User lists reorderable via drag-and-drop | ✅ | |
| Deleting a list does NOT delete articles | ✅ | Cascade on xref only |
| Renaming a list is supported | ⚠️ | Long-press in ListsFragment shows a rename sheet but the implementation was never confirmed to work end-to-end |

---

## § 2.5 — Tags

| Spec | Status | Gap |
|------|--------|-----|
| Free-form, user-defined, global tags | ✅ | |
| Article can have zero or more tags | ✅ | |
| Chip-style input (type + Enter/comma) | ✅ | |
| Existing tags autocomplete as user types | ❌ | No autocomplete implemented in SaveBottomSheet |
| Tags shown on article cards in library | ✅ | Tag chips visible in `item_article_card.xml` |
| **Tag name NOT shown on card** — just name-only chips | ⚠️ | Chips show text correctly but styling is very small and easy to miss |
| Tags management screen (rename, delete, counts) | ⚠️ | Counts shown, delete via long-press (currently deletes immediately — no confirmation — user reported this is wrong), rename is **missing** |
| Long-press tag → rename or delete choice | ❌ | Currently deletes on long-press with no dialog |
| Deleting a tag removes from all articles, not articles themselves | ✅ | `removeTagFromAllArticles` in DAO |

---

## § 2.6 — Read State

| Spec | Status | Gap |
|------|--------|-----|
| Three states: UNREAD → READING → READ | ✅ | Entity exists |
| Article saved → UNREAD | ✅ | |
| Article opened from library → READING | ❌ | `ReaderActivity` does **not** set READING on open |
| ✓ button taps → READ toggle | ⚠️ | Button exists in bottom bar but the toggle action needs verification |
| Swipe RIGHT on library card → READ | ❌ | Not implemented; `ItemTouchHelper` only has LEFT swipe for delete |
| Long-press → "Mark as Unread" | ❌ | Not in `ArticleContextBottomSheet` |
| Progress ≥ 90% → READ (auto) | ✅ | Implemented in `ReaderViewModel` |
| UNREAD: bold title + accent dot | ⚠️ | Dot present; bold styling is set uniformly (not conditional on state) |
| READING: smaller accent dot | ❌ | Same dot size regardless of state |
| READ: dimmed title + ✓ icon | ❌ | Title is not dimmed; no ✓ icon replaces the dot |

---

## § 2.7 — Reading Progress

| Spec | Status | Gap |
|------|--------|-----|
| Track scroll 0–100% via JS bridge | ✅ | |
| Progress bar: 2dp thin bar above bottom action bar | ✅ | |
| Persisted to Room on `onPause()` AND at ≥5% delta | ⚠️ | Only saves on scroll; `onPause()` flush not confirmed |
| Scroll restore on reopen | ✅ | |
| Auto-mark READ at 90% | ✅ | |
| Short page: skip tracking, mark READ on `onPageFinished` | ❌ | Not implemented |
| Dynamic page height re-calc on `resize` event | ❌ | JS injection does not include a `resize` listener |

---

## § 2.8 — Offline Saving

| Spec | Status | Gap |
|------|--------|-----|
| "Save Offline" via WebView.saveWebArchive | ✅ | |
| Path: `filesDir/offline/{urlHash}.mht` | ✅ | |
| Null callback → snackbar "Could not save offline — page has cross-origin restrictions" | ⚠️ | Shows generic toast instead of specific snackbar |
| On open: isOffline + offlineFilePath → load from file | ❌ | **Bug**: `allowFileAccess = true` was just added but previously missing; still failing for some — may need `WebView.setAllowUniversalAccessFromFileURLs` for MHT |
| 📥 badge on article cards | ✅ | `ivOffline` visible when `offlineFilePath != null` |
| "Offline" chip filter shows offline articles | ❌ | Filter chip exists but does not return results — likely the `offlineFilePath` is stored but the HomeViewModel query path is broken |
| Settings: shows total offline storage size | ❌ | Shows "Calculating..." but never resolves |
| Settings: "Clear offline files" button | ⚠️ | Button exists but was not visible due to scroll issue (now fixed) |
| Download button disabled if already downloaded | ✅ | Just added |

---

## § 2.9 — Raindrop Integration

| Spec | Status | Gap |
|------|--------|-----|
| Two save methods: Silent (API) or Via App | ✅ | Settings radio implemented |
| Token stored in EncryptedSharedPreferences | ✅ | |
| Token verify: GET /rest/v1/user | ✅ | |
| POST to Raindrop REST API | ✅ | |
| Send original Medium URL (not proxy) | ✅ | |
| Snackbar on 200: "Saved to Raindrop ✓" | ✅ | |
| Snackbar on failure with [Retry] | ⚠️ | Error shown but no Retry action in snackbar |
| Raindrop app not installed → fallback to API | ⚠️ | Not verified |
| Update `raindropSavedAt` on success | ⚠️ | Not confirmed |

---

## § 2.10 — Search

| Spec | Status | Gap |
|------|--------|-----|
| Search bar in header, tap 🔍 to expand inline | ✅ | |
| **Back arrow in search mode uses old back icon** | ❌ | User reported this; the collapse button still shows the legacy vector |
| Scope chip [All ▾] dropdown | ❌ | No scope selector implemented |
| Results update live with 300ms debounce | ✅ | |
| Empty state: "No articles match '{query}'" | ⚠️ | Shows generic empty state, not query-specific message |
| Case-insensitive | ✅ | SQL LIKE handles this |

---

## § 5.1 — HomeFragment UX

| Spec | Status | Gap |
|------|--------|-----|
| `[+ Save]` button next to `[Read]` | ❌ | Removed; only `[Read]` exists |
| Chip bar with All, Offline, Unsorted, user lists | ⚠️ | All/Offline/Unsorted present; user list chips removed in latest session (intentionally moved to ListsFragment) — but plan says they should also be on home |
| "Your Library" section header | ❌ | Not present |
| Swipe RIGHT → mark as read | ❌ | Only LEFT swipe (delete) implemented |
| Swipe LEFT → delete with 5s undo snackbar | ⚠️ | Delete implemented but 5s undo snackbar not confirmed |
| UNREAD: bold title | ❌ | All titles are bold regardless of state |
| READING: smaller dot | ❌ | |
| READ: dimmed + ✓ icon | ❌ | |
| Source URL + time ago on card | ⚠️ | URL shown; time-ago says "Saved X days ago" — plan says "medium.com · 2h ago" (domain + relative time) |
| Tag chips on card | ✅ | |
| List name shown on card | ❌ | **User's complaint** — cards show tag chips but NOT which list(s) the article belongs to |

---

## § 5.2 — ReaderActivity UX

| Spec | Status | Gap |
|------|--------|-----|
| ← back button styled correctly | ⚠️ | User says it looks bad, especially in search mode |
| Progress bar above bottom action bar | ✅ | |
| 🔖 outline when not saved, filled when saved | ⚠️ | Visual distinction not confirmed |
| ✓ greyed out when not saved | ⚠️ | Not confirmed |
| Settings icon hidden when reading (user request) | ❌ | Settings icon still visible during article reading |
| "Overflow menu" in reader | ❌ | The plan calls for a "⋯ More" overflow menu with: Move to List, Add/Edit Tags, Save Offline, Copy URL, Share |

---

## § 5.4 — ArticleContextBottomSheet (long-press home card)

| Spec | Status | Gap |
|------|--------|-----|
| "📖 Open in Reader" | ✅ | |
| "📋 Move to List" → list-picker sub-sheet | ❌ | Only shows a toast |
| "🏷 Add/Edit Tags" → tag-editor sub-sheet | ❌ | Only shows a toast |
| "🌧 Send to Raindrop" | ❌ | Not in context sheet |
| "🔗 Copy URL" | ❌ | Not in context sheet |
| "↗ Share" | ❌ | Not in context sheet |
| "✓ Mark as Read" / "Mark as Unread" | ❌ | Not in context sheet |
| "🗑 Remove from Library" with undo | ⚠️ | Delete exists but undo snackbar not confirmed |

---

## § 5.5 — ListsFragment UX

| Spec | Status | Gap |
|------|--------|-----|
| System rows (All, Offline, Unsorted) — non-draggable | ❌ | System rows not shown in ListsFragment at all; only user lists |
| Drag-and-drop reorder user lists | ✅ | ItemTouchHelper implemented |
| Tap row → filtered article list | ✅ | Now navigates to ArticleListFragment |
| Swipe LEFT user list → delete with undo | ⚠️ | Delete on swipe works; undo not confirmed |
| Long-press user list → rename/recolor | ⚠️ | Partial implementation |

---

## § 5.6 — TagsFragment UX

| Spec | Status | Gap |
|------|--------|-----|
| Chips with counts `[android(12)]` | ✅ | |
| Tap → filtered article list | ✅ | Now navigates to ArticleListFragment |
| Long-press → rename OR delete dialog | ❌ | Currently **deletes immediately** on long-press — no dialog |

---

## Priority Fix Queue

### P0 — Critical (Broken Features)
1. **Long-press tag → always deletes** (no rename/delete choice dialog)
2. **Offline filter chip returns 0 results** (HomeViewModel or DAO path broken)
3. **Already-offline article can't be viewed offline** (`allowFileAccess` fix in, but MHT loading may still need `setAllowUniversalAccessFromFileURLs`)
4. **Already-in-library banner shows for offline-only saves** (suppressBanner logic flaw)

### P1 — High Priority (Major UX gaps)
5. **Article card doesn't show which list it belongs to** — only tags shown
6. **Read state not reflected visually** — all cards look UNREAD regardless of state
7. **READING state not set when article is opened from library**
8. **Swipe RIGHT to mark as read** — completely missing
9. **[+ Save] button on home screen** — removed but should exist per spec
10. **ArticleContextBottomSheet is mostly stubs** — Move to List, Add/Edit Tags, Copy URL, Share, Mark as Read all missing

### P2 — Medium Priority
11. Back icon in search mode is the legacy drawable
12. Settings icon visible in reader (user wants it hidden)
13. Tag rename via long-press (choose rename vs delete)
14. Settings "Calculating..." storage size never resolves
15. Offline failure message should be specific snackbar, not generic toast
16. Swipe left undo snackbar (5s)
17. Read state visual differentiation on cards (bold/dimmed/dot size)

### P3 — Lower Priority
18. [+ Save] without loading article first (paste input second button)
19. Search scope chip [All ▾] dropdown
20. Short page auto-mark READ
21. JS resize event for dynamic page height
22. onPause() progress flush
23. Raindrop snackbar Retry action
24. "No articles match '{query}'" empty state per search query
