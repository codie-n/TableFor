package com.metrolist.tablefor

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** B20: the "None" diet can't be chosen together with a real diet. */
class NoneDietTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    @Test
    fun `tapping None clears the other diets`() {
        assertEquals(listOf("None"), Diets.toggle(listOf("Vegan", "Halal"), "None"))
    }

    @Test
    fun `tapping a diet clears None`() {
        assertEquals(listOf("Vegan"), Diets.toggle(listOf("None"), "Vegan"))
    }

    @Test
    fun `real diets still combine and toggle on their own`() {
        assertEquals(listOf("Vegan", "Halal"), Diets.toggle(listOf("Vegan"), "Halal"))
        assertEquals(listOf("Halal"), Diets.toggle(listOf("Vegan", "Halal"), "Vegan"))
    }

    @Test
    fun `tapping None again unselects it`() {
        assertEquals(emptyList<String>(), Diets.toggle(listOf("None"), "None"))
    }

    @Test
    fun `a saved list with None and real diets drops None`() {
        assertEquals(listOf("Vegan", "Halal"), Diets.cleaned(listOf("None", "Vegan", "Halal")))
        assertEquals(listOf("Vegetarian"), Diets.cleaned(listOf("Vegetarian", "None")))
    }

    @Test
    fun `a saved list with only None, or none at all, is kept`() {
        assertEquals(listOf("None"), Diets.cleaned(listOf("None")))
        assertEquals(emptyList<String>(), Diets.cleaned(emptyList()))
    }

    @Test
    fun `a loaded profile with None and a diet shows only the diet, on the Match tab too`() {
        val repo = FakeTableForRepository()
        val vm = TableForViewModel(repo, TEST_CLOCK)

        repo.profileStream.emit(UserProfile.blank("uid_test").copy(name = "Kent", diets = listOf("None", "Vegan")))

        assertEquals(listOf("Vegan"), vm.profile.diets)
        assertEquals("Vegan", vm.dinnerGroup!!.members.single { it.role == "You" }.diet)
    }
}
