package fr.openstreetmap.search.autocomplete.test;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import fr.openstreetmap.search.autocomplete.AutocompleteBuilder;


public class MultiAutocompleterBuilderTest {
    public static void main(String[] args) throws Exception {
        AutocompleteBuilder ab = new AutocompleteBuilder(new File("/data.2/unsorted"), new File("/data.2/sorted"), new File("/data.2/radix"), new File("/data.2/data"));
        ab.nbValues = 10000;
        
        
        BufferedReader br = new BufferedReader(new FileReader(new File("/home/clement/france-names")));
        int lno = 0;
        while (true) {
            String s = br.readLine();
            if (s == null) break;
            s.replace("\n", "");
            if (s.contains("'")) continue;
            
            s= s.toLowerCase();
            String[] chunks = s.split("\\s");
            ab.addMultiEntry(chunks, s, 42);
            
            if (lno++ % 5000 == 0) {
            	System.out.println("Added " + lno + " entries");
            }
        }
        
        ab.flush();
    }
}
