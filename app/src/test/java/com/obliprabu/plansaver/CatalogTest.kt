package com.obliprabu.plansaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CatalogTest {
    private val json = """
        {"phones":[
          {"id":"a","name":"Phone A","brand":"X","url":"https://x.com","price":799.0,"display":6.3,"batteryMah":4500,"chip":"A19","storage":"256GB, 512GB","updated":"2026-10-20","sources":{"price":"https://www.apple.com/a"}},
          {"id":"b","name":"Phone B","brand":"Y","url":"https://y.com","price":999.99,"batteryMah":5000}
        ],
        "cars":[
          {"id":"c1","name":"Gas Car","make":"G","url":"https://g.com","mpgCombined":35,"mpgCity":32,"mpgHighway":40,"mpgUnit":"MPG","annualFuelCost":1650,"price":25000.0},
          {"id":"c2","name":"EV","make":"E","url":"https://e.com","mpgCombined":130,"mpgUnit":"MPGe","annualFuelCost":800,"rangeMiles":300}
        ],
        "insurers":[
          {"id":"i1","name":"Cheap","url":"https://c.com","fullMonthly":100.0,"minMonthly":40.0,"sources":{"fullMonthly":"https://www.nerdwallet.com/p"}},
          {"id":"i2","name":"None","url":"https://n.com"},
          {"id":"i3","name":"Pricey","url":"https://p.com","fullMonthly":170.0}
        ]}
    """.trimIndent()
    private val catalog = Catalog.parse(json)

    @Test fun parsesNumbersAndLeavesMissingOnesNull() {
        assertEquals(799.0, catalog.phones[0].price!!, 0.001)
        assertNull(catalog.phones[1].chip)
        assertNull(catalog.phones[1].display)
        assertEquals("https://www.apple.com/a", catalog.phones[0].sources["price"])
        assertNull(catalog.insurers[1].fullMonthly)
    }

    @Test fun phoneComparisonMarksTheBetterSide() {
        val rows = comparePhones(catalog.phones[0], catalog.phones[1]).associateBy { it.label }
        assertEquals(0, rows.getValue("Starting price").better) // cheaper
        assertEquals(1, rows.getValue("Battery").better) // bigger
        assertEquals("not found yet", rows.getValue("Chip").right)
        assertNull(rows.getValue("Display").better)
    }

    @Test fun carComparisonDoesNotCompareMpgWithMpge() {
        val rows = compareCars(catalog.cars[0], catalog.cars[1]).associateBy { it.label }
        assertNull(rows.getValue("Fuel economy (combined)").better)
        assertEquals(1, rows.getValue("Fuel cost per year (EPA estimate)").better)
        assertEquals("130 MPGe", rows.getValue("Fuel economy (combined)").right)
        assertEquals("not found yet", rows.getValue("Starting price").right)
    }

    @Test fun insurersSortCheapestFirstAndMissingLast() {
        assertEquals(listOf("Cheap", "Pricey", "None"), sortInsurers(catalog.insurers, full = true).map { it.name })
        assertEquals("Cheap", sortInsurers(catalog.insurers, full = false).first().name)
    }

    @Test fun sourceDomainDropsWww() {
        assertEquals("nerdwallet.com", sourceDomain("https://www.nerdwallet.com/insurance/auto"))
        assertNull(sourceDomain(null))
    }

    @Test fun theCatalogFileInTheRepoParsesAndHasNames() {
        val real = Catalog.parse(File("../deals-data/catalog.json").readText())
        assertTrue(real.phones.size >= 10 && real.cars.size >= 10 && real.insurers.size >= 5)
    }
}
