import java.io.*;
import java.util.*;
import jdbm.helper.FastIterator;

public class TestProgram {
    public static void main(String[] args) throws Exception {
        String dbPath = args.length >= 1 ? args[0] : "spider_db";
        String outPath = args.length >= 2 ? args[1] : "spider_result.txt";

        Indexer indexer = new Indexer(dbPath);

        List<Integer> pageIds = new ArrayList<>();
        FastIterator it = indexer.getPageIdIterator();
        Object key;
        while ((key = it.next()) != null) pageIds.add((Integer) key);
        Collections.sort(pageIds);

        List<Integer> crawled = new ArrayList<>();
        for (int pid : pageIds) {
            if (indexer.getPageTitle(pid) != null) crawled.add(pid);
        }

        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(outPath)))) {
            for (int i = 0; i < crawled.size(); i++) {
                int pid = crawled.get(i);
                String title   = indexer.getPageTitle(pid);
                String url     = indexer.getUrl(pid);
                String lastMod = indexer.getPageLastMod(pid);
                long   size    = indexer.getPageSize(pid);

                pw.println(title != null && !title.isEmpty() ? title : "(no title)");
                pw.println(url);
                pw.println((lastMod != null && !lastMod.isEmpty() ? lastMod : "N/A")
                        + ", " + (size > 0 ? size : "N/A"));

                HashMap<Integer, Integer> freq = indexer.getBodyWordFreqs(pid);
                if (freq != null && !freq.isEmpty()) {
                    List<Map.Entry<Integer, Integer>> entries = new ArrayList<>(freq.entrySet());
                    entries.sort((a, b) -> b.getValue().compareTo(a.getValue()));
                    StringBuilder sb = new StringBuilder();
                    int cnt = 0;
                    for (Map.Entry<Integer, Integer> e : entries) {
                        if (cnt >= 10) break;
                        String w = indexer.getWord(e.getKey());
                        if (w != null) {
                            if (cnt > 0) sb.append("; ");
                            sb.append(w).append(" ").append(e.getValue());
                            cnt++;
                        }
                    }
                    pw.println(sb);
                } else {
                    pw.println();
                }

                Set<Integer> children = indexer.getChildIds(pid);
                int cnt = 0;
                for (int cid : children) {
                    if (cnt >= 10) break;
                    String cu = indexer.getUrl(cid);
                    if (cu != null) { pw.println(cu); cnt++; }
                }

                if (i < crawled.size() - 1)
                    pw.println("--------------------------------------------------");
            }
        }

        indexer.finalize();
        System.out.println("Written to " + outPath);
    }
}
