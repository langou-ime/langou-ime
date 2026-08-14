# PP-OCRv6 tiny model provenance

- Upstream: https://github.com/PaddlePaddle/PaddleOCR
- Upstream commit used by the paired Android SDK:
  `2661c7c0ef5c613e8f93c6e93b2e052399f0f854`
- License: Apache-2.0 (see `LICENSE`)
- Bundled for Windows: 2026-07-26

The model archives came from Paddle's official model storage:

- `PP-OCRv6_tiny_det_onnx_infer.tar`
  - archive SHA-256:
    `ff6ab415b0a6e0c488550f2fb5d5046f1719848df220b2dc21b56402a65bc05d`
- `PP-OCRv6_tiny_rec_onnx_infer.tar`
  - archive SHA-256:
    `1e13b22717b1edd89d4cde4fda272b6c17d5b505c97c2baea99da1a3a2d54b29`

Packaged file SHA-256 values:

- `det/inference.onnx`:
  `193bab7a04fca699a6c82e6abb5b81bdb28177f0abd4062552b04908dafb19f8`
- `rec/inference.onnx`:
  `9ef676d6ed3c88256a2d92c640c44f25b0c40947e111b14b8be8f594091563e6`
- `rec/inference.yml`:
  `66170210bad538e83fff3c4a3867e547d6bf20b50d64b20347c4b913f3034ea1`

OCR inference is local. Captured frames stay in memory, are disposed immediately
after recognition, and are never sent to the API or written to disk.
