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

    def test_plans_scope(self):
        entry = {"plans": ["6-month"]}
        self.assertTrue(r.plans_still_apply(entry, "Any 6 month plan for $10/month"))
        self.assertFalse(r.plans_still_apply(entry, "Up to $600 off Galaxy phones"))


if __name__ == "__main__":
    unittest.main()
