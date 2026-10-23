import sys
import unittest

sys.argv = ["x"]
import refresh_deals as r

GOOD = "Save up to $800 per line when you switch to AT&T and trade in your old phone or break your contract."
JUNK = ("Compare See device offers $0 with trade-in Black Quick view Apple Rated4.1out of 5 stars with4132reviews "
        "4.1 4K iPhone 17 Price was $25.84 per month, now As low as $0.00 per month As low as $0.00 /mo.")


class ScraperTests(unittest.TestCase):
    def test_junk_listing_scores_zero(self):
        self.assertEqual(r.score(JUNK), 0)

    def test_real_offer_scores(self):
        self.assertGreaterEqual(r.score(GOOD), r.MIN_SCORE)

    def test_free_phone_detection(self):
        self.assertTrue(r.offers_phone(GOOD))
        self.assertTrue(r.offers_phone("Get up to $1,000 off iPhone 18 Pro Max when you switch."))
        self.assertTrue(r.offers_phone("$800 off when you switch and trade in."))
        self.assertFalse(r.offers_phone("5-year price lock for new customers who bring their own phone ($20/month)."))

    def test_line_prices(self):
        self.assertGreater(r.lines_score("Unlimited is $25 per line for 4 lines with autopay."), 0)
        self.assertGreater(r.lines_score("Get 3 lines for $90/mo on our best plan."), 0)
        self.assertEqual(r.lines_score("Unlimited is $50 per month for one line."), 0)
        self.assertEqual(r.lines_score("Rated 4.1 out of 5 stars: $25 per line for 4 lines Quick view"), 0)

    def test_extras(self):
        self.assertGreater(r.extra_score("hotspot", "Includes 50GB of premium hotspot data each month."), 0)
        self.assertEqual(r.extra_score("hotspot", "Use your phone as a hotspot anywhere you like."), 0)
        self.assertGreater(r.extra_score("perks", "Netflix on Us is included with Unlimited Premium."), 0)
        self.assertGreater(r.extra_score("international", "Free calling and texting in Canada and Mexico."), 0)
        self.assertGreater(r.extra_score("slowdown", "Speeds may be slowed after 100GB of usage."), 0)
        self.assertEqual(r.extra_score("perks", "Rated 4.1 out of 5 stars Netflix Quick view"), 0)
        # Junk the first live run saved:
        self.assertEqual(r.extra_score("international", "Mastercard is a registered trademark of Mastercard International Incorporated."), 0)
        self.assertEqual(r.extra_score("slowdown", "Taxes & fees, speed upgrades and network management practices aren't included."), 0)
        self.assertEqual(r.extra_score("perks", "Streaming video defaults to 480p; customers can change it in account settings."), 0)
        self.assertEqual(r.extra_score("international", "^ Unlimited 5G built for real life See how we keep up with daily calls to Mexico."), 0)
        self.assertEqual(r.extra_score("international", "fcc.gov/consumer M0026739722VISPLUS00020255 Visible+ Pro plan facts Premium unlimited data, calling and texting to Canada and Mexico."), 0)
        # ...and the good lines it found:
        self.assertGreater(r.extra_score("hotspot", "footnote 2 Mobile hotspot with unlimited data at speeds up to 15 Mbps."), 0)
        self.assertGreater(r.extra_score("international", "footnote 3 Unlimited calling and messaging to Mexico and Canada when you are in the US."), 0)
        self.assertGreater(r.extra_score("slowdown", "After 2 GB/day of data, speeds reduced to 3G."), 0)

    def test_bing_parse(self):
        import base64
        real = base64.urlsafe_b64encode(b"https://www.visible.com/plans").decode().rstrip("=")
        page = ('<li class="b_algo" iid=1><a href="https://www.bing.com/ck/a?!&amp;p=x&amp;u=a1' + real +
                '&amp;ntb=1">Visible</a><p class="b_lineclamp2">Get Visible+ for $30/mo &amp; save.</p></li>')
        self.assertEqual(r.bing_parse(page), [("https://www.visible.com/plans", "Get Visible+ for $30/mo & save.")])

    def test_plans_scope(self):
        entry = {"plans": ["6-month"]}
        self.assertTrue(r.plans_still_apply(entry, "Any 6 month plan for $10/month"))
        self.assertFalse(r.plans_still_apply(entry, "Up to $600 off Galaxy phones"))


if __name__ == "__main__":
    unittest.main()


class SwitchingCostTests(unittest.TestCase):
    def test_activation_fee_amount(self):
        self.assertEqual(35.0, r.activation_amount("A one-time activation fee of $35 applies per line."))
        self.assertEqual(0.0, r.activation_amount("There is no activation fee when you activate online."))
        self.assertIsNone(r.activation_amount("The activation fee is $35 in store or $10 online."))

    def test_trade_in_best_case(self):
        self.assertEqual(800.0, r.trade_in_max("Save up to $800 per line when you trade in your old phone."))
        self.assertIsNone(r.trade_in_max("Trade in any phone and get credit."))

    def test_extras_pick_up_switching_costs(self):
        self.assertGreater(r.extra_score("activation", "A one-time activation fee of $35 applies per line."), 0)
        self.assertGreater(r.extra_score("tradein", "Get up to $800 off when you trade in your old phone."), 0)
        self.assertEqual(0, r.extra_score("tradein", "Learn more about how trade-in works for your phone."))

    def test_update_extra_stores_the_number(self):
        entry = {}
        r.update_extra(entry, "activation", (3, "A one-time activation fee of $35 applies per line.", "u"))
        self.assertEqual(35.0, entry["activationFee"])


class FragmentTests(unittest.TestCase):
    def test_fragments_are_rejected(self):
        junk = "plan w/AutoPay; plus taxes/fees) & trade-in (e.g., Save $1,000: Pixel 10/ Save $500: Pixel 5) required."
        self.assertEqual(0, r.extra_score("tradein", junk))
        self.assertTrue(r.is_fragment("plan w/AutoPay; plus taxes/fees) & trade-in required."))
        self.assertFalse(r.is_fragment("Get up to $800 off when you trade in your old phone."))

    def test_no_activation_fee_needs_the_words_close_together(self):
        self.assertEqual(0.0, r.activation_amount("Plus no upgrade or activation fees whenever you get a new device!"))
        self.assertIsNone(r.activation_amount("There is no waiver of anything. Ask about the connection process."))


class IgnoreTests(unittest.TestCase):
    def test_ignored_kinds_are_skipped_in_main(self):
        src = open(r.__file__).read()
        self.assertIn('entry.get("ignore", [])', src)


class ActivationNearTests(unittest.TestCase):
    def test_plan_price_is_not_the_fee(self):
        text = "Get unlimited data for $40. Covered by the Verizon 5G network. No activation fees !"
        self.assertEqual(0.0, r.activation_amount(text))
        self.assertIsNone(r.activation_amount("Unlimited data for $40 a month. Taxes apply."))
        self.assertEqual(35.0, r.activation_amount("Unlimited for $40. A $35 activation fee applies."))
