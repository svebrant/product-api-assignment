package com.svebrant.configuration

import com.svebrant.model.product.Country

val VAT_RATES: Map<Country, Double> =
    mapOf(
        Country.SWEDEN to 0.25,
        Country.GERMANY to 0.19,
        Country.FRANCE to 0.20,
    )
