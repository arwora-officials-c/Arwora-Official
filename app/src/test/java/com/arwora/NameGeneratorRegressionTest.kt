package com.arwora

import com.arwora.utils.CountryData
import com.arwora.utils.ModernGenerators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameGeneratorRegressionTest {

    @Test
    fun bangladeshFullName_withMiddle_neverProducesDoubleSurnames() {
        val genders = listOf("Male", "Female", "Unisex")
        for (gender in genders) {
            val surnames = CountryData.getLastNames("Bangladesh", gender).map { it.lowercase() }.toSet()
            val results = ModernGenerators.generateName(
                mode = "Full Name",
                gender = gender,
                country = "Bangladesh",
                style = "Modern",
                lastNameCategory = "Random",
                includeMiddle = true,
                customFirst = "",
                customMiddle = "",
                customLast = "",
                count = 200,
                lengthPref = "Medium",
                initialsOnly = false,
                excludeDuplicates = true,
                noRepeatSession = false
            )
            assertTrue("Expected non-empty results for $gender", results.isNotEmpty())
            for (result in results) {
                val parts = result.name.trim().split(Regex("\\s+"))
                assertEquals(
                    "Expected [First] [Middle] [Surname] for '${result.name}' ($gender)",
                    3,
                    parts.size
                )
                val first = parts[0].lowercase()
                val middle = parts[1].lowercase()
                val last = parts[2].lowercase()

                assertFalse("Middle name '$middle' must not be a Bangladesh surname in '${result.name}'", middle in surnames)
                assertTrue("Last name '$last' must be from Bangladesh surname pool in '${result.name}'", last in surnames)
                assertFalse("First and middle must differ in '${result.name}'", first == middle)
                assertFalse("Middle and last must differ in '${result.name}'", middle == last)
                assertFalse("First and last must differ in '${result.name}'", first == last)
            }
        }
    }

    @Test
    fun bangladeshFullName_withoutMiddle_producesFirstAndSingleSurname() {
        for (gender in listOf("Male", "Female", "Unisex")) {
            val surnames = CountryData.getLastNames("Bangladesh", gender).map { it.lowercase() }.toSet()
            val results = ModernGenerators.generateName(
                mode = "Full Name",
                gender = gender,
                country = "Bangladesh",
                style = "Modern",
                lastNameCategory = "Random",
                includeMiddle = false,
                customFirst = "",
                customMiddle = "",
                customLast = "",
                count = 100,
                lengthPref = "Medium",
                initialsOnly = false,
                excludeDuplicates = true,
                noRepeatSession = false
            )
            assertTrue(results.isNotEmpty())
            for (result in results) {
                val parts = result.name.trim().split(Regex("\\s+"))
                assertEquals("Expected [First] [Surname] for '${result.name}'", 2, parts.size)
                assertTrue("Last token '${parts[1]}' must be a valid surname", parts[1].lowercase() in surnames)
                assertFalse("First and last must differ in '${result.name}'", parts[0].equals(parts[1], ignoreCase = true))
            }
        }
    }

    @Test
    fun allCountries_middleNamesNeverOverlapSurnames() {
        for (country in CountryData.countries) {
            for (gender in listOf("Male", "Female", "Unisex")) {
                val surnames = CountryData.getLastNames(country, gender).map { it.lowercase() }.toSet()
                val middles = CountryData.getMiddleNames(country, gender)
                assertTrue("Middle pool empty for $country/$gender", middles.isNotEmpty())
                for (mid in middles) {
                    assertFalse(
                        "Country $country ($gender) has surname '$mid' in middle pool",
                        mid.lowercase() in surnames
                    )
                }
            }
        }
    }

    @Test
    fun surnameAndFirstMiddleModes_workCorrectly() {
        val bdSurnames = CountryData.getLastNames("Bangladesh", "Male").map { it.lowercase() }.toSet()
        val surnameResults = ModernGenerators.generateName(
            mode = "Surname",
            gender = "Male",
            country = "Bangladesh",
            style = "Modern",
            lastNameCategory = "Random",
            includeMiddle = false,
            customFirst = "",
            customMiddle = "",
            customLast = "",
            count = 15,
            lengthPref = "Medium",
            initialsOnly = false,
            excludeDuplicates = true,
            noRepeatSession = false
        )
        assertEquals(15, surnameResults.size)
        assertTrue(surnameResults.all { it.name.lowercase() in bdSurnames })

        val firstMiddleResults = ModernGenerators.generateName(
            mode = "First + Middle",
            gender = "Male",
            country = "Bangladesh",
            style = "Modern",
            lastNameCategory = "Random",
            includeMiddle = true,
            customFirst = "",
            customMiddle = "",
            customLast = "",
            count = 15,
            lengthPref = "Medium",
            initialsOnly = false,
            excludeDuplicates = true,
            noRepeatSession = false
        )
        assertEquals(15, firstMiddleResults.size)
        for (res in firstMiddleResults) {
            val parts = res.name.split(Regex("\\s+"))
            assertEquals("Expected 2 parts for First + Middle: '${res.name}'", 2, parts.size)
            assertFalse("Middle '${parts[1]}' must not be a surname", parts[1].lowercase() in bdSurnames)
        }
    }
}
