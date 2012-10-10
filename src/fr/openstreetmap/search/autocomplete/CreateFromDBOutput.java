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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.json.JSONObject;

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
    static class CityDesc {
        CityDesc(String name, long pop) {
            this.name = name;
            this.pop = pop;
        }
        String name;
        long pop;
    }

    Map<Long, CityDesc> cityNames =new HashMap<Long, CityDesc>();
    AutocompleteBuilder builder;
    File outDir;
    Writer rawOut;

    public static Set<String> definitelyStopWords = new HashSet<String>();
    static {
        //        definitelyStopWords.add("rue");
        //        definitelyStopWords.add("avenue");
        //        definitelyStopWords.add("route");
        //        definitelyStopWords.add("boulevard");
        //        definitelyStopWords.add("chemin");
    }

    public CreateFromDBOutput(File outDir) throws IOException {
        builder = new AutocompleteBuilder(new File(outDir, "tmp.unsorted"),
                new File(outDir, "tmp.sorted"), new File(outDir, "radix"), new File(outDir, "data"));
        builder.nbValues = 600000;
        this.outDir = outDir;
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

    public static void tokenize(String text, List<ScoredToken> tokens, long score) {
        StringTokenizer st = new StringTokenizer(text, "\t\n\r (),-'[]/");
        while (st.hasMoreTokens()) {
            String token = StringNormalizer.normalize(st.nextToken()).toLowerCase();
            tokens.add(new ScoredToken(token, score));
        }
    }
    public static void tokenize(String text, List<String> tokens) {
        StringTokenizer st = new StringTokenizer(text, "\t\n\r (),-'[]/");
        while (st.hasMoreTokens()) {
            String token = StringNormalizer.normalize(st.nextToken()).toLowerCase();
            tokens.add(token);
        }
    }

    // Input file : id | name | ref | type | cities | centroid
    public void parseNamedWaysOrNodes(String namedWaysFile, boolean isWays) throws Exception {
        List<ScoredToken> tokens = new ArrayList<ScoredToken>();

        File f = new File(namedWaysFile);
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "utf8"));
        int nlines = 0;
        long lastId = 0;
        while (true) {
            String line = br.readLine();
            if (line == null) break;
            if (nlines ++ <= 2) continue;

            tokens.clear();

            try {
                String[] chunks = StringUtils.split(line, '|');

                String c0 = chunks[0].replace(" ", "");
                if (!StringUtils.isNumeric(c0)) break;
                long id = Long.parseLong(c0);
                lastId = id;
                //
                //                System.out.println("*****");
                //                System.out.println(line);
                //                System.out.println(chunks[1]);
                //                System.out.println(chunks[2]);
                //                System.out.println(chunks[3]);
                //                System.out.println(chunks[4]);
                //                

                String name = chunks[1].replaceAll("^\\s+|\\s+$", "");
                String ref = chunks[2].replaceAll("^\\s+", "");
                String type = StringUtils.replace(chunks[3], " " ,"");
                String[] centroidCoords =  chunks[5].replaceAll(" POINT|\\(|\\)", "").split(" ");
                //                System.out.println("*CHUNK 5 IS " + chunks[5]);
                double lon = Double.parseDouble(centroidCoords[0]);
                double lat = Double.parseDouble(centroidCoords[1]);

                /* Build the list of cities this node/way is in */
                List<CityDesc> cityDescs = new ArrayList<CreateFromDBOutput.CityDesc>();
                String[] cities = chunks[4].replaceAll("\\s+|\\{|\\}", "").split(",");
                for (String cityIdStr: cities) {
                    if (cityIdStr.length() == 0) continue;
                    long cityId = Long.parseLong(cityIdStr);
                    CityDesc cityDesc = cityNames.get(cityId);
                    if (cityDesc != null) {
                        cityDescs.add(cityDesc);
                    }
                }
                /* Find out the largest population */
                long biggestPop = 0;
                for (CityDesc cd : cityDescs) biggestPop = Math.max(cd.pop, biggestPop);

                long score = isWays ? scoreWay(tokens, type, biggestPop) : scoreNode(tokens, type, biggestPop);
                if (name.equals("Le Bourg")) {
                    System.out.println("Le Bourg score= " + score + " type=" + type + " bp " + biggestPop);
                }


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

                /* All the tokens until now are scored like the original score */
                int tokensWithBaseScore = tokens.size();

                long cityScore = 1; // The display form of the city is less important.

                String cityDisplay = "";
                List<String> thisCityNames = new ArrayList<String>(); 
                for (CityDesc cityDesc : cityDescs) {
                    // We are scoring the city itself!  Boost the "name" tokens
                    if (!isWays) {
                        if (cityDesc.name.equals(name)) {
                            for (ScoredToken sc : tokens) sc.score += 10000;
                        }
                    }

                    thisCityNames.add(cityDesc.name);
                    tokenize(cityDesc.name, tokens, cityScore);
                    if (cityDisplay.length() > 0) cityDisplay += ", ";
                    cityDisplay += cityDesc.name;


                }
                
                if (name.equals("Le Bourg")) {
                    System.out.println("Le Bourg AFTER BOOST tokens=" + StringUtils.join(tokens, "-"));
                }

                // Beware, don't use type as a token. It's useless, it will be clipped anyway !
                // tokens.add(type);

                //                long[] scores = new long[tokens.size()];
                //                for (int i = 0; i < tokensWithBaseScore; i++) scores[i] = score;
                //                for (int i = tokensWithBaseScore; i < tokens.size(); i++) scores[i] = cityScore;

                /* Compute the display value */
                //                JSONObject obj = new JSONObject();
                //                obj.put("name", name.isEmpty() ? ref : name);
                //                obj.put("city", cityDisplay);
                //                obj.put("type", type);
                //                obj.put("lat", lat);
                //                obj.put("lon", lon);
                //                String value = obj.toString();
                byte[] value = new OSMAutocompleteUtils().encodeData(isWays, type, name.isEmpty() ? ref : name, 
                        thisCityNames.toArray(new String[0]), lon, lat);

                //                System.out.println("ADD " + name + " in " + thisCityNames + " toks=" + StringUtils.join(tokens, "-") + " sc=" + Arrays.toString(scores));
                builder.addEntry(tokens, value, true);
                // Good test: rue édouard de <-- will put "avenue édouard" in "rueil" first.
                
                rawOut.write(name.isEmpty() ? ref : name + "\t" + id + "\t" + type + "\t" + StringUtils.join(tokens, "-") + "\tpop:" + biggestPop + "\n");

                // TODO: Boost correct-length matches ?

                if (nlines % 50000 == 0) {
                    System.out.println("Parsed " +  nlines + (isWays ? " ways" : " nodes") + ", id=" + id + " name=" + name);
                }
                //                if (nlines > 50000) break;
            } catch (Exception e) {
                logger.error("Failed to parse, last known id: " + lastId);//, e);//\n" + line, e);
                //                throw e;
            }
        }
        System.out.println("Parsed " + nlines  +(isWays ? " ways" : " nodes"));
    }


    public void parseCityList(String cityListFile) throws Exception {
        File f = new File(cityListFile);
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "utf8"));

        int nlines = 0;
        int withPop = 0;
        while (true) {
            String line = br.readLine();
            if (line == null) break;
            if (nlines ++ <= 2) continue;

            String[] chunks = StringUtils.splitPreserveAllTokens(line, ',');

            if (chunks.length > 3) {
                System.out.println(line);
            }

            long id = Long.parseLong(chunks[0]);
            long pop = 0;
            if (chunks[1].length() != 0) {
                withPop++;
                pop = Long.parseLong(chunks[1]);
            }

            String name = chunks[2].replaceAll("^\\s+", "");

            cityNames.put(id, new CityDesc(name, pop));

            if (nlines % 1000 == 0) {
                System.out.println("Parsed " + nlines + " cities, id=" + id + " name=" + name );
            }
        }
        System.out.println("Parsed " + cityNames.size()  +" cities (" + withPop + " with pop info)");
    }

    public void flush() throws Exception {
        builder.flush();
        rawOut.close();

        FileWriter fwr = new FileWriter(outDir + "/stopwords");
        for (String s : builder.clippedWords) {
            fwr.write(s + "\n");
        }
        fwr.close();
    }

    public static void main(String[] args) throws Exception {
        Logger.getRootLogger().removeAllAppenders();
        BasicConfigurator.configure();
        String inCities = args[0];

        String inWays = args[1];
        String inNodes = args[2];
        String outDir = args[3];

        CreateFromDBOutput instance = new CreateFromDBOutput(new File(outDir));
        instance.parseCityList(inCities);
        instance.parseNamedWaysOrNodes(inNodes, false);
        instance.parseNamedWaysOrNodes(inWays, true);

        instance.flush();
    }

    private static Logger logger = Logger.getLogger("build");
}
