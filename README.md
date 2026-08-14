# 懒狗输入法

懒狗输入法 v1.0.0 单仓库生产工作区。

The previous agent output under `/Users/sommerzhao/Documents/懒狗输入法` is a
read-only legacy reference. New production code lives under `production/`.

## Single-repo layout

- `production/backend` — FastAPI service, release manifests, database migrations
- `production/android` — Android input method based on Trime
- `production/windows` — Windows input method based on Weasel, public installer target is EXE
- `production/website` — branded Next.js marketing and download site
- `production/infra` — single-server deployment templates containing no secrets
- `production/assets` — mascot exports, generated website visuals, brand assets
- `docs/release` — single-repo release operations and publishing references

## Public release model

- One public GitHub repository hosts Android, Windows, backend, website, assets, and docs.
- GitHub Releases publish Android APK and Windows EXE together under one version tag.
- `api.langou.tech` release manifests, `langou.tech` download links, and GitHub Release assets must point to the same files and hashes.

The release gate and remaining external prerequisites are tracked in
[`production/RELEASE_CHECKLIST.md`](production/RELEASE_CHECKLIST.md). The
single-repo publishing flow is documented in [`docs/release`](docs/release).

No credential, private signing key, chat content, APK, or EXE may be committed
to this repository.
