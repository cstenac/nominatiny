package fr.openstreetmap.search.polygons;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import au.com.bytecode.opencsv.CSVReader;

import fr.openstreetmap.search.autocomplete.AutocompleteBuilder;
import fr.openstreetmap.search.autocomplete.IndexBuilder.ScoredToken;
import fr.openstreetmap.search.simple.AdminDesc;
import fr.openstreetmap.search.text.StringNormalizer;

public class Builder {
    public static StringTokenizer getTokenizer(String text) {
        return  new StringTokenizer(text, "\t\n\r (),-'[]/");
    }

    public static void tokenize(String text, List<ScoredToken> tokens, long score) {
        StringTokenizer st = getTokenizer(text);
        while (st.hasMoreTokens()) {
            String token = StringNormalizer.normalize(st.nextToken()).toLowerCase();
            tokens.add(new ScoredToken(token, score));
        }
    }
    public static void tokenize(String text, List<String> tokens) {
        StringTokenizer st = getTokenizer(text);
        while (st.hasMoreTokens()) {
            String token = StringNormalizer.normalize(st.nextToken()).toLowerCase();
            tokens.add(token);
        }
    }

	
	 public static void main(String[] args) throws Exception {
	        Logger.getRootLogger().removeAllAppenders();
	        BasicConfigurator.configure();

	        File inDir  = new File(args[0]);
	        String outDir = args[1];
	        
	        
	        Map<Long, AdminDesc> adminRelations = AdminDesc.parseCityList(new File(inDir, "admin-list"));
	        AdminDesc.parseCityParents(new File(inDir, "admin-parents"), adminRelations);
	        
	        File f = new File(outDir);
	        f.mkdirs();

	        AutocompleteBuilder builder = new AutocompleteBuilder(f);
	        builder.nbValues = 4000000;

	        CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(new File(inDir, "admin-polygons")), "utf8"));
	        int nlines = 0;
	        while (true) {
	        	List<ScoredToken> tokens = new ArrayList<AutocompleteBuilder.ScoredToken>();
	        	
	            String[] chunks = reader.readNext();
	            if (chunks == null) break;
	            nlines++;
	            
	            long adminId = Long.parseLong(chunks[0]);
	            String wkt = chunks[1];

	            AdminDesc ad = adminRelations.get(adminId);
	            
	            if (ad == null) {
	            	System.out.println("PARSE " + adminId + " -> " + ad);
	            	continue;
	            }
	            
	            String name = ad.name;
	            tokenize(ad.name, tokens, 2000 + ad.pop / 100);
	            for (AdminDesc parent: ad.parents) {
	            	tokenize(parent.name, tokens, 1000);
	            	if (parent.displayable) {
	            		name += ", " + parent.name;
	            	}
	            }
	            
	            String displayForm = name + "|" + wkt + "|" + adminId;
	            
	            builder.addEntry(tokens, displayForm.getBytes("utf8"), true);
	        }
	        builder.flush();
	    }

	    private static Logger logger = Logger.getLogger("build");
}
