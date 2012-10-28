package fr.openstreetmap.search.autocomplete;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import fr.openstreetmap.search.binary.BinaryStreamEncoder;
import fr.openstreetmap.search.tree.RadixTreeWriter;


public abstract class IndexBuilder {
    public static class ScoredToken implements Comparable<ScoredToken>{
        public ScoredToken(String token, long score) {
            this.score = score; this.token = token;
        }
        public String token;
        public long score;
        @Override
        public int compareTo(ScoredToken arg0) {
            int tDiff = this.token.compareTo(arg0.token);
            if (tDiff != 0) return tDiff;
            if (score > arg0.score) return 1;
            if (score < arg0.score) return -1;
            return 0;
        }
        
        public String toString() {
            return token + "(" + score + ")";
        }
    }

    Map<String, ScoredToken> dedupMap = new HashMap<String, IndexBuilder.ScoredToken>();

    
    public void addEntry(List<ScoredToken> scoredTokens, byte[] data, boolean dedup) throws IOException {      
        if (dedup) {
            dedupMap.clear();
            for (ScoredToken sc : scoredTokens) {
                ScoredToken already = dedupMap.get(sc.token);
                if (already ==null) {
                    dedupMap.put(sc.token, sc);
                } else if (already.score < sc.score) {
                    // Better score, replace
                    dedupMap.put(sc.token, sc);
                } else {
//                    System.out.println("Eliminate dup on " + sc  + " from "+ StringUtils.join(scoredTokens, "-"));
                }
            }
            scoredTokens.clear();
            scoredTokens.addAll(dedupMap.values());
        }
        
        addEntry(scoredTokens, data);
    }
    
    public abstract void addEntry(List<ScoredToken> scoredTokens, byte[] data) throws IOException;

    static class EntryVal implements Comparable<EntryVal>{
        EntryVal(long value, long score) {
            this.value = value; 
            this.score = score;
        }
        long value;
        long score;
        @Override
        public int compareTo(EntryVal o) {
            if (score > o.score) return -1;
            if (score < o.score) return 1;
            return 0;
        }
    }

    static public int execAndLog(String[] args, String[] env) throws IOException, InterruptedException {
        Process p = Runtime.getRuntime().exec(args, env);
        Thread tout = new LoggingStreamEater(p.getInputStream(), Level.INFO);
        tout.start();
        Thread terr = new LoggingStreamEater(p.getErrorStream(), Level.WARN);
        terr.start();
        int rv = p.waitFor();
        tout.join();
        terr.join();
        return rv;
    }
    
    
    /* Eat a stream and log its output */
    static class LoggingStreamEater extends Thread {
        LoggingStreamEater(InputStream is, Level level) {
            this.is = is;
            this.level = level;
        }
        @Override
        public void run() {
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                while (true) {
                    String line = br.readLine();
                    if (line == null) break;
                    logger.log(level, line);
                }
                br.close();
            } catch (IOException e) {         
                logger.error("", e);
            }
        }
        private Level level;
        private InputStream is;
        private static Logger logger = Logger.getLogger("process");
    }


    public abstract void flush() throws IOException, InterruptedException;

    public List<String> clippedWords = new ArrayList<String>();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

}
