package fr.openstreetmap.search.autocomplete.test;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

import fr.openstreetmap.search.autocomplete.AutocompleteBuilder;
import fr.openstreetmap.search.autocomplete.Autocompleter;


public class AutocompleteBuilderTest {
    public static void main(String[] args) throws Exception {
        AutocompleteBuilder ab = new AutocompleteBuilder(new File("/data.2/unsorted"), new File("/data.2/sorted"), new File("/data.2/radix"), new File("/data.2/data"));
        //ab.nbValues = 2;
        
        
        BufferedReader br = new BufferedReader(new FileReader(new File("/usr/share/dict/american-english")));
        int lno = 0;
        while (true) {
            String s = br.readLine();
            if (s == null) break;
            s.replace("\n", "");
            if (s.contains("'")) continue;
            ab.addEntry(s.toLowerCase(), s, 42);
        }
        
//        ab.addEntry("test", 42, 1000);
//        ab.addEntry("pouet", 42, 1000);
//        ab.addEntry("prout", 45, 100);
        
        
        ab.flush();
        
        FileChannel radixChannel = new RandomAccessFile(new File("/data.2/radix"), "r").getChannel();
        MappedByteBuffer radixBuffer = radixChannel.map(MapMode.READ_ONLY, 0, radixChannel.size());
        
        FileChannel dataChannel = new RandomAccessFile(new File("/data.2/data"), "r").getChannel();
        MappedByteBuffer dataBuffer = dataChannel.map(MapMode.READ_ONLY, 0, dataChannel.size());
        
        Autocompleter a = new Autocompleter(radixBuffer, dataBuffer);
        
        for (Autocompleter.AutocompleterEntry ae : a.getOffsets("tesst", 1, null)) {
        	System.out.println(" " + ae.offset + " - " + ae.score + " " + ae.distance + " correct prefix=" + ae.correctedPrefix);
        	System.out.println("   " + a.getData(ae.offset));
        }
        
//        RadixTree rt = new RadixTree();
//        rt.buffer = radixBuffer;
//        rt.totalSize = radixBuffer.size();
//        rt.byteArrayMode = true;
//        
////        rt.getEntry("test");
//
//        RadixTreeFuzzyLookup rtfl = null;
//
////        for (int i = 0; i < 100; i++) {
//            rtfl = new RadixTreeFuzzyLookup();
//            rtfl.tree = rt;
//            rtfl.buffer = rt.buffer;
//            rtfl.totalSize = rt.totalSize;
//            rtfl.maxDistance = 1;        
//            rtfl.match("teachre");
//            System.out.println("****" + rtfl.matches.size());
//            for (int i = 0; i < rtfl.matches.size(); i++) {
//                System.out.println("*** " + rtfl.matches.get(i).key + " - " + rtfl.matches.get(i).distance);
//            }
//            
////        }
    }
}
