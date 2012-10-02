package fr.openstreetmap.search.autocomplete;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
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
    Map<Long, String> cityNames =new HashMap<Long, String>();
    AutocompleteBuilder builder;
    File outDir;
    
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
    }

    public long scoreWay(List<String> nameTokens, String type) {
        if (type.equals("residential")){
            if (nameTokens.contains("avenue") || nameTokens.contains("boulevard")) {
                return 1000;
            } else {
                return 800;
            }
        } else if (type.equals("highway")) {
            return 800;
        }
        return 600;
    }

    public long scoreNode(List<String> nameTokens, String type) {
        if (type.equals("place")){
            return 2000;
        } else {
            return 500;
        }
    }

    public static void tokenize(String text, List<String> tokens) {
        StringTokenizer st = new StringTokenizer(text, "\t\n\r (),-'[]");
        while (st.hasMoreTokens()) {
            String token = StringNormalizer.normalize(st.nextToken()).toLowerCase();
            tokens.add(token);
        }
    }

    // Input file : id | name | ref | type | cities | centroid
    public void parseNamedWaysOrNodes(String namedWaysFile, boolean isWays) throws Exception {
        List<String> tokens = new ArrayList<String>();

        File f = new File(namedWaysFile);
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "utf8"));
        int nlines = 0;
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
//
//                System.out.println("*****");
//                System.out.println(line);
//                System.out.println(chunks[1]);
//                System.out.println(chunks[2]);
//                System.out.println(chunks[3]);
//                System.out.println(chunks[4]);
//                
                
                String name = chunks[1].replaceAll("^\\s+", "").replaceAll("\\s+$", "");
                String ref = chunks[2].replaceAll("^\\s+", "");
                String type = chunks[3].replace(" ", "");
                String[] centroidCoords =  chunks[5].replace(" POINT", "").replace("(", "").replace(")", "").split(" ");
//                System.out.println("*CHUNK 5 IS " + chunks[5]);
                double lon = Double.parseDouble(centroidCoords[0]);
                double lat = Double.parseDouble(centroidCoords[1]);

                if (!name.isEmpty()) {
                    tokenize(name, tokens);
                }
                long score = isWays ? scoreWay(tokens, type) : scoreNode(tokens, type);

                if (!ref.isEmpty()) {
                    tokenize(ref, tokens);
                }
                
                /* Some words that we *know* will be stop words, so let's not waste time going through them to clip
                 * them later
                 */
                ListIterator<String> lit = tokens.listIterator();
                while (lit.hasNext()) {
                    String t = lit.next();
                    if (definitelyStopWords.contains(t)) { 
                        lit.remove();
                    }
                }

                String[] cities = chunks[4].replaceAll("\\s+", "").replace("{", "").replace("}", "").split(",");
                String cityDisplay = "";
                for (String cityIdStr: cities) {
                    if (cityIdStr.length() == 0) continue;
                    long cityId = Long.parseLong(cityIdStr);
                    String cityName = cityNames.get(cityId);
                    if (cityName != null) {
                        tokenize(cityName, tokens);
                        if (cityDisplay.length() > 0) cityDisplay += ", ";
                        cityDisplay += cityName;
                        
                        // We are scoring the city itself!  Boost !
                        if (cityName.equals(name)) {
                            score += 1000;
                        }
                    }
                }

                // Beware, don't use type as a token. It's useless, it will be clipped anyway !
                // tokens.add(type);


                /* Compute the display value */
                JSONObject obj = new JSONObject();
                obj.put("name", name.isEmpty() ? ref : name);
                obj.put("city", cityDisplay);
                obj.put("type", type);
                obj.put("lat", lat);
                obj.put("lon", lon);
                String value = obj.toString();

                builder.addMultiEntry(tokens.toArray(new String[0]), value, score);

                if (nlines % 5000 == 0) {
                    System.out.println("Parsed " +  nlines + (isWays ? " ways" : " nodes") + ", id=" + id + " name=" + name);
                }
//                if (nlines > 50000) break;
            } catch (Exception e) {
                logger.error("Failed to parse *********\n" + line, e);
//                throw e;
            }
        }
        System.out.println("Parsed " + nlines  +(isWays ? " ways" : " nodes"));
    }
    
  
    public void parseCityList(String cityListFile) throws Exception {
        File f = new File(cityListFile);
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "utf8"));

        int nlines = 0;
        while (true) {
            String line = br.readLine();
            if (line == null) break;
            if (nlines ++ <= 2) continue;

            String[] chunks = StringUtils.split(line, '|');

            String c0 = chunks[0].replace(" ", "");
            if (!StringUtils.isNumeric(c0)) break;
            long id = Long.parseLong(c0);

            String name = chunks[1].replaceAll("^\\s+", "");

            cityNames.put(id, name);

            if (nlines % 1000 == 0) {
                System.out.println("Parsed " + nlines + " cities, id=" + id + " name=" + name );
            }
        }
        System.out.println("Parsed " + cityNames.size()  +" cities");
    }

    public void flush() throws Exception {
        builder.flush();

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
        instance.parseNamedWaysOrNodes(inWays, true);
        instance.parseNamedWaysOrNodes(inNodes, false);
        instance.flush();
    }
    
    private static Logger logger = Logger.getLogger("build");
}