package comp4321;
import java.io.*;
import java.util.*;
import jdbm.RecordManager;
import jdbm.RecordManagerFactory;
import jdbm.htree.HTree;
import jdbm.helper.FastIterator;

public class Indexer {
    private RecordManager recman;

    // URL <-> pageID mappings
    private HTree urlToPageId;   // String -> Integer
    private HTree pageIdToUrl;   // Integer -> String

    // word stem <-> wordID mappings
    private HTree wordToWordId;  // String -> Integer
    private HTree wordIdToWord;  // Integer -> String

    // Page metadata
    private HTree pageTitle;     // Integer -> String
    private HTree pageLastMod;   // Integer -> String
    private HTree pageSize;      // Integer -> Long

    // Inverted indexes: wordID -> HashMap<Integer(pageID), ArrayList<Integer>(positions)>
    private HTree bodyInverted;
    private HTree titleInverted;

    // Forward indexes: pageID -> HashMap<Integer(wordID), Integer(tf)>
    private HTree bodyForward;
    private HTree titleForward;

    // Link relations
    private HTree childLinks;    // Integer(pageID) -> HashSet<Integer>(child pageIDs)
    private HTree parentLinks;   // Integer(pageID) -> HashSet<Integer>(parent pageIDs)

    // Counters
    private HTree pageCounter;   // "counter" -> Integer
    private HTree wordCounter;   // "counter" -> Integer

    public Indexer(String dbPath) throws IOException {
        recman = RecordManagerFactory.createRecordManager(dbPath);

        urlToPageId  = loadOrCreate("urlToPageId");
        pageIdToUrl  = loadOrCreate("pageIdToUrl");
        wordToWordId = loadOrCreate("wordToWordId");
        wordIdToWord = loadOrCreate("wordIdToWord");
        pageTitle    = loadOrCreate("pageTitle");
        pageLastMod  = loadOrCreate("pageLastMod");
        pageSize     = loadOrCreate("pageSize");
        bodyInverted = loadOrCreate("bodyInverted");
        titleInverted = loadOrCreate("titleInverted");
        bodyForward  = loadOrCreate("bodyForward");
        titleForward = loadOrCreate("titleForward");
        childLinks   = loadOrCreate("childLinks");
        parentLinks  = loadOrCreate("parentLinks");
        pageCounter  = loadOrCreate("pageCounter");
        wordCounter  = loadOrCreate("wordCounter");

        recman.commit();
    }

    private HTree loadOrCreate(String name) throws IOException {
        long recid = recman.getNamedObject(name);
        if (recid != 0) {
            return HTree.load(recman, recid);
        } else {
            HTree tree = HTree.createInstance(recman);
            recman.setNamedObject(name, tree.getRecid());
            return tree;
        }
    }

    public void finalize() {
        try {
            recman.commit();
            recman.close();
        } catch (IOException e) {
            System.err.println("Error closing DB: " + e.getMessage());
        }
    }

    public void commit() throws IOException {
        recman.commit();
    }

    // ---- Page ID management ----

    public int getOrAddPageId(String url) throws IOException {
        Integer existing = (Integer) urlToPageId.get(url);
        if (existing != null) return existing;

        Integer counter = (Integer) pageCounter.get("counter");
        int newId = (counter == null) ? 0 : counter;

        urlToPageId.put(url, newId);
        pageIdToUrl.put(newId, url);
        pageCounter.put("counter", newId + 1);

        return newId;
    }

    public Integer getPageId(String url) throws IOException {
        return (Integer) urlToPageId.get(url);
    }

    public String getUrl(int pageId) throws IOException {
        return (String) pageIdToUrl.get(pageId);
    }

    public int getPageCount() throws IOException {
        Integer counter = (Integer) pageCounter.get("counter");
        return (counter == null) ? 0 : counter;
    }

    public FastIterator getPageIdIterator() throws IOException {
        return pageIdToUrl.keys();
    }

    // ---- Word ID management ----

    public int getOrAddWordId(String stem) throws IOException {
        Integer existing = (Integer) wordToWordId.get(stem);
        if (existing != null) return existing;

        Integer counter = (Integer) wordCounter.get("counter");
        int newId = (counter == null) ? 0 : counter;

        wordToWordId.put(stem, newId);
        wordIdToWord.put(newId, stem);
        wordCounter.put("counter", newId + 1);

        return newId;
    }

    public Integer getWordId(String stem) throws IOException {
        return (Integer) wordToWordId.get(stem);
    }

    public String getWord(int wordId) throws IOException {
        return (String) wordIdToWord.get(wordId);
    }

    // ---- Page metadata ----

    public void setPageInfo(int pageId, String title, String lastMod, long size) throws IOException {
        pageTitle.put(pageId, title != null ? title : "");
        pageLastMod.put(pageId, lastMod != null ? lastMod : "");
        pageSize.put(pageId, size);
    }

    public String getPageTitle(int pageId) throws IOException {
        return (String) pageTitle.get(pageId);
    }

    public String getPageLastMod(int pageId) throws IOException {
        String val = (String) pageLastMod.get(pageId);
        return (val != null) ? val : "";
    }

    public long getPageSize(int pageId) throws IOException {
        Long val = (Long) pageSize.get(pageId);
        return (val != null) ? val : -1L;
    }

    // ---- Body indexing ----

    @SuppressWarnings("unchecked")
    public void addBodyPosting(int pageId, int wordId, int position) throws IOException {
        // Update body forward index: pageId -> {wordId -> tf}
        HashMap<Integer, Integer> fwdMap = (HashMap<Integer, Integer>) bodyForward.get(pageId);
        if (fwdMap == null) {
            fwdMap = new HashMap<>();
        }
        fwdMap.put(wordId, fwdMap.getOrDefault(wordId, 0) + 1);
        bodyForward.put(pageId, fwdMap);

        // Update body inverted index: wordId -> {pageId -> [positions]}
        HashMap<Integer, ArrayList<Integer>> invMap =
                (HashMap<Integer, ArrayList<Integer>>) bodyInverted.get(wordId);
        if (invMap == null) {
            invMap = new HashMap<>();
        }
        ArrayList<Integer> positions = invMap.get(pageId);
        if (positions == null) {
            positions = new ArrayList<>();
        }
        positions.add(position);
        invMap.put(pageId, positions);
        bodyInverted.put(wordId, invMap);
    }

    @SuppressWarnings("unchecked")
    public HashMap<Integer, Integer> getBodyWordFreqs(int pageId) throws IOException {
        return (HashMap<Integer, Integer>) bodyForward.get(pageId);
    }

    @SuppressWarnings("unchecked")
    public HashMap<Integer, ArrayList<Integer>> getBodyPostings(int wordId) throws IOException {
        return (HashMap<Integer, ArrayList<Integer>>) bodyInverted.get(wordId);
    }

    // ---- Title indexing ----

    @SuppressWarnings("unchecked")
    public void addTitlePosting(int pageId, int wordId, int position) throws IOException {
        // Update title forward index: pageId -> {wordId -> tf}
        HashMap<Integer, Integer> fwdMap = (HashMap<Integer, Integer>) titleForward.get(pageId);
        if (fwdMap == null) {
            fwdMap = new HashMap<>();
        }
        fwdMap.put(wordId, fwdMap.getOrDefault(wordId, 0) + 1);
        titleForward.put(pageId, fwdMap);

        // Update title inverted index: wordId -> {pageId -> [positions]}
        HashMap<Integer, ArrayList<Integer>> invMap =
                (HashMap<Integer, ArrayList<Integer>>) titleInverted.get(wordId);
        if (invMap == null) {
            invMap = new HashMap<>();
        }
        ArrayList<Integer> positions = invMap.get(pageId);
        if (positions == null) {
            positions = new ArrayList<>();
        }
        positions.add(position);
        invMap.put(pageId, positions);
        titleInverted.put(wordId, invMap);
    }

    @SuppressWarnings("unchecked")
    public HashMap<Integer, Integer> getTitleWordFreqs(int pageId) throws IOException {
        return (HashMap<Integer, Integer>) titleForward.get(pageId);
    }

    @SuppressWarnings("unchecked")
    public HashMap<Integer, ArrayList<Integer>> getTitlePostings(int wordId) throws IOException {
        return (HashMap<Integer, ArrayList<Integer>>) titleInverted.get(wordId);
    }

    // ---- Link management ----

    @SuppressWarnings("unchecked")
    public void addChildLink(int parentId, int childId) throws IOException {
        // Add to childLinks
        HashSet<Integer> children = (HashSet<Integer>) childLinks.get(parentId);
        if (children == null) {
            children = new HashSet<>();
        }
        children.add(childId);
        childLinks.put(parentId, children);

        // Add to parentLinks
        HashSet<Integer> parents = (HashSet<Integer>) parentLinks.get(childId);
        if (parents == null) {
            parents = new HashSet<>();
        }
        parents.add(parentId);
        parentLinks.put(childId, parents);
    }

    @SuppressWarnings("unchecked")
    public Set<Integer> getChildIds(int pageId) throws IOException {
        HashSet<Integer> children = (HashSet<Integer>) childLinks.get(pageId);
        return (children != null) ? children : new HashSet<>();
    }

    @SuppressWarnings("unchecked")
    public Set<Integer> getParentIds(int pageId) throws IOException {
        HashSet<Integer> parents = (HashSet<Integer>) parentLinks.get(pageId);
        return (parents != null) ? parents : new HashSet<>();
    }
}
