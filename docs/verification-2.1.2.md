# 2.1.2 verification

- Build: package cn.shenlink.guard, versionCode 7; existing certificate; v2/v3 signatures and alignment passed.
- 39 reminder checks, 17 journey checks, 32 JavaScript tests passed.
- Browser: create Liantang HK route; create and edit multi-leg Shenzhen option; save/reload; copy Sheung Shui split route with first-class segment; reverse return order without changing original template.
- Browser OCR recovery: remove window.Tesseract, verify specific engine-missing message, execute bundled engine source as the native injection now does, recognize all four supplied timetable courses and persist import.
- Mobile route picker screenshot inspected. Personal screenshot remains local in ignored QA files.
- The reported Tesseract undefined error confirms an unavailable engine; the precise cause of the original external script load failure on the user's WebView has not been reproduced. Native explicit engine initialization bypasses that external script dependency. No OPPO device or Android emulator runtime test was available. Other phone OCR failures may still require diagnosis.
