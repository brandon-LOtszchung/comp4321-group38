import java.io.*;
import java.util.*;

public class StopStem {
    private final HashSet<String> stopWords = new HashSet<>();
    private final Porter porter = new Porter();

    public StopStem(String filename) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim().toLowerCase();
                if (!line.isEmpty()) stopWords.add(line);
            }
        }
    }

    public boolean isStopWord(String word) {
        return stopWords.contains(word.toLowerCase().trim());
    }

    public String stem(String word) {
        return porter.stripAffixes(word.toLowerCase().trim());
    }

    public String stemAndCheck(String word) {
        word = word.toLowerCase().trim();
        if (word.isEmpty() || isStopWord(word)) return null;
        String stemmed = stem(word);
        if (stemmed.isEmpty() || isStopWord(stemmed)) return null;
        return stemmed;
    }
}
