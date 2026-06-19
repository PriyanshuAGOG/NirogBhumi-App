package in.nirogbhumi.app

import in.nirogbhumi.app.ui.screens.NirogScreens
import in.nirogbhumi.app.ui.screens.ScreenExperiences
import in.nirogbhumi.app.ui.screens.declaredDestinationRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenCatalogTest {
    @Test fun containsAllApprovedScreensExactlyOnce() {
        assertEquals(86, NirogScreens.size)
        assertEquals((1..86).toList(), NirogScreens.map { it.id })
        assertEquals(86, NirogScreens.map { it.route }.toSet().size)
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
