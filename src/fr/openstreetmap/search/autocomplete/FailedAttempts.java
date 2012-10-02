package fr.openstreetmap.search.autocomplete;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fr.openstreetmap.search.autocomplete.Autocompleter.AutocompleterEntry;
import fr.openstreetmap.search.autocomplete.MultipleWordsAutocompleter.DebugInfo;
import fr.openstreetmap.search.autocomplete.MultipleWordsAutocompleter.MultiWordAutocompleterEntry;
import fr.openstreetmap.search.binary.LongList;

public class FailedAttempts {
    
    public Autocompleter completer;

    static class InvertedList {
        int originalOffsetInInvertedListsList;
        LongList offsets = new LongList(1000);
        LongList scores = new LongList(1000);
        LongList distances = new LongList(1000);
        List<String> correcteds = new ArrayList<String>(1000);

        Map<Long, Integer> offsetsMap;// = new HashMap<Long, Integer>();

        int curIndex = -1;

        void initIteration() {

        }

        void map() {
            offsetsMap = new HashMap<Long, Integer>(offsets.size() * 2);
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

    static class ObjectInvertedList {
        int originalOffsetInInvertedListsList;
        List<AutocompleterEntry> list = new ArrayList<Autocompleter.AutocompleterEntry>(1000);

        Map<Long, Integer> offsetsMap;// = new HashMap<Long, Integer>();

        FastIntMap fastOffsetsMap;

        int curIndex = -1;

        void initIteration() {

        }

        void map() {
            offsetsMap = new HashMap<Long, Integer>(list.size() * 2);
            for (int i = 0; i < list.size() ;i++) {
                offsetsMap.put(list.get(i).offset, i);
            }
        }

        void mapFast() {
            fastOffsetsMap = new FastIntMap(list.size() * 2);
            for (int i = 0; i < list.size() ;i++) {
                fastOffsetsMap.put(list.get(i).offset, i);
            }
        }

        int curIndex() {
            return curIndex;
        }

        long curValue() {
            if (curIndex >= list.size()) return -1;
            return list.get(curIndex).offset;
        }
        long curScoreUnsafe() {
            return list.get(curIndex).score;
        }
        long curDistanceUnsafe() {
            return list.get(curIndex).distance;
        }
        String curCorrectedUnsafe() {
            return list.get(curIndex).correctedPrefix;
        }

        void gotoNext() {
            curIndex++;
        }

        void seekTo(long seekTo) {
            while (curIndex < list.size() && list.get(curIndex).offset < seekTo) ++curIndex; 
        }

        void append(long offset, long score, long distance, String corrected) {
            AutocompleterEntry ae = new AutocompleterEntry(offset, (int)distance, score);
            ae.correctedPrefix = corrected;
            list.add(ae);
        }
    }
    

    public List<MultiWordAutocompleterEntry> autocompleteNew3(List<String> tokens, int defaultMaxDistance, DebugInfo di) {
        List<MultiWordAutocompleterEntry> out = new ArrayList<MultiWordAutocompleterEntry>();
        if (tokens.size() == 0) return out;

        List<ObjectInvertedList> lists = new ArrayList<ObjectInvertedList>();

        for (int i = 0; i < tokens.size(); i++) {
            ObjectInvertedList ilist = new ObjectInvertedList();
            ilist.originalOffsetInInvertedListsList = i;
            Autocompleter.DebugInfo tokenDI = di  != null ? new Autocompleter.DebugInfo() : null;
            ilist.list = completer.getOffsets(tokens.get(i), defaultMaxDistance, tokenDI);
            //            System.out.println("After token " + i + ", have " + ilist.offsets.size() + " matches");
            lists.add(ilist);
        }

        //        if (tokens.size() == 1) {
        //            return lists.get(0);
        //        }

        Collections.sort(lists, new Comparator<ObjectInvertedList>() {
            @Override
            public int compare(ObjectInvertedList arg0, ObjectInvertedList arg1) {
                return arg0.list.size() - arg1.list.size();
            }
        });

        /* Init the seeked lists */
        for (int i =  1; i < lists.size(); i++) {
            lists.get(i).map();
        }

        int[] offsetsInOtherLists = new int[lists.size()];

        ObjectInvertedList l0 = lists.get(0);
        int offsetsInList0 = l0.list.size();

        for (int i = 0; i < offsetsInList0; i++) {
            long candidateOffset = l0.list.get(i).offset;
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
                    mwae.score = Math.min(lists.get(k).list.get(offsetsInOtherLists[k]).score, mwae.score);
                    mwae.distance = Math.min(lists.get(k).list.get(offsetsInOtherLists[k]).distance, mwae.distance);
                    mwae.correctedTokens[lists.get(k).originalOffsetInInvertedListsList] = lists.get(k).list.get(offsetsInOtherLists[k]).correctedPrefix;
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

    public List<MultiWordAutocompleterEntry> autocompleteNew4(List<String> tokens, int defaultMaxDistance, DebugInfo di) {
        List<MultiWordAutocompleterEntry> out = new ArrayList<MultiWordAutocompleterEntry>();
        if (tokens.size() == 0) return out;

        List<ObjectInvertedList> lists = new ArrayList<ObjectInvertedList>();

        for (int i = 0; i < tokens.size(); i++) {
            ObjectInvertedList ilist = new ObjectInvertedList();
            ilist.originalOffsetInInvertedListsList = i;
            Autocompleter.DebugInfo tokenDI = di  != null ? new Autocompleter.DebugInfo() : null;
            ilist.list = completer.getOffsets(tokens.get(i), defaultMaxDistance, tokenDI);
            //            System.out.println("After token " + i + ", have " + ilist.offsets.size() + " matches");
            lists.add(ilist);
        }

        //        if (tokens.size() == 1) {
        //            return lists.get(0);
        //        }

        Collections.sort(lists, new Comparator<ObjectInvertedList>() {
            @Override
            public int compare(ObjectInvertedList arg0, ObjectInvertedList arg1) {
                return -arg0.list.size() + arg1.list.size();
            }
        });

        /* Init the seeked lists */
        for (int i =  1; i < lists.size(); i++) {
            lists.get(i).mapFast();
        }

        int[] offsetsInOtherLists = new int[lists.size()];

        ObjectInvertedList l0 = lists.get(0);
        int offsetsInList0 = l0.list.size();

        for (int i = 0; i < offsetsInList0; i++) {
            long candidateOffset = l0.list.get(i).offset;
            boolean ok = true;
            for (int j= 1; j < lists.size(); j++) {
                long foundInList = lists.get(j).fastOffsetsMap.get(candidateOffset);
                if (foundInList == Long.MAX_VALUE) {
                    ok = false;
                    break;
                } else {
                    offsetsInOtherLists[j] = (int)foundInList;
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
                    mwae.score = Math.min(lists.get(k).list.get(offsetsInOtherLists[k]).score, mwae.score);
                    mwae.distance = Math.min(lists.get(k).list.get(offsetsInOtherLists[k]).distance, mwae.distance);
                    mwae.correctedTokens[lists.get(k).originalOffsetInInvertedListsList] = lists.get(k).list.get(offsetsInOtherLists[k]).correctedPrefix;
                }
                out.add(mwae);
            }
        }
        //        for (int i =  1; i < lists.size(); i++) {
        //            lists.get(i).fastOffsetsMap.stats();
        //        }

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

}
