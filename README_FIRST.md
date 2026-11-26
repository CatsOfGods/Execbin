# ExecBin — Buyer Package: Read Me First

This folder is the handover package for the **ExecBin** Android messenger source code.

## What is in this package

| File | Purpose |
|---|---|
| **`FINAL_EXECBIN_SOURCE.zip`** | **The source code** — complete Android Studio project, Firebase rules, Cloud Function, tests and developer docs (81 files) |
| `README_FIRST.md` | This file |
| `EXECBIN_PRODUCT_OVERVIEW.md` | What the app does and how it is built — implemented vs. partial features |
| `BUYER_HANDOVER.md` | Technical transfer: what you get, what is *not* transferred, setup order, acceptance checklist |
| `BUILD_AND_SETUP.md` | Step-by-step build, Firebase configuration, functions, rules testing, troubleshooting |
| `SECURITY.md` | Security assessment: controls, fixed issues, open findings, recommendations |
| `THIRD_PARTY_NOTICES.md` | Dependency and license inventory (158 Android + 337 npm entries), required notices |
| `TEST_AND_VERIFICATION_REPORT.md` | What was verified, how, with which results — and what was not |
| `KNOWN_LIMITATIONS.md` | Outstanding work, unverified behaviour, open questions |
| `IP_AND_TRANSFER_CHECKLIST.md` | Ownership, licensing, AI-disclosure, agreement, data and account checklist |
| `FILE_MANIFEST.txt` | Every file in this package and in the ZIP, with size and purpose |
| `SHA256SUMS.txt` | SHA-256 checksums of the ZIP and the package files |
| `Evidence/` | Sanitized logs and reports backing the verification report |

No demo materials (screenshots/video) are included: the package contains no Firebase project,
so the app cannot run past the login screen without the buyer's own backend.

## Where to begin

1. Check the ZIP's SHA-256 against `SHA256SUMS.txt` — ideally against a checksum the seller sent
   you through a separate channel (a checksum in the same folder only detects accidental damage,
   not tampering).
2. Read `EXECBIN_PRODUCT_OVERVIEW.md`, then `KNOWN_LIMITATIONS.md`.
3. Unzip `FINAL_EXECBIN_SOURCE.zip` and follow `BUILD_AND_SETUP.md` (inside the archive,
   `ExecBin/README.md` and `ExecBin/HANDOVER.md` cover the same ground for developers).
4. Use the acceptance checklist in `BUYER_HANDOVER.md` §7.

## How to read the verification report

`TEST_AND_VERIFICATION_REPORT.md` separates results that were **independently re-run on the
delivered ZIP** (with logs in `Evidence/`) from earlier reported results and from checks that
were **never performed**. Summary of the re-run on 2026-10-25: unit tests 27/27, Cloud Functions
tests 7/7, Firestore rules tests 29/29, debug and release builds succeeded, lint 0 errors /
508 warnings, no secrets found by the scans.

## What remains unverified

- The app has **not** been tested on real devices after the latest changes.
- Nothing has been deployed to Firebase; the app has not been published.
- No independent security audit has taken place.
- Ownership/IP questions (icon provenance, name, AI-assisted authorship) are for the parties and
  their lawyers to settle — see `IP_AND_TRANSFER_CHECKLIST.md`.

This package is a complete, documented source-code handover. It is not proof of production
readiness, of legal ownership, or of the absence of security vulnerabilities.

Seller contact details are not included in this package; use the contact channel agreed for the sale.
