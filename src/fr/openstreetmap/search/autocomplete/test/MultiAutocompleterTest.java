package fr.openstreetmap.search.autocomplete.test;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import fr.openstreetmap.search.autocomplete.Autocompleter;
import fr.openstreetmap.search.autocomplete.MultipleWordsAutocompleter;


public class MultiAutocompleterTest {
    public static void main(String[] args) throws Exception {
        FileChannel radixChannel = new RandomAccessFile(new File("/data/homes/stenac/public_html/osm/data/radix"), "r").getChannel();
        MappedByteBuffer radixDirectBuffer = radixChannel.map(MapMode.READ_ONLY, 0, radixChannel.size());
        
        byte[] radixData = new byte[(int)radixChannel.size()];
        radixDirectBuffer.get(radixData, 0, (int)radixChannel.size());
        ByteBuffer radixBuffer = ByteBuffer.wrap(radixData);
        
//        ByteBuffer radixBuffer = radixDirectBuffer;
        
        FileChannel dataChannel = new RandomAccessFile(new File("/data/homes/stenac/public_html/osm/data/data"), "r").getChannel();
        MappedByteBuffer dataBuffer = dataChannel.map(MapMode.READ_ONLY, 0, dataChannel.size());
        
        Autocompleter a = new Autocompleter(radixBuffer, dataBuffer);
        
        MultipleWordsAutocompleter mwa = new MultipleWordsAutocompleter();
        mwa.completer = a;
        
        List<String> tokens = new ArrayList<String>();
        
        tokens.add("boulogne");
        tokens.add("reine");
        
//        for (int i = 0; i < 200; i++) {
//        	mwa.autocomplete(tokens, 1);
//        }
//        
        long before = System.currentTimeMillis();
//        for (int i = 0; i < 1000; i++) {
//        	mwa.autocomplete(tokens, 1);
//        }
        long after = System.currentTimeMillis();
        
        List<Autocompleter.AutocompleterEntry> aelist = mwa.autocomplete(tokens, 1, null);
        Collections.sort(aelist);
        
        for (Autocompleter.AutocompleterEntry ae : aelist) {
        	System.out.println(" " + ae.offset + " - s=" + ae.score + " d=" + ae.distance + " correct prefix=" + ae.correctedPrefix);
        	System.out.println("   " + a.getData(ae.offset));
        }
        
        System.out.println("TIME IS " + (after-before));
        
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
