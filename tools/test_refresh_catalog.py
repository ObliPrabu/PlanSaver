import sys
import unittest

sys.path.insert(0, "tools")
import refresh_catalog as c

PHONES = ["iPhone 17", "iPhone 17 Pro", "iPhone Air", "Galaxy S26", "Galaxy S26+", "Galaxy S26 Ultra"]


class PriceTests(unittest.TestCase):
    def test_from_price(self):
        self.assertEqual(899.0, c.price_from_text('aria-label="From $899"', 150, 2500))
        self.assertEqual(29300.0, c.price_from_text("Starting MSRP $29,300", 12000, 250000))

    def test_implausible_prices_are_ignored(self):
        self.assertIsNone(c.price_from_text("From $5 a month", 150, 2500))
        self.assertIsNone(c.price_from_text("No price here", 150, 2500))


class PhoneTextTests(unittest.TestCase):
    def test_longer_name_is_not_the_shorter_phone(self):
        self.assertFalse(c.mentions("The iPhone 17 Pro has a 6.3-inch display.", "iPhone 17", PHONES))
        self.assertTrue(c.mentions("The iPhone 17 has a 6.3-inch display.", "iPhone 17", PHONES))
        self.assertTrue(c.mentions("The iPhone 17 Pro has a 6.3-inch display.", "iPhone 17 Pro", PHONES))
        self.assertFalse(c.mentions("The Galaxy S26 Ultra has a 6.9-inch screen.", "Galaxy S26", PHONES))

    def test_specs(self):
        specs = c.specs_from_sentences([
            "A 6.3-inch Super Retina XDR display with ProMotion.",
            "Powered by the A19 Pro chip with a 4,870 mAh battery.",
            "Storage options: 256GB, 512GB and 1TB.",
        ])
        self.assertEqual(6.3, specs["display"])
        self.assertEqual(4870, specs["batteryMah"])
        self.assertEqual("A19 Pro", specs["chip"])
        self.assertEqual("256GB, 512GB, 1TB", specs["storage"])

    def test_nonsense_numbers_are_not_specs(self):
        self.assertEqual({}, c.specs_from_sentences(["Save 12 GB of room. Weighs 9.9 inches of rain."]))


class CarTests(unittest.TestCase):
    def test_gas_car(self):
        out = c.epa_fields({"comb08": "35", "city08": "32", "highway08": "40", "fuelCost08": "1650",
                            "fuelType": "Regular Gasoline", "VClass": "Midsize Cars"})
        self.assertEqual((35, 32, 40, "MPG"), (out["mpgCombined"], out["mpgCity"], out["mpgHighway"], out["mpgUnit"]))
        self.assertNotIn("rangeMiles", out)

    def test_electric_car_has_mpge_and_range(self):
        out = c.epa_fields({"comb08": "130", "city08": "135", "highway08": "124", "fuelCost08": "800",
                            "fuelType1": "Electricity", "fuelType": "Electricity", "VClass": "Midsize Cars", "range": "363"})
        self.assertEqual(("MPGe", 363), (out["mpgUnit"], out["rangeMiles"]))


class InsurerTests(unittest.TestCase):
    def test_amount_next_to_the_name(self):
        s = "Full coverage, monthly average: GEICO $143, Progressive $136, State Farm $103 a month."
        self.assertEqual({"full": 143.0}, c.insurer_amounts(s, "GEICO"))
        self.assertEqual({"full": 103.0}, c.insurer_amounts(s, "State Farm"))

    def test_yearly_becomes_monthly_and_minimum_is_tagged(self):
        s = "State Farm charges about $600 per year for minimum coverage."
        self.assertEqual({"min": 50.0}, c.insurer_amounts(s, "State Farm"))

    def test_unlabeled_or_absurd_amounts_are_skipped(self):
        self.assertEqual({}, c.insurer_amounts("GEICO is rated 4 stars and costs $5 per month.", "GEICO"))
        self.assertEqual({}, c.insurer_amounts("GEICO had $143 of something.", "GEICO"))


class ApplyTests(unittest.TestCase):
    def test_only_found_values_change_and_sources_are_kept(self):
        e = {"name": "X", "price": 100.0}
        self.assertTrue(c.apply(e, {"price": 120.0}, {"price": "https://a"}))
        self.assertEqual((120.0, "https://a"), (e["price"], e["sources"]["price"]))
        self.assertFalse(c.apply(e, {"price": 120.0}, {"price": "https://a"}))
        self.assertFalse(c.apply(e, {}, {}))
        self.assertEqual(120.0, e["price"])


if __name__ == "__main__":
    unittest.main()


class InsurerPageTests(unittest.TestCase):
    def test_only_average_sentences_count(self):
        year = str(c.date.today().year)
        pages = {"u": [f"Insurance.com's profile in {year} prices GEICO at $2,159 a year for full coverage.",
                       f"Insurify's {year} averages for full coverage: GEICO $143 a month, Allstate $159."]}
        c._PAGES.update(pages)
        found, sources = c.refresh_insurer({"name": "GEICO"}, ["u"])
        self.assertEqual({"fullMonthly": 143.0}, found)
        self.assertEqual({"fullMonthly": "u"}, sources)
