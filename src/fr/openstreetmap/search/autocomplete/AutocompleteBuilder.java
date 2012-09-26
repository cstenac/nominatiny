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
import java.util.List;

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
                "sort -T " + temporaryFileSorted.getParent() + " -t '\t' " + temporaryFileUnsorted.getAbsolutePath() +
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

        while (true) {
            String line = br.readLine();
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
                values.clear();
                values.add(ev);
                previousKey = key;
            }
        }
        if (values.size() > 0) {
            emitKey(previousKey, values);
        }

        rtw.flush();
        bos.flush();
        fos.close();

        dataOutputBuffered.flush();
        dataOutput.close();
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
        
        System.out.println("EMIT " + encodedValues + " values");
        bse.writeVInt(encodedValues);
        for (int i = 0; i < encodedValues; i++) {
            System.out.println(" EMIT " + values.get(i).value +"  " + values.get(i).score);
            bse.writeVInt(values.get(i).value);
            bse.writeVInt(values.get(i).score);
        }
        if (key.startsWith("teac")) {
            System.out.println(key +  " ->  " + baos.toByteArray().length + " for " + encodedValues);
        }
        rtw.addEntry(key, 0, baos.toByteArray());
    }
}
