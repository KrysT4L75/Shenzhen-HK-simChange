# 2.1.3 verification

Confirmed root cause: 2.1.2 APK has ZIP entries assets/vendor\ocr\tesseract.min.js (and other nested OCR files), while Android AssetManager requests vendor/ocr/tesseract.min.js. This explains the missing engine and subsequent generic image-read error.

Build now normalizes entry separators before signing and asserts every source asset has an exact APK entry. Version code 8. Existing signature, APK alignment, 56 Java checks and 32 frontend tests passed. Extracted final APK assets were served in the browser and the supplied timetable recognized all four correct rows; import, persistence and engine recovery passed. No Android device/emulator runtime test was available.
