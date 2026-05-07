package comp4321;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class SearchEngine {
    private Indexer indexer;
    private StopStem stopStem;

    public SearchEngine(Indexer indexer, StopStem stopStem) {
        this.indexer = indexer;
        this.stopStem = stopStem;
    }

    /**
     * Parses the raw query string into two parts:
     *   phrases  - list of token arrays, one per "quoted phrase"
     *   freeTerms - stemmed free (unquoted) keywords with their TF
     */
    private static class ParsedQuery {
        List<String[]> phrases = new ArrayList<>();   // each entry = stemmed tokens of one quoted phrase
        Map<String, Integer> freeTermTfs = new HashMap<>();
    }

    private ParsedQuery parseQuery(String query) {
        ParsedQuery pq = new ParsedQuery();
        // Normalise curly/smart quotes (U+201C, U+201D, U+2018, U+2019) to ASCII quotes
        query = query.replace('“', '"').replace('”', '"')
                     .replace('‘', '\'').replace('’', '\'');
        // Extract quoted phrases first, collect remaining text for free terms
        Matcher m = Pattern.compile("\"([^\"]*)\"").matcher(query);
        StringBuffer remainder = new StringBuffer();
        while (m.find()) {
            String phraseText = m.group(1);
            String[] words = phraseText.trim().split("[^a-zA-Z0-9]+");
            List<String> stemmed = new ArrayList<>();
            for (String w : words) {
                if (!w.isEmpty()) {
                    String s = stopStem.stemAndCheck(w);
                    if (s != null) stemmed.add(s);
                }
            }
            if (!stemmed.isEmpty()) {
                pq.phrases.add(stemmed.toArray(new String[0]));
            }
            m.appendReplacement(remainder, " ");
        }
        m.appendTail(remainder);

        // Free keywords from the non-quoted portion
        String[] freeRaw = remainder.toString().split("[^a-zA-Z0-9]+");
        for (String rt : freeRaw) {
            String stem = stopStem.stemAndCheck(rt);
            if (stem != null) {
                pq.freeTermTfs.put(stem, pq.freeTermTfs.getOrDefault(stem, 0) + 1);
            }
        }
        return pq;
    }

    /**
     * Returns page IDs where all phrase tokens appear consecutively (positionally adjacent)
     * in the given inverted index (body or title).
     */
    private Set<Integer> phraseMatchInIndex(String[] stemmedPhrase, boolean useTitle) throws Exception {
        if (stemmedPhrase.length == 0) return new HashSet<>();

        Integer firstId = indexer.getWordId(stemmedPhrase[0]);
        if (firstId == null) return new HashSet<>();
        Map<Integer, ArrayList<Integer>> firstPostings = useTitle
            ? indexer.getTitlePostings(firstId)
            : indexer.getBodyPostings(firstId);
        if (firstPostings == null || firstPostings.isEmpty()) return new HashSet<>();

        // candidates: pageId -> set of valid starting positions for stemmedPhrase[0]
        Map<Integer, Set<Integer>> candidates = new HashMap<>();
        for (Map.Entry<Integer, ArrayList<Integer>> e : firstPostings.entrySet()) {
            candidates.put(e.getKey(), new HashSet<>(e.getValue()));
        }

        for (int i = 1; i < stemmedPhrase.length; i++) {
            Integer wid = indexer.getWordId(stemmedPhrase[i]);
            if (wid == null) return new HashSet<>();
            Map<Integer, ArrayList<Integer>> postings = useTitle
                ? indexer.getTitlePostings(wid)
                : indexer.getBodyPostings(wid);
            if (postings == null || postings.isEmpty()) return new HashSet<>();

            Map<Integer, Set<Integer>> nextCandidates = new HashMap<>();
            for (Map.Entry<Integer, Set<Integer>> ce : candidates.entrySet()) {
                int pid = ce.getKey();
                ArrayList<Integer> termPositions = postings.get(pid);
                if (termPositions == null) continue;
                Set<Integer> termPosSet = new HashSet<>(termPositions);

                Set<Integer> validStarts = new HashSet<>();
                for (int startPos : ce.getValue()) {
                    if (termPosSet.contains(startPos + i)) validStarts.add(startPos);
                }
                if (!validStarts.isEmpty()) nextCandidates.put(pid, validStarts);
            }
            candidates = nextCandidates;
            if (candidates.isEmpty()) return new HashSet<>();
        }
        return candidates.keySet();
    }

    /**
     * Returns page IDs where all phrase tokens appear consecutively in body OR title index.
     */
    private Set<Integer> phraseMatchPages(String[] stemmedPhrase) throws Exception {
        Set<Integer> matches = new HashSet<>(phraseMatchInIndex(stemmedPhrase, false)); // body
        matches.addAll(phraseMatchInIndex(stemmedPhrase, true));                        // title
        return matches;
    }

    public List<Result> search(String query) throws Exception {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // 1. Parse the query into phrases and free terms
        ParsedQuery pq = parseQuery(query);

        // Collect all stemmed terms for TF-IDF (phrases contribute their tokens as free terms too)
        Map<String, Integer> queryTfs = new HashMap<>(pq.freeTermTfs);
        for (String[] phrase : pq.phrases) {
            for (String t : phrase) {
                queryTfs.put(t, queryTfs.getOrDefault(t, 0) + 1);
            }
        }

        // If there are only phrases and all phrase tokens are stop-words, nothing to search
        if (queryTfs.isEmpty() && pq.phrases.isEmpty()) return new ArrayList<>();

        // If all terms were stop-words but phrases exist, still run phrase filter
        // Determine phrase-match page filter (intersection across all phrases)
        Set<Integer> phraseFilter = null; // null means "no phrase filter active"
        if (!pq.phrases.isEmpty()) {
            phraseFilter = new HashSet<>();
            boolean first = true;
            for (String[] phrase : pq.phrases) {
                Set<Integer> matchPages = phraseMatchPages(phrase);
                if (first) {
                    phraseFilter.addAll(matchPages);
                    first = false;
                } else {
                    phraseFilter.retainAll(matchPages);
                }
            }
            if (phraseFilter.isEmpty()) return new ArrayList<>();
        }

        // Pure phrase query with no scorable terms: return matched pages ranked by title
        if (queryTfs.isEmpty()) {
            List<Result> results = new ArrayList<>();
            for (int pid : phraseFilter) {
                results.add(new Result(pid, 1.0, indexer));
            }
            results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
            return results.size() > 50 ? results.subList(0, 50) : results;
        }

        int totalDocs = indexer.getPageCount();
        if (totalDocs == 0) totalDocs = 1;

        // 2. Calculate query vector weights
        Map<String, Double> queryWeights = new HashMap<>();
        double queryLengthSq = 0.0;
        Map<String, Double> idfs = new HashMap<>();

        for (String term : queryTfs.keySet()) {
            Integer wid = indexer.getWordId(term);
            int df = 0;
            if (wid != null) {
                Map<Integer, ArrayList<Integer>> postings = indexer.getBodyPostings(wid);
                if (postings != null) {
                    df = postings.size();
                }
            }
            double idf = (df == 0) ? 0.0 : Math.log((double) totalDocs / df) / Math.log(2);
            idfs.put(term, idf);

            double tf = queryTfs.get(term);
            double weight = tf * idf;
            queryWeights.put(term, weight);
            queryLengthSq += weight * weight;
        }
        double queryLen = Math.sqrt(queryLengthSq);

        // 3. Traverse documents and calculate cosine similarity with title boost
        Map<Integer, Double> docScores = new HashMap<>();

        for (String term : queryTfs.keySet()) {
            Integer wid = indexer.getWordId(term);
            if (wid == null) continue;

            double idf = idfs.get(term);
            double qWeight = queryWeights.get(term);

            // Handle Body Matching
            Map<Integer, ArrayList<Integer>> postings = indexer.getBodyPostings(wid);
            if (postings != null) {
                for (Map.Entry<Integer, ArrayList<Integer>> entry : postings.entrySet()) {
                    int pid = entry.getKey();
                    HashMap<Integer, Integer> freqs = indexer.getBodyWordFreqs(pid);
                    int tf = (freqs != null) ? freqs.getOrDefault(wid, 0) : 0;
                    double dWeight = tf * idf;
                    docScores.put(pid, docScores.getOrDefault(pid, 0.0) + (qWeight * dWeight));
                }
            }

            // Handle Title Matching and Weighting (title terms get 5x boost)
            Map<Integer, ArrayList<Integer>> titlePostings = indexer.getTitlePostings(wid);
            if (titlePostings != null) {
                for (int pid : titlePostings.keySet()) {
                    HashMap<Integer, Integer> titleFreqs = indexer.getTitleWordFreqs(pid);
                    int tf = (titleFreqs != null) ? titleFreqs.getOrDefault(wid, 0) : 0;
                    double dWeight = tf * idf * 5.0;
                    docScores.put(pid, docScores.getOrDefault(pid, 0.0) + (qWeight * dWeight));
                }
            }
        }

        // 4. Apply phrase filter: keep only pages that satisfy all quoted phrases
        if (phraseFilter != null) {
            docScores.keySet().retainAll(phraseFilter);
            if (docScores.isEmpty()) return new ArrayList<>();
        }

        // 5. Normalise by document vector length (cosine similarity)
        List<Result> results = new ArrayList<>();
        for (Map.Entry<Integer, Double> entry : docScores.entrySet()) {
            int pid = entry.getKey();
            double dotProduct = entry.getValue();

            double docLengthSq = 0.0;
            HashMap<Integer, Integer> freqs = indexer.getBodyWordFreqs(pid);
            if (freqs != null) {
                for (Map.Entry<Integer, Integer> fEntry : freqs.entrySet()) {
                    int wId = fEntry.getKey();
                    String wStem = indexer.getWord(wId);
                    if (wStem != null) {
                        Map<Integer, ArrayList<Integer>> p = indexer.getBodyPostings(wId);
                        int df = (p != null) ? p.size() : 1;
                        double idf = Math.log((double) totalDocs / df) / Math.log(2);
                        double w = fEntry.getValue() * idf;
                        docLengthSq += w * w;
                    }
                }
            }
            double docLen = Math.sqrt(docLengthSq);

            double score = 0.0;
            if (queryLen > 0 && docLen > 0) {
                score = dotProduct / (queryLen * docLen);
            } else {
                score = dotProduct;
            }

            results.add(new Result(pid, score, indexer));
        }

        // 6. Sort descending by score
        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        // 7. Return top 50
        return results.size() > 50 ? results.subList(0, 50) : results;
    }

    // Search result encapsulation class (using standard Getters/Setters, perfectly compatible with JSP EL expressions)
    public static class Result implements Serializable {
        private int pid;
        private double score;
        private String title;
        private String url;
        private String lastMod;
        private long size;
        private List<String> topWords;
        private List<String> parentUrls;
        private List<String> childUrls;

        public Result(int pid, double score, Indexer indexer) throws Exception {
            this.pid = pid;
            this.score = score;
            this.title = indexer.getPageTitle(pid);
            this.url = indexer.getUrl(pid);
            this.lastMod = indexer.getPageLastMod(pid);
            this.size = indexer.getPageSize(pid);
            this.topWords = new ArrayList<>();
            this.parentUrls = new ArrayList<>();
            this.childUrls = new ArrayList<>();

            // Extract the top 5 most frequently used keywords on this page
            HashMap<Integer, Integer> freqs = indexer.getBodyWordFreqs(pid);
            if (freqs != null && !freqs.isEmpty()) {
                List<Map.Entry<Integer, Integer>> sortedFreqs = new ArrayList<>(freqs.entrySet());
                sortedFreqs.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));
                for (int i = 0; i < Math.min(5, sortedFreqs.size()); i++) {
                    String word = indexer.getWord(sortedFreqs.get(i).getKey());
                    int count = sortedFreqs.get(i).getValue();
                    if (word != null) {
                        this.topWords.add(word + " " + count);
                    }
                }
            }

            // Extract the parent link URL
            Set<Integer> parents = indexer.getParentIds(pid);
            if (parents != null) {
                for (int parentId : parents) {
                    String pUrl = indexer.getUrl(parentId);
                    if (pUrl != null) this.parentUrls.add(pUrl);
                }
            }

            // Extract sub-link URL
            Set<Integer> children = indexer.getChildIds(pid);
            if (children != null) {
                for (int childId : children) {
                    String cUrl = indexer.getUrl(childId);
                    if (cUrl != null) this.childUrls.add(cUrl);
                }
            }
        }

        // Getter function
        public int getPid() { return pid; }
        public double getScore() { return score; }
        public String getTitle() { return (title == null || title.isEmpty()) ? "No Title" : title; }
        public String getUrl() { return url; }
        public String getLastMod() { return (lastMod == null) ? "N/A" : lastMod; }
        public long getSize() { return size; }
        public List<String> getTopWords() { return topWords; }
        public List<String> getParentUrls() { return parentUrls; }
        public List<String> getChildUrls() { return childUrls; }
    }
}
