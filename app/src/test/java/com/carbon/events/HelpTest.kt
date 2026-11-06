/*
 * HelpTest.kt (Aditya)
 *
 * Purpose: Unit tests for Help.kt: searching Help & support (US 01.11.01).
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 6, 2026). Prompt: "Write
 * a test file for each source file that has none." Reviewed, edited and run by Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import org.junit.Assert.*
import org.junit.Test

class HelpTest {
    @Test fun blankQuery_returnsEveryArticle() = assertEquals(HELP_ARTICLES, searchHelp(HELP_ARTICLES, "   "))

    @Test fun search_matchesEveryWordInAnyField() {
        val hits = searchHelp(HELP_ARTICLES, "pay check-in")
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.all { "pay" in "${it.question} ${it.answer}".lowercase() && "check-in" in "${it.question} ${it.answer}".lowercase() })
        assertEquals("How do I pay?", searchHelp(HELP_ARTICLES, "HOW DO I PAY").first().question)
    }

    @Test fun search_byTopicAndNoMatch() {
        assertTrue(searchHelp(HELP_ARTICLES, "owners").all { it.topic == "Owners" || "owner" in it.answer.lowercase() })
        assertTrue(searchHelp(HELP_ARTICLES, "zzzz-not-a-word").isEmpty())
    }

    @Test fun everyTopic_hasAnswers() {
        val topics = HELP_ARTICLES.map { it.topic }.toSet()
        assertEquals(setOf("Lotteries", "Bookings", "Account", "Owners"), topics)
        assertTrue(HELP_ARTICLES.all { it.question.endsWith("?") || it.question.isNotBlank() && it.answer.isNotBlank() })
    }
}
