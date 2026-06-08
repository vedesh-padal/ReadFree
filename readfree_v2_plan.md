# ReadFree v2.0 — Implementation Plan

> **Part coverage**: This document has two halves.
> Sections 1–4: Overview, Features, Data Model, Architecture.
> Sections 5–8: UX Screens, Dependencies, Implementation Phases, Edge Cases.

> **Purpose**: This document is the single source of truth for building ReadFree v2.0.
> It is written for a developer (or AI) picking this up cold and implementing it completely.
> Every feature, edge case, UX interaction, data model decision, and architectural choice is documented here.

---

## 1. Project Overview

ReadFree is a personal Android reading manager. Its core function:
proxy Medium articles through a Freedium mirror to bypass the paywall,
render them in-app, and let the user build a personal reading library.

### 1.1 Constraints
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Release APK size**: < 10 MB
- **Language**: Kotlin
- **UI**: ViewBinding + XML layouts (no Compose)
- **No login, no backend, no cloud sync** — fully local-first
- **Single-activity architecture** with Fragments for home; ReaderActivity stays separate

### 1.2 Existing Codebase State
The following classes already exist and must be preserved/extended:
- `MainActivity.kt` — currently hosts both home screen and reader; will be refactored to host Fragments
- `MirrorRepository.kt` — mirror selection, failover, SharedPreferences persistence
- `ReadFreeWebViewClient.kt` — named WebViewClient with typed Listener interface
- `UrlUtils.kt` — pure URL utility functions (`extractUrl`, `isMediumDomain`, `formatUrlForDisplay`)
- Package: `com.vedesh.readfree`

---

## 2. Final Agreed Feature Set

### 2.1 Reading (existing, extended)
- Proxy Medium URLs through Freedium mirror; non-Medium URLs load directly
- Mirror failover: user-preferred mirror → default mirror → error panel
- Mirror settings in a BottomSheet (already exists)
- SSL warning dialog on certificate errors
- "Open in Browser" via Chrome Custom Tab

### 2.2 Universal Library — Save Any URL
- Any URL (Medium or otherwise) can be saved to the library
- Medium URLs are proxied via Freedium when opened in the reader
- Non-Medium URLs are opened directly in the WebView (no proxy, no failover)
- The library layer (lists, tags, read state) works identically for both URL types

### 2.3 Save Flow
**From share sheet (any app → ReadFree):**
1. App opens, reader loads the article immediately (no blocking dialog)
2. A non-blocking save banner appears at the top of the reader, auto-dismisses after 5 seconds:
   `"Add to library?  [Save]  [✕]"`
3. User can ignore banner → just reads, banner gone after 5s
4. User taps [Save] → Save Bottom Sheet opens
5. 🔖 button in bottom action bar is always available throughout reading

**From home screen paste input:**
Two buttons: `[Read]` and `[+ Save]`
- `[Read]` → opens reader (existing behavior), banner appears
- `[+ Save]` → opens Save Bottom Sheet immediately without loading article first; title field shows URL (no WebView needed)

**Edge cases:**
- If article is already saved: banner shows `"Already in library  [Edit]  [✕]"` instead
- Title fallback: if `WebView.getTitle()` returns null/empty, use the URL's hostname + path
- Save & Close: save dialog has a "Save & Close" button that saves and calls `finish()`

### 2.4 Lists (Folders)
- User creates named lists with an emoji and a color (from a preset palette of 8 colors)
- One article can belong to **multiple lists** (many-to-many relationship)
- System lists (not deletable, not reorderable):
  - **All** — every saved article
  - **Offline** — articles with an offline file saved
- User lists:
  - **Unsorted** is not a real list — it's a query: articles with no list assignment
  - User lists shown below system lists, reorderable via drag-and-drop
- Deleting a list does NOT delete its articles; they remain accessible via "All"
- Renaming a list is supported

### 2.5 Tags
- Free-form, user-defined, global (not scoped to a list)
- An article can have zero or more tags
- Tags input: chip-style input field (type + Enter or comma to add)
- Existing tags autocomplete as user types
- Tags shown as small chips on article cards in the library
- Tags management screen: see all tags with article counts, rename, delete
- Deleting a tag removes it from all articles but does not delete articles

### 2.6 Read State
Three states per article: `UNREAD` → `READING` → `READ`

| Trigger | State change |
|---------|-------------|
| Article saved | → UNREAD |
| Article opened from library | UNREAD → READING |
| User taps ✓ in reader | → READ (toggles: READ → UNREAD) |
| Swipe right on library card | → READ |
| Long-press → "Mark as Unread" | READ → UNREAD |
| Progress reaches 90% | → READ (auto) |

Visual in library:
- UNREAD: bold title + colored dot (accent color)
- READING: normal title + smaller accent dot
- READ: dimmed title + ✓ icon

### 2.7 Reading Progress
- Track scroll 0–100% via a JavaScript bridge injected after page load
- Progress bar: 2dp thin bar directly above the bottom action bar
- Persisted to Room: on `onPause()` AND when delta from last save ≥ 5%
- On reopening a saved article: scroll to saved position via JS
- Auto-marks READ when progress ≥ 90%

**JS injection (after onPageFinished):**
```javascript
(function() {
  var h = document.body.scrollHeight - window.innerHeight;
  if (h <= 0) return;
  window.addEventListener('scroll', function() {
    var pct = Math.min(100, Math.round((window.scrollY / h) * 100));
    ReadFreeProgress.onProgress(pct);
  }, { passive: true });
})();
```

**Scroll restore on open:**
```javascript
window.scrollTo(0, document.body.scrollHeight * SAVED_PROGRESS / 100);
```

Edge cases:
- Some pages have dynamic height (lazy-loaded content). Re-calculate `h` on `resize` event.
- If `scrollHeight <= innerHeight` (short page): skip tracking, mark as READ immediately.

### 2.8 Offline Saving
- Triggered via reader overflow menu: "Save Offline"
- Uses `WebView.saveWebArchive(path, false, callback)` — the callback returns the path on success or `null` on failure
- Stored path in Room: `Article.offlineFilePath`
- On article open from library: if `offlineFilePath != null` AND `NetworkUtils.isOffline(context)` → load `file://path` instead of network URL
- 📥 indicator badge on article cards in library
- "Offline" system list shows all articles with an offline file
- Settings: shows total offline storage size; "Clear Offline Files" button deletes all `.mht` files and sets `offlineFilePath = null` on all articles

Edge cases:
- `saveWebArchive` can silently fail on cross-origin pages. Always check callback result; show a snackbar on failure: "Could not save offline — page has cross-origin restrictions."
- File path uses article URL hash as filename to avoid collisions: `${filesDir}/offline/${urlHash}.mht`

### 2.9 Raindrop.io Integration

**Two save methods — user chooses in Settings:**

| Setting | Behavior |
|---------|----------|
| Silent (API) | Calls Raindrop REST API directly; saves in background; shows snackbar confirmation |
| Via App | Fires `ACTION_SEND` intent to Raindrop app's share handler |

**Fallback logic:**
```
Token configured?
├── YES → use selected method (Silent or Via App)
│         if Via App but Raindrop not installed → use Silent API automatically
└── NO  → always Via App (share intent, requires Raindrop app)
           if Raindrop app not installed → show generic share sheet
```

**REST API call:**
```
POST https://api.raindrop.io/rest/v1/raindrop
Authorization: Bearer <token>
Content-Type: application/json
{ "link": "<original medium.com URL>", "title": "<title>", "tags": ["readfree"] }
```
- Always send the **original Medium URL**, not the Freedium proxy URL
- Token stored in `EncryptedSharedPreferences`
- On 200: snackbar "Saved to Raindrop ✓"
- On failure (network/auth): snackbar "Raindrop save failed  [Retry]"

**Token verification flow (in Settings):**
```
GET https://api.raindrop.io/rest/v1/user
Authorization: Bearer <token>
```
On 200: show "✓ Connected as {name}"
On 401: show "✗ Invalid token"

### 2.10 Search
- Scope: metadata only — article title and tag names. No content extraction.
- Implementation: SQL `LIKE '%query%'` on `title` + join on tag names
- UI: search bar in home screen header (tap 🔍 to expand inline)
- Scope selector chip: [All ▾] → dropdown: All / specific list name / Offline only
- Results update live as user types (debounced 300ms)
- Empty state: "No articles match '{query}'"
- Search is case-insensitive

---

## 3. Data Model

### 3.1 Entities

```kotlin
// Article — core entity. URL is the natural primary key.
@Entity(tableName = "articles")
data class Article(
    @PrimaryKey val url: String,
    val title: String,
    val savedAt: Long = System.currentTimeMillis(),
    val readState: ReadState = ReadState.UNREAD,
    val scrollProgress: Int = 0,           // 0–100
    val offlineFilePath: String? = null,
    val raindropSavedAt: Long? = null,
    val isMediumUrl: Boolean = false        // drives proxy vs direct load decision
)

enum class ReadState { UNREAD, READING, READ }

// User-created list (folder)
@Entity(tableName = "lists")
data class ArticleList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "📁",
    val colorHex: String = "#6C63FF",
    val sortOrder: Int = 0
)

// Join: article <-> list (many-to-many)
@Entity(tableName = "article_list_xref", primaryKeys = ["articleUrl", "listId"])
data class ArticleListXRef(
    val articleUrl: String,
    val listId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

// Tag
@Entity(tableName = "tags")
data class Tag(@PrimaryKey val name: String)

// Join: article <-> tag (many-to-many)
@Entity(tableName = "article_tag_xref", primaryKeys = ["articleUrl", "tagName"])
data class ArticleTagXRef(val articleUrl: String, val tagName: String)
```

### 3.2 Relation Types

```kotlin
data class ArticleWithTags(
    @Embedded val article: Article,
    @Relation(
        parentColumn = "url",
        entityColumn = "name",
        associateBy = Junction(
            ArticleTagXRef::class,
            parentColumn = "articleUrl",
            entityColumn = "tagName"
        )
    )
    val tags: List<Tag>
)

data class ListWithCount(
    @Embedded val list: ArticleList,
    val articleCount: Int
)
```

### 3.3 ArticleDao

```kotlin
@Dao
interface ArticleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(article: Article)
    @Update suspend fun update(article: Article)
    @Delete suspend fun delete(article: Article)
    @Query("SELECT * FROM articles WHERE url = :url") suspend fun getByUrl(url: String): Article?
    @Query("SELECT EXISTS(SELECT 1 FROM articles WHERE url = :url)") suspend fun exists(url: String): Boolean

    @Transaction @Query("SELECT * FROM articles ORDER BY savedAt DESC")
    fun getAll(): Flow<List<ArticleWithTags>>

    @Transaction @Query("""
        SELECT a.* FROM articles a
        INNER JOIN article_list_xref x ON a.url = x.articleUrl
        WHERE x.listId = :listId ORDER BY x.addedAt DESC
    """) fun getByList(listId: Long): Flow<List<ArticleWithTags>>

    @Transaction @Query("""
        SELECT * FROM articles
        WHERE url NOT IN (SELECT articleUrl FROM article_list_xref)
        ORDER BY savedAt DESC
    """) fun getUnsorted(): Flow<List<ArticleWithTags>>

    @Transaction @Query("SELECT * FROM articles WHERE offlineFilePath IS NOT NULL ORDER BY savedAt DESC")
    fun getOffline(): Flow<List<ArticleWithTags>>

    // Metadata search: title LIKE query OR has matching tag
    @Transaction @Query("""
        SELECT DISTINCT a.* FROM articles a
        LEFT JOIN article_tag_xref t ON a.url = t.articleUrl
        WHERE a.title LIKE '%' || :query || '%'
           OR t.tagName LIKE '%' || :query || '%'
        ORDER BY a.savedAt DESC
    """) fun search(query: String): Flow<List<ArticleWithTags>>

    // Scoped search within a list
    @Transaction @Query("""
        SELECT DISTINCT a.* FROM articles a
        INNER JOIN article_list_xref x ON a.url = x.articleUrl
        LEFT JOIN article_tag_xref t ON a.url = t.articleUrl
        WHERE x.listId = :listId
          AND (a.title LIKE '%' || :query || '%' OR t.tagName LIKE '%' || :query || '%')
        ORDER BY x.addedAt DESC
    """) fun searchInList(query: String, listId: Long): Flow<List<ArticleWithTags>>

    @Query("UPDATE articles SET readState = :state WHERE url = :url")
    suspend fun updateReadState(url: String, state: ReadState)

    @Query("UPDATE articles SET scrollProgress = :progress WHERE url = :url")
    suspend fun updateProgress(url: String, progress: Int)

    @Query("UPDATE articles SET offlineFilePath = :path WHERE url = :url")
    suspend fun updateOfflinePath(url: String, path: String?)

    @Query("UPDATE articles SET raindropSavedAt = :ts WHERE url = :url")
    suspend fun updateRaindropTs(url: String, ts: Long?)

    @Query("UPDATE articles SET offlineFilePath = NULL")
    suspend fun clearAllOfflinePaths()
}
```

### 3.4 ListDao

```kotlin
@Dao
interface ListDao {
    @Insert suspend fun insert(list: ArticleList): Long
    @Update suspend fun update(list: ArticleList)
    @Delete suspend fun delete(list: ArticleList)

    @Query("""
        SELECT l.*, COUNT(x.articleUrl) as articleCount
        FROM lists l LEFT JOIN article_list_xref x ON l.id = x.listId
        GROUP BY l.id ORDER BY l.sortOrder ASC
    """) fun getAllWithCounts(): Flow<List<ListWithCount>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addArticleToList(xref: ArticleListXRef)

    @Query("DELETE FROM article_list_xref WHERE articleUrl = :url AND listId = :listId")
    suspend fun removeArticleFromList(url: String, listId: Long)

    @Query("SELECT listId FROM article_list_xref WHERE articleUrl = :url")
    suspend fun getListIdsForArticle(url: String): List<Long>

    @Query("UPDATE lists SET sortOrder = :order WHERE id = :id")
    suspend fun updateSortOrder(id: Long, order: Int)
}
```

### 3.5 TagDao

```kotlin
@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(tag: Tag)
    @Delete suspend fun delete(tag: Tag)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTagToArticle(xref: ArticleTagXRef)

    @Query("DELETE FROM article_tag_xref WHERE articleUrl = :url AND tagName = :tag")
    suspend fun removeTagFromArticle(url: String, tag: String)

    @Query("DELETE FROM article_tag_xref WHERE tagName = :tag")
    suspend fun removeTagFromAllArticles(tag: String)

    @Query("SELECT DISTINCT name FROM tags ORDER BY name ASC")
    fun getAll(): Flow<List<Tag>>

    @Query("""
        SELECT t.name, COUNT(x.articleUrl) as articleCount
        FROM tags t LEFT JOIN article_tag_xref x ON t.name = x.tagName
        GROUP BY t.name ORDER BY articleCount DESC
    """) fun getAllWithCounts(): Flow<List<TagWithCount>>

    @Query("SELECT tagName FROM article_tag_xref WHERE articleUrl = :url")
    suspend fun getTagsForArticle(url: String): List<String>

    @Query("UPDATE article_tag_xref SET tagName = :newName WHERE tagName = :oldName")
    suspend fun renameTagInXRef(oldName: String, newName: String)

    @Query("UPDATE tags SET name = :newName WHERE name = :oldName")
    suspend fun renameTag(oldName: String, newName: String)
}

data class TagWithCount(val name: String, val articleCount: Int)
```

### 3.6 AppDatabase

```kotlin
@Database(
    entities = [Article::class, ArticleList::class, ArticleListXRef::class, Tag::class, ArticleTagXRef::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun listDao(): ListDao
    abstract fun tagDao(): TagDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "readfree.db")
                    .fallbackToDestructiveMigration() // dev only; use proper migrations for release
                    .build().also { INSTANCE = it }
            }
    }
}

class Converters {
    @TypeConverter fun fromReadState(s: ReadState) = s.name
    @TypeConverter fun toReadState(s: String) = ReadState.valueOf(s)
}
```

---

## 4. Architecture

### 4.1 Layer Map

```
UI Layer
  MainActivity (Fragment host)
  HomeFragment          — library, paste input, search
  ListsFragment         — list management
  TagsFragment          — tag browser
  ReaderActivity        — WebView reader (separate Activity)
  [Various BottomSheets] — save, create list, settings, context menu

ViewModel Layer
  HomeViewModel         — article list (Flow), active filter, search query
  ReaderViewModel       — article save state, scroll progress, Raindrop action
  ListsViewModel        — lists CRUD
  TagsViewModel         — tags CRUD

Repository Layer
  ArticleRepository     — wraps ArticleDao; all article operations
  ListRepository        — wraps ListDao; all list operations
  TagRepository         — wraps TagDao; all tag operations
  RaindropRepository    — OkHttp calls to Raindrop REST API
  MirrorRepository      — existing; mirror selection + failover (unchanged)

Data Layer
  AppDatabase (Room)
  EncryptedSharedPreferences (Raindrop token, Raindrop save method preference)
  SharedPreferences (mirror prefs — existing)
  Internal file storage (offline .mht files)
```

### 4.2 Navigation Structure

```
MainActivity
├── HomeFragment (default destination)
│   ├── SaveBottomSheet
│   ├── CreateListBottomSheet
│   └── ArticleContextBottomSheet
├── ListsFragment
│   └── CreateListBottomSheet
├── TagsFragment
└── SettingsBottomSheet (accessible from any screen)

ReaderActivity (started via Intent from HomeFragment or on share/view intent)
├── SaveBottomSheet
├── SettingsBottomSheet
└── ArticleContextBottomSheet (overflow menu)
```

Use `androidx.navigation:navigation-fragment-ktx` for Fragment navigation inside MainActivity.
Bottom nav or top-level navigation is **not** needed — the home screen contains the library.
Navigation from HomeFragment to ListsFragment/TagsFragment uses the Navigation Component back stack.

### 4.3 Intent Handling

`MainActivity.handleIntent()` must handle:

| Intent | Action |
|--------|--------|
| `ACTION_SEND` + `text/plain` | Extract URL via `UrlUtils.extractUrl()` → start `ReaderActivity` with URL |
| `ACTION_VIEW` + medium.com URL | Start `ReaderActivity` with URL |
| `ACTION_MAIN` (launcher) | Show `HomeFragment` |

`ReaderActivity` receives the URL via `intent.getStringExtra("url")`.
`ReaderActivity` also handles `ACTION_SEND` and `ACTION_VIEW` directly (for when the OS routes to it).

### 4.4 URL Routing in ReaderActivity

```kotlin
fun isProxied(url: String): Boolean = UrlUtils.isMediumDomain(url)

fun buildLoadUrl(originalUrl: String): String =
    if (isProxied(originalUrl)) mirrors.buildProxyUrl(originalUrl)
    else originalUrl
```

Failover (`tryNextMirrorOrShowError`) only triggers for proxied URLs.
For direct URLs, `onReceivedError` shows the error panel immediately with message:
"Could not load this page. Check your connection."

---

---

## 5. Screen-by-Screen UX

### 5.1 HomeFragment

```
┌────────────────────────────────────┐
│  📖 ReadFree              [🔍][⚙] │  ← search expands inline; gear = settings
│────────────────────────────────────│
│  [ Paste or type a URL...        ] │
│  [ Read ]  [ + Save ]              │
│────────────────────────────────────│
│  Your Library                      │
│  [📚All][Read Later][Work][🤖 AI]  │  ← horizontal scrollable chips
│  [Unsorted][📥Offline][+ New List] │
│────────────────────────────────────│
│  🔵  Article Title (UNREAD bold)   │  unread = blue dot + bold
│       medium.com · 2h ago          │
│       [android] [kotlin]           │
│  ·   Another Article (READING)     │  reading = small accent dot
│       dev.to · 1d ago              │
│  ✓   Read Article (READ dimmed)    │  read = checkmark + dimmed
│       medium.com · 3d ago          │
└────────────────────────────────────┘
```

**Search mode** (tap 🔍):
- Search bar expands inline, replacing title row
- [← back] collapses and clears search
- Scope chip [All ▾]: dropdown → All / list names / Offline only
- Results update live with 300ms debounce on query changes

**Article card interactions:**
- Tap → `ReaderActivity` with url; if UNREAD → set READING
- Swipe RIGHT → green "✓ Mark as Read" reveal → sets READ on completion
- Swipe LEFT → red "🗑 Remove" reveal → deletes with 5s undo snackbar
- Long-press → `ArticleContextBottomSheet`

**Chip bar:**
- Default: "All" selected (filled chip)
- Tapping any chip updates `HomeViewModel.activeFilter`
- "+ New List" → `CreateListBottomSheet`

**Empty states:**
- Library empty: "📚 Your library is empty. Share any article to ReadFree to save it."
- List empty: "This list is empty. Save articles and assign them here."
- Search no results: "No articles match '{query}'"

### 5.2 ReaderActivity

```
┌────────────────────────────────────┐
│ [←]  freedium-mirror.cfd  [⋯ More] │
│━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━│
│  ┌─────────────────────────────┐   │
│  │ Add to library? [Save] [✕] │   │  ← auto-dismisses 5s
│  └─────────────────────────────┘   │
│          WebView content           │
│────────────────────────────────────│
│ ████████░░░░░░░░░░░░░░  38%       │  ← 2dp progress bar
│ [🔖][✓][🌧][Browser][⚙]          │  ← bottom action bar
└────────────────────────────────────┘
```

**Save banner logic:**
- Appears immediately; async check: `articleDao.exists(url)`
  - false → "Add to library? [Save][✕]"
  - true → "In your library  [Edit][✕]"
- Auto-dismisses after 5s via `Handler.postDelayed`
- [✕] cancels immediately
- [Save]/[Edit] → `SaveBottomSheet`

**Bottom bar state table:**

| Button | Not saved | Saved |
|--------|-----------|-------|
| 🔖 | Outline; opens Save sheet | Filled; opens Edit sheet |
| ✓ | Greyed out | Active; toggles READ/UNREAD |
| 🌧 | Always active (saves without library entry) | Active |
| Browser | Always active | Always active |
| ⚙ | Always active | Always active |

**Overflow menu:**
- Move to List (if saved)
- Add/Edit Tags (if saved)
- Save Offline (if page loaded)
- Copy URL
- Share

**Error panel for direct (non-Medium) URLs:**
- Shows: ⚠️ + "Could not load this page." + [Retry]
- No "Configure Mirror" button (mirror irrelevant for direct URLs)

**Loading text differentiation:**
- Medium URLs: "Contacting Freedium…"
- Non-Medium URLs: "Loading…"

### 5.3 SaveBottomSheet

```
┌─────────────────────────────────────┐
│  ─── Save Article                   │
│  Title: [ Article title here...   ] │  ← editable; pre-filled
│  Add to list:                       │
│  [✓Read Later][Work][🤖AI]  scroll  │  ← multi-select toggleable chips
│  [+ Create new list]                │
│  Tags (Enter/comma to add):         │
│  [android✕][kotlin✕]  [          ] │  ← chip input + autocomplete
│  [ Save ]    [ Save & Close ]       │
└─────────────────────────────────────┘
```

- Title: `WebView.getTitle()` or `Uri.parse(url).host + path` as fallback
- List chips: multi-select; tap toggles inclusion
- "Save": insert/update + close sheet; 🔖 turns filled
- "Save & Close": insert/update + `ReaderActivity.finish()`
- Edit mode: pre-filled with existing data; button says "Update"

### 5.4 ArticleContextBottomSheet (long-press)

```
│  ─── Article Title (truncated)   │
│  [ 📖 Open in Reader           ] │
│  [ 📋 Move to List             ] │  → list-picker sub-sheet
│  [ 🏷 Add / Edit Tags          ] │  → tag-editor sub-sheet
│  [ 🌧 Send to Raindrop         ] │
│  [ 🔗 Copy URL                 ] │
│  [ ↗ Share                     ] │
│  [ ✓ Mark as Read              ] │  or "Mark as Unread"
│  [ 🗑 Remove from Library      ] │  → confirm via undo snackbar
```

### 5.5 ListsFragment

```
│ ← My Lists                  [+New] │
│  SYSTEM (non-draggable)            │
│  📚 All Articles             42    │
│  📥 Offline                   8    │
│     Unsorted                 11    │
│  MY LISTS (draggable)              │
│  ≡  📁 Read Later            12    │
│  ≡  💼 Work                   5    │
│  ≡  🤖 AI Stuff               9    │
```

- Tap row → filtered article list
- Swipe LEFT user list → delete with undo
- Long-press user list → rename/recolor bottom sheet
- ≡ drag handle via `ItemTouchHelper` (system rows non-draggable)
- Drag end: batch update `sortOrder` for all user lists

### 5.6 TagsFragment

```
│ ← Tags                             │
│  [android(12)][kotlin(8)][ai(7)]   │
│  [system-design(3)][work(5)]...    │
│  Tap to filter · Long-press to edit│
```

- Tap → filtered article list via HomeFragment with tag filter
- Long-press → rename or delete tag

### 5.7 CreateListBottomSheet

```
│  ─── New List                     │
│  Name: [ Read Later             ] │
│  Emoji: 📁 📚 💼 🤖 🎯 ⭐ 🔬 🎨  │  ← horizontal grid, scrollable
│          🌐 📝 🏠 💡 🔥 🌱 🎵 🎮 │
│  Color:  ● ● ● ● ● ● ● ●        │  ← 8 color swatches
│  [ Create ]                       │
```

### 5.8 SettingsBottomSheet

```
│  ─── Settings                     │
│  MIRROR                           │
│  ● freedium-mirror.cfd (default)  │
│  ○ Custom: [ https://...        ] │
│  [ Apply ]                        │
│  ──────────────────────────────── │
│  RAINDROP.IO                      │
│  Token: [•••••••••] [Verify]     │
│  ✓ Connected as {name}            │
│  Save method:                     │
│  ● Silent (API)                   │
│  ○ Via App (share sheet)          │
│  ──────────────────────────────── │
│  OFFLINE STORAGE                  │
│  Used: 14.2 MB                    │
│  [ Clear offline files ]          │
│  ──────────────────────────────── │
│  ReadFree v2.0                    │
```

---

## 6. Dependencies

Add to `app/build.gradle`:

```kotlin
plugins {
    id("com.google.devtools.ksp") version "2.0.21-1.0.28"
}

dependencies {
    val roomVersion = "2.7.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
```

Add to root `build.gradle` (project-level):
```kotlin
plugins {
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}
```

**APK size (release build with R8):**

| Component | Size |
|-----------|------|
| Room | ~200KB |
| ViewModel + Fragment + Navigation | ~300KB |
| OkHttp | ~800KB |
| security-crypto | ~50KB |
| Current app | ~3.5MB |
| **Total projected** | **~4.85MB** |

---

## 7. Implementation Phases

### Phase 1 — Data Foundation
Create all entities, DAOs, database, and repositories. Zero UI changes.

Files: `data/db/entity/`, `data/db/dao/`, `data/db/AppDatabase.kt`, `data/db/Converters.kt`, `data/model/`, `data/repository/`

- Add Room + KSP to both `build.gradle` files
- Define all entities as in Section 3 — include `@ForeignKey(onDelete = CASCADE)` on both xref tables
- Define all DAOs as in Section 3
- `ArticleRepository`: suspend wrappers around DAO; no business logic
- `ListRepository`: CRUD + `batchUpdateSortOrder(ids: List<Long>)` which updates all in one transaction
- `TagRepository`: CRUD + `rename(old, new)` as a `@Transaction` function

**Commit:** `feat(data): Room schema with articles, lists, tags — entities, DAOs, repositories`

### Phase 2 — ReaderActivity Split
Extract reader from `MainActivity` into `ReaderActivity`. `MainActivity` becomes a Fragment host.

- Create `ReaderActivity.kt` — move all WebView, toolbar, loading overlay, error panel code
- `ReaderActivity` reads `intent.getStringExtra("url")` as the article URL
- Add `isMediumUrl` check: `UrlUtils.isMediumDomain(url)` → proxy if true; direct load if false
- Non-Medium direct URL failures: show error panel with generic message, no "Configure Mirror" button
- `MainActivity` now only hosts a `FragmentContainerView` with nav graph
- Move existing home screen XML to `fragment_home.xml` as a placeholder `HomeFragment`
- Update `AndroidManifest.xml`: `ReaderActivity` gets `ACTION_SEND` + `ACTION_VIEW` intent filters; `MainActivity` keeps only `ACTION_MAIN`
- `ReaderActivity` must declare `configChanges = "orientation|screenSize|screenLayout|smallestScreenSize"`

**Commit:** `refactor(reader): extract ReaderActivity; MainActivity becomes Fragment host`

### Phase 3 — Library Home Screen
Replace static home screen with dynamic reading list.

Files: `ui/home/HomeFragment.kt`, `HomeViewModel.kt`, `ArticleAdapter.kt`, `fragment_home.xml`, `item_article_card.xml`

- `LibraryFilter` sealed class: `All`, `ByList(id: Long)`, `ByTag(name: String)`, `Offline`, `Unsorted`, `Search(query: String, scope: LibraryFilter)`
- `HomeViewModel`: `activeFilter: MutableStateFlow<LibraryFilter>`, `articles: Flow` switching on filter, `lists: Flow` for chip bar
- `ArticleAdapter`: `ListAdapter` + `DiffUtil` on `ArticleWithTags`; bind read state visuals
- `ItemTouchHelper`: swipe right = mark read; swipe left = delete with undo snackbar
- Chip bar: separate horizontal `RecyclerView`
- Search: `TextInputLayout` in header that expands/collapses; 300ms debounce using `Flow.debounce`
- Paste input: [Read] starts `ReaderActivity`; [+ Save] opens `SaveBottomSheet` without reader

**Commit:** `feat(home): library screen with dynamic list, filter chips, swipe gestures, search`

### Phase 4 — Save Flow
Files: `ui/sheet/SaveBottomSheet.kt`, `ui/reader/ReaderViewModel.kt`, `bottom_sheet_save.xml`

- `ReaderViewModel`: `isSaved: StateFlow<Boolean>`, `currentArticle: StateFlow<Article?>`; check existence on init
- Save banner: `MaterialCardView` overlaid at top of reader; driven by `isSaved`; auto-dismiss in 5s
- `SaveBottomSheet`: receives `url`, `title`; fetches existing article data if editing
- Tag chip input: `ChipGroup` + `TextInputEditText`; autocomplete from `tagRepository.getAll()`
- Save coroutine: `articleRepository.save()` → `listRepository.assign()` → `tagRepository.assign()` all sequentially
- On save complete: emit event → caller updates 🔖 icon state

**Commit:** `feat(save): non-blocking save banner, SaveBottomSheet with list/tag assignment`

### Phase 5 — Lists & Tags Management
Files: `ui/lists/`, `ui/tags/`, `ui/sheet/CreateListBottomSheet.kt`, `ArticleContextBottomSheet.kt`

- `ListsFragment` + `ListsViewModel`: RecyclerView with `ItemTouchHelper` drag; system rows non-draggable
- Drag completion: call `listRepository.batchUpdateSortOrder()`
- Delete list: `listRepository.delete(list)` → cascade removes xref rows → articles unaffected
- `TagsFragment` + `TagsViewModel`: `ChipGroup` of all tags with counts
- `ArticleContextBottomSheet`: shown on long-press; fires actions back via a callback/shared ViewModel
- `CreateListBottomSheet`: emoji RecyclerView (20 emojis) + 8 color swatch RadioButtons

**Commit:** `feat(lists-tags): list management, tag browser, context menu`

### Phase 6 — Read State & Progress
- Add `ReadState` `@TypeConverter` to `Converters.kt`
- `ReaderActivity.onPageFinished`: inject scroll JS via `webView.evaluateJavascript()`
- `addJavascriptInterface(ProgressBridge { pct -> viewModel.onProgress(pct) }, "ReadFreeProgress")`
- `ReaderViewModel.onProgress()`: debounce writes; auto-READ at 90%
- Scroll restore: if `article.scrollProgress > 5`, inject restore JS after `onPageFinished`
- ✓ button: `readerViewModel.toggleReadState()`
- Short page: if `scrollHeight <= innerHeight + 50`, skip tracking, mark READ on `onPageFinished`
- `ReaderActivity.onPause()`: flush pending progress immediately

**Commit:** `feat(reader): JS scroll progress bridge, read state transitions, auto-mark READ`

### Phase 7 — Raindrop Integration
Files: `data/repository/RaindropRepository.kt`

- `RaindropRepository.saveBookmark(url, title): Result<Unit>` — `POST /rest/v1/raindrop`
- `RaindropRepository.verifyToken(token): Result<String>` — `GET /rest/v1/user`
- `OkHttpClient` with 10s timeouts; all calls on `Dispatchers.IO`
- `EncryptedSharedPreferences`: store token + save method preference
- Settings: token field + [Verify]; save method radio (Silent / Via App)
- 🌧 button decision tree per Section 2.9
- Update `article.raindropSavedAt` on successful API save

**Commit:** `feat(raindrop): REST API integration, token management, share intent fallback`

### Phase 8 — Offline Saving
- "Save Offline" in overflow: `webView.saveWebArchive(path, false) { savedPath -> ... }`
- Path: `"${filesDir}/offline/${url.hashCode()}.mht"`
- On null callback: snackbar "Could not save offline"
- On article open: `NetworkUtils.isOffline()` + `offlineFilePath != null` → load from file
- 📥 badge on article cards
- "Offline" chip filter in HomeFragment
- Settings: calculate storage, show it, [Clear] deletes files + clears DB paths

**Commit:** `feat(offline): saveWebArchive offline saving with auto-load and storage management`

---

## 8. Global Edge Cases

| Concern | Handling |
|---------|---------|
| All Room ops on main thread | All DAO calls are `suspend`; called from coroutines; never from UI thread directly |
| Configuration change (rotation) | `configChanges` on both Activities; ViewModel survives; WebView not recreated |
| Same URL saved twice | `OnConflictStrategy.REPLACE`; but check existing first to preserve `readState` + `scrollProgress` |
| Tag rename atomicity | `@Transaction` function: update `article_tag_xref` then `tags` table |
| List delete cascade | `@ForeignKey(onDelete = CASCADE)` on `ArticleListXRef.listId` |
| Article delete cascade | `@ForeignKey(onDelete = CASCADE)` on both xref tables referencing `Article.url`; also delete offline file |
| `saveWebArchive` failure | Check callback for null; show snackbar; do not update Room |
| Raindrop API auth failure (401) | Snackbar "Invalid token — update in Settings" |
| Raindrop app not installed + Via App | Fall back to REST API if token available; else show "Raindrop app not installed" |
| Short article (no scroll) | Skip JS bridge; mark READ immediately on `onPageFinished` |
| Dynamic page height (lazy load) | Re-calculate scroll height on `resize` event in JS |
| Network state for offline load | `ConnectivityManager.activeNetworkInfo?.isConnected`; check fresh per open |
| ProGuard | `-keep class com.vedesh.readfree.** { *; }` covers all; OkHttp `-dontwarn` already present |
| Database migration | `version = 1` initially; write `Migration` objects for any schema change; never use destructive migration in release |

---

## 9. Final File Structure

```
app/src/main/java/com/vedesh/readfree/
├── data/
│   ├── db/
│   │   ├── AppDatabase.kt
│   │   ├── Converters.kt
│   │   ├── dao/
│   │   │   ├── ArticleDao.kt
│   │   │   ├── ListDao.kt
│   │   │   └── TagDao.kt
│   │   └── entity/
│   │       ├── Article.kt
│   │       ├── ArticleList.kt
│   │       ├── ArticleListXRef.kt
│   │       ├── ArticleTagXRef.kt
│   │       ├── ReadState.kt
│   │       └── Tag.kt
│   ├── model/
│   │   ├── ArticleWithTags.kt
│   │   ├── ListWithCount.kt
│   │   └── TagWithCount.kt
│   └── repository/
│       ├── ArticleRepository.kt
│       ├── ListRepository.kt
│       ├── MirrorRepository.kt      (existing)
│       ├── RaindropRepository.kt
│       └── TagRepository.kt
├── ui/
│   ├── home/
│   │   ├── HomeFragment.kt
│   │   ├── HomeViewModel.kt
│   │   └── ArticleAdapter.kt
│   ├── lists/
│   │   ├── ListsFragment.kt
│   │   └── ListsViewModel.kt
│   ├── tags/
│   │   ├── TagsFragment.kt
│   │   └── TagsViewModel.kt
│   ├── reader/
│   │   ├── ReaderActivity.kt
│   │   └── ReaderViewModel.kt
│   └── sheet/
│       ├── SaveBottomSheet.kt
│       ├── CreateListBottomSheet.kt
│       ├── ArticleContextBottomSheet.kt
│       └── SettingsBottomSheet.kt
├── util/
│   ├── NetworkUtils.kt
│   └── UrlUtils.kt                  (existing)
├── MainActivity.kt
└── ReadFreeWebViewClient.kt         (existing)

app/src/main/res/layout/
├── activity_main.xml                (fragment container only)
├── activity_reader.xml              (WebView + toolbar + bottom bar + overlays)
├── fragment_home.xml
├── fragment_lists.xml
├── fragment_tags.xml
├── item_article_card.xml
├── item_list_row.xml
├── bottom_sheet_save.xml
├── bottom_sheet_settings.xml        (existing, expanded)
├── bottom_sheet_create_list.xml
└── bottom_sheet_article_context.xml
```
