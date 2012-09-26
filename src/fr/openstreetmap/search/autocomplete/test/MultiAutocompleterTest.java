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
import fr.openstreetmap.search.autocomplete.MultipleWordsAutocompleter.MultiWordAutocompleterEntry;


public class MultiAutocompleterTest {
    public static void main(String[] args) throws Exception {
        System.out.println("go");
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
//        tokens.add("anna");
        tokens.add("ana");

        for (int i = 0; i < 200; i++) {
        	mwa.autocomplete(tokens, 1, null);
        }
//        
        long before = System.currentTimeMillis();
        for (int i = 0; i < 500; i++) {
        	mwa.autocomplete(tokens, 1, null);
        }
        long after = System.currentTimeMillis();
        
        List<Autocompleter.AutocompleterEntry> aelist = mwa.autocomplete(tokens, 1, null);
//        System.out.println("OU1: " + aelist.size());
//        Collections.sort(aelist);
//        
        for (Autocompleter.AutocompleterEntry ae : aelist) {
        	System.out.println(" " + ae.offset + " - s=" + ae.score + " d=" + ae.distance + " correct prefix=" + ae.correctedPrefix);
        	System.out.println("   " + a.getData(ae.offset));
        }
//        
        System.out.println("TIME-OLD IS " + (after-before));
        
        for (int i = 0; i < 200; i++) {
            mwa.autocompleteNew(tokens, 1, null);
        }
//        
        before = System.currentTimeMillis();
        for (int i = 0; i < 500; i++) {
            mwa.autocompleteNew(tokens, 1, null);
        }
        after = System.currentTimeMillis();

        
        List<MultiWordAutocompleterEntry> mwael =  mwa.autocompleteNew(tokens,  1, null);
//        Collections.sort(mwae);
//      
      for (MultiWordAutocompleterEntry mwae : mwael) {
          System.out.println(" " + mwae.offset + " - s=" + mwae.score + " d=" + mwae.distance + " correct prefix=" + mwae.correctedTokens);
          System.out.println("   " + a.getData(mwae.offset));
      }
      
      System.out.println("TIME-NEW IS " + (after-before));
        
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
