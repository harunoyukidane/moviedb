package com.moviecatalogue.catalogue.domain

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

/**
 * Pins the exact wording of the person-date/credit conflict messages (V2.8-03).
 *
 * `frontend/src/lib/errors.ts` parses this prose with two `VALIDATION_REWRITES`
 * regexes to render the inline field copy, exactly as it does for
 * [TextRules.screen] (V2.7-04) - and exactly as there, nothing else binds the
 * two sides together. Rewording a message here would silently stop matching in
 * the frontend, which would then fall back to showing the raw backend string
 * under the field, with no test failing on either side.
 *
 * The matching frontend assertions live in `frontend/src/lib/errors.test.ts`
 * ("rewrites a birth-date-conflicts-credit message..."); the literals in the
 * two files must stay in step.
 */
class CreditRulesFrontendContractTest {

    private fun movie(title: String, releaseDate: LocalDate) =
        CreditRules.CreditedMovieDate(UUID.randomUUID(), title, releaseDate)

    @Test
    fun `birth-date conflict message matches the frontend's VALIDATION_REWRITES shape`() {
        assertThatThrownBy {
            CreditRules.validatePersonDatesAgainstCredits(
                birthDate = LocalDate.of(2024, 9, 3),
                deathDate = null,
                creditedMovies = listOf(movie("Old Film", LocalDate.of(2006, 11, 1))),
            )
        }.isInstanceOf(PersonDateConflictsCreditException::class.java)
            .hasMessage(
                "birthDate 2024-09-03 is after the release date of \"Old Film\" (2006-11-01). " +
                    "Check the date and try again.",
            )
    }

    @Test
    fun `death-date conflict message matches the frontend's VALIDATION_REWRITES shape`() {
        assertThatThrownBy {
            CreditRules.validatePersonDatesAgainstCredits(
                birthDate = null,
                deathDate = LocalDate.of(1953, 1, 1),
                creditedMovies = listOf(movie("The Matrix Reloaded", LocalDate.of(2003, 5, 15))),
            )
        }.isInstanceOf(PersonDateConflictsCreditException::class.java)
            .hasMessage(
                "deathDate 1953-01-01 is more than 5 years before the release date of " +
                    "\"The Matrix Reloaded\" (2003-05-15). Check the date and try again.",
            )
    }

    @Test
    fun `more than three conflicting movies are summarised, still matching the same shape`() {
        assertThatThrownBy {
            CreditRules.validatePersonDatesAgainstCredits(
                birthDate = null,
                deathDate = LocalDate.of(1953, 1, 1),
                creditedMovies = listOf(
                    movie("One", LocalDate.of(2003, 5, 15)),
                    movie("Two", LocalDate.of(2004, 5, 15)),
                    movie("Three", LocalDate.of(2005, 5, 15)),
                    movie("Four", LocalDate.of(2006, 5, 15)),
                    movie("Five", LocalDate.of(2007, 5, 15)),
                ),
            )
        }.isInstanceOf(PersonDateConflictsCreditException::class.java)
            .hasMessage(
                "deathDate 1953-01-01 is more than 5 years before the release date of " +
                    "\"One\" (2003-05-15), \"Two\" (2004-05-15), \"Three\" (2005-05-15) and 2 more. " +
                    "Check the date and try again.",
            )
    }

    /**
     * The add-credit and release-date paths deliberately produce prose the
     * frontend does *not* parse: it already reads as a sentence, so
     * `humanizeValidationMessage` passes it through unchanged. Pinned here so
     * that stays true - a reword starting with a raw field key would suddenly
     * be rewritten by a different rule.
     */
    @Test
    fun `credit-path messages are already user-facing sentences, not parsed shapes`() {
        assertThatThrownBy {
            CreditRules.validateCreditDates(
                "Jane Doe",
                LocalDate.of(2024, 1, 1),
                null,
                LocalDate.of(2006, 11, 1),
                field = "personId",
            )
        }.isInstanceOf(ValidationException::class.java)
            .hasMessage(
                "Can't save: Jane Doe was born on 2024-01-01, after this movie's release on 2006-11-01. " +
                    "Check the dates and try again.",
            )

        assertThatThrownBy {
            CreditRules.validateCreditDates(
                "Jane Doe",
                null,
                LocalDate.of(1953, 1, 1),
                LocalDate.of(2006, 11, 1),
                field = "personId",
            )
        }.isInstanceOf(ValidationException::class.java)
            .hasMessage(
                "Can't save: Jane Doe died on 1953-01-01, more than 5 years before this movie's release " +
                    "on 2006-11-01. Check the dates and try again.",
            )
    }
}
