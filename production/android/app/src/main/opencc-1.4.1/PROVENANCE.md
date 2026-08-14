# OpenCC Android resource bundle provenance

- Source: OpenCC 1.4.1 (`https://github.com/BYVoid/OpenCC`)
- Locked source commit: `81223ed87ae53283ef518e2deac34b7971f8a39e`
- License: Apache-2.0; see `app/src/main/jni/OpenCC/LICENSE`
- Dictionary format: OpenCC portable `ocd2`
- Generated on: 2026-07-29

The bundle was generated from the locked source tree with:

```sh
cmake -S app/src/main/jni/OpenCC -B <temporary-build-directory> \
  -DBUILD_SHARED_LIBS=OFF \
  -DBUILD_TESTING=OFF \
  -DENABLE_GTEST=OFF \
  -DENABLE_BENCHMARK=OFF \
  -DBUILD_DOCUMENTATION=OFF \
  -DOPENCC_ENABLE_INSTALL=OFF \
  -DOPENCC_DICT_FORMAT=ocd2 \
  -DCMAKE_BUILD_TYPE=Release
cmake --build <temporary-build-directory> --target Dictionaries
```

The 22 generated `.ocd2` dictionaries and the 16 runtime JSON
configurations are checked into this directory. Android release builds sync
this exact set into `assets/shared/opencc`; the source dictionaries and build
scripts remain available in the locked OpenCC submodule.
