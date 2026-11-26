# ZEN Public Section Schedule Repository Guide

This folder contains the schema templates, your ready-to-publish **NFC IEFR BS-CS 5th Semester (Section A & B)** core timetables, and the offline cryptographic packaging tool (`ZenSchedulePublisher`) for publishing to `schedules/` in this repository.

## Why Your Public Schedules Stay Tamper-Proof & Private

1. **Zero Student Privacy Leakage**: The Android app never uploads or queries with student names, IDs, or profile details. It performs an anonymous HTTPS `GET` for `index.json` (with `If-None-Match` ETag caching and a 6-hour cooldown) and matches your cohort locally on-device (`"NFC"` and `"NFC IEFR"` both match).
2. **ECDSA P-256 Asymmetric Signatures**: Every `index.json` and `.zensched` file is signed with your offline `private.key` (ignored via `.gitignore` inside `offline_keys/`). Only the `public.key` is stored in the app or **Settings -> Configure Repo**.
3. **Encrypted `.zensched` Containers + SHA-256 Checksums**: Section packages use AES-256-GCM encryption and double SHA-256 digests (`package_sha256` and `plaintext_sha256`), plus strict field validation before merging.
4. **Electives & Personal Classes Stay Untouched**: Electives (`Data Warehousing`, `Computer Vision and Image Processing`, `Machine Learning`, `Cyber Security`) are intentionally excluded from the synced JSON files. Students add their chosen electives manually once, and weekly sync updates (`V1.0` -> `V1.1`) only merge core `SYNCED` slots—leaving personal electives and past attendance intact.

## One-Command Multi-Section Publishing (Section A + Section B)

1. Compile the publisher tool (built-in JDK libraries only):
   ```bash
   javac schedule-repo-template/tools/ZenSchedulePublisher.java
   ```
2. Generate your ECDSA keypair once (`offline_keys/` is git-ignored—**never commit `private.key`**):
   ```bash
   java -cp schedule-repo-template/tools ZenSchedulePublisher keygen ./offline_keys
   ```
3. Copy the printed **Public Key (Base64)** into either:
   - `ScheduleCryptoVerifier.DEFAULT_PUBLIC_KEY_SPKI_BASE64` before building your APK, or
   - The app's **Settings & Storage -> Section Schedule Sync -> Configure Repo** dialog.
4. Publish **both Section A and Section B** plus signed `schedules/index.json` in one command:
   ```bash
   java -cp schedule-repo-template/tools ZenSchedulePublisher publish-all \
     schedule-repo-template/nfc_cs_sem5 \
     schedules \
     ./offline_keys/private.key \
     ./offline_keys/public.key
   ```
5. Commit and push `schedules/` to `main` on GitHub (`MrSpark714/Zen-Mobile-Application`).
