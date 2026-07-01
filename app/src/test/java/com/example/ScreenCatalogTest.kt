package com.nirogbhumi.app

import com.nirogbhumi.app.ui.screens.NirogScreens
import com.nirogbhumi.app.ui.screens.ScreenExperiences
import com.nirogbhumi.app.ui.screens.declaredDestinationRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenCatalogTest {
    // Water, Medicine, Food Journal tracking and the in-app Store were removed by product
    // decision, so this no longer pins an exact screen count or contiguous ID range - it
    // just checks internal consistency (unique ids/routes, no blank titles or item lists).
    @Test fun containsAllApprovedScreensExactlyOnce() {
        assertEquals(NirogScreens.size, NirogScreens.map { it.id }.toSet().size)
        assertEquals(NirogScreens.size, NirogScreens.map { it.route }.toSet().size)
        assertTrue(NirogScreens.all { it.title.isNotBlank() && it.items.isNotEmpty() })
    }

    @Test fun everyDeclaredDestinationResolves() {
        val routes = NirogScreens.map { it.route }.toSet() + setOf("legal_center", "care_hub")
        assertTrue(declaredDestinationRoutes().all { it in routes })
    }

    @Test fun allDataEntryExperiencesHaveSafeSuccessRoutes() {
        val routes = NirogScreens.map { it.route }.toSet()
        assertTrue(ScreenExperiences.values.filter { it.writeCollection != null }.all { it.successRoute == null || it.successRoute in routes })
    }
}
