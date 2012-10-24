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

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import fr.openstreetmap.search.binary.BinaryStreamEncoder;
import fr.openstreetmap.search.tree.RadixTreeWriter;

public class AutocompleteBuilder {

    File temporaryFileUnsorted;
    File temporaryFileSorted;
    File outputFile;
    File outputDataFile;

    public int minEntrySize = 3;
    public int nbValues = 1000;

    RadixTreeWriter rtw;

    OutputStream dataOutput;
    OutputStream dataOutputBuffered;
    BinaryStreamEncoder bse;

    Writer temporaryFileWriter;
    
    public static class ScoredToken implements Comparable<ScoredToken>{
        public ScoredToken(String token, long score) {
            this.score = score; this.token = token;
        }
        String token;
        long score;
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

    public AutocompleteBuilder(File temporaryFileUnsorted, File temporaryFileSorted, File outputFile,File outputDataFile) throws IOException {
        this.temporaryFileSorted  = temporaryFileSorted;
        this.temporaryFileUnsorted = temporaryFileUnsorted;
        this.outputFile = outputFile;
        this.outputDataFile = outputDataFile;

        temporaryFileWriter = new FileWriter(temporaryFileUnsorted);
        dataOutput = new FileOutputStream(outputDataFile);
        dataOutputBuffered = new BufferedOutputStream(dataOutput);
        bse = new BinaryStreamEncoder(dataOutputBuffered);
    }

    public void addMultiEntry(String[] entries, String data, long score) throws IOException {
        byte[] utf8Data = data.getBytes("utf8");

        long offset = bse.getWritten();
        bse.writeVInt(utf8Data.length);
        bse.writeBytes(utf8Data);

        for (String entry: entries) {
            for (int i = minEntrySize; i <= entry.length(); i++) {
                temporaryFileWriter.write(entry.substring(0, i)+ "\t" + offset + "\t" + score + "\n");
            }
        }
    }
    
    public void addMultiEntry(String[] entries, byte[] data, long[] scores) throws IOException {
        long offset = bse.getWritten();
        bse.writeVInt(data.length);
        bse.writeBytes(data);

        for (int eidx = 0; eidx < entries.length; eidx++) {
            for (int i = minEntrySize; i <= entries[eidx].length(); i++) {
                temporaryFileWriter.write(entries[eidx].substring(0, i)+ "\t" + offset + "\t" + scores[eidx] + "\n");
            }
        }
    }
    
    Map<String, ScoredToken> dedupMap = new HashMap<String, AutocompleteBuilder.ScoredToken>();

    
    public void addEntry(List<ScoredToken> scoredTokens, byte[] data, boolean dedup) throws IOException {
        long offset = bse.getWritten();
        bse.writeVInt(data.length);
        bse.writeBytes(data);
        
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
        
        for (ScoredToken sc : scoredTokens) {
            for (int i = minEntrySize; i <= sc.token.length(); i++) {
                temporaryFileWriter.write(sc.token.substring(0, i)+ "\t" + offset + "\t" + sc.score + "\n");
            }
        }
    }

    public void addEntry(String entry, String data, long score) throws IOException {
        byte[] utf8Data = data.getBytes("utf8");

        long offset = bse.getWritten();
        if (entry.startsWith("teac")) {
            System.out.println("WRITE " + entry + " AT " + offset);
        }
        bse.writeVInt(utf8Data.length);
        bse.writeBytes(utf8Data);

        for (int i = minEntrySize; i <= entry.length(); i++) {
            temporaryFileWriter.write(entry.substring(0, i)+ "\t" + offset + "\t" + score + "\n");
        }
    }

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


    public void flush() throws IOException, InterruptedException{
        temporaryFileWriter.flush();
        temporaryFileWriter.close();
        System.out.println("Sorting entries");

        int retCode = execAndLog(new String[]{"/bin/sh", "-c",
                "sort -S 2G -T " + temporaryFileSorted.getParent() + " -t '\t' " + temporaryFileUnsorted.getAbsolutePath() +
                "> " + temporaryFileSorted.getAbsolutePath()}, new String[]{"LC_ALL=C"});
        if (retCode != 0) {
            throw new Error("Failed to sort");
        }

        System.out.println("Building radix tree");

        FileOutputStream fos = new FileOutputStream(outputFile);
        BufferedOutputStream bos = new BufferedOutputStream(fos);
        rtw = new RadixTreeWriter(bos);
        rtw.byteBuf = true;

        BufferedReader br = new BufferedReader(new FileReader(temporaryFileSorted));

        String previousKey = null;
        List<EntryVal> values = new ArrayList<EntryVal>();

        int nlines = 0;
        int nkeys = 0;
        while (true) {
            String line = br.readLine();
            ++nlines;

            //            System.out.println("  Handle " + line);
            if (line == null) break;
            String[] chunks = line.split("\t");
            String key = chunks[0];
            EntryVal ev = new EntryVal(Long.parseLong(chunks[1]), Long.parseLong(chunks[2]));
            if (previousKey == null) {
                previousKey = key;
                values.add(ev);
            } else if (previousKey.equals(key)) {
                values.add(ev);
            } else {
                emitKey(previousKey, values);
                if (++nkeys % 100000 == 0) {
                    System.out.println("Emitted " + nkeys + " keys / " + nlines + " lines");
                }
                values.clear();
                values.add(ev);
                previousKey = key;
            }
        }
        if (values.size() > 0) {
            emitKey(previousKey, values);
        }
        ++nlines;

        rtw.flush();
        bos.flush();
        fos.close();

        dataOutputBuffered.flush();
        dataOutput.close();
        
        System.out.println("Done, nkeys=" + nkeys +" nlines=" + nlines +
                " dataSize=" + outputDataFile.length() + " radixSize=" + outputFile.length());
    }

    List<String> clippedWords = new ArrayList<String>();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    private void emitKey(String key, List<EntryVal> values) throws IOException {
        //        System.out.println("  Emit " + key + " with " + values.size() + " candidates");
        Collections.sort(values);
        baos.reset();
        BinaryStreamEncoder bse = new BinaryStreamEncoder(baos);

        int encodedValues =  Math.min(values.size(), nbValues);
        if (encodedValues < values.size()) {
            clippedWords.add(key);
            System.out.println("CLIPPED: " + key + " " + encodedValues + "/" + values.size());
        }
        values = values.subList(0, encodedValues);
        Collections.sort(values, new Comparator<EntryVal>() {
            @Override
            public int compare(EntryVal o1, EntryVal o2) {
                return (int)(o1.value - o2.value);
            }
            
        });
        
//        System.out.println("EMIT " + encodedValues + " values");
        bse.writeVInt(encodedValues);
        for (int i = 0; i < encodedValues; i++) {
//            System.out.println(" EMIT " + values.get(i).value +"  " + values.get(i).score);
            bse.writeVInt(values.get(i).value);
            bse.writeVInt(values.get(i).score);
        }
        rtw.addEntry(key, 0, baos.toByteArray());
    }
}
