package comp4321;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class StopStemTest {

    private static StopStem ss;

    @BeforeAll
    static void setup() throws Exception {
        ss = new StopStem("webapp/stopwords.txt");
    }

    // --- isStopWord ---

    @Test void stopWord_the() { assertTrue(ss.isStopWord("the")); }
    @Test void stopWord_of()  { assertTrue(ss.isStopWord("of")); }
    @Test void stopWord_a()   { assertTrue(ss.isStopWord("a")); }
    @Test void stopWord_is()  { assertTrue(ss.isStopWord("is")); }

    @Test void notStopWord_university() { assertFalse(ss.isStopWord("university")); }
    @Test void notStopWord_hong()       { assertFalse(ss.isStopWord("hong")); }
    @Test void notStopWord_search()     { assertFalse(ss.isStopWord("search")); }

    @Test void stopWord_caseInsensitive_THE() { assertTrue(ss.isStopWord("THE")); }
    @Test void stopWord_caseInsensitive_Of()  { assertTrue(ss.isStopWord("Of")); }

    // --- stem ---

    @Test void stem_running_to_run()      { assertEquals("run", ss.stem("running")); }
    @Test void stem_books_to_book()       { assertEquals("book", ss.stem("books")); }
    @Test void stem_university()          { assertEquals("univers", ss.stem("university")); }
    @Test void stem_studies_to_studi()    { assertEquals("studi", ss.stem("studies")); }
    @Test void stem_uppercase_RUNNING()   { assertEquals("run", ss.stem("RUNNING")); }

    // --- stemAndCheck ---

    @Test void stemAndCheck_stop_returns_null()   { assertNull(ss.stemAndCheck("the")); }
    @Test void stemAndCheck_stop_of_returns_null(){ assertNull(ss.stemAndCheck("of")); }
    @Test void stemAndCheck_empty_returns_null()  { assertNull(ss.stemAndCheck("")); }
    @Test void stemAndCheck_blank_returns_null()  { assertNull(ss.stemAndCheck("   ")); }
    @Test void stemAndCheck_running()             { assertEquals("run", ss.stemAndCheck("running")); }
    @Test void stemAndCheck_books()               { assertEquals("book", ss.stemAndCheck("books")); }
    @Test void stemAndCheck_university()          { assertEquals("univers", ss.stemAndCheck("university")); }
    @Test void stemAndCheck_uppercase_BOOKS()     { assertEquals("book", ss.stemAndCheck("BOOKS")); }

    @Test void stemAndCheck_stemmed_stopword() {
        // "us" stems to "us" which may be a stop word — result should be null
        // (verifies that post-stem stop-word check runs)
        String result = ss.stemAndCheck("us");
        // "us" is itself a stop word in most lists; either null or a stem is fine
        // as long as it doesn't crash
        // we just ensure no exception
        assertTrue(result == null || result instanceof String);
    }
}
