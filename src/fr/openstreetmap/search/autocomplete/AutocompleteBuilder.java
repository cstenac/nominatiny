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

/**
 * Builds the data for an autocompletion searcher
 */
public class AutocompleteBuilder extends IndexBuilder {

    File temporaryFileUnsorted;
    File temporaryFileSorted;
    File outputFile;
    File outputDataFile;
    File outputFreqFile;

    public int minEntrySize = 3;
    public int nbValues = 1000;

    RadixTreeWriter rtw;

    OutputStream dataOutput;
    OutputStream dataOutputBuffered;
    BinaryStreamEncoder bse;

    Writer temporaryFileWriter;
    
    Writer freqFileWriter;
    
    public AutocompleteBuilder(File outputDir) throws IOException {
        this.temporaryFileSorted  = new File(outputDir, "tmp.sorted");
        this.temporaryFileUnsorted = new File(outputDir, "tmp.unsorted");
        this.outputFile = new File(outputDir, "radix");
        this.outputDataFile = new File(outputDir, "data");
        this.outputFreqFile = new File(outputDir, "frequencies");

        temporaryFileWriter = new FileWriter(temporaryFileUnsorted);
        freqFileWriter = new FileWriter(outputFreqFile);
        dataOutput = new FileOutputStream(outputDataFile);
        dataOutputBuffered = new BufferedOutputStream(dataOutput);
        bse = new BinaryStreamEncoder(dataOutputBuffered);
    }

//    public void addMultiEntry(String[] entries, String data, long score) throws IOException {
//        byte[] utf8Data = data.getBytes("utf8");
//
//        long offset = bse.getWritten();
//        bse.writeVInt(utf8Data.length);
//        bse.writeBytes(utf8Data);
//
//        for (String entry: entries) {
//            for (int i = minEntrySize; i <= entry.length(); i++) {
//                temporaryFileWriter.write(entry.substring(0, i)+ "\t" + offset + "\t" + score + "\n");
//            }
//        }
//    }
//    
//    public void addMultiEntry(String[] entries, byte[] data, long[] scores) throws IOException {
//        long offset = bse.getWritten();
//        bse.writeVInt(data.length);
//        bse.writeBytes(data);
//
//        for (int eidx = 0; eidx < entries.length; eidx++) {
//            for (int i = minEntrySize; i <= entries[eidx].length(); i++) {
//                temporaryFileWriter.write(entries[eidx].substring(0, i)+ "\t" + offset + "\t" + scores[eidx] + "\n");
//            }
//        }
//    }
//    
//    Map<String, ScoredToken> dedupMap = new HashMap<String, AutocompleteBuilder.ScoredToken>();

    @Override
    public void addEntry(List<ScoredToken> scoredTokens, byte[] data) throws IOException {
        long offset = bse.getWritten();
        bse.writeVInt(data.length);
        bse.writeBytes(data);
                
        for (ScoredToken sc : scoredTokens) {
            for (int i = minEntrySize; i <= sc.token.length(); i++) {
                temporaryFileWriter.write(sc.token.substring(0, i)+ "\t" + offset + "\t" + sc.score + "\n");
            }
        }
    }

//    public void addEntry(String entry, String data, long score) throws IOException {
//        byte[] utf8Data = data.getBytes("utf8");
//
//        long offset = bse.getWritten();
//        if (entry.startsWith("teac")) {
//            System.out.println("WRITE " + entry + " AT " + offset);
//        }
//        bse.writeVInt(utf8Data.length);
//        bse.writeBytes(utf8Data);
//
//        for (int i = minEntrySize; i <= entry.length(); i++) {
//            temporaryFileWriter.write(entry.substring(0, i)+ "\t" + offset + "\t" + score + "\n");
//        }
//    }

   
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
            String[] chunks = StringUtils.split(line, '\t');
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
        
        System.out.println("childPos=" + rtw.writtenChildrenPositions + " cPS=" + rtw.writtenChildrenPositionsSize + " data=" + rtw.writtenNodesDataSize);
        
        bos.flush();
        fos.close();

        dataOutputBuffered.flush();
        dataOutput.close();
        
        freqFileWriter.close();
        
        System.out.println("Done, nkeys=" + nkeys +" nlines=" + nlines +
                " dataSize=" + outputDataFile.length() + " radixSize=" + outputFile.length());
    }

    public List<String> clippedWords = new ArrayList<String>();
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
        freqFileWriter.write("" + values.size() + " " + key + "\n");
        values = values.subList(0, encodedValues);
        Collections.sort(values, new Comparator<EntryVal>() {
            @Override
            public int compare(EntryVal o1, EntryVal o2) {
                return (int)(o1.value - o2.value);
            }
            
        });
        
//        System.out.println("EMIT " + encodedValues + " values");
        bse.writeVInt(encodedValues);
        long prev = 0;
        for (int i = 0; i < encodedValues; i++) {
//            System.out.println(" EMIT " + values.get(i).value +"  " + values.get(i).score);
            bse.writeVInt(values.get(i).value  - prev);
            prev = values.get(i).value;
            bse.writeVInt(values.get(i).score);
        }
        rtw.addEntry(key, 0, baos.toByteArray());
    }
}
