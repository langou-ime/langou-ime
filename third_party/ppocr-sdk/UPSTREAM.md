# PaddleOCR Android SDK provenance

- Upstream: `https://github.com/PaddlePaddle/PaddleOCR`
- Commit: `2661c7c0ef5c613e8f93c6e93b2e052399f0f854`
- Source path: `deploy/ppocr-android/ppocr-sdk`
- License: Apache-2.0 (see `LICENSE`)
- Vendored on: 2026-07-26

Local changes are limited to the Gradle build file so the SDK uses the parent
project's Android/Kotlin toolchain and dependency catalog. Production source
files retain the upstream copyright and license headers.

## Models

The bundled PP-OCRv6 tiny ONNX models were downloaded from Paddle's official
model storage:

- Detection: `PP-OCRv6_tiny_det_onnx_infer.tar`
  - SHA-256: `ff6ab415b0a6e0c488550f2fb5d5046f1719848df220b2dc21b56402a65bc05d`
- Recognition: `PP-OCRv6_tiny_rec_onnx_infer.tar`
  - SHA-256: `1e13b22717b1edd89d4cde4fda272b6c17d5b505c97c2baea99da1a3a2d54b29`

Only `inference.onnx` and the recognition `inference.yml` are packaged. They
are used for local inference and are never uploaded.
