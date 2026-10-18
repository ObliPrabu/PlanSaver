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
        # ...and the good lines it found:
        self.assertGreater(r.extra_score("hotspot", "footnote 2 Mobile hotspot with unlimited data at speeds up to 15 Mbps."), 0)
        self.assertGreater(r.extra_score("international", "footnote 3 Unlimited calling and messaging to Mexico and Canada when you are in the US."), 0)
        self.assertGreater(r.extra_score("slowdown", "After 2 GB/day of data, speeds reduced to 3G."), 0)

    def test_plans_scope(self):
        entry = {"plans": ["6-month"]}
        self.assertTrue(r.plans_still_apply(entry, "Any 6 month plan for $10/month"))
        self.assertFalse(r.plans_still_apply(entry, "Up to $600 off Galaxy phones"))


if __name__ == "__main__":
    unittest.main()
