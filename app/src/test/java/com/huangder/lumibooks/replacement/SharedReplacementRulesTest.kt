package com.huangder.lumibooks.replacement

import android.app.Application
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SharedReplacementRulesTest {
    private val rename = ReplacementRule("rename", name = "改名", find = "小明", replacement = "阿明", regex = true)
    private val removeAds = ReplacementRule("ads", find = "广告", enabled = false)

    @Test fun newBookReusesDefinitionsButStartsWithAllSwitchesOff() {
        val preferences = mutablePreferencesOf()
        SharedReplacementRules.update(preferences, "a") { it.copy(enabled = true).upsert(rename) }

        val other = SharedReplacementRules.read(preferences, "b")
        assertFalse(other.enabled)
        assertEquals(listOf(rename.copy(enabled = false)), other.rules)
        SharedReplacementRules.update(preferences, "b") { it.copy(enabled = true) }
        assertFalse(SharedReplacementRules.read(preferences, "b").rules.single().enabled)
        assertTrue(SharedReplacementRules.read(preferences, "a").rules.single().enabled)
    }

    @Test fun selectionAndMasterSwitchAreIndependentButEditsAreShared() {
        val preferences = mutablePreferencesOf()
        SharedReplacementRules.update(preferences, "a") { it.copy(enabled = true).upsert(rename) }
        SharedReplacementRules.update(preferences, "b") { it.upsert(it.rules.single().copy(replacement = "新名字")) }
        val first = SharedReplacementRules.read(preferences, "a")
        assertTrue(first.enabled)
        assertTrue(first.rules.single().enabled)
        assertEquals("新名字", first.rules.single().replacement)
        val second = SharedReplacementRules.read(preferences, "b")
        assertFalse(second.enabled)
        assertFalse(second.rules.single().enabled)

        SharedReplacementRules.update(preferences, "b") { it.copy(enabled = true).upsert(it.rules.single().copy(enabled = true)) }
        SharedReplacementRules.update(preferences, "a") { it.copy(enabled = false).upsert(it.rules.single().copy(enabled = false)) }
        assertTrue(SharedReplacementRules.read(preferences, "b").enabled)
        assertTrue(SharedReplacementRules.read(preferences, "b").rules.single().enabled)
    }

    @Test fun migrationFindsAllLegacyBooksAndKeepsTheirSelectionsAndOrder() {
        val first = BookReplacementRules(true, listOf(rename, removeAds))
        val second = BookReplacementRules(false, listOf(removeAds.copy(enabled = true), rename.copy(enabled = false)))
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("local_replacement_rules_a") to first.encode(),
            stringPreferencesKey("local_replacement_rules_b") to second.encode()
        )
        SharedReplacementRules.migrate(preferences)
        assertEquals(first, SharedReplacementRules.read(preferences, "a"))
        assertEquals(second, SharedReplacementRules.read(preferences, "b"))
        assertEquals(2, SharedReplacementRules.read(preferences, "new").rules.size)
        assertTrue(SharedReplacementRules.read(preferences, "new").rules.none { it.enabled })
        assertFalse(SharedReplacementRules.read(preferences, "new").enabled)
        assertEquals(first.encode(), preferences[stringPreferencesKey("local_replacement_rules_a")])
    }

    @Test fun migrationPreservesDifferentRulesWithTheSameLegacyId() {
        val different = rename.copy(find = "小红", replacement = "阿红")
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("local_replacement_rules_a") to BookReplacementRules(true, listOf(rename)).encode(),
            stringPreferencesKey("local_replacement_rules_b") to BookReplacementRules(true, listOf(different)).encode()
        )
        SharedReplacementRules.migrate(preferences)
        val a = SharedReplacementRules.read(preferences, "a")
        val b = SharedReplacementRules.read(preferences, "b")
        assertEquals(2, a.rules.map { it.id }.distinct().size)
        assertEquals("小明", a.rules.single { it.enabled }.find)
        assertEquals("小红", b.rules.single { it.enabled }.find)
        assertEquals("阿明", ReplacementProgram(a.rules).apply("小明").display)
        assertEquals("阿红", ReplacementProgram(b.rules).apply("小红").display)
    }

    @Test fun repeatedIdenticalStagesInOneLegacyBookAreNotCollapsed() {
        val stage = ReplacementRule("one", find = "aa", replacement = "a")
        val stages = listOf(stage, stage.copy(id = "two"))
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("local_replacement_rules_a") to BookReplacementRules(true, stages).encode()
        )
        SharedReplacementRules.migrate(preferences)
        val migrated = SharedReplacementRules.read(preferences, "a")
        assertEquals(stages, migrated.rules)
        assertEquals("a", ReplacementProgram(migrated.rules).apply("aaaa").display)
    }

    @Test fun sharedDeletionDoesNotResurrectOnMigrationAndNewRulesStayOffElsewhere() {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("local_replacement_rules_a") to BookReplacementRules(true, listOf(rename)).encode()
        )
        SharedReplacementRules.migrate(preferences)
        SharedReplacementRules.update(preferences, "b") { it.copy(enabled = true).upsert(it.rules.single().copy(enabled = true)) }
        SharedReplacementRules.update(preferences, "a") { it.copy(rules = emptyList()) }
        SharedReplacementRules.migrate(preferences)
        assertTrue(SharedReplacementRules.read(preferences, "b").rules.isEmpty())
        assertTrue(SharedReplacementRules.read(preferences, "b").enabled)
        // Reusing even the old ID must not restore another book's deleted selection.
        SharedReplacementRules.update(preferences, "a") { it.upsert(rename) }
        assertFalse(SharedReplacementRules.read(preferences, "b").rules.single().enabled)
    }

    @Test fun reorderingAndAddingRulesDoNotChangeAnotherBooksActivePipeline() {
        val preferences = mutablePreferencesOf()
        SharedReplacementRules.update(preferences, "a") { it.copy(enabled = true, rules = listOf(rename, removeAds.copy(enabled = true))) }
        SharedReplacementRules.update(preferences, "b") { it.copy(enabled = true, rules = it.rules.map { rule -> rule.copy(enabled = true) }) }
        val original = SharedReplacementRules.read(preferences, "a")
        SharedReplacementRules.update(preferences, "b") { it.move("ads", -1) }
        assertEquals(original, SharedReplacementRules.read(preferences, "a"))
        assertEquals(listOf("ads", "rename"), SharedReplacementRules.read(preferences, "b").rules.map { it.id })
        SharedReplacementRules.update(preferences, "b") { it.upsert(ReplacementRule("extra", find = "提示")) }
        val after = SharedReplacementRules.read(preferences, "a")
        assertEquals(original.rules, after.rules.filter { it.enabled })
        assertFalse(after.rules.single { it.id == "extra" }.enabled)
    }
}
