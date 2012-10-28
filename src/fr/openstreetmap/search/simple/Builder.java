package fr.openstreetmap.search.simple;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import au.com.bytecode.opencsv.CSVReader;

import fr.openstreetmap.search.autocomplete.AutocompleteBuilder;
import fr.openstreetmap.search.autocomplete.CompleteWordBuilder;
import fr.openstreetmap.search.autocomplete.IndexBuilder;
import fr.openstreetmap.search.autocomplete.IndexBuilder.ScoredToken;
import fr.openstreetmap.search.text.StringNormalizer;

/**
 * Creates the autocomplete data from DB exports.
 *  
 * Required are:
 *   - A select dump from the city_geom table
 *   - A select dump from the named_ways table
 *   - A select dump from the named_nodes table
 */
public class Builder {
    Map<Long, AdminDesc> adminRelations;

    Map<String, IndexBuilder> builders = new HashMap<String, IndexBuilder>();
    File outDir;
    Writer rawOut;
    
    boolean prefix;

    public static Set<String> definitelyStopWords = new HashSet<String>();
    static {
        // No definitely stop words yet :)
    }

    private IndexBuilder newBuilder(String name) throws IOException {
        File f = new File(outDir, name);
        f.mkdirs();

        if (prefix) {
        	AutocompleteBuilder builder = new AutocompleteBuilder(f);
        	builder.nbValues = 4000000;
        	return builder;
        } else {
        	CompleteWordBuilder builder = new CompleteWordBuilder(f);
        	builder.nbValues = 4000000;
        	return builder;

        }
    }

    private void  addBuilder(String name) throws IOException {
        builders.put(name, newBuilder(name));
    }

    static String getBuilderName(double lon, double lat) {
        // France: -6 41, -6 51, 10 51, 10 41, -6 41
        if (41 < lat && lat < 51.5 && -6 < lon && lon < 10) return "france";
        if (lon < 7) return "less7";
        if (lon < 14.0) return "8-14";
        return "more14";
    }

    public Builder(File outDir) throws IOException {
        this.outDir = outDir;
        addBuilder("france");
        addBuilder("less7");
        addBuilder("8-14");
        addBuilder("more14");

        rawOut = new OutputStreamWriter(new FileOutputStream(new File(outDir, "rawout")), "utf8");
    }

    public long scoreWay(List<ScoredToken> nameTokens, String type, long biggestPop) {
        long baseScore = 0;
        if (type.equals("residential")){
            baseScore = 8000;
            for (ScoredToken sc : nameTokens) {
                if (sc.token.equals("avenue") || sc.token.equals("boulevard")) {
                    baseScore = 10000;
                }
            }
        } else if (type.equals("highway")) {
            baseScore= 8000;
        } else {
            baseScore = 6000;
        }
        /* Max boost for a 10M city: 1999 */
        baseScore += (biggestPop * 1999) / (10 * 1000 * 1000);

        return baseScore;
    }

    public long scoreNode(List<ScoredToken> nameTokens, String type, long biggestPop) {
        long baseScore = 0;
        if (type.equals("place")){
            baseScore = 20000;
        } else {
            baseScore = 5000;
        }
        /* Max boost for a 10M city: 14999 */
        baseScore += (biggestPop * 14999) / (10 * 1000 * 1000);

        return baseScore;
    }

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

    // Input file : id | name | ref | type | cities | centroid
    public void parseNamedWaysOrNodes(File f, boolean isWays) throws Exception {
        List<ScoredToken> tokens = new ArrayList<ScoredToken>();

        CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(f), "utf8"));
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "utf8"));
        int nlines = 0;
        long lastId = 0;
        while (true) {
            String[] chunks = reader.readNext();
            if (chunks == null) break;
            nlines++;

            //            if (nlines % 3 != 0) continue; // Sampling, TEMPORARY

            tokens.clear();

            try {
                String c0 = chunks[0];//.replace(" ", "");
                if (!StringUtils.isNumeric(c0)) break;
                long id = Long.parseLong(c0);
                lastId = id;

                String name = chunks[1];//.replaceAll("^\\s+|\\s+$", "");
                String ref = chunks[2];//.replaceAll("^\\s+", "");
                String type = chunks[3];//StringUtils.replace(chunks[3], " " ,"");
                String[] centroidCoords =  chunks[5].replaceAll("POINT|\\(|\\)", "").split(" ");
                double lon = Double.parseDouble(centroidCoords[0]);
                double lat = Double.parseDouble(centroidCoords[1]);

                if (getBuilderName(lon, lat) == null) continue;

                /* Build the list of cities (admin level 8) we have definitely registered this /node/way as being in */
                List<AdminDesc> directlyReferenced = new ArrayList<AdminDesc>();
                String[] cities = chunks[4].substring(1, chunks[4].length() - 1).split(",");
                if (cities.length == 1 && cities[0].length() == 0) cities = new String[0];

                int minLevel = Integer.MAX_VALUE, maxLevel = 0;
                for (String cityIdStr: cities) {
                    if (cityIdStr.length() == 0) continue;
                    long cityId = Long.parseLong(cityIdStr);
                    AdminDesc cityDesc = adminRelations.get(cityId);
                    if (cityDesc != null) {
                        minLevel = Math.min(minLevel, cityDesc.level);
                        maxLevel = Math.max(maxLevel, cityDesc.level);
                        directlyReferenced.add(cityDesc);
                    }
                }
                /* Only keep the innermost level */
                {
                    List<AdminDesc> tmp = new ArrayList<AdminDesc>();
                    for (AdminDesc d : directlyReferenced) {
                        if (d.level == maxLevel) tmp.add(d);
                    }
                    directlyReferenced = tmp;
                }

                /* Find out the largest population */
                long biggestPop = 0;
                for (AdminDesc cd : directlyReferenced) biggestPop = Math.max(cd.pop, biggestPop);

                long score = isWays ? scoreWay(tokens, type, biggestPop) : scoreNode(tokens, type, biggestPop);

                if (!name.isEmpty()) {
                    tokenize(name, tokens, score);
                }
                if (!ref.isEmpty()) {
                    tokenize(ref, tokens, score);
                }

                /* Some words that we *know* will be stop words, so let's not waste time going through them to clip
                 * them later
                 */
                ListIterator<ScoredToken> lit = tokens.listIterator();
                while (lit.hasNext()) {
                    ScoredToken t = lit.next();
                    if (definitelyStopWords.contains(t.token)) { 
                        lit.remove();
                    }
                }

                long adminScore = 1; // The display form of the admin relations is less important.
                
                List<Long> storedIds = new ArrayList<Long>();
                Set<String> indexableNames = new HashSet<String>();
//                Set<AdminDesc> displayableNames = new HashSet<AdminDesc>();
                List<String> thisAllAdminNames = new ArrayList<String>(); 

                for (AdminDesc cityDesc : directlyReferenced) {
                    // Debug
                    thisAllAdminNames.add(cityDesc.name +  "(l=" + cityDesc.level + ",i=" + cityDesc.indexable + ",d=" + cityDesc.displayable + ")");

                    if (!isWays) {
                        // We are scoring the place itself!  Boost the "name" tokens
                        if (cityDesc.name.equals(name)) {
                            for (ScoredToken sc : tokens) sc.score += 10000;
                        }
                    }
                    storedIds.add(cityDesc.osmId);
//                    displayableNames.add(cityDesc);
                    indexableNames.add(cityDesc.name);

                    for (AdminDesc parentDesc : cityDesc.parents) {
                        // Debug
                        thisAllAdminNames.add(parentDesc.name + "(l=" + parentDesc.level + ",i=" + parentDesc.indexable + ",d=" + parentDesc.displayable + ")");

                        if (!isWays) {
                            // We are scoring the place itself (it's actually an admin_level higher than 8) !  Boost the "name" tokens
                            if (parentDesc.name.equals(name)) {
                                for (ScoredToken sc : tokens) sc.score += 5000;
                            }
                        }

//                        if (parentDesc.displayable) {
//                            displayableNames.add(parentDesc);
//                        }
                        if (parentDesc.indexable) {
                            indexableNames.add(parentDesc.name);
                        }
                    }
                }
                for (String indexableName : indexableNames) {
                    tokenize(indexableName, tokens, adminScore);
                }

//                List<String> sortedDisplayableNames = new ArrayList<String>();
//                AdminDesc[] l = displayableNames.toArray(new AdminDesc[0]);
//                Arrays.sort(l);
//                for (AdminDesc a : l) sortedDisplayableNames.add(a.name);
                long[] d = new long[storedIds.size()];
                for (int i = 0; i < d.length; i++) d[i] = storedIds.get(i);

                /* Compute the display value */
                byte[] value = new OSMAutocompleteUtils().encodeData(isWays, type, name.isEmpty() ? ref : name, 
                        d, lon, lat, id);

                builders.get(getBuilderName(lon, lat)).addEntry(tokens, value, true);

                // Debugging output
                rawOut.write(name.isEmpty() ? ref : name + "\t" + id + "\t" + type + "\t" + StringUtils.join(tokens, "-") + "\t(lat=" + lat + ",lon=" + lon + ")");
                rawOut.write("\tisIn: " + StringUtils.join(thisAllAdminNames, " // ") + "\tpop:" + biggestPop + "\n");

                // TODO: Boost correct-length matches ?

                if (nlines % 50000 == 0) {
                    System.out.println("Parsed " +  nlines + (isWays ? " ways" : " nodes") + ", id=" + id + " name=" + name);
                }
//                if (nlines > 200000) break;
                //                if (nlines > 50000) break;
            } catch (Exception e) {
                logger.error("Failed to parse, last known id: " + lastId, e);//\n" + line, e);
                //                throw e;
            }
        }
        System.out.println("Parsed " + nlines  +(isWays ? " ways" : " nodes"));
    }

    public void flush() throws Exception {
        rawOut.close();

        for (String name : builders.keySet()) {
            System.out.println("Flushing builder " + name);
            IndexBuilder builder = builders.get(name);
            builder.flush();
            FileWriter fwr = new FileWriter(outDir + "/" + name + "/stopwords");
            for (String s : builder.clippedWords) {
                fwr.write(s + "\n");
            }

            fwr.close();
        }
    }

    public static void main(String[] args) throws Exception {
        Logger.getRootLogger().removeAllAppenders();
        BasicConfigurator.configure();
        File inDir  = new File(args[0]);
        String outDir = args[1];
        boolean prefix = Boolean.parseBoolean(args[2]);

        Builder instance = new Builder(new File(outDir));
        instance.prefix = prefix;
        
        instance.adminRelations = AdminDesc.parseCityList(new File(inDir, "admin-list"));
        AdminDesc.parseCityParents(new File(inDir, "admin-parents"), instance.adminRelations);
        
        instance.parseNamedWaysOrNodes(new File(inDir, "named-nodes"), false);
        instance.parseNamedWaysOrNodes(new File(inDir, "named-ways"), true);

        instance.flush();
    }

    private static Logger logger = Logger.getLogger("build");
}