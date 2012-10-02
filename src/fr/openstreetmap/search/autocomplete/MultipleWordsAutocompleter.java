package fr.openstreetmap.search.autocomplete;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.ArrayUtils;

import fr.openstreetmap.search.autocomplete.Autocompleter.AutocompleterEntry;

/** This class is not thread safe */
public class MultipleWordsAutocompleter {
    public Autocompleter completer;

    public int[] distanceMap = new int[]{0,0,0, 1, 1, 1};

    public static class DebugInfo {
        List<Autocompleter.DebugInfo> tokensDebugInfo = new ArrayList<Autocompleter.DebugInfo>();
        long intersectionTime;
    }

    public static class MultiWordAutocompleterEntry implements Comparable<MultiWordAutocompleterEntry>{
        public long offset;
        public long score;
        public long distance;
        public String[] correctedTokens;
        
        @Override
        public int compareTo(MultiWordAutocompleterEntry o) {
            if (distance < o.distance) return -1;
            if (distance > o.distance) return 1;
            if (score > o.score) return -1;
            if (score < o.score) return 1;
            return 0;
        }
    }
    
    
    
    public List<MultiWordAutocompleterEntry> autocomplete(String[] tokens, int maxDistance, DebugInfo di) {
        List<String> list = new ArrayList<String>();
        for (String t : tokens) list.add(t);
        return autocomplete(list, maxDistance, di);
    }

    public List<MultiWordAutocompleterEntry> autocomplete(List<String> tokens, int defaultMaxDistance, DebugInfo di) {

        List<MultiWordAutocompleterEntry> out = new ArrayList<MultiWordAutocompleterEntry>();
        if (tokens.size() == 0) return out;

        /* We will always decode all lists, so do it all at once. The advantage is that we can then sort the lists,
         * and start by the smallest one, to prune the insertions in the hashmaps
         */
        List<List<AutocompleterEntry>> lists = new ArrayList<List<AutocompleterEntry>>();

        for (int i = 0; i < tokens.size(); i++) {
            Autocompleter.DebugInfo tokenDI = null;
            if (di != null) tokenDI = new Autocompleter.DebugInfo();

            lists.add(completer.getOffsets(tokens.get(i), defaultMaxDistance, tokenDI));

            if (di != null) di.tokensDebugInfo.add(tokenDI);
        }

        Collections.sort(lists, new Comparator<List<AutocompleterEntry>>() {
            @Override
            public int compare(List<AutocompleterEntry> arg0, List<AutocompleterEntry> arg1) {
                return arg0.size() - arg1.size();
            }
        });

        Map<Long, MultiWordAutocompleterEntry> prevMap = null;

        for (int i = 0; i < tokens.size(); i++) {
            Map<Long, MultiWordAutocompleterEntry> mapAfter = new HashMap<Long, MultipleWordsAutocompleter.MultiWordAutocompleterEntry>(2000);
            if (prevMap == null) {// First token, always take everything
                //                System.out.println("FIRST TOKEN, prev list" + lists.get(i).size());
                for (AutocompleterEntry ae : lists.get(i)) {
                    MultiWordAutocompleterEntry candidate = new MultiWordAutocompleterEntry();
                    candidate.offset = ae.offset; candidate.score = ae.score; candidate.distance = ae.distance;
                    candidate.correctedTokens = new String[]{ae.correctedPrefix};
                    mapAfter.put(ae.offset, candidate);
                }
            } else {
                //                System.out.println("SECOND TOKEN, prev list" + lists.get(i).size());

                for (AutocompleterEntry ae : lists.get(i)) {
                    MultiWordAutocompleterEntry prev =  prevMap.get(ae.offset);
                    if (prev != null) {
                        prev.score = Math.min(prev.score, ae.score);
                        prev.distance = Math.min(prev.distance, ae.distance);
                        prev.correctedTokens = (String[])ArrayUtils.add(prev.correctedTokens, ae.correctedPrefix);
                        mapAfter.put(ae.offset, prev);
                    }
                }
                //                System.out.println("AFTER SECOND TOKEN,  map is " );

            }
            prevMap = mapAfter;
        }
        out.addAll(prevMap.values());
        return out;
    }
    
    
    /* Previous version that did not correctly compute the corrected tokens, and that did not sort the inverted lists */
    public List<Autocompleter.AutocompleterEntry> autocompleteOld(String[] tokens, int maxDistance, DebugInfo di) {
        List<String> list = new ArrayList<String>();
        for (String t : tokens) list.add(t);
        return autocompleteOld(list, maxDistance, di);
    }

    public List<Autocompleter.AutocompleterEntry> autocompleteOld(List<String> tokens, int defaultMaxDistance, DebugInfo di) {
        long t0 = 0;

        Autocompleter.DebugInfo token0DI = null;
        if (di != null) {
            token0DI = new Autocompleter.DebugInfo();
        }

        /* Very poor's man inverted list intersection !!! */
        Map<Long, Autocompleter.AutocompleterEntry> map = new HashMap<Long, Autocompleter.AutocompleterEntry>();
        for (Autocompleter.AutocompleterEntry e :  completer.getOffsets(tokens.get(0), defaultMaxDistance, token0DI)) {
            map.put(e.offset, e);
        }
        //        System.out.println("After token 0, have " + map.size() + " matches");
        if (di != null) {
            di.tokensDebugInfo.add(token0DI);
        }

        for (int i = 1; i < tokens.size() ; i++) {
            Map<Long, Autocompleter.AutocompleterEntry> prevMap = map;
            map = new HashMap<Long, Autocompleter.AutocompleterEntry>();

            Autocompleter.DebugInfo tokenDI = null;
            if (di != null) {
                tokenDI = new Autocompleter.DebugInfo();
            }
            List<Autocompleter.AutocompleterEntry> thisList = completer.getOffsets(tokens.get(i), defaultMaxDistance, tokenDI);
            if (di != null) {
                di.tokensDebugInfo.add(tokenDI);
                t0 = System.nanoTime();
            }
            //            System.out.println("TOKEN HAS " + thisList.size());

            for (Autocompleter.AutocompleterEntry e : thisList) {
                Autocompleter.AutocompleterEntry prevE = prevMap.get(e.offset);
                if (prevE == null) continue;
                e.distance = Math.max(prevE.distance, e.distance);
                e.score = Math.min(prevE.score, e.score);
                map.put(e.offset, e);
            }
            if (di != null) {
                di.intersectionTime += (System.nanoTime() - t0)/1000;
            }
            //            System.out.println("After token " + i + ", have " + map.size() + " matches");

        }

        List<Autocompleter.AutocompleterEntry> ret = new ArrayList<Autocompleter.AutocompleterEntry>();
        for (Autocompleter.AutocompleterEntry e : map.values()) {
            ret.add(e);
        }
        return ret;
    }
}