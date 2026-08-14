# Langou Single-Repo EXE Website Rollout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reshape Langou into a single-repo released product, switch the public Windows artifact from MSI to EXE, and turn the website into a visually branded download experience tied to one release source of truth.

**Architecture:** Keep the existing Android, Windows, backend, and website codebases intact as bounded directories, but unify release naming, manifests, and website presentation around one public GitHub repository and one release model. Reuse the existing NSIS EXE installer path in Windows instead of inventing a new installer system, and update backend plus website contracts to make EXE the canonical Windows artifact.

**Tech Stack:** Python/FastAPI, pytest, Next.js/React, TypeScript, NSIS, GitHub Releases, existing Langou assets

---

### Task 1: Freeze the new public release contract

**Files:**
- Modify: `/Users/sommerzhao/Documents/懒狗/production/README.md`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/RELEASE_CHECKLIST.md`
- Test: `/Users/sommerzhao/Documents/懒狗/docs/superpowers/specs/2026-08-12-langou-single-repo-release-and-website-design.md`

- [ ] **Step 1: Update the product README to describe the single-repo release model**

Add sections covering:

```md
## Public Release Model

- One public repository hosts `android/`, `windows/`, `backend/`, and `website/`
- GitHub Releases publish the Android APK and Windows EXE together
- `api.langou.tech` release manifests and `langou.tech` downloads must point to the same assets
```

- [ ] **Step 2: Update the release checklist to replace Windows MSI language with EXE language**

Replace checklist expectations such as:

```md
- Windows public artifact: `langou-ime-windows-x64-v1.0.0.exe`
- GitHub Release, API manifest, and website all reference the same EXE hash
- Internal MSI references are not allowed in public download copy
```

- [ ] **Step 3: Manually review the spec and checklist wording**

Run: `rg -n "msi|多仓库|multiple repos" /Users/sommerzhao/Documents/懒狗/production/README.md /Users/sommerzhao/Documents/懒狗/production/RELEASE_CHECKLIST.md`

Expected: no stale public-facing MSI requirement remains in these release docs

- [ ] **Step 4: Commit**

```bash
git add /Users/sommerzhao/Documents/懒狗/production/README.md /Users/sommerzhao/Documents/懒狗/production/RELEASE_CHECKLIST.md
git commit -m "docs: align release model with single repo and windows exe"
```

### Task 2: Make backend release tooling treat EXE as the Windows source of truth

**Files:**
- Modify: `/Users/sommerzhao/Documents/懒狗/production/backend/src/langou_backend/release_tooling.py`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/backend/tests/test_release_tooling.py`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/backend/tests/test_release_manifest.py`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/backend/tests/test_schemas.py`

- [ ] **Step 1: Write or adjust the failing backend tests for EXE naming**

Update test fixtures to expect:

```python
artifact = tmp_path / "langou-ime-windows-x64-v1.0.0.exe"
artifact.write_bytes(b"signed exe")
```

and manifest URLs such as:

```python
"https://api.langou.tech/downloads/langou-ime-windows-x64-v1.0.0.exe"
```

- [ ] **Step 2: Run the targeted backend release tests and verify they fail before the implementation**

Run: `cd /Users/sommerzhao/Documents/懒狗/production/backend && ./.venv/bin/pytest tests/test_release_tooling.py tests/test_release_manifest.py tests/test_schemas.py -q`

Expected: failure mentioning `.msi` expectations or Windows release filename mismatch

- [ ] **Step 3: Implement the minimal release-tooling change**

Update logic so Windows release artifacts use:

```python
required_suffix = ".apk" if platform == "android" else ".exe"
expected_name = (
    f"langou-ime-android-v{version}.apk"
    if platform == "android"
    else f"langou-ime-windows-x64-v{version}.exe"
)
```

- [ ] **Step 4: Re-run the targeted backend release tests**

Run: `cd /Users/sommerzhao/Documents/懒狗/production/backend && ./.venv/bin/pytest tests/test_release_tooling.py tests/test_release_manifest.py tests/test_schemas.py -q`

Expected: PASS

- [ ] **Step 5: Run a broader backend regression slice**

Run: `cd /Users/sommerzhao/Documents/懒狗/production/backend && ./.venv/bin/pytest tests/test_openapi_contract.py tests/test_production_factory.py tests/test_config.py -q`

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add /Users/sommerzhao/Documents/懒狗/production/backend/src/langou_backend/release_tooling.py /Users/sommerzhao/Documents/懒狗/production/backend/tests/test_release_tooling.py /Users/sommerzhao/Documents/懒狗/production/backend/tests/test_release_manifest.py /Users/sommerzhao/Documents/懒狗/production/backend/tests/test_schemas.py
git commit -m "feat: publish windows releases as exe artifacts"
```

### Task 3: Switch website download logic and copy from MSI to EXE

**Files:**
- Modify: `/Users/sommerzhao/Documents/懒狗/production/website/app/release-manifest.ts`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/website/test/release-manifest.test.ts`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/website/messages/zh.json`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/website/messages/en.json`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/website/live-snapshot/en/download.html`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/website/live-snapshot/zh/download.html`

- [ ] **Step 1: Update website tests to require EXE wording**

Change assertions to look for:

```ts
expect(copy).toContain(".exe")
expect(copy).not.toContain("MSI")
```

- [ ] **Step 2: Run website release-manifest and copy tests before implementation**

Run: `cd /Users/sommerzhao/Documents/懒狗/production/website && npm test -- --runInBand`

Expected: failures in release or download copy tests if MSI wording is still present

- [ ] **Step 3: Update release-manifest helpers and copy**

Ensure all public Windows release references use:

```ts
langou-ime-windows-x64-v1.0.0.exe
```

and download instructions such as:

```text
① Download EXE installer
② Double-click to start installation
```

- [ ] **Step 4: Re-run website tests**

Run: `cd /Users/sommerzhao/Documents/懒狗/production/website && npm test -- --runInBand`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add /Users/sommerzhao/Documents/懒狗/production/website/app/release-manifest.ts /Users/sommerzhao/Documents/懒狗/production/website/test/release-manifest.test.ts /Users/sommerzhao/Documents/懒狗/production/website/messages/zh.json /Users/sommerzhao/Documents/懒狗/production/website/messages/en.json /Users/sommerzhao/Documents/懒狗/production/website/live-snapshot/en/download.html /Users/sommerzhao/Documents/懒狗/production/website/live-snapshot/zh/download.html
git commit -m "feat: switch website download flow to windows exe"
```

### Task 4: Rebuild the website into a brand-forward product site

**Files:**
- Modify: `/Users/sommerzhao/Documents/懒狗/production/website/app/page.tsx`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/website/app/globals.css`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/website/messages/zh.json`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/website/messages/en.json`
- Create: `/Users/sommerzhao/Documents/懒狗/production/website/public/...`
- Create: `/Users/sommerzhao/Documents/懒狗/production/assets/website/...`
- Test: `/Users/sommerzhao/Documents/懒狗/production/website/test/ci-workflow.test.ts`

- [ ] **Step 1: Define the homepage sections in translation data**

Add content for sections including:

```json
{
  "hero": {},
  "featureHighlights": {},
  "keyboardModes": {},
  "aiScenes": {},
  "safety": {},
  "downloads": {},
  "faq": {}
}
```

- [ ] **Step 2: Replace the current page implementation with componentized branded sections**

Build sections for:

```tsx
<HeroSection />
<ProductPreviewSection />
<KeyboardModesSection />
<AiScenesSection />
<SafetySection />
<DownloadSection />
<FaqSection />
```

- [ ] **Step 3: Add or wire branded visual assets**

Use existing mascot exports first:

```text
/Users/sommerzhao/Documents/懒狗/production/assets/exports/langou-app-icon-512.png
/Users/sommerzhao/Documents/懒狗/production/assets/mascot/langou-mascot-master.png
```

and reserve `production/assets/website/` for newly generated web graphics.

- [ ] **Step 4: Run website tests and a production build**

Run: `cd /Users/sommerzhao/Documents/懒狗/production/website && npm test -- --runInBand`

Expected: PASS

Run: `cd /Users/sommerzhao/Documents/懒狗/production/website && npm run build`

Expected: successful Next.js production build

- [ ] **Step 5: Commit**

```bash
git add /Users/sommerzhao/Documents/懒狗/production/website/app/page.tsx /Users/sommerzhao/Documents/懒狗/production/website/app/globals.css /Users/sommerzhao/Documents/懒狗/production/website/messages/zh.json /Users/sommerzhao/Documents/懒狗/production/website/messages/en.json /Users/sommerzhao/Documents/懒狗/production/website/public /Users/sommerzhao/Documents/懒狗/production/assets/website
git commit -m "feat: redesign website with langou brand visuals"
```

### Task 5: Prepare the Windows EXE packaging path for public release

**Files:**
- Modify: `/Users/sommerzhao/Documents/懒狗/production/windows/xbuild.bat`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/windows/output/install.nsi`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/windows/update/appcast.xml`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/windows/update/testing-appcast.xml`
- Modify: `/Users/sommerzhao/Documents/懒狗/production/windows/.github/workflows/...`

- [ ] **Step 1: Inspect current output naming and add a failing assertion or script check**

Create or update a script/test so the expected output name is:

```text
langou-ime-windows-x64-v1.0.0.exe
```

- [ ] **Step 2: Adjust NSIS output naming and release references**

Update installer output logic from the upstream-style generic name to the Langou public name.

- [ ] **Step 3: Update appcast or release metadata to reference EXE**

Any update metadata that currently points to MSI or old names must align with the new EXE filename.

- [ ] **Step 4: Validate by grepping for stale public MSI references**

Run: `rg -n "langou-ime-windows-x64-v.*\\.msi|Download MSI|MSI installer" /Users/sommerzhao/Documents/懒狗/production/windows`

Expected: no remaining public-release MSI references

- [ ] **Step 5: Commit**

```bash
git add /Users/sommerzhao/Documents/懒狗/production/windows/xbuild.bat /Users/sommerzhao/Documents/懒狗/production/windows/output/install.nsi /Users/sommerzhao/Documents/懒狗/production/windows/update/appcast.xml /Users/sommerzhao/Documents/懒狗/production/windows/update/testing-appcast.xml /Users/sommerzhao/Documents/懒狗/production/windows/.github
git commit -m "build: align windows installer outputs with public exe release"
```

### Task 6: Unify the repository story and release handoff

**Files:**
- Modify: `/Users/sommerzhao/Documents/懒狗/README.md`
- Create: `/Users/sommerzhao/Documents/懒狗/docs/release/github-release-operations.md`
- Create: `/Users/sommerzhao/Documents/懒狗/docs/release/repository-layout.md`

- [ ] **Step 1: Document the public single-repo layout**

Write:

```md
android/
windows/
backend/
website/
assets/
releases/
```

- [ ] **Step 2: Document the GitHub Release process**

Cover:

```md
1. Build Android APK
2. Build Windows EXE
3. Generate hashes
4. Upload both assets to one Release
5. Update API manifests
6. Publish website links
```

- [ ] **Step 3: Review the workspace for contradictory repo language**

Run: `rg -n "android repo|windows repo|backend repo|website repo|multiple repos|多仓库" /Users/sommerzhao/Documents/懒狗`

Expected: remaining mentions are historical notes only, not active release guidance

- [ ] **Step 4: Commit**

```bash
git add /Users/sommerzhao/Documents/懒狗/README.md /Users/sommerzhao/Documents/懒狗/docs/release/github-release-operations.md /Users/sommerzhao/Documents/懒狗/docs/release/repository-layout.md
git commit -m "docs: define single repo release operations"
```

### Task 7: Final verification before public rollout

**Files:**
- Verify: `/Users/sommerzhao/Documents/懒狗/production/backend/...`
- Verify: `/Users/sommerzhao/Documents/懒狗/production/website/...`
- Verify: `/Users/sommerzhao/Documents/懒狗/production/windows/...`

- [ ] **Step 1: Run backend release tests**

Run: `cd /Users/sommerzhao/Documents/懒狗/production/backend && ./.venv/bin/pytest tests/test_release_tooling.py tests/test_release_manifest.py tests/test_schemas.py tests/test_openapi_contract.py -q`

Expected: PASS

- [ ] **Step 2: Run website tests and build**

Run: `cd /Users/sommerzhao/Documents/懒狗/production/website && npm test -- --runInBand`

Expected: PASS

Run: `cd /Users/sommerzhao/Documents/懒狗/production/website && npm run build`

Expected: PASS

- [ ] **Step 3: Verify Windows public naming**

Run: `rg -n "langou-ime-windows-x64-v1\\.0\\.0\\.exe|\\.msi" /Users/sommerzhao/Documents/懒狗/production/windows /Users/sommerzhao/Documents/懒狗/production/backend /Users/sommerzhao/Documents/懒狗/production/website`

Expected: EXE references are canonical; any MSI references are internal-only or removed

- [ ] **Step 4: Summarize remaining non-code gates**

List clearly:

```md
- Real GitHub single-repo remote creation / push
- Real Android release build
- Real Windows signed EXE build
- Real website deployment
```

- [ ] **Step 5: Commit final aligned code/docs state**

```bash
git add /Users/sommerzhao/Documents/懒狗
git commit -m "chore: align langou release system with single repo and exe distribution"
```
