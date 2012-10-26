package fr.openstreetmap.search.tree;
import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Ignore;
import org.junit.Test;

import fr.openstreetmap.search.tree.RadixTreeFuzzyLookup.ApproximateMatch;


public class RadixTreeTest {
//    public static void main(String[] args) throws Exception {
//        new RadixTreeTest().big();
//    }
    
    @Test
//    @Ignore
    public void big() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        RadixTreeWriter rtw = new RadixTreeWriter(baos);
        
        long before = System.currentTimeMillis();
        
        BufferedReader br = new BufferedReader(new FileReader(new File("/usr/share/dict/american-english")));
        int lno = 0;
        while (true) {
            String s = br.readLine();
            if (s == null) break;
            s.replace("\n", "");
            rtw.addEntry(s, ++lno);
        }
        
        rtw.flush();
        
        long after = System.currentTimeMillis();
        
        System.out.println("SIZE = " + baos.toByteArray().length + " in " + (after-before));


        RadixTree rt = new RadixTree();
        rt.buffer = baos.toByteArray();
        rt.totalSize = baos.toByteArray().length;

        RadixTreeFuzzyLookup rtfl = null;

        for (int i = 0; i < 100; i++) {
            rtfl = new RadixTreeFuzzyLookup(rt);
            rtfl.match("weldre", 1);
        }
        
        before = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            rtfl = new RadixTreeFuzzyLookup(rt);
            rtfl.match("weldre", 1);
        }
        after = System.currentTimeMillis();
        System.out.println("100 fuzzy in " + (after-before));

        
        System.out.println("****" + rtfl.getMatches().size());
        for (int i = 0; i < rtfl.getMatches().size(); i++) {
            System.out.println("*** " + rtfl.getMatches().get(i).key + " - " + rtfl.getMatches().get(i).distance);
        }
        
       //System.out.println(rt.getEntry("weld"));
    }
    
    @Test
//    @Ignore
    public void fyzzy( ) throws IOException{

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        RadixTreeWriter rtw = new RadixTreeWriter(baos);

        rtw.addEntry("abef", 1);
        rtw.addEntry("abefa", 2);
        rtw.addEntry("abeg", 3);
        rtw.addEntry("abehi", 4);
        rtw.addEntry("abehy", 5);
        rtw.addEntry("abehzk", 6);
        rtw.addEntry("abehzx", 7);
        rtw.addEntry("acd", 8);
        rtw.addEntry("acef", 42);

        rtw.addEntry("acefa", 9);
        rtw.addEntry("acefx", 10);

        rtw.flush();


        RadixTree rt = new RadixTree();
        rt.buffer = baos.toByteArray();
        rt.totalSize = baos.toByteArray().length;

        RadixTreeFuzzyLookup rtfl = new RadixTreeFuzzyLookup(rt);
        rtfl.match("acefh", 1);
        System.out.println("****" + rtfl.getMatches().size());
        for (int i = 0; i < rtfl.getMatches().size(); i++) {
            System.out.println("*** " + rtfl.getMatches().get(i).key + " - " + rtfl.getMatches().get(i).distance);
        }
    } 

    @Test
    @Ignore
    public void fuzzyWithNoRadix( ) throws IOException{

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        RadixTreeWriter rtw = new RadixTreeWriter(baos);

        rtw.addEntry("a", 1);
        rtw.addEntry("ab", 2);
        rtw.addEntry("abc", 2);
        rtw.addEntry("abcd", 2);
        rtw.addEntry("abcde", 2);
        rtw.addEntry("ac", 2);
        rtw.addEntry("aca", 2);
        rtw.addEntry("acb", 2);
        rtw.addEntry("acbd", 2);
        rtw.flush();


        RadixTree rt = new RadixTree();
        rt.buffer = baos.toByteArray();
        rt.totalSize = baos.toByteArray().length;

        RadixTreeFuzzyLookup rtfl = new RadixTreeFuzzyLookup(rt);

        rtfl.match("acd", 1);
        System.out.println("****" + rtfl.getMatches().size());
        for (int i = 0; i < rtfl.getMatches().size(); i++) {
            System.out.println("*** " + rtfl.getMatches().get(i).key + " - " + rtfl.getMatches().get(i).distance);
        }
    } 
    
    public static void main(String[] args) throws Exception {
        new RadixTreeTest().fuzzyWithNoRadix2();
    }
    
    @Test
    @Ignore
    public void fuzzyWithNoRadix2( ) throws IOException{
        byte[] data = IOUtils.toByteArray(new FileInputStream("/data/homes/stenac/public_html/osm/data/radix"));
        
        RadixTree rt = new RadixTree();
        rt.buffer = data;
        rt.totalSize = data.length;
        rt.byteArrayMode = true;

        RadixTreeFuzzyLookup rtfl = new RadixTreeFuzzyLookup(rt);
        rtfl.match("detaillz", 1);
        System.out.println("****" + rtfl.getMatches().size());
        for (int i = 0; i < rtfl.getMatches().size(); i++) {
            System.out.println("*** " + rtfl.getMatches().get(i).key + " - " + rtfl.getMatches().get(i).distance);
        }
    } 
    
    @Test
//    @Ignore
    public void aprefix( ) throws IOException{

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        RadixTreeWriter rtw = new RadixTreeWriter(baos);

        rtw.addEntry("abef", 1);
        rtw.addEntry("abefa", 2);
        rtw.addEntry("abeg", 3);
        rtw.addEntry("abehi", 4);
        rtw.addEntry("abehy", 5);
        rtw.addEntry("abehzk", 6);
        rtw.addEntry("abehzx", 7);
        rtw.addEntry("acd", 8);
        rtw.addEntry("acef", 42);

        rtw.addEntry("acefa", 9);
        rtw.addEntry("acefx", 10);

        rtw.flush();


        System.out.println("OUTPUT\n" + new String(baos.toByteArray()));
        System.out.println("SIZE IS " + baos.toByteArray().length);

        RadixTree rt = new RadixTree();
        rt.buffer = baos.toByteArray();
        rt.totalSize = baos.toByteArray().length;

        assertEquals(1, rt.getEntry("abef"));
        assertEquals(2, rt.getEntry("abefa"));
        assertEquals(3, rt.getEntry("abeg"));


        //            System.out.println("--> " + rt.getEntry("abef"));
        //            System.out.println("--> " + rt.getEntry("abeg"));
        //            System.out.println("--> " + rt.getEntry("abehi"));
        //            System.out.println("--> " + rt.getEntry("abehy"));
        //            System.out.println("--> " + rt.getEntry("abehzk"));
        //            System.out.println("--> " + rt.getEntry("abehzx"));
        //            System.out.println("--> " + rt.getEntry("acd"));
        System.out.println("--> " + rt.getEntry("abefa"));
        //            System.out.println("--> " + rt.getEntry("acefx"));
        //            System.out.println("--> " + rt.getEntry("acef"));
        //            System.out.println("--> " + rt.getEntry("acde"));
        //            System.out.println("--> " + rt.getEntry("acdf"));



    }

    @Test
    @Ignore
    public void distanceOnIncomplete( ) throws IOException{

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        RadixTreeWriter rtw = new RadixTreeWriter(baos);

        rtw.addEntry("abc", 3);
        rtw.addEntry("abcd", 4);
        rtw.addEntry("abcde", 5);
        rtw.addEntry("abcdef", 6);
        rtw.addEntry("abcdefg", 7);
        rtw.flush();

        RadixTree rt = new RadixTree();
        rt.buffer = baos.toByteArray();
        rt.totalSize = baos.toByteArray().length;

        RadixTreeFuzzyLookup rtfl = new RadixTreeFuzzyLookup(rt);
        rtfl.match("abcdef", 1);
        for (ApproximateMatch am : rtfl.getMatches()) {
            System.out.println("" + am.key + " - " + am.distance);
        }


        //            System.out.println("--> " + rt.getEntry("abef"));
        //            System.out.println("--> " + rt.getEntry("abeg"));
        //            System.out.println("--> " + rt.getEntry("abehi"));
        //            System.out.println("--> " + rt.getEntry("abehy"));
        //            System.out.println("--> " + rt.getEntry("abehzk"));
        //            System.out.println("--> " + rt.getEntry("abehzx"));
//                    System.out.println("--> " + rt.getEntry("acd"));
//        System.out.println("--> " + rt.getEntry("abefa"));
        //            System.out.println("--> " + rt.getEntry("acefx"));
        //            System.out.println("--> " + rt.getEntry("acef"));
        //            System.out.println("--> " + rt.getEntry("acde"));
        //            System.out.println("--> " + rt.getEntry("acdf"));



    }

    @Test
    @Ignore
    public void missingLetter( ) throws IOException{

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        RadixTreeWriter rtw = new RadixTreeWriter(baos);

        rtw.addEntry("mar", 3);
        rtw.addEntry("mart", 4);
        rtw.addEntry("marti", 5);
        rtw.addEntry("martig", 6);
        rtw.addEntry("martigu", 7);
        rtw.addEntry("martigue", 7);
        rtw.addEntry("martigues", 7);
        rtw.flush();

        RadixTree rt = new RadixTree();
        rt.buffer = FileUtils.readFileToByteArray(new File("/data/stenac/osm/search/data/out/radix"));// baos.toByteArray();
        rt.byteArrayMode = true;
        rt.totalSize = rt.buffer.length;

        RadixTreeFuzzyLookup rtfl = new RadixTreeFuzzyLookup(rt);
        rtfl.match("martgues", 1);
        for (ApproximateMatch am : rtfl.getMatches()) {
            System.out.println("" + am.key + " - " + am.distance);
        }

    }
    
    /*
    @Test
    public void a( ) throws IOException{
        RadixTreeWriter rtw = new RadixTreeWriter();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        rtw.os = baos;

        rtw.addEntry("abef", 0);
        rtw.addEntry("abeg", 0);
        rtw.addEntry("abehi", 0);
        rtw.addEntry("abehy", 0);
        rtw.addEntry("abehzk", 0);
        rtw.addEntry("abehzx", 0);
        rtw.addEntry("acd", 0);
        rtw.addEntry("acefa", 0);
        rtw.addEntry("acefx", 0);

        rtw.flush();

        System.out.println("OUTPUT\n" + new String(baos.toByteArray()));
    }
     */

}
