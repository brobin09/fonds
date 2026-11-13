# Fonds for Mac

Companion to the Fonds Android app. Open the **Complete package (ZIP)** exported from the phone, then browse, search, edit, and print finding aids on the Mac.

- Import by dropping the ZIP on the window, File → Open Package, or double-clicking via Open With.
- Importing the same collection again updates it and keeps your edits made here.
- Edits are stored beside the package (edits.json); the original data.json is never modified.
- Exports: PDF, HTML, EAD 2002, container CSV, item CSV, JSON.

Develop: `cd mac && npm install && npm start`. Tests: `npm test`.
Build: GitHub Actions (`mac.yml`) produces an unsigned universal DMG. First launch: right-click → Open.
