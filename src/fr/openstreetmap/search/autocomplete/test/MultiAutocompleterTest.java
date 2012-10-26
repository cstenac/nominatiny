package fr.openstreetmap.search.autocomplete.test;
import java.io.File;
import java.io.RandomAccessFile;
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

        int nbPreHeat = 500;
        int nbLoops = 500;

        //        System.in.read();

        //        System.out.println("go");
        FileChannel radixChannel = new RandomAccessFile(new File("/data/homes/stenac/public_html/osm/data/radix"), "r").getChannel();
        MappedByteBuffer radixDirectBuffer = radixChannel.map(MapMode.READ_ONLY, 0, radixChannel.size());

        byte[] radixData = new byte[(int)radixChannel.size()];
        radixDirectBuffer.get(radixData, 0, (int)radixChannel.size());

        //        ByteBuffer radixBuffer = radixDirectBuffer;

        FileChannel dataChannel = new RandomAccessFile(new File("/data/homes/stenac/public_html/osm/data/data"), "r").getChannel();
        MappedByteBuffer dataBuffer = dataChannel.map(MapMode.READ_ONLY, 0, dataChannel.size());

        MultipleWordsAutocompleter mwa = new MultipleWordsAutocompleter();
        mwa.radixBuffer = radixData;
        mwa.dataBuffer = dataBuffer;


        List<String> tokens = new ArrayList<String>();
        tokens.add("ana");

//        tokens.add("boulogne");
        //        tokens.add("anna");

        for (int i = 0; i < nbPreHeat; i++) {
            mwa.autocomplete(tokens, 1, null);
        }
        long before = System.currentTimeMillis();
        for (int i = 0; i < nbLoops; i++) {
            mwa.autocomplete(tokens, 1, null);
        }
        long after = System.currentTimeMillis();
        System.out.println("TIME-OLD IS " + (after-before));


        List<Autocompleter.AutocompleterEntry> aelist = mwa.autocompleteOld(tokens, 1, null);
        //        System.out.println("OU1: " + aelist.size());
        Collections.sort(aelist);
        //        
        //        for (Autocompleter.AutocompleterEntry ae : aelist) {
        //        	System.out.println(" " + ae.offset + " - s=" + ae.score + " d=" + ae.distance + " correct prefix=" + ae.correctedPrefix);
        //        	System.out.println("   " + a.getData(ae.offset));
        //        }





        //        for (int i = 0; i < 200; i++) {
        //            mwa.autocompleteNew(tokens, 1, null);
        //        }
        //        before = System.currentTimeMillis();
        //        for (int i = 0; i < 200; i++) {
        //            mwa.autocompleteNew(tokens, 1, null);
        //        }
        //        after = System.currentTimeMillis();
        //        System.out.println("TIME-NEW IS " + (after-before));


        for (int i = 0; i < nbPreHeat; i++) {
            mwa.autocomplete(tokens, 1, null);
        }
        before = System.currentTimeMillis();
        for (int i = 0; i < nbLoops; i++) {
            mwa.autocomplete(tokens, 1, null);
        }
        after = System.currentTimeMillis();
        System.out.println("TIME-NEW IS " + (after-before));
        
        
        for (int i = 0; i < nbPreHeat; i++) {
            mwa.autocompleteLong(tokens, 1, null);
        }
        before = System.currentTimeMillis();
        for (int i = 0; i < nbLoops; i++) {
            mwa.autocompleteLong(tokens, 1, null);
        }
        after = System.currentTimeMillis();
        System.out.println("TIME-NEW-HASH IS " + (after-before));

        //        for (int i = 0; i < nbPreHeat; i++) {
        //            mwa.autocompleteNew3(tokens, 1, null);
        //        }
        //        before = System.currentTimeMillis();
        //        for (int i = 0; i < nbLoops; i++) {
        //            mwa.autocompleteNew3(tokens, 1, null);
        //        }
        //        after = System.currentTimeMillis();
        //        System.out.println("TIME-NEW3 IS " + (after-before));
        //
        //        
        //        for (int i = 0; i < nbPreHeat; i++) {
        //            mwa.autocompleteNew4(tokens, 1, null);
        //        }
        //        before = System.currentTimeMillis();
        //        for (int i = 0; i < nbLoops; i++) {
        //            mwa.autocompleteNew4(tokens, 1, null);
        //        }
        //        after = System.currentTimeMillis();
        //        System.out.println("TIME-NEW4 IS " + (after-before));


        List<MultiWordAutocompleterEntry> mwael =  mwa.autocomplete(tokens,  1, null);
        System.out.println("FOUND " + mwael + " entries");
        //        Collections.sort(mwae);
        //      
        for (MultiWordAutocompleterEntry mwae : mwael) {
//            System.out.println(" " + mwae.offset + " - s=" + mwae.score + " d=" + mwae.distance + " correct prefix=" + mwae.correctedTokens);
            //          System.out.println("   " + a.getData(mwae.offset));
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
