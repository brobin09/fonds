# Fonds

A phone app for turning boxes of archival papers into a finding aid. You scan by box and folder. The phone reads the text and drafts descriptions. You review the drafts, arrange folders into series and export EAD, HTML, CSV or a complete package.

Built for a Pixel. Everything runs on the phone unless you choose to send hard pages to Claude.

## Install on the Pixel

1. On the phone, open **https://github.com/brobin09/fonds/releases/latest**
2. Tap **fonds.apk** to download it, then open it.
3. If Android asks, allow your browser to install apps ("Install unknown apps").
4. To update later, do the same thing. New builds install over the old one and keep your collections.

## First run

- **Settings → On-device AI**: if Gemini Nano shows as downloadable, tap *Download model* (use Wi-Fi). If it says unavailable, update *Android System Intelligence / AICore* from the Play Store. Until then, Fonds uses pattern matching for dates and letter writers.
- **Settings → Claude (optional)**: paste an Anthropic API key to have handwritten items transcribed and described by Claude. Only items you mark handwritten (or route to Claude) are sent.
- **Settings → Processing**: add your name and default repository.

## How it works

1. **Capture**: set Box and Folder, type the folder tab label if there is one, then tap **Scan document**. Scan all pages of one document, tap Save, and that's one item. *Next folder* and *Next box* move you along. Turn on **One item per page** for clippings or photos, and **Handwritten** for manuscript material.
2. **Background**: each item is read with on-device OCR (ML Kit), then described (Gemini Nano on the phone, Claude for handwriting if set up, or pattern matching). Drafts are proposals and are never saved into your fields until you accept them.
3. **Review**: the Review tab lists drafts. Approve one, approve all, or open an item to accept, edit or dismiss each suggestion against the page image.
4. **Arrange**: the Series tab groups folders into series (Fonds can suggest an arrangement). Box and folder order never changes.
5. **Finding aid**: preview it, fill in collection-level notes (Fonds drafts abstract, scope and content, dates and extent), then **Export** or **Print / PDF**.

## Exports

| Format | Use |
|---|---|
| EAD 2002 XML | ArchivesSpace import, catalogs |
| HTML | Printable, shareable finding aid |
| Container list CSV | Box/folder list for Sheets or Excel |
| Item list CSV | Every item with all fields, Chicago citation and full text |
| JSON | Everything, including pending drafts |
| ZIP | All of the above plus every page image and transcription (your backup) |

Pick Google Drive in the save dialog to have exports on your computer.

## Privacy

Scans, text and descriptions stay in the app's private storage on the phone. Gemini Nano runs on the phone's chip. Claude is used only with your API key and only for items routed to it; those page images and text go to Anthropic's API. Check the archive's policy on reproductions.

## Building

GitHub Actions builds `fonds.apk` on every push to `main` and publishes it as the latest release. Locally: open in Android Studio, or `./gradlew assembleDebug`. The repo includes a fixed debug signing key so every build can update the previous install.

Stack: Kotlin, Jetpack Compose (Material 3), Room, ML Kit Document Scanner, ML Kit Text Recognition, ML Kit GenAI Prompt API (Gemini Nano), Anthropic Messages API.
