package comp4321;
import java.net.*;
import java.util.*;
import java.io.*;
import java.text.*;
import org.htmlparser.*;
import org.htmlparser.beans.*;
import org.htmlparser.filters.*;
import org.htmlparser.tags.*;
import org.htmlparser.util.*;

public class Spider {
    private final Indexer indexer;
    private final StopStem stopStem;

    public Spider(Indexer indexer, StopStem stopStem) {
        this.indexer = indexer;
        this.stopStem = stopStem;
    }

    public void crawl(String startUrl, int maxPages) throws Exception {
        Queue<String> queue = new LinkedList<>();
        Set<String> queued = new HashSet<>();

        queue.add(startUrl);
        queued.add(startUrl);
        int count = 0;

        while (!queue.isEmpty() && count < maxPages) {
            String url = queue.poll();

            try {
                if (!shouldFetch(url)) {
                    System.out.println("Skip (up to date): " + url);
                    count++;
                    addChildren(url, queue, queued);
                    continue;
                }
            } catch (Exception e) {
                System.err.println("Check failed: " + url);
                continue;
            }

            System.out.println("Fetching (" + (count + 1) + "/" + maxPages + "): " + url);

            try {
                List<String> children = fetchAndIndex(url);
                count++;
                for (String child : children) {
                    if (!queued.contains(child)) {
                        queue.add(child);
                        queued.add(child);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error: " + url + " - " + e.getMessage());
            }
        }

        indexer.commit();
        System.out.println("Done. Pages indexed: " + count);
    }

    private void addChildren(String url, Queue<String> queue, Set<String> queued) {
        try {
            Integer pid = indexer.getPageId(url);
            if (pid == null) return;
            for (int cid : indexer.getChildIds(pid)) {
                String cu = indexer.getUrl(cid);
                if (cu != null && !queued.contains(cu)) {
                    queue.add(cu);
                    queued.add(cu);
                }
            }
        } catch (Exception e) { /* ignore */ }
    }

    private boolean shouldFetch(String url) throws Exception {
        Integer pid = indexer.getPageId(url);
        if (pid == null) return true;

        String stored = indexer.getPageLastMod(pid);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.connect();
            long lastMod = conn.getLastModified();
            if (lastMod == 0 || stored == null || stored.isEmpty()) return true;
            SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
            return lastMod > sdf.parse(stored).getTime();
        } catch (Exception e) {
            return true;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private List<String> fetchAndIndex(String url) throws Exception {
        HttpURLConnection conn = null;
        String lastModStr = "";
        long size = -1;

        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.connect();

            long lm = conn.getLastModified();
            if (lm != 0) {
                lastModStr = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH).format(new Date(lm));
            } else {
                String d = conn.getHeaderField("Date");
                if (d != null) lastModStr = d;
            }
            size = conn.getContentLength();
        } catch (Exception e) { /* ignore */ } finally {
            if (conn != null) conn.disconnect();
        }

        Parser parser = new Parser(url);
        parser.setEncoding("UTF-8");

        String title = url;
        try {
            NodeList nodes = parser.extractAllNodesThatMatch(new NodeClassFilter(TitleTag.class));
            if (nodes.size() > 0) {
                String t = ((TitleTag) nodes.elementAt(0)).getTitle().trim();
                if (!t.isEmpty()) title = t;
            }
        } catch (Exception e) { /* ignore */ }

        parser.reset();
        String body = "";
        try {
            StringBean sb = new StringBean();
            sb.setLinks(false);
            sb.setCollapse(true);
            parser.visitAllNodesWith(sb);
            body = sb.getStrings() != null ? sb.getStrings() : "";
        } catch (Exception e) { /* ignore */ }

        if (size <= 0) size = body.length();

        int pageId = indexer.getOrAddPageId(url);
        indexer.setPageInfo(pageId, title, lastModStr, size);
        indexTitleWords(pageId, title);
        indexBodyWords(pageId, body);

        parser.reset();
        List<String> childUrls = new ArrayList<>();
        try {
            NodeList links = parser.extractAllNodesThatMatch(new NodeClassFilter(LinkTag.class));
            for (int i = 0; i < links.size(); i++) {
                LinkTag link = (LinkTag) links.elementAt(i);
                String href = link.extractLink();
                if (href == null || href.isEmpty()) continue;
                href = href.trim();
                if (href.startsWith("#") || href.startsWith("javascript:") || href.startsWith("mailto:")) continue;
                try {
                    String abs = new URL(new URL(url), href).toExternalForm();
                    int h = abs.indexOf('#');
                    if (h >= 0) abs = abs.substring(0, h);
                    if (abs.isEmpty()) continue;
                    int childId = indexer.getOrAddPageId(abs);
                    indexer.addChildLink(pageId, childId);
                    childUrls.add(abs);
                } catch (Exception e) { /* ignore bad URLs */ }
            }
        } catch (Exception e) { /* ignore */ }

        return childUrls;
    }

    private void indexTitleWords(int pageId, String title) throws Exception {
        String[] words = title.split("[^a-zA-Z0-9]+");
        int pos = 0;
        for (String w : words) {
            if (w.isEmpty()) continue;
            String stem = stopStem.stemAndCheck(w);
            if (stem != null) indexer.addTitlePosting(pageId, indexer.getOrAddWordId(stem), pos);
            pos++;
        }
    }

    private void indexBodyWords(int pageId, String body) throws Exception {
        String[] words = body.split("[^a-zA-Z0-9]+");
        int pos = 0;
        for (String w : words) {
            if (w.isEmpty()) continue;
            String stem = stopStem.stemAndCheck(w);
            if (stem != null) indexer.addBodyPosting(pageId, indexer.getOrAddWordId(stem), pos);
            pos++;
        }
    }

    public static void main(String[] args) throws Exception {
        String startUrl = "https://hitcslj.github.io/TestPages/testpage.htm";
        int maxPages = 300;
        if (args.length >= 1) startUrl = args[0];
        if (args.length >= 2) maxPages = Integer.parseInt(args[1]);

        Indexer indexer = new Indexer("spider_db");
        StopStem stopStem = new StopStem("stopwords.txt");
        new Spider(indexer, stopStem).crawl(startUrl, maxPages);
        indexer.finalize();
    }
}
