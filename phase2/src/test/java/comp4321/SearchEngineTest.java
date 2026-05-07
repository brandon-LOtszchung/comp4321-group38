package comp4321;

import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for SearchEngine against the real spider_db (299 pages).
 *
 * Demo-day query set (TA3 checklist):
 *   - Single keyword
 *   - Multi-keyword
 *   - Phrase in double quotes
 *   - Zero-results query
 *   - Title-boost demo
 *   - Stop-word-only query
 *   - Query-side stemming
 *   - Edge cases: smart quotes, empty quotes, unclosed quotes, single quotes,
 *     uppercase, crash regression (single-word phrase in title+body index)
 */
@TestMethodOrder(MethodOrderer.DisplayName.class)
public class SearchEngineTest {

    private static SearchEngine engine;

    @BeforeAll
    static void setup() throws Exception {
        Indexer indexer = new Indexer("webapp/spider_db");
        StopStem stopStem = new StopStem("webapp/stopwords.txt");
        engine = new SearchEngine(indexer, stopStem);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private List<SearchEngine.Result> search(String q) throws Exception {
        return engine.search(q);
    }

    private int count(String q) throws Exception {
        return search(q).size();
    }

    // -----------------------------------------------------------------------
    // TA3 valid-set queries (Station 3 checklist)
    // -----------------------------------------------------------------------

    @Test @DisplayName("TA3-1: single keyword 'book'")
    void singleKeyword_book() throws Exception {
        assertTrue(count("book") > 0, "Expected results for 'book'");
    }

    @Test @DisplayName("TA3-2: multi-keyword 'hong kong university'")
    void multiKeyword_hongKongUniversity() throws Exception {
        assertTrue(count("hong kong university") > 0);
    }

    @Test @DisplayName("TA3-3: phrase query \"hong kong\"")
    void phrase_hongKong() throws Exception {
        int phraseCount = count("\"hong kong\"");
        int freeCount   = count("hong kong");
        assertTrue(phraseCount > 0, "Phrase search must return results");
        assertTrue(phraseCount <= freeCount, "Phrase search must be <= free keyword search");
    }

    @Test @DisplayName("TA3-4: zero-results query 'xyzzy quux'")
    void zeroResults_nonsense() throws Exception {
        assertEquals(0, count("xyzzy quux"));
    }

    @Test @DisplayName("TA3-5: title-boost — 'news' ranks page with 'News' in title first")
    void titleBoost_news() throws Exception {
        List<SearchEngine.Result> results = search("news");
        assertFalse(results.isEmpty(), "Expected results for 'news'");
        String topTitle = results.get(0).getTitle().toLowerCase();
        assertTrue(topTitle.contains("news"), "Top result title should contain 'news'");
    }

    @Test @DisplayName("TA3-6: stop-word-only query 'the of a' → 0 results")
    void stopWordsOnly() throws Exception {
        assertEquals(0, count("the of a"));
    }

    @Test @DisplayName("TA3-7: stemming — 'books' same result count as 'book'")
    void stemming_booksEqualsBook() throws Exception {
        assertEquals(count("book"), count("books"));
    }

    @Test @DisplayName("TA3-8: stemming — 'running' same as 'run'")
    void stemming_runningEqualsRun() throws Exception {
        assertEquals(count("run"), count("running"));
    }

    @Test @DisplayName("TA3-9: stemming — 'studies' same as 'study'")
    void stemming_studiesEqualsStudy() throws Exception {
        assertEquals(count("study"), count("studies"));
    }

    // -----------------------------------------------------------------------
    // Phrase search correctness
    // -----------------------------------------------------------------------

    @Test @DisplayName("Phrase: single word \"book\" — no crash (regression #1)")
    void phrase_singleWord_book() throws Exception {
        // Previously crashed with UnsupportedOperationException when word
        // appears in both body and title indices.
        assertDoesNotThrow(() -> search("\"book\""));
        assertEquals(count("book"), count("\"book\""),
            "Single-word phrase should match same docs as keyword");
    }

    @Test @DisplayName("Phrase: single word \"university\"")
    void phrase_singleWord_university() throws Exception {
        assertDoesNotThrow(() -> search("\"university\""));
        assertTrue(count("\"university\"") > 0);
    }

    @Test @DisplayName("Phrase: single word \"news\" — no crash (regression #2)")
    void phrase_singleWord_news() throws Exception {
        assertDoesNotThrow(() -> search("\"news\""));
        assertTrue(count("\"news\"") > 0);
    }

    @Test @DisplayName("Phrase: \"running\" and \"run\" same result count (phrase stemming)")
    void phrase_stemming() throws Exception {
        assertEquals(count("\"run\""), count("\"running\""));
    }

    @Test @DisplayName("Phrase: \"HONG KONG\" uppercase same as lowercase")
    void phrase_uppercase() throws Exception {
        assertEquals(count("\"hong kong\""), count("\"HONG KONG\""));
    }

    @Test @DisplayName("Phrase: two phrases AND — \"hong kong\" \"university\"")
    void phrase_twoPhrasesAnd() throws Exception {
        int both = count("\"hong kong\" \"university\"");
        int hk   = count("\"hong kong\"");
        assertTrue(both <= hk, "AND of phrases must be a subset");
    }

    @Test @DisplayName("Phrase: results are subset of keyword results")
    void phrase_subsetOfKeyword() throws Exception {
        assertTrue(count("\"hong kong\"") <= count("hong kong"));
    }

    // -----------------------------------------------------------------------
    // Empty / null / malformed input
    // -----------------------------------------------------------------------

    @Test @DisplayName("Edge: null query → 0 results, no crash")
    void nullQuery() throws Exception {
        assertDoesNotThrow(() -> assertEquals(0, count(null)));
    }

    @Test @DisplayName("Edge: empty string → 0 results")
    void emptyQuery() throws Exception {
        assertEquals(0, count(""));
    }

    @Test @DisplayName("Edge: whitespace only → 0 results")
    void whitespaceQuery() throws Exception {
        assertEquals(0, count("   "));
    }

    @Test @DisplayName("Edge: \"\" empty quotes → 0 results")
    void emptyQuotes() throws Exception {
        assertEquals(0, count("\"\""));
    }

    @Test @DisplayName("Edge: \"  \" whitespace-only phrase → 0 results")
    void whitespacePhrase() throws Exception {
        assertEquals(0, count("\"  \""));
    }

    @Test @DisplayName("Edge: \"\" with keyword — empty quote ignored, keyword searched")
    void emptyQuotePlusKeyword() throws Exception {
        int withEmpty = count("\"\" hong");
        int plain     = count("hong");
        assertEquals(plain, withEmpty, "Empty phrase quote should be ignored");
    }

    @Test @DisplayName("Edge: \"hong kong\" \"\" — empty quote ignored")
    void phraseWithEmptyQuote() throws Exception {
        assertEquals(count("\"hong kong\""), count("\"hong kong\" \"\""));
    }

    @Test @DisplayName("Edge: unclosed quote 'hong kong treated as free text")
    void unclosedQuote() throws Exception {
        // No closing quote → regex doesn't match → both words are free terms
        int unclosed  = count("\"hong kong");
        int freeTerms = count("hong kong");
        assertEquals(freeTerms, unclosed);
    }

    // -----------------------------------------------------------------------
    // Smart / curly quotes (macOS auto-correction)
    // -----------------------------------------------------------------------

    @Test @DisplayName("Smart quotes “hong kong” same as straight quotes")
    void smartQuotes_phrase() throws Exception {
        assertEquals(count("\"hong kong\""), count("“hong kong”"));
    }

    @Test @DisplayName("Smart quotes “book” no crash + same count")
    void smartQuotes_singleWord() throws Exception {
        assertDoesNotThrow(() -> {});
        assertEquals(count("\"book\""), count("“book”"));
    }

    // -----------------------------------------------------------------------
    // Result structure / UI fields (TA3 requires all fields present)
    // -----------------------------------------------------------------------

    @Test @DisplayName("Result fields: score, title, url, lastMod, size all non-null")
    void resultFields_allPresent() throws Exception {
        List<SearchEngine.Result> results = search("hong kong");
        assertFalse(results.isEmpty());
        SearchEngine.Result r = results.get(0);
        assertNotNull(r.getTitle());
        assertNotNull(r.getUrl());
        assertNotNull(r.getLastMod());
        assertTrue(r.getSize() > 0);
        assertTrue(r.getScore() > 0);
    }

    @Test @DisplayName("Result fields: top-5 keywords present")
    void resultFields_keywords() throws Exception {
        List<SearchEngine.Result> results = search("hong kong");
        assertFalse(results.isEmpty());
        assertFalse(results.get(0).getTopWords().isEmpty(), "Top keywords must not be empty");
    }

    @Test @DisplayName("Result fields: child links present for seed page")
    void resultFields_childLinks() throws Exception {
        List<SearchEngine.Result> results = search("hong kong");
        // At least one result should have child links
        boolean anyChild = results.stream().anyMatch(r -> !r.getChildUrls().isEmpty());
        assertTrue(anyChild, "At least one result should have child links");
    }

    // -----------------------------------------------------------------------
    // Ranking / top-K
    // -----------------------------------------------------------------------

    @Test @DisplayName("Ranking: at most 50 results returned")
    void maxFiftyResults() throws Exception {
        assertTrue(count("hong kong") <= 50);
        assertTrue(count("news") <= 50);
    }

    @Test @DisplayName("Ranking: results sorted descending by score")
    void sortedByScoreDescending() throws Exception {
        List<SearchEngine.Result> results = search("hong kong university");
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i - 1).getScore() >= results.get(i).getScore(),
                "Results must be in descending score order");
        }
    }

    @Test @DisplayName("Ranking: phrase search stricter than keyword search")
    void phraseStricterThanKeyword() throws Exception {
        assertTrue(count("\"hong kong\"") <= count("hong kong"));
    }

    // -----------------------------------------------------------------------
    // Weird / adversarial terms (TA3 hidden queries)
    // -----------------------------------------------------------------------

    @Test @DisplayName("Weird: digits only '123' → no crash")
    void digitsOnly() throws Exception {
        assertDoesNotThrow(() -> search("123"));
    }

    @Test @DisplayName("Weird: special chars '!@#$%' → no crash, 0 results")
    void specialCharsOnly() throws Exception {
        assertDoesNotThrow(() -> search("!@#$%"));
        assertEquals(0, count("!@#$%"));
    }

    @Test @DisplayName("Weird: very long query → no crash")
    void veryLongQuery() throws Exception {
        String longQ = "hong ".repeat(50).trim();
        assertDoesNotThrow(() -> search(longQ));
    }

    @Test @DisplayName("Weird: mixed stop-words and real term 'the book of' → same as 'book'")
    void mixedStopAndReal() throws Exception {
        assertEquals(count("book"), count("the book of"));
    }

    @Test @DisplayName("Weird: single character 'a' is stop word → 0 results")
    void singleCharStopWord() throws Exception {
        assertEquals(0, count("a"));
    }

    @Test @DisplayName("Weird: phrase of stop-words \"the of\" → 0 results")
    void phraseOfStopWords() throws Exception {
        assertEquals(0, count("\"the of\""));
    }

    @Test @DisplayName("Weird: repeated term 'book book book' → same as 'book'")
    void repeatedTerm() throws Exception {
        assertEquals(count("book"), count("book book book"));
    }

    @Test @DisplayName("Weird: single apostrophe ' in query → no crash")
    void singleApostrophe() throws Exception {
        assertDoesNotThrow(() -> search("'"));
    }

    @Test @DisplayName("Weird: only double-quote char → no crash, 0 results")
    void singleQuoteChar() throws Exception {
        assertDoesNotThrow(() -> search("\""));
        assertEquals(0, count("\""));
    }

    @Test @DisplayName("Weird: tab and newline in query → no crash")
    void tabNewlineQuery() throws Exception {
        assertDoesNotThrow(() -> search("hong\tkong\nuniversity"));
    }

    @Test @DisplayName("Weird: phrase with number \"page 1\" → no crash")
    void phraseWithNumber() throws Exception {
        assertDoesNotThrow(() -> search("\"page 1\""));
    }

    @Test @DisplayName("Weird: query of all spaces → 0 results")
    void allSpaces() throws Exception {
        assertEquals(0, count("      "));
    }
}
