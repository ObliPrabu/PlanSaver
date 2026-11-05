# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

PlanSaver compares US cell-phone plans (and phone deals) for a user's lines/data/budget. Android (Kotlin + Compose) in
`app/`, iOS (SwiftUI, xcodegen) in `ios/`, plus GitHub-Action "robots" that refresh `deals-data/deals.json` (phone-plan deals) and
`deals-data/catalog.json` (phones, cars, car insurers for the Insurance / Phones / Cars tabs).
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

- Logic tests (no simulator, compiled for the Mac): `ios/run-logic-tests.sh` (`ios/LogicTests/main.swift` mirrors the Android unit tests).

Catalog robot: `python3 tools/refresh_catalog.py [path/to/catalog.json]` (stdlib only; `CATALOG_LIMIT` entries per run, default 12, the ones checked
longest ago; test against a COPY). Manual run: `gh workflow run catalog-robot.yml`.

Deals robot: `python3 tools/refresh_deals.py [path/to/deals.json]` (stdlib only; takes ~1 min because it sleeps between
requests - test against a COPY of the file, it rewrites in place). Manual workflow run: `gh workflow run deals-robot.yml`.

## Working rules (from the owner)

- Simulators/emulators are fine to use (the old no-simulator rule came from an earlier computer that was short on disk space; this one has room). iOS can also be compiled for the generic device, and Android is normally installed on the real vivo.
- NEVER install/download the app on Arjun Prabu's iPhone (`A9E704CA-91F2-5E2D-A171-093F439E4808`), under any
  circumstances, even if asked generically to install "on my devices" - the only iPhone target is Mithran Prabu's
  (`C13C4BB8-65E2-5CA7-BB9F-425747B421EE`).
- Do not install on Mithran Prabu's iPhone unless asked (the owner often uses it). Committing and pushing is automatic - see "Git workflow" below.
- Keep Android and iOS in sync: every logic/data change goes into both. The 45 plans in `PlanData.kt` were converted to
  `ios/PlanSaver/PlanData.swift` by a script - edit both by hand or re-convert.
- Version is kept equal on both: Android `versionCode`/`versionName` in `app/build.gradle.kts`, iOS
  `CURRENT_PROJECT_VERSION`/`MARKETING_VERSION` in `ios/project.yml` (currently 0.2.3 / 5). Bump only when asked.
- No paid services and no API keys. A search/AI key must never be inside the app. The owner is a minor: anything needing
  an adult account (affiliate networks CJ/Impact, a Gemini key, Play payouts) is deferred until a parent does it.
- Do not invent facts: plan prices/deals come from third-party roundups, unverified against carrier sites - say so.


## Git workflow

_Standing instruction from the owner (2026-11-05): commit and push automatically. Do not ask first._

- **Commit and push on your own.** After finishing a change that builds and whose tests pass, commit it and push it to the branch you are on - including `main` when that is where you are working. Do this at natural stopping points, not after every tiny edit, and do not stop to ask for permission.
- **If a push is rejected** because GitHub has newer commits (for example a robot or a cloud session pushed), run `git pull --rebase` to put your commits on top of theirs, run the build/tests again if the incoming changes touch code you changed, then push again. If the rebase has conflicts you cannot resolve safely, stop and tell the owner.
- **Never force-push** and never rewrite history that is already pushed.
- **Never commit secrets or local files:** `local.properties`, `*.jks` / `*.keystore`, `GoogleService-Info.plist`, API keys, provisioning files, and `.idea/` changes (unless asked). Stage the project's own files by name instead of `git add -A`.
- **Cloud-session branches** (`claude/...`): fetching is fine, but merging one into your work still happens only when the owner says to pull.
- **Write real commit messages:** a short summary line, then what changed and why in plain English, ending with the usual `Co-Authored-By` line.
- **Pushing is not releasing or installing.** Uploading to Google Play, publishing a Mac `.dmg`, editing the website, and installing on the owner's own devices still happen only when asked. Never install on Arjun Prabu's iPhone.

## Architecture

Tabs (Android `AppShell` in CompareScreens.kt, iOS `RootView` in CompareViews.swift): Plans (the search flow below), Insurance, Phones,
Cars. The dark-mode toggle floats over all tabs. `deals-data/catalog.json` is bundled in both apps (Android assets via
`sourceSets`, iOS resource in project.yml) as an offline fallback and downloaded fresh when a tab opens (`CATALOG_URL` /
`catalogURL`). It holds NAMES only; `tools/refresh_catalog.py` fills every number and the page it came from (`sources`) and the date
(`updated`) - nothing is hand-researched, and the app shows "not found yet" for anything the robot has not found. Cars: fuel
economy from the free official fueleconomy.gov API (one EPA trim per model, `epaModel`), price from the maker's page when it shows
one. Phones: price only from a page that belongs to one phone; specs only from sentences naming that phone (maker pages,
gsmarena/phonearena snippets). Insurers: only "average/median" sentences naming the current year, read from the pages listed in
`insurancePages` (a single imagined-driver quote is rejected so figures stay comparable). Logic mirrored in Catalog.kt/Catalog.swift
(compare rows, insurer sort), tested in `CatalogTest.kt`, `ios/LogicTests/main.swift` and `tools/test_refresh_catalog.py`.

Cars tab "My own questions" (2026-10-31): a toggle at the top of the Cars tab switches from the catalog comparison to a questionnaire for two vehicles (make/model/year, price, how paying, MPG, liability and full insurance with how often it is paid, oil change cost and months between changes, a set of tires and optionally how many years it lasts, yearly fuel cost, years kept, why picked, safety features). Everything is typed by the user, nothing is looked up. `CarPlan.kt`/`CarPlan.swift` hold the data and math (mirrored; tests `CarPlanTest.kt` and `ios/LogicTests/main.swift`, which the runner now compiles with `CarPlan.swift`); `CarPlanScreen.kt`/`CarPlanView.swift` hold the forms; `compareMyCars` reuses `CompareRow`/`CompareTable`. Totals add only the costs entered (blank = nothing) and tires count as one set unless a lifespan is given; loan interest, lease fees, repairs and depreciation are not included.

AI search (2026-10-31): after the normal search shows results, both apps call a Cloudflare Worker (`ai-proxy/worker.js`, deployed as `plansaver-ai` at https://plansaver-ai.plansaver-app.workers.dev) that holds the Gemini key as a Worker secret (a parent created the key; the owner is a minor) and answers with `{text}`. `AiSuggest.kt`/`AiSuggest.swift` build the request from the whole `SearchRequest` (`lines`, `dataNeedGb`, `currentMonthly` only if entered, `includePostpaid`, `freePhoneOnly`, `includeOtherPhones`, `phone`, per-line `lineDataNeeds`, `tradeInPhone` when a trade-in is on) plus the 12 cheapest plans for the picked phone (no other-phone deals), so the AI answers for exactly what was searched; the Worker's `buildPrompt` turns each field into a sentence and show an "AI suggestion" card above the results; on any failure or a busy Gemini (it answers 503 at times) nothing is shown and the normal results stand. Gemini only sees plans the app already found. The Search button keeps its label. The apps never contain the key. The Worker's default model is `gemini-3.5-flash-lite` (Google retired 2.5-flash for new users; 3.8-flash was often overloaded with 503s and gemini-4-argon returned 429 quota-exceeded on the free key, so a lighter model was chosen on 2026-10-31); a `GEMINI_MODEL` Worker variable overrides it, so remove that variable if a model starts failing. The Worker also gives Gemini at most 20 s and allows 2000 output tokens (newer models spend output tokens on hidden thinking, and 400 cut answers off mid-sentence). Tests: `AiSuggestTest.kt` and `ios/LogicTests/main.swift`. The owner removed the assistant's Cloudflare access; redeploying the Worker needs a new login.

Deal plan requirement (2026-10-31): a deals.json entry may carry `requiresPlans` (e.g. AT&T: `["Premium","Elite"]`, because AT&T's trade-in credit needs Premium 2.0 or Elite 2.0 per tradein.att.com), a hand-set rule that the robot never edits (unlike `plans`, which the robot drops when the new deal text stops naming it). The deal text and its free-phone flag apply only to plans whose name contains one of the fragments, and a free-phone search (`freePhoneOnly`) only considers plans its carrier's free-phone deal covers (`searchAllowing` / `bundledSearch(allow:)`), so custom-line searches pick a qualifying plan instead of the cheapest non-qualifying one. Tests: `MixedLinesTest.kt` and `ios/LogicTests/main.swift`.

Search flow (identical on both platforms): `MainActivity.kt`/`ContentView.swift` build a `SearchRequest` -> 
`RemotePlanRepository.search` (Android) / `PlanRepository.search` (iOS) -> results screen.
1. Filter + sort the bundled plans (`PlanData`): postpaid toggle, data need, and a plan is dropped when `lines` is below
   the smallest line count it has a price for (a plan priced only for N+ lines is hidden below N; currently every plan has a 1-line price). `Plan.perLine(n)` falls back to
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

## Status and open items (as of 2026-10-18)

- Everything is committed and pushed (main). The vivo and Mithran Prabu's iPhone do NOT yet have the latest build
  (customized lines + dark mode + price corrections); install only when asked.
- Dark/light mode (2026-10-18): Android `Theme.kt` (`ThemeMode` Auto/Light/Dark, Material You colors on Android 12+, fixed
  blue/teal otherwise, fixed orange `MaterialTheme.dealColor` for deal highlights; also holds `scaledTypography(1.25f)`);
  iOS `ContentView.swift` has the same floating top-right toggle and `secondarySystemBackground` cards. The theme choice
  is not saved across restarts on iOS (`@State`); on Android it survives rotation/process death (`rememberSaveable`).
- Plan data corrections (2026-10-18, from an outside verification): Metro now one plan "Unlimited Premium 5G data" $60; T-Mobile
  "Essentials Saver" and "Experience More 2.0"; Verizon Unlimited Plus 1 line $80; AT&T "Extra 2.0" now also has a 1-line $70.
  Plan-name fragments in deals.json `plans` (e.g. "Experience") must keep matching these names.
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
- Customized lines (checkbox "Different data for each line", shown for 2+ lines): `SearchRequest.lineDataNeeds`; per carrier
  every line gets the cheapest plan covering its own data need, total = sum (`mixedSearch` in PlanRepository.kt/.swift),
  card lists "Line n: plan - price". Whether a carrier allows mixed plans on one account is NOT checked (card says so).
  Choosing a specific plan by name per line is not built.
- Quiz / top picks / total cost (2026-10-18, UNCOMMITTED, both platforms compile, Android tests pass, not installed): "Not sure?
  Answer 3 quick questions" fills the data choice (`quizDataIndex`); results start with a "Top picks" box (`topPicks`: cheapest,
  cheapest unlimited, priciest unlimited - labeled by what they are, perks not checked); the form has an optional phone price +
  trade-in checkbox/value, typed by the user (no phone prices are stored - none researched); each card has "How much will I
  pay in total?" showing 12/24/36 months of plan + phone (full price minus trade-in) via `totalCost`. Carrier bill credits
  are NOT modeled (card says so). Logic in Plan.kt/Plan.swift, tested in `TotalCostTest.kt`.
- Switching costs (2026-10-18, UNCOMMITTED): the free-or-discounted-phone checkbox section has (phone picker, then "I'll trade in", old phone picked from the same brand/model/version lists -
  no value typed; a trade-in only exists with a new phone). The robot adds `activation` and `tradein` extras and parses numbers
  into `activationFee` (per line; 0 = none) and `tradeInMax` ("up to $N", a best case) on a carrier's deals.json entry. The
  total card adds activation x lines and subtracts `tradeInMax` as the credit; if the robot found nothing it says so and counts
  nothing. Robot only trusts the carrier's own domain. 2026-10-20: kept only T-Mobile's $35 (t-mobile.com: "$35 device connection charge
  due at sale"); removed Verizon's (reported waived for Loyalty members), AT&T's (ambiguous) and junk trade-in notes - the robot may
  re-add them from carrier pages; it now rejects sentence fragments (`is_fragment`). Fees are carrier-level, not per plan.
- Results filters (2026-10-18, UNCOMMITTED): chips under the sort row - Verizon / AT&T / T-Mobile (any of the chosen), Prepaid /
  Postpaid, Has a deal, Unlimited only - applied by `applyFilters` (Plan.kt/.swift, `FilterTest.kt`), with "Showing X of Y plans".
  They stay set between searches. "Most data" looks the same as "Lowest price" when only unlimited plans match (all tie on data).
- Added 7 plans 2026-10-18 from web roundups (UNCOMMITTED, 1-line prices only, unverified): Tello 10GB $15 / 20GB $20 / Unlimited $25 (50GB),
  Verizon Unlimited Welcome $55 and Ultimate $85 (Droid Life, May 2026), AT&T Premium 2.0 $90, T-Mobile Experience Beyond $100. Sources
  disagree: Droid Life lists Verizon Unlimited Plus at $70 but the plan already in the app says $80 (left as is - check on verizon.com).
  Helium Mobile, Red Pocket and Ultra Mobile were skipped: only weak or marketing sources.
- Phones/Cars/Insurance tabs (2026-10-23, UNCOMMITTED): built and compiled on both platforms; the catalog starts EMPTY of numbers and fills as
  `.github/workflows/catalog-robot.yml` runs (separate from the deals robot; asks for every 30 min, GitHub runs it less often). Local
  test runs found EPA fuel economy for all tested cars, Samsung Galaxy S26 price, and several insurer averages; search engines
  often block (DuckDuckGo 202) and many maker sites return 403, so prices will fill slowly. Not yet checked on a device.
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

## Known Issues

_Maintained by the PlanSaver QA pass (run `/test-app`-style review; first pass 2026-10-31). Entries are numbered and never reused._

### Open

#### ISSUE-8: Catalog data looks wrong for at least one phone, and the robot's regexes allow it
- **Severity:** MEDIUM
- **Area:** data
- **File:** deals-data/catalog.json (iPhone 17); tools/refresh_catalog.py:46-105
- **Found by:** QA review (2026-10-31)
- **Description:** iPhone 17 shows 6.9 in and storage '512GB' only; the price regex accepts 'From $X/mo', trade-in credits and promos.
- **Suggested fix:** Add a negative lookahead after prices, require the model name before a display size, label storage 'options found'.
- **Last attempt:** 2026-10-31 — Robot code fixed 2026-10-31 (prices followed by /mo, trade-in or credit are rejected; a display size needs the model name right before it; storage only when the smallest tier is seen; 38 tests), but deals-data/catalog.json still holds the old iPhone 17 values (6.9 in, 512GB) until the robot refreshes it or someone corrects it by hand.

#### ISSUE-14: Android status-bar/dark-mode handling is likely wrong (light XML theme, no enableEdgeToEdge, wide layout ignores bottom inset)
- **Severity:** MEDIUM
- **Area:** ui
- **File:** res/values/themes.xml:2; MainActivity.kt:31-40; CompareScreens.kt:50-72
- **Found by:** QA review (2026-10-31)
- **Description:** In-app Dark or system dark probably draws dark status-bar icons on a dark surface; white flash on cold start; content can sit under the gesture bar on wide screens. Not confirmed without running.
- **Suggested fix:** Call enableEdgeToEdge with SystemBarStyle from the effective ThemeMode, DayNight parent theme with a night variant, add navigation-bar padding.
- **Last attempt:** 2026-10-31 — Partly done 2026-10-31: `enableEdgeToEdge` with a theme-driven `SystemBarStyle`, a `values-night` theme and the wide-layout bottom inset were added. Not verified on a device: status-bar icon colour in each theme mode and the cold-start flash. The parent theme is still plain Material (not DayNight).

#### ISSUE-15: Android chip rows aren't scrollable and overflow at large text on a 360dp phone
- **Severity:** MEDIUM
- **Area:** ui
- **File:** CompareScreens.kt:131-134; CarPlanScreen.kt:90-92; MainActivity.kt:208-217,254-259
- **Found by:** QA review (2026-10-31)
- **Description:** 'Every 6 months' row, Insurance coverage row and brand chips have no horizontalScroll while the app scales type by 1.25.
- **Suggested fix:** Add horizontalScroll or FlowRow.
- **Last attempt:** 2026-10-31 — Partly done 2026-10-31: the Insurance coverage chips and the car form's pay-method/insurance-frequency chips scroll horizontally. The brand chips and other chip rows in MainActivity.kt were not changed.

#### ISSUE-18: Lint: UseKtx x4, launcher icon not round x5, UseTomlInstead x2, newer Gradle/AGP/org.json
- **Severity:** LOW
- **Area:** build
- **File:** CompareScreens.kt:115; MainActivity.kt:397,407,441; res/mipmap-*; app/build.gradle.kts:50-51
- **Found by:** QA review (2026-10-31)
- **Description:** Style/tooling nags; builds and tests pass.
- **Suggested fix:** Use toUri(), provide a round/adaptive icon, move to version catalog, bump versions when convenient.
- **Last attempt:** 2026-10-31 — Partly done 2026-10-31: the UseKtx items in MainActivity.kt and CompareScreens.kt use `toUri()`. Still open: round launcher icon, UseTomlInstead, newer Gradle/AGP/org.json.

#### ISSUE-22: Theme chip can collide with titles at big text; orange deal text fails contrast on light backgrounds
- **Severity:** LOW
- **Area:** ui
- **File:** CompareScreens.kt:92,101; Theme.kt:42; ContentView.swift:379,392
- **Found by:** QA review (2026-10-31)
- **Description:** Floating chip reserves a fixed 72dp/80pt; #EF6C00 on white is about 3.3:1 and iOS .orange about 2:1.
- **Suggested fix:** Put the chip in a header row; use a darker orange in light mode.
- **Last attempt:** 2026-10-31 — Partly done 2026-10-31: light-mode deal orange darkened on both platforms; the title reserve for the chip is 100dp on the Plans screen. The Insurance/Phones/Cars titles in CompareScreens.kt still reserve the old width.

#### ISSUE-24: Commission disclosure is only at the bottom of Plans results; 'Prices researched Oct 2026' is hardcoded
- **Severity:** LOW
- **Area:** ui
- **File:** MainActivity.kt:362-364; ContentView.swift:330; CompareScreens.kt:175
- **Found by:** QA review (2026-10-31)
- **Description:** Insurance/Phones/Cars links carry no disclosure; the date will go stale.
- **Suggested fix:** Show a short disclosure near the first link and in the Insurance footer; derive the date from the data.
- **Last attempt:** 2026-10-31 — Partly done 2026-10-31: the Plans results show the commission line at the top as well and the bottom text no longer says 'Oct 2026'. The Insurance, Phones and Cars screens still have no commission disclosure.

### Fixed

#### ISSUE-1: AI Worker is an open, unauthenticated, unthrottled endpoint and returns Google's error details
- **Severity:** HIGH
- **Area:** security
- **File:** ai-proxy/worker.js:53-88
- **Found by:** QA review (2026-10-31)
- **Fixed by:** fixer agent on 2026-10-31
- **Summary:** `worker.js` refuses any request with an `Origin` header (403), requires `content-type: application/json` (415), rate-limits per IP (Cloudflare `RATE_LIMITER` binding, 20/60 s, plus an in-memory fallback of 20/min per isolate) and no longer returns Google's `status`/`detail` (only when the `DEBUG` variable is "1"). Deployed and tested live: 405/403/415 and a normal 200 work. Limit: a fast burst on one connection is blocked (429), but requests spread over many new connections landed on different servers and were not limited, so a patient script is not stopped; a Cloudflare WAF rate-limit rule (dashboard) would be stronger.

#### ISSUE-2: Typed answers in "My own questions" are lost when switching to "Catalog cars" and back (both platforms)
- **Severity:** MEDIUM
- **Area:** cars
- **File:** CompareScreens.kt:249-255 + CarPlanScreen.kt:30-31; ios CompareViews.swift:195,204 + CarPlanView.swift:6-7
- **Found by:** QA review (2026-10-31)
- **Fixed by:** fixer agent on 2026-10-31
- **Summary:** `CarsScreen`/`CarsView` own the two `CarPlanEntry` states and pass them to the form, so switching Catalog/My-questions keeps the answers. Verified on the vivo: typed text survived the flip.

#### ISSUE-3: The green tick favours a car that simply left fields blank
- **Severity:** MEDIUM
- **Area:** cars
- **File:** CarPlan.kt:70-82,109-111; CarPlan.swift:69-88
- **Found by:** QA review (2026-10-31)
- **Fixed by:** fixer agent on 2026-10-31
- **Summary:** Aggregate rows (running cost, total, per year) mark no winner and append " (partial)" when the two cars entered different sets of components (insurance, oil, fuel, tires); identical on both platforms with tests.

#### ISSUE-4: T-Mobile and Verizon deals apply to every plan, so a free-phone search can choose a non-qualifying plan
- **Severity:** MEDIUM
- **Area:** plans
- **File:** deals-data/deals.json:4-51; PlanRepository.kt:76-81; PlanRepository.swift:90-93
- **Found by:** QA review (2026-10-31)
- **Fixed by:** fixer agent on 2026-10-31
- **Summary:** Hand-set `requiresPlans` added to the T-Mobile (`["Experience"]`, matching its trade-in text) and Verizon (`["Ultimate","Plus"]`) entries in deals.json. Verizon's text names only Ultimate; Plus was added as the other premium tier and should be confirmed against Verizon's real terms.

#### ISSUE-5: AT&T Value 2.0 keeps its stale bundled deal and free-phone flag when the live deal's requiresPlans excludes it
- **Severity:** MEDIUM
- **Area:** plans
- **File:** PlanRepository.kt:91-95; PlanRepository.swift:104; PlanData.kt:35-36
- **Found by:** QA review (2026-10-31)
- **Fixed by:** fixer agent on 2026-10-31
- **Summary:** When a carrier has live deals but none covers a plan, that plan's bundled deal, free-phone flag, link and date are cleared (`mergeLiveDeals`, both platforms); offline keeps the bundled data and says so in a comment.

#### ISSUE-6: 'Better phone' logic treats iPhone Air/XS/XR as generation 0
- **Severity:** MEDIUM
- **Area:** plans
- **File:** Plan.kt:98-114; Plan.swift:116-134
- **Found by:** QA review (2026-10-31)
- **Fixed by:** fixer agent on 2026-10-31
- **Summary:** `brandGen` now uses an x10 scale: numbered iPhones N*10, XS/XR 105, Air 175, Duo 990, `e` models N*10-5, so Air/XS/XR order correctly and an e-model is never 'better' than the same number.

#### ISSUE-7: Android drops ALL live deals or the whole live catalog if one entry is malformed (iOS skips just that entry)
- **Severity:** MEDIUM
- **Area:** plans
- **File:** PlanRepository.kt:149-164; Catalog.kt:46-61 vs PlanRepository.swift:170-182, Catalog.swift:63-82
- **Found by:** QA review (2026-10-31)
- **Fixed by:** fixer agent on 2026-10-31
- **Summary:** Android parses deals (`parseLiveDeals`) and the catalog (`Catalog.parse`) per entry inside its own try, so one bad entry is skipped; JSON null gives the default, not the text "null", and numeric helpers accept only real numbers (iOS also rejects booleans).

#### ISSUE-9: Android Plans tab re-runs the search and the Gemini request every time you return to the tab
- **Severity:** MEDIUM
- **Area:** plans
- **File:** MainActivity.kt:69-81,94-117
- **Found by:** QA review (2026-10-31)
- **Fixed by:** fixer agent on 2026-10-31
- **Summary:** A `PlansCache` keyed by the form inputs keeps results, AI text and phone across tab switches; process-death restore re-searches but asks no AI; a late search answer no longer reopens results after Back. Verified on the vivo (tab switch restored results and the same AI text). Limit: an AI request still in flight when you leave the tab is lost.

#### ISSUE-10: The AI data-sharing notice appears only after the data was sent, is incomplete, and has no opt-in
- **Severity:** MEDIUM
- **Area:** privacy
- **File:** MainActivity.kt:105-112,330-338; ContentView.swift:246-251,293-308
- **Found by:** QA review (2026-10-31)
- **Fixed by:** fixer agent on 2026-10-31
- **Summary:** Both apps have an "Ask AI for a suggestion after searching" switch (default on, off = nothing is sent) with a note under it listing what is sent, a loading line that says the search is sent to Google's Gemini, and a fuller card footnote. Verified on the vivo.

#### ISSUE-11: iOS PlanSaver has no PrivacyInfo.xcprivacy and store privacy forms aren't addressed
- **Severity:** MEDIUM
- **Area:** privacy
- **File:** ios/ (none found); ios/project.yml
- **Found by:** QA review (2026-10-31)
- **Fixed by:** fixer agent on 2026-10-31
- **Summary:** Added `ios/PlanSaver/PrivacyInfo.xcprivacy` (no tracking; Other User Content for AppFunctionality; no accessed-API entries since the app uses no UserDefaults) and `ITSAppUsesNonExemptEncryption=false`; built into the app. The App Store / Play data-safety forms themselves still have to be filled in by the owner or a parent.

#### ISSUE-12: Robots publish scraped text straight to main with no validation, and it flows into app UI and AI prompts
- **Severity:** MEDIUM
- **Area:** security
- **File:** tools/refresh_deals.py:300-396; .github/workflows/deals-robot.yml
- **Found by:** QA review (2026-10-31)
- **Fixed by:** fixer agent on 2026-10-31
- **Summary:** `tools/validate_data.py` plus shared validation in `refresh_deals.py`/`refresh_catalog.py`: JSON must parse, entry lists must not shrink, every url/source must be https, strings at most 400 chars with no control characters or injection phrases; atomic temp-file writes; both workflows run the validator before committing; hand-set keys (`requiresPlans`, `ignore`, `expires`, `ignoreFreePhone`) survive. 38 robot tests pass.

#### ISSUE-13: Prompt injection through Worker-visible fields; system prompt doesn't tell the model to treat them as data
- **Severity:** MEDIUM
- **Area:** security
- **File:** ai-proxy/worker.js:8-43
- **Found by:** QA review (2026-10-31)
- **Fixed by:** fixer agent on 2026-10-31
- **Summary:** The Worker's system prompt now says to treat plan/carrier/deal/phone fields as data only, answer in plain text with no markdown, and mention only listed plans by exact name. The optional JSON-picks-validated-by-the-app step was not built.

#### ISSUE-16: Car-form fields have no accessible label (both platforms)
- **Severity:** MEDIUM
- **Area:** ui
- **File:** CarPlanScreen.kt:68-84; CarPlanView.swift:53-88
- **Found by:** QA review (2026-10-31)
- **Fixed by:** fixer agent on 2026-10-31
- **Summary:** Every question field has an accessible label "<vehicle>: <question>" (Android semantics, iOS accessibilityLabel) and the pickers have real labels.

#### ISSUE-17: iOS forces extra-extra-large text, including on Mac Catalyst
- **Severity:** MEDIUM
- **Area:** ui
- **File:** ios/PlanSaver/PlanSaverApp.swift:6-9
- **Found by:** QA review (2026-10-31)
- **Fixed by:** fixer agent on 2026-10-31
- **Summary:** The extra-extra-large text floor applies only off Mac Catalyst and updates on content-size changes.

#### ISSUE-19: Scraping politeness: spoofed Chrome user agent, no robots.txt check, DuckDuckGo/Bing HTML scraping, 5-minute cron
- **Severity:** LOW
- **Area:** security
- **File:** tools/refresh_deals.py:15-33,306-320; refresh_catalog.py
- **Found by:** QA review (2026-10-31)
- **Fixed by:** fixer agent on 2026-10-31
- **Summary:** Honest User-Agent with the project URL, robots.txt respected for direct page fetches, deals workflow cron slowed to hourly. (The Deals pipeline paragraph above still says every 5 minutes.)

#### ISSUE-20: AI card gaps: Back doesn't bump aiRun, connection never disconnect()ed, Gemini markdown shows literal asterisks
- **Severity:** LOW
- **Area:** ai
- **File:** MainActivity.kt:101-113; AiSuggest.kt:51-65; AiSuggest.swift:40-49
- **Found by:** QA review (2026-10-31)
- **Fixed by:** fixer agent on 2026-10-31
- **Summary:** Back bumps `aiRun`; `disconnect()` runs in a finally; `stripMarkdown`/`plainAiText` remove `**` and bullet markers; tests on both platforms.

#### ISSUE-21: Android manifest: allowBackup=true with no rules; release build not minified
- **Severity:** LOW
- **Area:** build
- **File:** AndroidManifest.xml; app/build.gradle.kts:32
- **Found by:** QA review (2026-10-31)
- **Fixed by:** fixer agent on 2026-10-31
- **Summary:** `allowBackup="false"` in the manifest.

#### ISSUE-23: Decimal-comma keyboards turn '12,50' into 1250; '1.2.3' silently becomes 'not entered'
- **Severity:** LOW
- **Area:** ui
- **File:** MainActivity.kt:186; CarPlanScreen.kt:78; CarPlanView.swift:80 (and Insurance, iOS ContentView.swift:118,150)
- **Found by:** QA review (2026-10-31)
- **Fixed by:** fixer agent on 2026-10-31
- **Summary:** Number inputs map ',' to '.', accept only ASCII 0-9 and one '.', in the Plans, Insurance and car fields on both platforms (`cleanNumberInput`/`sanitizeNumberInput`); no 'enter a number' hint was added.

#### ISSUE-25: iOS typo and parity nits: stray comma in trade-in text; Insurance chip says 'Minimum' vs 'Minimum (liability)'; iOS Back button outside the 720 frame
- **Severity:** LOW
- **Area:** ui
- **File:** ContentView.swift:434; CompareViews.swift:80; ContentView.swift:260-264
- **Found by:** QA review (2026-10-31)
- **Fixed by:** fixer agent on 2026-10-31
- **Summary:** Removed the stray comma, constrained the iOS results header to the card width, and the iOS Insurance chip now says "Minimum (liability)".

#### ISSUE-26: Android and iOS round numbers differently (half-up vs half-even) and format with different locales; MPG winner is picked on exact values
- **Severity:** LOW
- **Area:** cars
- **File:** CarPlan.kt:86,100-108; CarPlan.swift:91-107; Catalog.kt:97; Catalog.swift:122-128
- **Found by:** QA review (2026-10-31)
- **Fixed by:** fixer agent on 2026-10-31
- **Summary:** Both platforms format amounts en_US with half-up rounding; MPG and years show one decimal only when fractional.

#### ISSUE-27: Tire sets can be off by one from floating-point division; liability is shown but never used in totals; total rows don't say they assume full coverage
- **Severity:** LOW
- **Area:** cars
- **File:** CarPlan.kt:65,70-82; CarPlan.swift:64
- **Found by:** QA review (2026-10-31)
- **Fixed by:** fixer agent on 2026-10-31
- **Summary:** Tire sets use `ceil(keep/setYears - 1e-9)`; the total and per-year rows are labelled "(full coverage)".

#### ISSUE-28: Catalog parsing differs: Android optString turns JSON null into 'null' and coerces numeric strings; iOS doesn't
- **Severity:** LOW
- **Area:** data
- **File:** Catalog.kt:49-61; Catalog.swift:67-82
- **Found by:** QA review (2026-10-31)
- **Fixed by:** fixer agent on 2026-10-31
- **Summary:** Android and iOS read catalog and deal fields the same way (null gives the default; booleans and numeric strings are not numbers).

#### ISSUE-29: Android total-cost expansion follows list position, not the plan; 'More than 4 lines' note misdescribes most plans; extras show raw keys in different order per platform
- **Severity:** LOW
- **Area:** plans
- **File:** MainActivity.kt:402,221; ContentView.swift:293-296; PlanRepository.kt:124-131; PlanRepository.swift:142-149
- **Found by:** QA review (2026-10-31)
- **Fixed by:** fixer agent on 2026-10-31
- **Summary:** ResultCard is wrapped in `key(...)` on Android; the 'More than 4 lines' note says the nearest known per-line price is used; extras get 'Activation'/'Trade-in' labels and sort alphabetically on both platforms.

#### ISSUE-30: Mixed-line results: random order for equal totals on iOS, carrier-wide deal shown even if only some lines use the covered plan, Android maxBy throws on empty needs
- **Severity:** LOW
- **Area:** plans
- **File:** PlanRepository.swift:62; PlanRepository.kt:38
- **Found by:** QA review (2026-10-31)
- **Fixed by:** fixer agent on 2026-10-31
- **Summary:** Mixed-line results sort by total then carrier name on both platforms; Android no longer throws for empty needs.

#### ISSUE-31: Deal data nits: Visible marked free-phone though its text is a plan promo; Mint promo that 'ends Oct 22, 2026' still shown on Oct 31
- **Severity:** LOW
- **Area:** data
- **File:** deals-data/deals.json:79,101-103
- **Found by:** QA review (2026-10-31)
- **Fixed by:** fixer agent on 2026-10-31
- **Summary:** Visible got a hand-set `ignoreFreePhone: true` (both apps treat it as not free-phone, robot-proof) and Mint a hand-set `expires: 2026-10-22`; both apps skip an expired deal.
