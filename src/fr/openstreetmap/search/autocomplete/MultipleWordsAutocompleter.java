package fr.openstreetmap.search.autocomplete;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.lang.ArrayUtils;

import fr.openstreetmap.search.autocomplete.Autocompleter.AutocompleterEntry;
import fr.openstreetmap.search.binary.LongList;

/** This class is thread safe */
public class MultipleWordsAutocompleter {
    public ByteBuffer dataBuffer;
    public byte[] radixBuffer;
    public String shardName;
    
    public MultipleWordsAutocompleter() {
        executor = Executors.newFixedThreadPool(8);
    }
    
    private Autocompleter newAutocompleter() {
        return new Autocompleter(radixBuffer, dataBuffer);
    }
    
    ExecutorService executor;

    Map<String, long[]> filters = new HashMap<String, long[]>();

    /**
     * Adds this word as a filter. 
     * Unlike regular tokens, for filters, score is not evaluated. It only prunes non-matching hits.
     * Filters are suited for long lists. They are evaluated lists to query the filter as little as possible.
     * */
    public void addFilter(String token, long[] matchingOffsets) {
        filters.put(token, matchingOffsets);
    }
    
    public void computeAndAddFilter(String token) {
        addFilter(token, computeFilter(token));
    }
    
    public void computeAndAddFilterRecursive(String token) {
        for (int i = token.length() - 1; i >= 3; i--) {
            String t = token.substring(0, i);
            addFilter(t, computeFilter(t));
        }
    }
    
    public long[] computeFilter(String token) {
        List<AutocompleterEntry> list = newAutocompleter().getOffsets(token, 0, null);
        long[] ret = new long[list.size()];
        for (int i = 0; i < list.size(); i++) ret[i] = list.get(i).offset;
        Arrays.sort(ret);
        return ret;
    }
    
    public void dumpFiltersInfo() {
        long totalSize = 0;
        for (String k : filters.keySet()) {
            long[] v = filters.get(k);
            totalSize += v.length;
            System.out.println(k + "\t\t" + v.length);
        }
        System.out.println("TOTAL: "+ totalSize + " (" + (totalSize*8/1024/1024) + " MB)");
    }


    public int[] distanceMap = new int[]{0,0,0, 1, 1, 1};

    public static class DebugInfo {
        String shardName;
        List<Autocompleter.DebugInfo> tokensDebugInfo = new ArrayList<Autocompleter.DebugInfo>();
        long totalTokensMatchTime;
        long intersectionTime;
        long filterTime;
    }

    public static class MultiWordAutocompleterEntry implements Comparable<MultiWordAutocompleterEntry>{
        public long offset;
        public long score;
        public long distance;
        public String[] correctedTokens;
        
        public MultipleWordsAutocompleter source;

        @Override
        public int compareTo(MultiWordAutocompleterEntry o) {
            if (distance < o.distance) return -1;
            if (distance > o.distance) return 1;
            if (score > o.score) return -1;
            if (score < o.score) return 1;
            return 0;
        }
    }
    
    public byte[] getByteData(long offset) throws IOException {
        return newAutocompleter().getByteData(offset);
    }

    public List<MultiWordAutocompleterEntry> autocomplete(String[] tokens, int maxDistance, DebugInfo di) throws Exception {
        List<String> list = new ArrayList<String>();
        for (String t : tokens) list.add(t);
        return autocomplete(list, maxDistance, di);
    }
    
    static class TokenLookupResult {
        List<AutocompleterEntry> entries;
        String token;
        int dist;
        Autocompleter.DebugInfo tokenDI = new Autocompleter.DebugInfo();
        long matchTime;
    }

    public List<MultiWordAutocompleterEntry> autocomplete(List<String> origTokens, int defaultMaxDistance, DebugInfo di) throws InterruptedException, ExecutionException {
        List<MultiWordAutocompleterEntry> out = new ArrayList<MultiWordAutocompleterEntry>();
        if (origTokens.size() == 0) return out;

        final List<String> nonFilterTokens = new ArrayList<String>();
        List<String> filterTokens = new ArrayList<String>();
        for (String token : origTokens) {
            if (filters.containsKey(token )) {
                filterTokens.add(token);
            } else {
                nonFilterTokens.add(token);
            }
        }

        if (nonFilterTokens.size() == 0) return out;
        
        /* We will always decode all lists, so do it all at once. The advantage is that we can then sort the lists,
         * and start by the smallest one, to prune the insertions in the hashmaps
         */
        List<Future<TokenLookupResult>> futures = new ArrayList<Future<TokenLookupResult>>();
        List<List<AutocompleterEntry>> lists = new ArrayList<List<AutocompleterEntry>>();

        for (int i = 0; i < nonFilterTokens.size(); i++) {
            final TokenLookupResult tlr = new TokenLookupResult();
            tlr.dist = nonFilterTokens.get(i).length() == 3 ? 0 : defaultMaxDistance;
            tlr.token = nonFilterTokens.get(i); 
            
            futures.add(executor.submit(new Callable<TokenLookupResult>() {
                @Override
                public TokenLookupResult call() throws Exception {
                    tlr.entries = newAutocompleter().getOffsets(tlr.token, tlr.dist, tlr.tokenDI);
                    tlr.matchTime = tlr.tokenDI.listsDecodingTime + tlr.tokenDI.radixTreeMatchTime;
                    return tlr;
                }
            }));
        }
        for (int i = 0; i < futures.size(); i++) {
            TokenLookupResult tlr = futures.get(i).get();
            lists.add(tlr.entries);
            if (di != null) {
                di.tokensDebugInfo.add(tlr.tokenDI);
                di.totalTokensMatchTime += tlr.matchTime;
            }
        }

        long beforeIntersect = System.nanoTime();

        Collections.sort(lists, new Comparator<List<AutocompleterEntry>>() {
            @Override
            public int compare(List<AutocompleterEntry> arg0, List<AutocompleterEntry> arg1) {
                return arg0.size() - arg1.size();
            }
        });

        Map<Long, MultiWordAutocompleterEntry> prevMap = null;

        for (int i = 0; i < nonFilterTokens.size(); i++) {
            Map<Long, MultiWordAutocompleterEntry> mapAfter = new HashMap<Long, MultipleWordsAutocompleter.MultiWordAutocompleterEntry>(2000);
            if (prevMap == null) {// First token, always take everything
                //                System.out.println("FIRST TOKEN, prev list" + lists.get(i).size());
                for (AutocompleterEntry ae : lists.get(i)) {
//                    if (mapAfter.containsKey(ae.offset)) {
//                        MultiWordAutocompleterEntry candidate = mapAfter.get(ae.offset);
//                        candidate.score += ae.score;
//                    } else {
                        MultiWordAutocompleterEntry candidate = new MultiWordAutocompleterEntry();
                        candidate.offset = ae.offset; candidate.score = ae.score; candidate.distance = ae.distance;
                        candidate.correctedTokens = new String[]{ae.correctedPrefix};
                        mapAfter.put(ae.offset, candidate);
//                    }
                }
            } else {
                //                System.out.println("SECOND TOKEN, prev list" + lists.get(i).size());

                for (AutocompleterEntry ae : lists.get(i)) {
                    MultiWordAutocompleterEntry prev =  prevMap.get(ae.offset);
                    if (prev != null) {
                        prev.score = prev.score + ae.score;
                        prev.distance = Math.max(prev.distance, ae.distance);
                        prev.correctedTokens = (String[])ArrayUtils.add(prev.correctedTokens, ae.correctedPrefix);
                        mapAfter.put(ae.offset, prev);
                    }
                }
                //                System.out.println("AFTER SECOND TOKEN,  map is " );

            }
            prevMap = mapAfter;
        }

        if (di != null) {
            long afterIntersect = System.nanoTime();
            di.intersectionTime += (afterIntersect - beforeIntersect)/1000;
        }

        /* Now, apply the filters */
        for (String token : filterTokens) {
            Autocompleter.DebugInfo tokenDI = new Autocompleter.DebugInfo();
            tokenDI.radixTreeMatches = 0;
            tokenDI.radixTreeMatchTime = 0;
            long before = System.nanoTime();

            long[] filter = filters.get(token);
            tokenDI.decodedMatches = filter.length; 

            LongList removedOffsets = new LongList();
            for (long offsetToFilter : prevMap.keySet()) {
                if (Arrays.binarySearch(filter, offsetToFilter) < 0) {
                    removedOffsets.add(offsetToFilter);
                }
            }
            for (int i = 0; i < removedOffsets.size(); i++) {
                prevMap.remove(removedOffsets.get(i));
            }

            tokenDI.value = token + " (filter) (removed:" + removedOffsets.size() + ")";

            tokenDI.listsDecodingTime = (System.nanoTime() - before)/1000;
            if (di != null) {
                di.tokensDebugInfo.add(tokenDI);
                di.filterTime += tokenDI.listsDecodingTime;
            }
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
        for (Autocompleter.AutocompleterEntry e :  newAutocompleter().getOffsets(tokens.get(0), defaultMaxDistance, token0DI)) {
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
            List<Autocompleter.AutocompleterEntry> thisList = newAutocompleter().getOffsets(tokens.get(i), defaultMaxDistance, tokenDI);
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