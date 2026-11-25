# ZEN Public Section Schedule Repository Guide

This folder contains the schema templates and offline cryptographic packaging tool for publishing tamper-resistant university section timetables to your public GitHub repository.

## Why Your Public Schedules Stay Tamper-Proof & Private

1. **Zero Student Privacy Leakage**: The Android app never uploads or queries with student names, IDs, or profile details. It performs an anonymous HTTPS `GET` for `index.json` (with `If-None-Match` ETag caching and a 6-hour cooldown) and matches your cohort locally on-device.
2. **ECDSA P-256 Asymmetric Signatures**: Every `index.json` and `.zensched` file is signed with your offline `private.key`. Only the `public.key` is stored in the app or Settings -> **Configure Repo**. Anyone trying to tamper with a public JSON file on GitHub cannot forge a valid signature.
3. **Encrypted `.zensched` Containers + SHA-256 Checksums**: Section packages use AES-256-GCM encryption and double SHA-256 digests (`package_sha256` and `plaintext_sha256`), plus strict field validation before merging.
4. **Non-Destructive Smart Diff Merge**: Updating from `V1.0` to `V1.1` only updates synced slots—all past attendance history and personal student-added classes remain untouched.

## Quick Setup for Maintainers

1. Compile the publisher tool (uses built-in JDK libraries only):
   ```bash
   javac tools/ZenSchedulePublisher.java
   ```
2. Generate your ECDSA keypair once (keep `private.key` strictly offline, **never commit it**):
   ```bash
   java -cp tools ZenSchedulePublisher keygen ./offline_keys
   ```
3. Copy the printed **Public Key (Base64)** into either:
   - The app's **Settings & Storage -> Section Schedule Sync -> Configure Repo** dialog, or
   - `ScheduleCryptoVerifier.DEFAULT_PUBLIC_KEY_SPKI_BASE64` before building your cohort APK.
4. Package & sign a section timetable (add an optional passphrase as the 6th argument for cohort-only decryption):
   ```bash
   java -cp tools ZenSchedulePublisher package sections/cs_sem5_a_raw.json schedules/sections/cs_sem5_a.zensched offline_keys/private.key offline_keys/public.key
   ```
5. Put the printed `package_sha256` into `schedules/index.json`, sign the canonical index string with `sign-canonical`, and push `schedules/index.json` and `schedules/sections/*.zensched` to your GitHub repo (`main` branch).
