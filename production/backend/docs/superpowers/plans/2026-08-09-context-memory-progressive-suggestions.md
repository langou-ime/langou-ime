# Context Memory and Progressive Suggestions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate fast, progressive AI replies from current chat context plus per-conversation local memory, never from draft-only fallback.

**Architecture:** Android owns capture gating, anonymous conversation identity, encrypted recent turns, retrieval, and trigger deduplication. The backend receives only bounded redacted text and streams validated suggestions as soon as MiMo completes each line; summary updates run after suggestions and do not block the first result.

**Tech Stack:** Kotlin/coroutines/Android Keystore, kotlinx.serialization, FastAPI, Pydantic, httpx async streaming, pytest/Kotest.

---

### Task 1: Extend the backward-compatible suggestion contract

**Files:**
- Modify: `/Users/sommerzhao/Documents/懒狗/production/backend/src/langou_backend/schemas.py`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/backend/tests/test_schemas.py`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/backend/tests/test_openapi_contract.py`

- [ ] **Step 1: Write failing schema tests**

```python
payload["conversation_id"] = "conv_0123456789abcdef"
payload["memory_summary"] = "朋友；喜欢简短轻松回复；还在确认周末时间。"
payload["trigger"] = "context_change"
request = SuggestionRequest.model_validate(payload)
assert request.trigger == "context_change"
```

Also assert `screenshot`, `image`, and unknown fields remain forbidden.

- [ ] **Step 2: Run schema tests**

Run: `.venv/bin/pytest tests/test_schemas.py tests/test_openapi_contract.py -q`

Expected: FAIL because the new fields are not defined.

- [ ] **Step 3: Add optional bounded fields**

```python
conversation_id: Identifier | None = None
memory_summary: Annotated[str | None, Field(max_length=4000)] = None
trigger: Literal["context_change", "manual_refresh"] = "context_change"
```

- [ ] **Step 4: Run tests**

Run: `.venv/bin/pytest tests/test_schemas.py tests/test_openapi_contract.py -q`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/langou_backend/schemas.py tests/test_schemas.py tests/test_openapi_contract.py
git commit -m "feat: accept anonymous conversation memory"
```

### Task 2: Stream provider suggestions progressively

**Files:**
- Modify: `/Users/sommerzhao/Documents/懒狗/production/backend/src/langou_backend/suggestions.py`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/backend/src/langou_backend/main.py`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/backend/tests/test_mimo_provider.py`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/backend/tests/test_suggestion_stream.py`

- [ ] **Step 1: Write a fake fragmented OpenAI stream test**

```python
chunks = [
    'data: {"choices":[{"delta":{"content":"natural\\t好"}}]}\n\n',
    'data: {"choices":[{"delta":{"content":"呀～\\n"}}]}\n\n',
    'data: {"choices":[{"delta":{"content":"gentle\\t听起来不错\\n"}}]}\n\n',
    'data: {"choices":[{"delta":{"content":"boundary\\t今天不方便哦\\n"}}]}\n\n',
    'data: [DONE]\n\n',
]
assert [item.text async for item in provider.generate(request)] == [
    "好呀～", "听起来不错", "今天不方便哦",
]
```

- [ ] **Step 2: Verify the existing provider fails the async-iterator test**

Run: `.venv/bin/pytest tests/test_mimo_provider.py tests/test_suggestion_stream.py -q`

Expected: FAIL because `generate()` returns one buffered list.

- [ ] **Step 3: Implement a bounded incremental line parser**

```python
async def generate(self, request: SuggestionRequest) -> AsyncIterator[Suggestion]:
    async for text_delta in self._stream_model(request):
        for completed_line in parser.feed(text_delta):
            suggestion = validate_line(completed_line)
            if suggestion.style not in emitted_styles:
                emitted_styles.add(suggestion.style)
                yield suggestion
```

The parser accepts only the ordered styles `natural`, `gentle`, `boundary`, strips optional code fences, caps buffered text, and never emits the same style twice.

- [ ] **Step 4: Include memory in the untrusted user payload**

```python
context = {
    "application": request.application,
    "memory_summary": request.memory_summary,
    "turns": [turn.model_dump(mode="json") for turn in request.turns],
}
```

Do not put user memory in the system message and do not log it.

- [ ] **Step 5: Emit SSE as each provider item arrives**

```python
async for suggestion in suggestion_provider.generate(sanitized):
    yield sse("suggestion", suggestion_payload(suggestion, index))
```

Emit `done` after one to three valid suggestions. If no suggestion is emitted, send the existing retryable error. If the primary model fails before output, try the fallback once; after partial output, do not duplicate with another model.

- [ ] **Step 6: Run backend tests and quality gates**

Run: `.venv/bin/pytest -q && .venv/bin/ruff check . && .venv/bin/pip-audit -r requirements.lock`

Expected: all tests PASS, Ruff clean, no known vulnerabilities.

- [ ] **Step 7: Commit**

```bash
git add src/langou_backend/suggestions.py src/langou_backend/main.py tests/test_mimo_provider.py tests/test_suggestion_stream.py
git commit -m "feat: stream AI suggestions progressively"
```

### Task 3: Deliver Android SSE items immediately

**Files:**
- Modify: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/main/java/com/osfans/trime/langou/network/LangouApiClient.kt`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/main/java/com/osfans/trime/langou/network/Models.kt`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/test/java/com/osfans/trime/langou/network/LangouApiClientTest.kt`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/main/java/com/osfans/trime/ime/bar/InputBarDelegate.kt`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/main/java/com/osfans/trime/ime/core/InputView.kt`

- [ ] **Step 1: Write a callback-order test**

```kotlin
val delivered = mutableListOf<String>()
client.streamSuggestions(token, request) { delivered += it.text }
delivered shouldBe listOf("第一条", "第二条", "第三条")
```

Assert the callback has received the first item before the fake transport sends `done`.

- [ ] **Step 2: Run the failing network test**

Run: `./gradlew :app:testDebugUnitTest --tests '*LangouApiClientTest*'`

Expected: FAIL because the client buffers a list.

- [ ] **Step 3: Add progressive network delivery and optional request fields**

```kotlin
suspend fun streamSuggestions(
    bearerToken: String,
    request: SuggestionRequest,
    onSuggestion: (Suggestion) -> Unit,
) { /* redact, post SSE, validate max three, invoke immediately */ }
```

Add serialized optional `conversation_id`, `memory_summary`, and `trigger`; unknown SSE events remain ignored.

- [ ] **Step 4: Add append/update UI method**

```kotlin
fun showAiSuggestions(values: List<String>, onSelect: (String) -> Unit) {
    inputBar.showAiSuggestions(values, onSelect)
}
```

The orchestrator maintains an immutable accumulated list and calls this method after each item, so the first item replaces loading and later items append without flicker.

- [ ] **Step 5: Run Android unit tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*LangouApiClientTest*' --tests '*AiSuggestionSelectionTest*'`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/osfans/trime/langou/network app/src/main/java/com/osfans/trime/ime/bar/InputBarDelegate.kt app/src/main/java/com/osfans/trime/ime/core/InputView.kt app/src/test/java/com/osfans/trime/langou/network/LangouApiClientTest.kt
git commit -m "feat: render AI suggestions as they arrive"
```

### Task 4: Remove draft-only generation and key triggers by context

**Files:**
- Modify: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/main/java/com/osfans/trime/langou/ai/AutoSuggestionGate.kt`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/main/java/com/osfans/trime/ime/core/TrimeInputMethodService.kt`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/test/java/com/osfans/trime/langou/ai/AutoSuggestionGateTest.kt`

- [ ] **Step 1: Add trigger regression tests**

```kotlin
gate.evaluate(signals, conversationId, contextFingerprint, Trigger.ContextChange) shouldBe Generate
gate.evaluate(signals, conversationId, contextFingerprint, Trigger.DraftChanged) shouldBe SkipDraftOnly
gate.evaluate(signals, conversationId, contextFingerprint, Trigger.ContextChange) shouldBe SkipDuplicate
```

- [ ] **Step 2: Run the failing gate tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*AutoSuggestionGateTest*'`

Expected: FAIL because trigger and conversation identity are absent.

- [ ] **Step 3: Delete editor-context fallback**

```kotlin
val snapshot = ContextSnapshotStore.get(packageName) ?: run {
    inputView?.showAiPermissionRequired()
    return@launch
}
```

Do not build `turns` from `getTextBeforeCursor()` and do not schedule from `onUpdateSelection`.

- [ ] **Step 4: Generate only for fresh context fingerprints**

Use the current conversation ID and normalized snapshot-turn hash. Keep current suggestions while the user types. Manual refresh explicitly bypasses duplicate suppression once.

- [ ] **Step 5: Run targeted service and gate tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*AutoSuggestionGateTest*' --tests '*TrimeInputMethodService*'`

Expected: PASS and no test observes an API call from draft changes.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/osfans/trime/langou/ai/AutoSuggestionGate.kt app/src/main/java/com/osfans/trime/ime/core/TrimeInputMethodService.kt app/src/test/java/com/osfans/trime/langou/ai/AutoSuggestionGateTest.kt
git commit -m "fix: generate replies only from chat context"
```

### Task 5: Add anonymous conversation identity and encrypted local memory

**Files:**
- Create: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/main/java/com/osfans/trime/langou/memory/ConversationIdentityResolver.kt`
- Create: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/main/java/com/osfans/trime/langou/memory/EncryptedConversationStore.kt`
- Create: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/main/java/com/osfans/trime/langou/memory/ConversationMemory.kt`
- Create: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/main/java/com/osfans/trime/langou/memory/MemoryRetriever.kt`
- Create: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/test/java/com/osfans/trime/langou/memory/ConversationMemoryTest.kt`

- [ ] **Step 1: Write identity, retention, and isolation tests**

```kotlin
resolver.resolve("wechat", "小夏", High).id shouldNotBe resolver.resolve("wechat", "阿杰", High).id
store.read(firstId).turns.size shouldBe 100
store.prune(now.plusDays(31)).read(firstId).turns shouldBe emptyList()
```

Assert low-confidence identities are ephemeral and never match a persisted identity.

- [ ] **Step 2: Run the failing tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*ConversationMemoryTest*'`

Expected: FAIL because the memory package does not exist.

- [ ] **Step 3: Implement focused memory models and retrieval**

```kotlin
@Serializable
data class ConversationMemory(
    val conversationId: String,
    val turns: List<StoredTurn>,
    val summary: String = "",
    val updatedAtEpochMillis: Long,
)
```

Deduplicate adjacent captured turns, cap at 100, drop turns older than 30 days, and select a bounded summary plus recent/relevant turns.

- [ ] **Step 4: Implement Keystore AES-GCM production storage**

Use an injectable `MemoryCipher` for unit tests. Production creates alias `langou_conversation_memory_v1`, random 12-byte nonce per write, authenticated version header, atomic temporary-file rename in `noBackupFilesDir`, and file permissions private to the app.

- [ ] **Step 5: Run memory tests and Android lint**

Run: `./gradlew :app:testDebugUnitTest --tests '*ConversationMemoryTest*' :app:lintDebug`

Expected: PASS and lint clean.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/osfans/trime/langou/memory app/src/test/java/com/osfans/trime/langou/memory
git commit -m "feat: add encrypted per-chat memory"
```

### Task 6: Enrich accessibility snapshots and enforce permission state

**Files:**
- Modify: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/main/java/com/osfans/trime/langou/context/ContextSnapshotStore.kt`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/main/java/com/osfans/trime/langou/context/LangouAccessibilityService.kt`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/main/java/com/osfans/trime/langou/context/ContextPermissionStatus.kt`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/test/java/com/osfans/trime/langou/context/ContextSnapshotStoreTest.kt`

- [ ] **Step 1: Add snapshot identity and clear-on-sensitive tests**

```kotlin
snapshot.conversationHint shouldBe "小夏"
snapshot.identityConfidence shouldBe IdentityConfidence.High
service.onSensitiveWindow()
ContextSnapshotStore.snapshots.value shouldBe null
```

- [ ] **Step 2: Run the failing context tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*ContextSnapshotStoreTest*'`

Expected: FAIL for missing identity metadata.

- [ ] **Step 3: Extract a bounded conversation hint**

Use supported-app adapters to select a visible top-bar title, excluding generic application names, timestamps, buttons, unread counts, and message text. Return low confidence instead of guessing.

- [ ] **Step 4: Tie capture to IME visibility and AI state**

Publish capture-active state from the IME. When false, accessibility processing returns immediately and clears the current snapshot. Sensitive windows always override active state.

- [ ] **Step 5: Run context/privacy tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*Context*' --tests '*SensitiveContextPolicy*'`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/osfans/trime/langou/context app/src/test/java/com/osfans/trime/langou/context
git commit -m "feat: bind chat capture to active conversations"
```

### Task 7: Make AI default-on but permission-gated in UI

**Files:**
- Modify: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/main/java/com/osfans/trime/ui/main/LangouAccountFragment.kt`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/main/java/com/osfans/trime/ui/main/SetupActivity.kt`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/main/res/values/strings.xml`
- Create: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/test/java/com/osfans/trime/langou/ai/AiDefaultsTest.kt`

- [ ] **Step 1: Assert default-on and no-permission behavior**

```kotlin
preferences.autoSuggest shouldBe true
orchestrator.state(noPermission) shouldBe AiState.PermissionRequired
fakeApi.requestCount shouldBe 0
```

- [ ] **Step 2: Run the failing UI/state test**

Run: `./gradlew :app:testDebugUnitTest --tests '*AiDefaultsTest*'`

Expected: FAIL until permission-required state is represented end to end.

- [ ] **Step 3: Implement copy and setup behavior**

Chinese copy: `AI 回复已默认开启。开启聊天理解权限后，懒狗才能根据当前对话生成建议；跳过后仍可正常离线输入。`

AI remains enabled when permission is skipped, but its state is `PermissionRequired` and it sends no request. Turning AI off stops capture immediately.

- [ ] **Step 4: Run unit and resource tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*AiDefaultsTest*' :app:lintDebug`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/osfans/trime/ui/main app/src/main/res/values app/src/main/res/values-zh-rCN app/src/test/java/com/osfans/trime/langou/ai/AiDefaultsTest.kt
git commit -m "feat: enable AI by default with honest permission gating"
```

### Task 8: Add non-blocking summary updates and memory controls

**Files:**
- Modify: `/Users/sommerzhao/Documents/懒狗/production/backend/src/langou_backend/suggestions.py`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/backend/src/langou_backend/main.py`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/backend/tests/test_suggestion_stream.py`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/main/java/com/osfans/trime/langou/network/Models.kt`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/main/java/com/osfans/trime/ime/core/TrimeInputMethodService.kt`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/main/java/com/osfans/trime/ui/main/LangouAccountFragment.kt`
- Create: `/Users/sommerzhao/Documents/懒狗/production/android/app/src/test/java/com/osfans/trime/langou/memory/MemoryControlsTest.kt`

- [ ] **Step 1: Write failing event-order and controls tests**

```python
assert event_names[:2] == ["meta", "suggestion"]
assert event_names[-2:] == ["memory", "done"]
```

```kotlin
controls.deleteConversation(conversationId)
store.read(conversationId) shouldBe null
controls.deleteAll()
store.listIds() shouldBe emptyList()
```

- [ ] **Step 2: Run focused failing tests**

Run: `.venv/bin/pytest tests/test_suggestion_stream.py -q`

Run: `./gradlew :app:testDebugUnitTest --tests '*MemoryControlsTest*'`

Expected: FAIL because `memory` events and local controls do not exist.

- [ ] **Step 3: Generate a compact summary after suggestion output**

```python
summary = await suggestion_provider.summarize(
    previous=request.memory_summary,
    turns=request.turns,
)
yield sse("memory", {"summary": summary[:4000]})
```

This call starts only after the first suggestion has been emitted, has a shorter timeout than reply generation, and is omitted on error. The prompt preserves only relationship, names, tone preferences, facts, and open loops; it must not copy the full conversation.

- [ ] **Step 4: Persist accepted summaries locally**

Android decodes `memory` as `MemoryUpdate`, redacts it again, and writes it only to the active high-confidence conversation. Ignore events for a conversation that changed while the request was in flight.

- [ ] **Step 5: Expose refresh and deletion controls**

The AI bar refresh action calls the orchestrator with `trigger=manual_refresh` and never uses the current draft as context. Account settings expose “删除当前聊天记忆” when an identified conversation is active and retain the existing “清空全部历史” action for all local memory plus server history.

- [ ] **Step 6: Run focused and full tests**

Run: `.venv/bin/pytest tests/test_suggestion_stream.py -q`

Run: `./gradlew :app:testDebugUnitTest --tests '*MemoryControlsTest*' --tests '*LangouApiClientTest*'`

Expected: PASS; summary failure leaves suggestions successful.

- [ ] **Step 7: Commit in each repository**

```bash
git add src/langou_backend/suggestions.py src/langou_backend/main.py tests/test_suggestion_stream.py
git commit -m "feat: return compact conversation summaries"
```

```bash
git add app/src/main/java/com/osfans/trime/langou/network/Models.kt app/src/main/java/com/osfans/trime/ime/core/TrimeInputMethodService.kt app/src/main/java/com/osfans/trime/ui/main/LangouAccountFragment.kt app/src/test/java/com/osfans/trime/langou/memory/MemoryControlsTest.kt
git commit -m "feat: add reply refresh and memory controls"
```

### Task 9: End-to-end verification and deployment

**Files:**
- Verify: `/Users/sommerzhao/Documents/懒狗/production/backend`
- Verify: `/Users/sommerzhao/Documents/懒狗/production/android`

- [ ] **Step 1: Run full backend gates**

Run: `.venv/bin/pytest -q && .venv/bin/ruff check . && .venv/bin/pip-audit -r requirements.lock`

Expected: all tests PASS with no vulnerability report.

- [ ] **Step 2: Run full Android gates**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Deploy backend candidate and smoke SSE timing**

Build the immutable production image using the existing deployment workflow, switch only `langou-v1`, verify `/ready`, then send a redacted test conversation. Record time to first `suggestion`, total time, event order, model fallback behavior, and confirm logs contain no body text.

- [ ] **Step 4: Verify Android phone behavior**

Confirm no-permission state sends zero requests; after enabling permission, a new incoming message triggers once, typing does not retrigger, suggestions appear progressively, and switching contacts does not mix memory.

- [ ] **Step 5: Verify security and failure behavior**

Test password, payment, bank, and system security screens for zero capture; disable network during input and confirm full-pinyin/9-key continue to work.

- [ ] **Step 6: Commit evidence updates and keep release private**

Do not publish v1.0.0 until Android acceptance, Windows signed MSI acceptance, and the 24-hour RC gate all pass.
