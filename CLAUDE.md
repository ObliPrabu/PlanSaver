# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

PlanSaver compares US cell-phone plans (and phone deals) for a user's lines/data/budget. Android (Kotlin + Compose) in
`app/`, iOS (SwiftUI, xcodegen) in `ios/`, plus a GitHub-Action "robot" that refreshes `deals-data/deals.json`.
Package / bundle ID: `com.obliprabu.plansaver` (both platforms; not yet published anywhere - still changeable).
Remote: `ObliPrabu/PlanSaver` on GitHub, currently PUBLIC (see "Deals pipeline" for why that matters). `NOTES.md` has the
research sources and affiliate payout numbers.

## Commands

Android (AGP 9 built-in Kotlin, Compose; copied from the sibling `~/AndroidStudioProjects/Menalith` project):
- Build debug APK: `./gradlew assembleDebug` -> `app/build/outputs/apk/debug/app-debug.apk`
- Install on the user's vivo: `~/Library/Android/sdk/platform-tools/adb -s T8ZXNJ896T99FMZX install -r app/build/outputs/apk/debug/app-debug.apk`
- Unit tests: `./gradlew testDebugUnitTest` (phone matching). Robot tests: `PYTHONPATH=tools python3 -m unittest discover -s tools -p "test_*.py"` (the workflow runs them before every scrape). No lint setup yet.

iOS (`ios/project.yml` is the source of truth; `ios/PlanSaver.xcodeproj` is generated and gitignored):
- Regenerate after editing `project.yml` or adding files: `cd ios && xcodegen generate`
- Compile check (no signing): `xcodebuild -project PlanSaver.xcodeproj -scheme PlanSaver -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build`
- Signed build for the phone: same with `-allowProvisioningUpdates -derivedDataPath <scratch>` (team `PS8DGXJ8S8`), then
  `xcrun devicectl device install app --device C13C4BB8-65E2-5CA7-BB9F-425747B421EE <PlanSaver.app>`.

Deals robot: `python3 tools/refresh_deals.py [path/to/deals.json]` (stdlib only; takes ~1 min because it sleeps between
requests - test against a COPY of the file, it rewrites in place). Manual workflow run: `gh workflow run deals-robot.yml`.

## Working rules (from the owner)

- NEVER use simulators/emulators. iOS is only compiled for the generic device; Android is installed on the real vivo.
- Do not install on the iPhone unless asked (the owner often uses it); do not commit/push unless asked.
- Keep Android and iOS in sync: every logic/data change goes into both. The 39 plans in `PlanData.kt` were converted to
  `ios/PlanSaver/PlanData.swift` by a script - edit both by hand or re-convert.
- Version is kept equal on both: Android `versionCode`/`versionName` in `app/build.gradle.kts`, iOS
  `CURRENT_PROJECT_VERSION`/`MARKETING_VERSION` in `ios/project.yml` (currently 0.2.3 / 5). Bump only when asked.
- No paid services and no API keys. A search/AI key must never be inside the app. The owner is a minor: anything needing
  an adult account (affiliate networks CJ/Impact, a Gemini key, Play payouts) is deferred until a parent does it.
- Do not invent facts: plan prices/deals come from third-party roundups, unverified against carrier sites - say so.

## Architecture

Search flow (identical on both platforms): `MainActivity.kt`/`ContentView.swift` build a `SearchRequest` -> 
`RemotePlanRepository.search` (Android) / `PlanRepository.search` (iOS) -> results screen.
1. Filter + sort the bundled plans (`PlanData`): postpaid toggle, data need, and a plan is dropped when `lines` is below
   the smallest line count it has a price for (AT&T Extra is priced only for 4 lines). `Plan.perLine(n)` falls back to
   the nearest known lower line count, so multi-line prices are estimates for most plans.
2. On EVERY Search tap, download `DEALS_URL` (raw.githubusercontent.com/ObliPrabu/PlanSaver/main/deals-data/deals.json;
   `buildConfigField` in `app/build.gradle.kts`, `dealsURL` in `ios/.../PlanRepository.swift`). Network failure/private
   repo -> silently use the bundled deal text. Deals attach to ALL plans of a matching carrier (`sameCarrier`).
3. Phone rules (`Plan.kt` / `Plan.swift`, ported 1:1): a deal is hidden unless it (a) names no phone, or (b) names the
   picked phone (`dealMatchesPhone`, prefix match either way after normalizing, so picking "iPhone 16" matches an
   "iPhone 16 Pro" mention). With the "Check for better phones as well" checkbox (`includeOtherPhones`), deals for a newer
   phone of the same brand are kept (`dealIsBetterPhone`: same brand, generation >= picked; iPhone Duo counts as gen 99)
   and listed after the picked phone's results, labeled "better phone deal". Older phones are never shown.
   Phone names are detected by regex over the deal text (`phoneMention`), so deal wording drives the filtering.
4. `freePhoneOnly` keeps only carriers whose deal says free/discounted phone; the picker (`Phones.kt`) shows one model at a
   time after a pick (Android: chip + "Change"; iOS: dropdown).

Deals pipeline: `.github/workflows/deals-robot.yml` (cron `*/5 * * * *` + manual; the cron only asks for every 5 minutes - GitHub runs it best-effort, anywhere from 5 minutes to 1 day apart; seen so far: about every 2 hours) runs `tools/refresh_deals.py`, which for
each carrier in `deals.json` reads that carrier's own page and DuckDuckGo's HTML results (keyless scraping; only text from
the carrier's own domain is accepted, filtered by price/free keywords), keeps the old entry on any failure, and the
workflow commits as `plansaver-robot` only when the file changed. It never touches `phones`; it re-derives `freePhone` only when a deal's text changes. The raw URL
only works while the repo is PUBLIC; if made private, the app falls back to bundled deals and the free Actions minutes
(2,000/mo) would run out quickly if it really ran every 5 minutes.

## Status and open items (as of 2026-10-17)

- Installed on the vivo; iOS installed earlier on "Mithran Prabu's iPhone" (before the latest changes).
- UNCOMMITTED: iPhone Duo in the picker + matching, the "better phones" checkbox, one-model picker, and the bug fixes
  below (fewer-lines filter, https-only deal links, Android `configChanges` so rotation keeps the search). Last compile
  of both platforms succeeded after these edits.
- Robot verified 2026-10-17: scheduled runs do fire (first one about 1h47m after the new workflow was pushed), then roughly
  every 2 hours - not every 5 minutes. Deals therefore refresh a few times a day, between 5 minutes and 1 day apart.
- Fixed 2026-10-17 (uncommitted, Android installed on vivo, iOS compiled only): per-plan deal scoping via optional `plans`
  name fragments in deals.json (T-Mobile "Experience", Mint "6-month"; robot never edits `plans`); phone matching is now
  exact (base phone no longer matches Pro deals; "S25 FE"/"Edge" are separate phones); lines picker is 1-6 (5+ uses the
  4-line price, results say so); each deal shows "(as of date)" from its per-entry `updated`; the robot re-derives
  `freePhone` when a carrier's text changes; Android form state survives process death (results screen re-searches).
- Round 2 fixes (2026-10-17): Z Flip/Fold deals match any generation; a bare family mention ("iPhone 16") also matches the
  Pro you picked; iPhone 16e/Air and Pixel Pro Fold are recognized as other phones; per-entry "as of" dates only (no
  root-date fallback; an entry gets `updated` only when the robot changes its text); robot rejects product-listing
  scraps, only replaces a deal when the new text scores 2+ higher (no flip-flop), widens `freePhone` detection, and drops
  a `plans` scope when the new text no longer names one of its plans; cards say when a multi-line price is only an
  estimate; iOS text is at least extra-extra-large but never below the user's own setting, lines is a Stepper and data a
  menu so big text fits.
- Robot also looks for multi-line prices ("$25 per line for 4 lines"): the best sentence is stored as `lines`/`linesUpdated`
  on the carrier's deals.json entry and shown on cards when 2+ lines are searched, labeled unverified. It never changes the
  computed prices. Not run live yet (DuckDuckGo answered 202 to repeated local requests, so nothing was found in testing).
- Robot also looks for hotspot / international / perks / slowdown sentences per carrier (`extras` in deals.json, each with its
  own `updated`), shown on every card as "Found online ... (unverified; may differ by plan)". Carrier-level, not per plan.
- Known issues not fixed: Mint's site returns 403 to scripts (bot protection - not bypassed), so its deal only updates from
  search snippets; multi-line prices are still mostly 1-line x lines (needs research per plan); prices are unverified;
  extra plan data (hotspot, roaming, perks) is not in the app because it would have to be researched; layout at the larger text sizes and Android
  process-death restore have not been checked on a device; the repo is public so NOTES.md is exposed (owner's decision).
- Data caveats: Mint plan sizes disagree between sources (5GB vs 6GB; 6GB used); the Mint anniversary promo end date is
  unclear; T-Mobile deals URL was corrected to `t-mobile.com/offers`; Cricket has no deal; carrier "View plan" links are
  plain home pages, not affiliate links.
- Next ideas: ask parents for a Gemini key and let the robot (GitHub secret, never the app) use Gemini web search;
  CJ/Impact signup -> swap in affiliate links + commission disclosure; Play listing (Data Safety form, closed testing).

## Earning money: CJ Affiliate and Impact (what the owner needs)

How it works: carriers (advertisers) pay an affiliate network a commission per new customer; the network gives approved
publishers (PlanSaver) tracking links and pays them. Users pay nothing; the app needs no Stripe or payment code, and a
free Play app has no Google fee (Google's 15% only applies to paid downloads / in-app purchases through Play).
- Networks: CJ Affiliate = https://www.cj.com (type the `www.`; bare `cj.com` failed to connect from this machine) -
  US Mobile and Cricket use it. Impact = https://impact.com - Visible uses it. Mint has its own program (about $20 per
  activation, US Mobile about $30, Cricket $0-10 - from affiliate directories, unverified; see NOTES.md).
- Cost to the publisher: nothing to join, apply or use links. Commissions can be reversed if a customer cancels; payout
  needs a minimum balance and may have a bank/PayPal fee (CJ's current payout fees not checked - read the payment
  settings page). Commissions are taxable income for the account holder.
- Needs an adult: both networks require an adult account holder (identity, tax form, bank/PayPal). The owner is a
  minor, so a parent/guardian must sign up, read the Publisher Service Agreement (accepted at signup; also in the
  account and on https://www.cj.com/support) and receive the money. Never pay anyone to "unlock" approval.
- Each carrier approves publishers separately and usually wants a real, live app/site; apply after PlanSaver is public
  (or at least has a working listing). CJ can remove a publisher for misleading content, so deal text must be accurate.
- To wire it in once approved: (1) get each carrier's tracking link from the network dashboard; (2) put it in
  `signupUrl` for that carrier's plans in `PlanData.kt` AND `ios/PlanSaver/PlanData.swift`; (3) keep `deals.json` urls
  as plain carrier pages (the robot overwrites them); (4) add a visible "PlanSaver may earn a commission" disclosure
  (the results screen already has one line) plus the same statement in the Play description and privacy policy.

## Publishing checklist (Android)

Play Console account already exists (BuddyVerse was published from it); `com.obliprabu.plansaver` needs its own new app
entry and its own upload key (do not reuse BuddyVerse's keystore settings by accident). Recent personal accounts must run
a closed test with testers for about two weeks before production. Still to do: app icon (placeholder from Menalith),
privacy policy URL, Data Safety form (the app sends only a GET to raw.githubusercontent.com, no personal data), content
rating, signed release config (none yet in `app/build.gradle.kts`; `./gradlew bundleRelease` would produce an unsigned
bundle), and a decision on repo visibility (public is required for the live deals download; NOTES.md exposes the
affiliate research while public).
