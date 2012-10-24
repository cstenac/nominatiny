package fr.openstreetmap.search.autocomplete;

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

import fr.openstreetmap.search.autocomplete.AutocompleteBuilder.ScoredToken;
import fr.openstreetmap.search.text.StringNormalizer;

/**
 * Creates the autocomplete data from DB exports.
 *  
 * Required are:
 *   - A select dump from the city_geom table
 *   - A select dump from the named_ways table
 *   - A select dump from the named_nodes table
 */
public class CreateFromDBOutput {
    static class AdminDesc implements Comparable<AdminDesc>{
        AdminDesc(String name, int level, long pop, long osmId) {
            if (name.equals("France métropolitaine — eaux territoriales")) name = "France";
            this.name = name;
            this.level = level;
            this.pop = pop;
            this.osmId = osmId;
        }
        String name;
        long osmId;
        long pop;
        int level;
        ArrayList<AdminDesc> parents = new ArrayList<AdminDesc>();

        boolean displayable;
        boolean indexable;

        String country;

        public void addParent(AdminDesc parent) {
            // N-W England & co -> not useful
            if (parent.osmId == 151261) return;
            if (parent.osmId == 151164) return;

            if (parent.level < this.level) parents.add(parent);
        }

        public void computeCountry() {
            for (AdminDesc parent: parents) {
                if (parent.level == 2) {
                    country = parent.name;
                    break;
                }
            }
        }

        public void computeAttributes() {
            if (country != null && country.equals("France")) {
                displayable = (level == 2 || level == 6 || level == 8);
                indexable = (level == 2 || level == 6 || level == 8);
            } else if (country != null && country.equals("United Kingdom")) {
                displayable = (level == 2 || level == 6 || level == 8);
                indexable = (level == 2 || level == 6 || level == 8);
            } else {
                displayable = true;
                indexable = true;
            }
        }

        @Override
        public int compareTo(AdminDesc arg0) {
            return -this.level + arg0.level;
        }
    }

    Map<Long, AdminDesc> adminRelations =new HashMap<Long, AdminDesc>();

    Map<String, AutocompleteBuilder> builders = new HashMap<String, AutocompleteBuilder>();
    File outDir;
    Writer rawOut;

    public static Set<String> definitelyStopWords = new HashSet<String>();
    static {
        // No definitely stop words yet :)
    }

    private AutocompleteBuilder newBuilder(String name) throws IOException {
        File f = new File(outDir, name);
        f.mkdirs();

        AutocompleteBuilder builder = new AutocompleteBuilder(new File(f, "tmp.unsorted"),
                new File(f, "tmp.sorted"), new File(f, "radix"), new File(f, "data"));
        builder.nbValues = 4000000;

        return builder;
    }

    private void  addBuilder(String name) throws IOException {
        builders.put(name, newBuilder(name));
    }

    static String getBuilderName(double lon, double lat) {
        // France: -6 41, -6 51, 10 51, 10 41, -6 41
        if (41 < lat && lat < 51 && -6 < lon && lon < 10) return "france";
        if (lon < 8) return "less7";
        return null;
        //        if (lon < 14.0) return "8-14";
        //        return "more14";
    }

    public CreateFromDBOutput(File outDir) throws IOException {
        this.outDir = outDir;
        addBuilder("france");
        addBuilder("less7");
        addBuilder("4-8");
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
                List<AdminDesc> directlyReferenced = new ArrayList<CreateFromDBOutput.AdminDesc>();
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
                    List<AdminDesc> tmp = new ArrayList<CreateFromDBOutput.AdminDesc>();
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
                Set<String> indexableNames = new HashSet<String>();
                Set<AdminDesc> displayableNames = new HashSet<AdminDesc>();
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
                    displayableNames.add(cityDesc);
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

                        if (parentDesc.displayable) {
                            displayableNames.add(parentDesc);
                        }
                        if (parentDesc.indexable) {
                            indexableNames.add(parentDesc.name);
                        }
                    }
                }
                for (String indexableName : indexableNames) {
                    tokenize(indexableName, tokens, adminScore);
                }

                List<String> sortedDisplayableNames = new ArrayList<String>();
                AdminDesc[] l = displayableNames.toArray(new AdminDesc[0]);
                Arrays.sort(l);
                for (AdminDesc a : l) sortedDisplayableNames.add(a.name);

                /* Compute the display value */
                byte[] value = new OSMAutocompleteUtils().encodeData(isWays, type, name.isEmpty() ? ref : name, 
                        sortedDisplayableNames.toArray(new String[0]), lon, lat, id);

                builders.get(getBuilderName(lon, lat)).addEntry(tokens, value, true);

                // Debugging output
                rawOut.write(name.isEmpty() ? ref : name + "\t" + id + "\t" + type + "\t" + StringUtils.join(tokens, "-") + "\t(lat=" + lat + ",lon=" + lon + ")");
                rawOut.write("\tisIn: " + StringUtils.join(thisAllAdminNames, " // ") + "\tpop:" + biggestPop + "\n");

                // TODO: Boost correct-length matches ?

                if (nlines % 50000 == 0) {
                    System.out.println("Parsed " +  nlines + (isWays ? " ways" : " nodes") + ", id=" + id + " name=" + name);
                }
                //                if (nlines > 50000) break;
            } catch (Exception e) {
                logger.error("Failed to parse, last known id: " + lastId, e);//\n" + line, e);
                //                throw e;
            }
        }
        System.out.println("Parsed " + nlines  +(isWays ? " ways" : " nodes"));
    }


    public void parseCityList(File  f) throws Exception {
        CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(f), "utf8"));
        //BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "utf8"));

        int nlines = 0;
        int withPop = 0;
        while (true) {
            //            String line = 
            //            if (line == null) break;
            //            if (nlines ++ <= 2) continue;

            try {
                String[] chunks = reader.readNext();;//StringUtils.splitPreserveAllTokens(line, ',');
                if (chunks == null) break;
                nlines++;

                if (chunks.length > 4) {
                    //                    System.out.println(line);
                }

                long id = Long.parseLong(chunks[0]);
                long pop = 0;
                if (chunks[1].length() != 0) {
                    withPop++;
                    pop = Long.parseLong(chunks[1]);
                }

                String name = chunks[2].replaceAll("^\\s+", "");
                int level = Integer.parseInt(chunks[3]);

                adminRelations.put(id, new AdminDesc(name, level, pop, id));

                if (nlines % 10000 == 0) {
                    System.out.println("Parsed " + nlines + " cities, id=" + id + " name=" + name );
                }
            } catch (Exception e) {
                System.out.println("FAILED TO PARSE " );
            }

        }
        System.out.println("Parsed " + adminRelations.size()  +" cities (" + withPop + " with pop info)");
    }

    public void parseCityParents(File f) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "utf8"));
        int nlines = 0;
        while (true) {
            String line = br.readLine();
            if (line == null) break;
            if (nlines ++ <= 2) continue;

            String[] chunks = StringUtils.splitPreserveAllTokens(line, ',');

            long parent_id = Long.parseLong(chunks[0]);
            long child_id = Long.parseLong(chunks[1]);

            AdminDesc parent = adminRelations.get(parent_id);
            AdminDesc child = adminRelations.get(child_id);
            if (parent == null || child == null) {
                System.out.println("DID NOT FIND " + parent_id + " " + child_id);
            } else {
                child.addParent(parent);
            }
        }

        System.out.println("Computing admin attributes");
        for (AdminDesc ad : adminRelations.values()) ad.computeCountry();
        for (AdminDesc ad : adminRelations.values()) ad.computeAttributes();
        for (AdminDesc ad : adminRelations.values()) Collections.sort(ad.parents);

    }

    public void flush() throws Exception {
        rawOut.close();

        for (String name : builders.keySet()) {
            System.out.println("Flushing builder " + name);
            AutocompleteBuilder builder = builders.get(name);
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

        CreateFromDBOutput instance = new CreateFromDBOutput(new File(outDir));
        instance.parseCityList(new File(inDir, "admin-list"));
        instance.parseCityParents(new File(inDir, "admin-parents"));
        instance.parseNamedWaysOrNodes(new File(inDir, "named-nodes"), false);
        instance.parseNamedWaysOrNodes(new File(inDir, "named-ways"), true);

        instance.flush();
    }

    private static Logger logger = Logger.getLogger("build");
}