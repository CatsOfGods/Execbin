# IP and Transfer Checklist

A practical checklist for seller and buyer. **Nothing in this package establishes that ownership
or transfer rights exist** — those come from the parties' facts and a written agreement. Items
marked ⚖️ should be reviewed by a qualified lawyer in the relevant jurisdiction.

## 1. Seller's authority to transfer

- [ ] Seller confirms they are the author/owner of the ExecBin source code, or hold rights to sell it ⚖️
- [ ] Any co-authors, contractors or employers with possible claims identified and cleared ⚖️
- [ ] No prior sale, license or exclusivity granted to someone else
- [ ] Earlier copies of the project on the seller's machines (older project folders and an
      earlier, outdated archive were found) are not separately sold or licensed

## 2. Name, icon, graphics and other assets

- [ ] Origin of the launcher icon confirmed (own design / generated / licensed) and documented.
      A 1280×1280 image named `ExecBin.jpg` exists on the seller's machine (not in the package) —
      seller to confirm whether it is the icon source and who created it
- [ ] Trademark search for "ExecBin" in the target markets ⚖️ (not performed)
- [ ] Whether the name and icon are transferred, and in which form
- [ ] No third-party logos or brand assets used (none found in the archive besides system emoji/drawables)

## 3. Third-party license compliance

- [ ] Buyer accepts that dependencies remain under their own licenses (`THIRD_PARTY_NOTICES.md`)
- [ ] Open-source notices screen added before distribution (SQLCipher notice is mandatory for binaries)
- [ ] Google proprietary SDK terms (Firebase Auth, Play services, Play Integrity) accepted by the publisher
- [ ] Flagged npm items reviewed (`limiter` license field, `node-forge` dual license)
- [ ] Decide whether SQLCipher Community Edition terms are acceptable for the buyer's use ⚖️

## 4. AI-assisted development disclosure

- [ ] Seller discloses that parts of the code and documentation were written or modified with AI
      coding assistants (including the October 2026 security changes and these handover documents)
- [ ] Parties consider how this affects copyright protection and warranties in their jurisdiction ⚖️
- [ ] Check the terms of the AI tools used regarding ownership of their output ⚖️
- [ ] Agreement wording reflects the disclosure (no warranty of sole human authorship unless true)

## 5. Written sale agreement

- [ ] Precise scope: source code only, or also name, icon, domain, accounts, store listing, data ⚖️
- [ ] Type of transfer: assignment of copyright vs. license; exclusivity; seller's retained rights ⚖️
- [ ] Identification of the delivered archive by file name **and SHA-256** (`SHA256SUMS.txt`)
- [ ] Warranties and disclaimers (e.g. "as is", no production-readiness or security guarantee) ⚖️
- [ ] Governing law and dispute resolution ⚖️
- [ ] Non-compete / non-solicitation, if any ⚖️

## 6. Payment, delivery, acceptance, support

- [ ] Price, payment method and timing (e.g. escrow until acceptance)
- [ ] Delivery method for the ZIP (and the checksum through a separate channel)
- [ ] Acceptance criteria — suggested: `BUYER_HANDOVER.md` §7 checklist
- [ ] Acceptance period and what happens if a check fails
- [ ] Post-sale support scope and duration (questions, bug fixes, knowledge transfer), if any
- [ ] Seller deletes or retains copies after transfer (agree explicitly)

## 7. Firebase / backend, data and billing

- [ ] Whether the seller's existing Firebase project is transferred or the buyer starts fresh
- [ ] If transferred: Google Cloud project ownership change, billing account change, IAM cleanup
- [ ] Existing user accounts and data: transfer, deletion, or migration — with a lawful basis ⚖️
- [ ] Responsibility for usage costs from the handover date
- [ ] Anthropic API: buyer uses their own account (no key is transferred)

## 8. Signing keys, Google Play, releases

- [ ] Whether an app is already published under the seller's Play account (and whether it transfers via Google's app-transfer process)
- [ ] Signing/upload keys are **never** sent inside the source package; if an existing listing is
      transferred, follow Google Play App Signing procedures
- [ ] Buyer chooses a new application id (current: `com.example.execbin`)
- [ ] Responsibility for future releases, store compliance and user support lies with the buyer from the handover date

## 9. Personal data and privacy obligations

- [ ] Buyer publishes its own privacy policy (in-app text exists; contact is a placeholder) ⚖️
- [ ] Data-safety form: email (hashed), device id (`ANDROID_ID`), messages, photos/audio, crash data (Crashlytics incl. user id), push token
- [ ] Legal basis, retention and deletion processes under applicable law (e.g. GDPR/UK GDPR, CCPA) ⚖️
- [ ] Obligations for a public chat with user-generated content (moderation, reporting, minors) ⚖️
- [ ] Export-control / encryption regulations for apps with end-to-end encryption in target countries ⚖️ (Google Play also asks about encryption)
- [ ] No personal data of real users is contained in this package (confirmed by scan for the archive; the scan cannot prove absence)
