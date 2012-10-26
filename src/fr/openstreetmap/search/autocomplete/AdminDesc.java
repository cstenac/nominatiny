package fr.openstreetmap.search.autocomplete;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import au.com.bytecode.opencsv.CSVReader;

class AdminDesc implements Comparable<AdminDesc>{
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
        } else if (country != null && country.equals("Deutschland")) {
            displayable = (level == 2 || level == 4 || level == 8 || level == 10);
            indexable = displayable;
        } else {
            displayable = true;
            indexable = true;
        }
    }

    @Override
    public int compareTo(AdminDesc arg0) {
        return -this.level + arg0.level;
    }
    
    
    public static Map<Long, AdminDesc> parseCityList(File  f) throws Exception {
        Map<Long, AdminDesc> adminRelations = new HashMap<Long, AdminDesc>();
        
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
        return adminRelations;
    }
    

    public static void parseCityParents(File f,  Map<Long, AdminDesc> adminRelations) throws Exception {
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
    
}