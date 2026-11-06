# Third-party components

The offline timetable importer bundles these upstream components without modifying their distributed runtime code:

- Tesseract.js 6.0.1 — Apache-2.0, https://github.com/naptha/tesseract.js. License: `app/src/main/assets/vendor/ocr/LICENSE.md`.
- Tesseract.js-core 6.0.0 — Apache-2.0, https://github.com/naptha/tesseract.js-core. The LSTM-only SIMD and non-SIMD builds are included; OCR always uses OEM 1. License: `app/src/main/assets/vendor/ocr/core/LICENSE`.
- Tesseract fast language models (eng, chi_sim, chi_tra) — Apache-2.0, https://github.com/tesseract-ocr/tessdata_fast, commit `87416418657359cb625c412a48b6e1d6d41c29bd`. Model URLs and SHA-256 values are recorded in `app/src/main/assets/vendor/ocr/models.json`. The Apache license is also included in the core directory.

Distributed JavaScript bundles retain upstream license notices. Other bundled dependency notices, where emitted by the upstream distribution, are kept alongside the runtime files.

The CUHK term date presets are factual dates from the Graduate School engineering programme calendar (including MSc Artificial Intelligence), updated by CUHK on 26 May 2026: https://www.gs.cuhk.edu.hk/download/2627_TDPP_ERG.pdf. Presets were checked on 7 November 2026 and do not automatically refresh.
