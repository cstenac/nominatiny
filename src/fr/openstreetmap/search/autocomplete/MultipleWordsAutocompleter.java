package fr.openstreetmap.search.autocomplete;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import fr.openstreetmap.search.autocomplete.Autocompleter.AutocompleterEntry;
import fr.openstreetmap.search.binary.LongList;

/** This class is not thread safe */
public class MultipleWordsAutocompleter {
    public Autocompleter completer;

    public int[] distanceMap = new int[]{0,0,0, 1, 1, 1};

    public static class DebugInfo {
        List<Autocompleter.DebugInfo> tokensDebugInfo = new ArrayList<Autocompleter.DebugInfo>();
        long intersectionTime;
    }
    public List<Autocompleter.AutocompleterEntry> autocomplete(String[] tokens, int maxDistance, DebugInfo di) {
        List<String> list = new ArrayList<String>();
        for (String t : tokens) list.add(t);
        return autocomplete(list, maxDistance, di);
    }

    public static class MultiWordAutocompleterEntry {
        public long offset;
        public long score;
        public long distance;
        public String[] correctedTokens;
    }

    static class InvertedList {
        int originalOffsetInInvertedListsList;
        LongList offsets = new LongList(1000);
        LongList scores = new LongList(1000);
        LongList distances = new LongList(1000);
        List<String> correcteds = new ArrayList<String>(1000);
        
        Map<Long, Integer> offsetsMap = new HashMap<Long, Integer>();

        int curIndex = -1;

        void initIteration() {

        }
        
        void map() {
            for (int i = 0; i < offsets.size() ;i++) {
                offsetsMap.put(offsets.get(i), i);
            }
        }

        int curIndex() {
            return curIndex;
        }

        long curValue() {
            if (curIndex >= offsets.size()) return -1;
            return offsets.get(curIndex);
        }
        long curScoreUnsafe() {
            return scores.get(curIndex);
        }
        long curDistanceUnsafe() {
            return distances.get(curIndex);
        }
        String curCorrectedUnsafe() {
            return correcteds.get(curIndex);
        }

        void gotoNext() {
            curIndex++;
        }

        void seekTo(long seekTo) {
            while (curIndex < offsets.size() && offsets.get(curIndex) < seekTo) ++curIndex; 
        }

        void append(long offset, long score, long distance, String corrected) {
            offsets.add(offset);
            scores.add(score);
            distances.add(distance);
            correcteds.add(corrected);
        }
    }
    
    public List<MultiWordAutocompleterEntry> autocompleteNew2(List<String> tokens, int defaultMaxDistance, DebugInfo di) {
        List<MultiWordAutocompleterEntry> out = new ArrayList<MultiWordAutocompleterEntry>();
        if (tokens.size() == 0) return out;

        List<InvertedList> lists = new ArrayList<InvertedList>();

        for (int i = 0; i < tokens.size(); i++) {
            InvertedList ilist = new InvertedList();
            ilist.originalOffsetInInvertedListsList = i;
            Autocompleter.DebugInfo tokenDI = di  != null ? new Autocompleter.DebugInfo() : null;
            completer.getOffsets(tokens.get(i), defaultMaxDistance, tokenDI, ilist.offsets, ilist.scores, ilist.distances, ilist.correcteds);
//            System.out.println("After token " + i + ", have " + ilist.offsets.size() + " matches");
            lists.add(ilist);
        }

//        if (tokens.size() == 1) {
//            return lists.get(0);
//        }

        Collections.sort(lists, new Comparator<InvertedList>() {
            @Override
            public int compare(InvertedList arg0, InvertedList arg1) {
                return arg0.offsets.size() - arg1.offsets.size();
            }
        });
        
        lists.get(0).map();
        
        for (int i = 1; i < lists.size(); i++) {
            Map<Long, Integer> prevMap = lists.get(i-1).offsetsMap;
        }
        
        /* Init the seeked lists */
        for (int i =  1; i < lists.size(); i++) {
            lists.get(i).map();
        }

        int[] offsetsInOtherLists = new int[lists.size()];
        
        InvertedList l0 = lists.get(0);
        int offsetsInList0 = l0.offsets.size();

        for (int i = 0; i < offsetsInList0; i++) {
            long candidateOffset = l0.offsets.get(i);
            boolean ok = true;
            for (int j= 1; j < lists.size(); j++) {
                Integer foundInList = lists.get(j).offsetsMap.get(candidateOffset);
                if (foundInList == null) {
                    ok = false;
                    break;
                } else {
                    offsetsInOtherLists[j] = foundInList;
                }
            }
            offsetsInOtherLists[0] = i;

            if (ok) {
                // We have a match
                MultiWordAutocompleterEntry mwae = new MultiWordAutocompleterEntry();
                mwae.offset = candidateOffset;
                mwae.correctedTokens = new String[tokens.size()];
                mwae.score = Long.MAX_VALUE;
                mwae.distance = 0;
                for (int k =  0; k < lists.size(); k++) {
                    mwae.score = Math.min(lists.get(k).scores.get(offsetsInOtherLists[k]), mwae.score);
                    mwae.distance = Math.min(lists.get(k).distances.get(offsetsInOtherLists[k]), mwae.distance);
                    mwae.correctedTokens[lists.get(k).originalOffsetInInvertedListsList] = lists.get(k).correcteds.get(offsetsInOtherLists[k]);
                }
                out.add(mwae);
            }
        }
        
        
//        System.out.println("Will NEXT on a list with " + lists.get(0).offsets.size() );
//
//
//        while (true) {
//            lists.get(0).gotoNext();
//            long firstListValue = lists.get(0).curValue();
//            System.out.println("goto next returned " + firstListValue);
//
//            if (firstListValue == -1) break;
//
//            boolean broken = false;
//            for (int i =  1; i < lists.size(); i++) {
//                lists.get(i).seekTo(firstListValue);
//                if (lists.get(i).curValue() != firstListValue) { broken = true; break; }
//            }
//
//            if (!broken) {
//                /* We have a match */
//                MultiWordAutocompleterEntry mwae = new MultiWordAutocompleterEntry();
//                mwae.offset = firstListValue;
//                mwae.correctedTokens = new String[tokens.size()];
//                mwae.score = Long.MAX_VALUE;
//                mwae.distance = 0;
//                for (int i =  0; i < lists.size(); i++) {
//                    mwae.score = Math.min(lists.get(i).curScoreUnsafe(), mwae.score);
//                    mwae.distance = Math.min(lists.get(i).curDistanceUnsafe(), mwae.distance);
//                    mwae.correctedTokens[lists.get(i).originalOffsetInInvertedListsList] = lists.get(i).curCorrectedUnsafe();
//                }
//                out.add(mwae);
//            }
//        }
        return out;
    }

    public List<MultiWordAutocompleterEntry> autocompleteNew(List<String> tokens, int defaultMaxDistance, DebugInfo di) {
        List<MultiWordAutocompleterEntry> out = new ArrayList<MultiWordAutocompleterEntry>();
        if (tokens.size() == 0) return out;

        List<InvertedList> lists = new ArrayList<InvertedList>();

        for (int i = 0; i < tokens.size(); i++) {
            InvertedList ilist = new InvertedList();
            ilist.originalOffsetInInvertedListsList = i;
            Autocompleter.DebugInfo tokenDI = di  != null ? new Autocompleter.DebugInfo() : null;
            completer.getOffsets(tokens.get(i), defaultMaxDistance, tokenDI, ilist.offsets, ilist.scores, ilist.distances, ilist.correcteds);
//            System.out.println("After token " + i + ", have " + ilist.offsets.size() + " matches");
            lists.add(ilist);
        }

//        if (tokens.size() == 1) {
//            return lists.get(0);
//        }

        Collections.sort(lists, new Comparator<InvertedList>() {
            @Override
            public int compare(InvertedList arg0, InvertedList arg1) {
                return arg0.offsets.size() - arg1.offsets.size();
            }
        });
        
        /* Init the seeked lists */
        for (int i =  1; i < lists.size(); i++) {
            lists.get(i).map();
        }

        int[] offsetsInOtherLists = new int[lists.size()];
        
        InvertedList l0 = lists.get(0);
        int offsetsInList0 = l0.offsets.size();

        for (int i = 0; i < offsetsInList0; i++) {
            long candidateOffset = l0.offsets.get(i);
            boolean ok = true;
            for (int j= 1; j < lists.size(); j++) {
                Integer foundInList = lists.get(j).offsetsMap.get(candidateOffset);
                if (foundInList == null) {
                    ok = false;
                    break;
                } else {
                    offsetsInOtherLists[j] = foundInList;
                }
            }
            offsetsInOtherLists[0] = i;

            if (ok) {
                // We have a match
                MultiWordAutocompleterEntry mwae = new MultiWordAutocompleterEntry();
                mwae.offset = candidateOffset;
                mwae.correctedTokens = new String[tokens.size()];
                mwae.score = Long.MAX_VALUE;
                mwae.distance = 0;
                for (int k =  0; k < lists.size(); k++) {
                    mwae.score = Math.min(lists.get(k).scores.get(offsetsInOtherLists[k]), mwae.score);
                    mwae.distance = Math.min(lists.get(k).distances.get(offsetsInOtherLists[k]), mwae.distance);
                    mwae.correctedTokens[lists.get(k).originalOffsetInInvertedListsList] = lists.get(k).correcteds.get(offsetsInOtherLists[k]);
                }
                out.add(mwae);
            }
        }
        
        
//        System.out.println("Will NEXT on a list with " + lists.get(0).offsets.size() );
//
//
//        while (true) {
//            lists.get(0).gotoNext();
//            long firstListValue = lists.get(0).curValue();
//            System.out.println("goto next returned " + firstListValue);
//
//            if (firstListValue == -1) break;
//
//            boolean broken = false;
//            for (int i =  1; i < lists.size(); i++) {
//                lists.get(i).seekTo(firstListValue);
//                if (lists.get(i).curValue() != firstListValue) { broken = true; break; }
//            }
//
//            if (!broken) {
//                /* We have a match */
//                MultiWordAutocompleterEntry mwae = new MultiWordAutocompleterEntry();
//                mwae.offset = firstListValue;
//                mwae.correctedTokens = new String[tokens.size()];
//                mwae.score = Long.MAX_VALUE;
//                mwae.distance = 0;
//                for (int i =  0; i < lists.size(); i++) {
//                    mwae.score = Math.min(lists.get(i).curScoreUnsafe(), mwae.score);
//                    mwae.distance = Math.min(lists.get(i).curDistanceUnsafe(), mwae.distance);
//                    mwae.correctedTokens[lists.get(i).originalOffsetInInvertedListsList] = lists.get(i).curCorrectedUnsafe();
//                }
//                out.add(mwae);
//            }
//        }
        return out;
    }

    public List<Autocompleter.AutocompleterEntry> autocomplete(List<String> tokens, int defaultMaxDistance, DebugInfo di) {
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
