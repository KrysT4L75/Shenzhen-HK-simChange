# Android 2.1.1 verification

Date: 2026-11-09

- Build: `build.ps1`; package `cn.shenlink.guard`, version code 6.
- Passed 39 reminder checks, 17 journey inference checks, and 30 JavaScript domain/parser tests.
- APK v2/v3 signature and zip alignment verified with the existing local signing certificate.
- Tested the actual supplied timetable image through the bundled browser OCR engine: four course rows, correct Tuesday/Wednesday/Thursday assignment, explicit times and multiline venues; no date-header rows.
- Browser workflow verified review, import, persistence after reload, independent return start without outbound records, and active-trip persistence.
- Homepage now shows start card then timetable. Verified rendered positions and no page horizontal overflow at widths 360, 412, and 1280; inspected mobile screenshot. No browser JavaScript errors.
- OCR image and QA screenshots are local only and excluded from source distribution.
- Direction remains user-confirmed. Location records supported journey nodes; it does not infer outbound versus return. Return can start independently, from the time it is enabled.
- Android device runtime, OPPO background behavior, and real border crossings were not tested in this revision. iOS unchanged.
