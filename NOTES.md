# PlanSaver research notes (Oct 2026)

Plan data in `PlanData.kt` comes from third-party roundups (US Mobile blog, NerdWallet, US News, PhoneArena, ShopBack),
NOT yet verified on the carriers' own sites. Verify before publishing.

## Affiliate payouts (approx., from affiliate directories)
- Mint Mobile ~$20 per activation
- US Mobile ~$30 per approved sale (CJ Affiliate)
- Cricket $0-10 per sale (CJ Affiliate)
- Visible via Impact (payout unknown)

## Deals (change weekly; usually need trade-in + 24-36 months of bill credits)
- T-Mobile Keep and Switch: up to $800 to pay off old phone
- Verizon: free phones on new lines on select unlimited plans
- AT&T: switcher deals, up to $800 device payoff

## Rules
- Never put a search/AI API key in the app; live search runs on a server (Cloud Function) with caching.
- Disclose affiliate commissions in the app and Play listing.
