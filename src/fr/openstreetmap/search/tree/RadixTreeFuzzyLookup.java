package fr.openstreetmap.search.tree;
import java.util.ArrayList;
import java.util.List;

import fr.openstreetmap.search.binary.BinaryUtils;
import fr.openstreetmap.search.binary.LongList;


/**
 * This class is not thread safe, it contains some context.
 */
public class RadixTreeFuzzyLookup {
    private RadixTree tree;
    private int maxDistance;
    private List<ApproximateMatch> matches = new ArrayList<RadixTreeFuzzyLookup.ApproximateMatch>();

    /** Result of the fuzzy lookup */
    public static class ApproximateMatch {
        public long value;
        public byte[] byteArrayValue;
        public int distance;
        public String key;
    }

    public RadixTreeFuzzyLookup(RadixTree tree) {
        this.tree = tree;
    }

    public void match(String entry, int maxDistance) {
        this.maxDistance = maxDistance;
        long headerNodePos = tree.getHeaderNodePos();
        List<Character> corrected = new ArrayList<Character>();
        matchRec(headerNodePos, -1, entry, 0, corrected, (char)0, (char)0);
    }

    public List<ApproximateMatch> getMatches() {
        return matches;
    }

    /**
     * Get the list of positions of the subnodes of a node
     */
    private void getChildrenPositions(long childrenListPos, LongList list) {
        BinaryUtils.VInt vint = new BinaryUtils.VInt();
        BinaryUtils.readVInt(tree.buffer, (int)childrenListPos, vint);
        int nbChildren = (int) vint.value;
        
        int pos = (int)childrenListPos + vint.codeSize;
        for (int i = 0; i < nbChildren; i++) {
            BinaryUtils.readVInt(tree.buffer, pos, vint);
            long childPos = vint.value;
            list.add(childPos);
            pos += vint.codeSize;
//            buffer.position((int)childrenListPos);
//            buffer.get(tmpBuf, 0, Math.min(10, buffer.remaining()));
//            BinaryUtils.readVInt(tmpBuf, 0, vint);
//            long childPos = vint.value;
//            list.add(childPos);
//            childrenListPos += vint.codeSize;
        }
    }

    private String indent(int pos) {
        String s = "";
        for (int i = 0; i <  pos; i++) {
            s += "  ";
        }
        return s;
    }

    private BinaryUtils.VInt lastVInt = new BinaryUtils.VInt();
    private void readVInt(long pos) {
        BinaryUtils.readVInt(tree.buffer, (int)pos, lastVInt);
    }

    private void matchInRadix(long childrenOfRadixListPos, int strPos, String str, int currentDistance,
            int radixPos, char[] radix, int radixLength, List<Character> corrected,
            long positionOfRadixValue, char substitutionFoundChar, char substitutionExpectedChar) {
        //        System.out.println(indent(strPos) + " matchInRadix str=" +  str + " pos=" + strPos + " radix=" + new String(radix) + " rpos=" + radixPos + " cD=" + currentDistance);

        /* First, handle the case when we're called in substitution mode */
        if (substitutionExpectedChar != (char)0) {
            if (strPos >= str.length() || radixPos >= radixLength) {
                //                System.out.println(indent(strPos) + "  matchInRadix: SUBST CANCEL LENGTH");
                return;
            }
            char sExpected2 = str.charAt(strPos);
            char sFound2 = radix[radixPos];
            //            System.out.println(indent(strPos) + "  matchInRadix: SUBST ?" + substitutionExpectedChar + "/" + substitutionFoundChar + "  vs " + sExpected2 + "/" + sFound2);
            if (substitutionExpectedChar == sFound2 && substitutionFoundChar == sExpected2) {
                //                System.out.println(indent(strPos) + "  matchInRadix: SUBST");
                matchInRadix(childrenOfRadixListPos, strPos +1 , str, currentDistance  , radixPos +1 , radix, radixLength,  corrected, 
                        positionOfRadixValue, (char)0, (char)0);
            } else {
                //                System.out.println(indent(strPos) + "  matchInRadix: SUBST FAIL");
            }
            return;
        }

        if (str.length() <= strPos) {
            //            System.out.println(indent(strPos) + " matchInRadix EOS");
            // End of the string, add the remaining radix length (N deletions) and see if the value
            // of this radix can be accepted
            int remainingRadixLength = radixLength - radixPos;
            if (positionOfRadixValue != Long.MAX_VALUE) {
                registerMatch(positionOfRadixValue, currentDistance + remainingRadixLength, corrected);
            }
            return;
        }
        if (radixLength <= radixPos) {
            //            System.out.println(indent(strPos) + " matchInRadix EOR children at " + childrenOfRadixListPos);
            // End of the radix, resume normal work
            if (childrenOfRadixListPos != Long.MAX_VALUE) {
                LongList children = new LongList();
                getChildrenPositions(childrenOfRadixListPos, children);
                //                System.out.println(indent(strPos) + " matchInRadix EOR: " + children.size()  + " children matchRec at=" + currentDistance);

                for (long childPos : children) {
                    //                    System.out.println(indent(strPos) + " matchInRadix EOR: GO TO CHILD at " + childPos + " at=" + currentDistance);

                    matchRec(childPos, strPos, str, currentDistance, corrected, (char)0, (char)0);
                }
                //                System.out.println(indent(strPos) + " matchInRadix EOR: done children");

            }
            return;
        }

        if (str.charAt(strPos) == radix[radixPos]) {
            // Normal match, go to next char in radix and str
            //            System.out.println(indent(strPos) + " matchInRadix MATCH");
            matchInRadix(childrenOfRadixListPos, strPos + 1, str, currentDistance, radixPos + 1, radix, radixLength, corrected, 
                    positionOfRadixValue, (char)0, (char)0);
        } else if (radixPos + 1 < radixLength && str.charAt(strPos) == radix[radixPos + 1]) {
            //            System.out.println(indent(strPos) + " matchInRadix DEL");

            // Deletion
            matchInRadix(childrenOfRadixListPos, strPos +1 , str, currentDistance + 1 , radixPos, radix, radixLength, corrected, 
                    positionOfRadixValue, (char)0, (char)0);
        } else if (strPos + 1 < str.length() && str.charAt(strPos + 1) == radix[radixPos]) {
            //            System.out.println(indent(strPos) + " matchInRadix INSER");

            // Insertion
            matchInRadix(childrenOfRadixListPos, strPos , str, currentDistance + 1 , radixPos + 1, radix, radixLength, corrected, 
                    positionOfRadixValue, (char)0, (char)0);
        } else if (strPos + 1 < str.length() && radixPos + 1  < radixLength &&
                str.charAt(strPos + 1) == radix[radixPos+1]) {
            //            System.out.println(indent(strPos) + " matchInRadix SUBS");

            // Substitution
            matchInRadix(childrenOfRadixListPos, strPos + 1  , str, currentDistance + 1 , radixPos + 1, radix, radixLength, corrected,
                    positionOfRadixValue, (char)0, (char)0);
        } else {
            // There might be a substitution after the radix if we are at the end. That might match too much ...
            if (radixPos + 1 == radixLength) {
                matchInRadix(childrenOfRadixListPos, strPos + 1  , str, currentDistance + 1 , radixPos + 1, radix, radixLength, corrected,
                        positionOfRadixValue, (char)0, (char)0);
            }

            // Try a permutation
            char sExpected = str.charAt(strPos);
            char sFound = radix[radixPos];
            matchInRadix(childrenOfRadixListPos, strPos + 1, str, currentDistance + 1, radixPos + 1, radix, radixLength, corrected,
                    positionOfRadixValue, sFound, sExpected);
        }
    }

    /* Match in a NODE_INTERNAL_VALUE_ONECHAR */
    private void matchRecNIVO(long currentNodePos, int strPos, String str, int currentDistance,
            List<Character> corrected, char substitutionFoundChar, char substitutionExpectedChar) {
        char c = (char)BinaryUtils.decodeLE16(tree.buffer, (int)currentNodePos + 1);
//        System.out.println(indent(strPos) + " VALUE_ONECHAR " + c);//+ " trying to match=" + str.charAt(strPos));

        // Skip value
        long posAfterValue = tree.getPosAfterValue(currentNodePos + 3);

        /* In permutation mode, don't recurse, but increase position */
        if (substitutionExpectedChar != (char)0) {
            if (strPos >= str.length()) {
                //              System.out.println(indent(strPos) + "  matchREC: PERMUT CANCEL LENGTH");
                return;
            }
            char sExpected2 = str.charAt(strPos);
            char sFound2 = c;
            //          System.out.println(indent(strPos) + "  matchREC: PERMUT ?" + substitutionExpectedChar + "/" + substitutionFoundChar + "  vs " + sExpected2 + "/" + sFound2);
            if (substitutionExpectedChar == sFound2 && substitutionFoundChar == sExpected2) {
                //              System.out.println(indent(strPos) + "  matchREC: PERMUT OK ! cD=" + currentDistance + " strPos=" + strPos);

                if (strPos + 1 >= str.length()) {
                    corrected.add(c);
                    registerMatch(currentNodePos + 3, currentDistance  , corrected);
                    corrected.remove(corrected.size() - 1);
                } else {
                    LongList children = new LongList();
                    getChildrenPositions(posAfterValue, children);

                    /* Recurse without increasing the distance further */
                    for (long childPos : children) {
                        corrected.add(c);
                        matchRec(childPos, strPos + 1, str, currentDistance, corrected, (char)0, (char)0);
                        corrected.remove(corrected.size() - 1);
                    }
                }

                //              currentDistance++;
            } else {
                //              System.out.println(indent(strPos) + "  matchREC: PERMUT FAIL");
                return;
            }
        }


        if (strPos < str.length() && c == str.charAt(strPos)) {
            //          System.out.println(indent(strPos) + " perfect match, recursing");
//            System.out.println(indent(strPos ) + " NICO MATCH " + c);//+ " trying to match=" + str.charAt(strPos));

            if (strPos + 1 >= str.length()) {
                corrected.add(c);
                registerMatch(currentNodePos + 3, currentDistance  , corrected);
                corrected.remove(corrected.size() - 1);
            }

            LongList children = new LongList();
            getChildrenPositions(posAfterValue, children);

            for (long childPos : children) {
                corrected.add(c);
                matchRec(childPos, strPos + 1, str, currentDistance, corrected, (char)0, (char)0);
                corrected.remove(corrected.size() - 1);

            }
        } else {
            /* There is a value here, so if we are at end of the string, but don't match, we still record a match at distance + 1 */
            if (strPos + 1 >= str.length()) {
                corrected.add(c);
                registerMatch(currentNodePos + 3, currentDistance + 1, corrected);
                corrected.remove(corrected.size() - 1);
            }

            if (currentDistance < maxDistance) {
//                        System.out.println(indent(strPos ) + " NIVO NO-match INSERT ");//+ " trying to match=" + str.charAt(strPos));
            

                // Try insertion: advance the string by 1, at same position in tree
                matchRec(currentNodePos, strPos + 1, str, currentDistance + 1, corrected, (char)0,  (char)0);

                LongList children = new LongList();
                getChildrenPositions(posAfterValue, children);

//                System.out.println(indent(strPos ) + " NIVO NO-match DELETE ");//+ " trying to match=" + str.charAt(strPos));

                // Try deletion: try the same current leter in the children
                corrected.add(c);
                for (long childPos : children) {
                    matchRec(childPos, strPos, str, currentDistance + 1, corrected, (char)0, (char)0);
                }
                corrected.remove(corrected.size() - 1);
//                System.out.println(indent(strPos ) + " NIVO NO-match SUBST ");//+ " trying to match=" + str.charAt(strPos));

                // Substitution: Ignore the current letter, go to children and advance string
                for (long childPos : children) {
                    corrected.add(c);
                    matchRec(childPos, strPos + 1, str, currentDistance + 1, corrected, (char)0, (char)0);
                    corrected.remove(corrected.size() - 1);
                }
//                System.out.println(indent(strPos ) + " NIVO NO-match PERM ");//+ " trying to match=" + str.charAt(strPos));

                // Permutation: Remember the current letter, current expected letter, advance both,
                // and we'll check next time
                if (strPos < str.length()) {
                    for (long childPos : children) {
                        corrected.add(c);
                        matchRec(childPos, strPos + 1, str, currentDistance + 1, corrected, c, str.charAt(strPos));
                        corrected.remove(corrected.size() - 1);
                    }
                }
//                System.out.println(indent(strPos ) + " NIVO NO-match DONE");//+ " trying to match=" + str.charAt(strPos));

            }

        }
    }

    /* Match in a NODE_INTERNAL_VALUE_RADIX */
    private void matchRecNIVR(long currentNodePos, int strPos, String str, int currentDistance,
            List<Character> corrected, char substitutionFoundChar, char substitutionExpectedChar) {
        readVInt(currentNodePos + 1);
        int radixLength = (int)lastVInt.value;
        byte[] strBuf = new byte[radixLength];
        System.arraycopy(tree.buffer, (int)currentNodePos + 1 + lastVInt.codeSize, strBuf, 0, (int)lastVInt.value);

        char[] radixChars = new char[radixLength];
        int radixCharLength = BinaryUtils.decodeUTF8(strBuf, radixChars);
        //String radix = new String(strBuf); // TODO UTF8

        for (int i = 0; i < radixCharLength; i++) {
            corrected.add(radixChars[i]);
        }
        //String radix = new String(strBuf); // TODO UTF8
        //
        //for (char c : radix.toCharArray()) {
        //            corrected.add(c);
        //}

        int posAfterRadix = (int)currentNodePos + 1 + lastVInt.codeSize + radixLength;

        // Skip value
        long posAfterValue = tree.getPosAfterValue(posAfterRadix);

        //        System.out.println("postAfterRadix=" + posAfterRadix + " afterValue=" + posAfterValue + " total=" + totalSize);
        matchInRadix(posAfterValue,
                strPos, str, currentDistance, 0, radixChars, radixCharLength, corrected, posAfterRadix, (char)0, (char)0);

        for (int i = 0; i < radixCharLength; i++) corrected.remove(corrected.size() - 1);
    }

    /* Match in a NODE_INTERNAL_NOVALUE_ONECHAR */
    private void matchRecNINVO(long currentNodePos, int strPos, String str, int currentDistance,
            List<Character> corrected, char substitutionFoundChar, char substitutionExpectedChar) {
        char c = (char)BinaryUtils.decodeLE16(tree.buffer, (int)currentNodePos + 1);
//        		System.out.println(indent(strPos ) + " NOVALUE_ONECHAR " + c + " GIVES " + curKey(corrected, c));// + " trying to match=" + str.charAt(strPos));

        /* In permutation mode, don't recurse, but increase position */
        if (substitutionExpectedChar != (char)0) {
            if (strPos >= str.length()) {
                //               System.out.println(indent(strPos) + "  matchREC: PERMUT CANCEL LENGTH");
                return;
            }
            char sExpected2 = str.charAt(strPos);
            char sFound2 = c;
            //           System.out.println(indent(strPos) + "  matchREC: PERMUT ?" + substitutionExpectedChar + "/" + substitutionFoundChar + "  vs " + sExpected2 + "/" + sFound2);
            if (substitutionExpectedChar == sFound2 && substitutionFoundChar == sExpected2) {
                //               System.out.println(indent(strPos) + "  matchREC: PERMUT");
                //               currentDistance++;
            } else {
                //               System.out.println(indent(strPos) + "  matchREC: PERMUT FAIL");
                return;
            }
        }


        if (str.length() > strPos && c == str.charAt(strPos)) {
            LongList children = new LongList();
            getChildrenPositions(currentNodePos + 3, children);
            //           System.out.println(indent(strPos) + " match");
            for (long childPos : children) {
                corrected.add(c);
                matchRec(childPos, strPos + 1, str, currentDistance, corrected, (char)0, (char)0);
                corrected.remove(corrected.size() - 1);
            }
        } else {
            if (currentDistance < maxDistance ){
                if (strPos + 1 <= str.length()) {
                    //               System.out.println(indent(strPos ) + " trying deletion");
                    //  Try deletion: advance the string by 1
                    corrected.add(c);
                    matchRec(currentNodePos, strPos + 1, str, currentDistance + 1, corrected, (char)0, (char)0);
                    corrected.remove(corrected.size() - 1);
                }

                LongList children = new LongList();
                getChildrenPositions(currentNodePos + 3, children);
                // Try insertion: ignore the current letter, go to children

                for (long childPos : children) {
                    corrected.add(c);
                    //               System.out.println(indent(strPos ) + " trying insertion");
                    matchRec(childPos, strPos, str, currentDistance + 1, corrected, (char)0, (char)0);
                    corrected.remove(corrected.size() - 1);
                }

                // Substitution: Ignore the current letter, go to children and advance string
                for (long childPos : children) {
                    //               System.out.println(indent(strPos) + " trying substitution");
                    corrected.add(c);
                    matchRec(childPos, strPos + 1, str, currentDistance + 1, corrected, (char)0, (char)0);
                    corrected.remove(corrected.size() - 1);
                }

                // Permutation: Remember the current letter, current expected letter, advance both,
                // and we'll check next time
                if (strPos < str.length()) {
                    //               System.out.println(indent(strPos) + " trying permutation on " + currentDistance + " cK=" + curKey(corrected) + " permut=" + c + " and " + str.charAt(strPos));
                    for (long childPos : children) {
                        corrected.add(c);
                        matchRec(childPos, strPos + 1, str, currentDistance + 1, corrected, c, str.charAt(strPos));
                        corrected.remove(corrected.size() - 1);
                    }
                    //               System.out.println(indent(strPos) + " DONE trying permutation on " + currentDistance + " cK=" + curKey(corrected) + " permut=" + c + " and " + str.charAt(strPos));

                }
            }
        }
    }

    private void matchRecNINVR(long currentNodePos, int strPos, String str, int currentDistance,
            List<Character> corrected, char substitutionFoundChar, char substitutionExpectedChar) {
        readVInt(currentNodePos + 1);
        int radixLength = (int)lastVInt.value;
        byte[] strBuf = new byte[radixLength];
        System.arraycopy(tree.buffer, (int)currentNodePos + 1 + lastVInt.codeSize, strBuf, 0, (int)lastVInt.value);

        char[] radixChars = new char[radixLength];
        int radixCharLength = BinaryUtils.decodeUTF8(strBuf, radixChars);
        //String radix = new String(strBuf); // TODO UTF8

        for (int i = 0; i < radixCharLength; i++) {
            corrected.add(radixChars[i]);
        }

        int posAfterRadix = (int)currentNodePos + 1 + lastVInt.codeSize + radixLength;
        matchInRadix(posAfterRadix, strPos, str, currentDistance, 0, radixChars, radixCharLength, corrected, Long.MAX_VALUE
                , (char)0, (char)0);

        for (int i = 0; i < radixCharLength; i++) corrected.remove(corrected.size() - 1);
    }

    private void matchRec(long currentNodePos, int strPos, String str, int currentDistance,
            List<Character> corrected, char substitutionFoundChar, char substitutionExpectedChar) {
//        		System.out.println(indent(strPos) + " FUZZY cD=" + currentDistance + " s=" + str + " p=" + strPos + " at=" + currentNodePos + " curKey=" + curKey(corrected) + " SUBSTMODE=" + substitutionExpectedChar);

        // EOF --> Do not cancel, we must keep going down, until we reach the maximum distance
        if (strPos +1 > str.length()) {
            //            System.out.println(indent(strPos) + " FUZZY eOS !!");
            //            return;
        }
        if (currentDistance > maxDistance) { // && substitutionFoundChar == (char)0) {
            //            System.out.println(indent(strPos) + " FUZZY Abort distance !");
            return;
        }

        byte nodeType = tree.buffer[(int)currentNodePos];

        if (nodeType == RadixTreeWriter.NODE_INTERNAL_NOVALUE_ONECHAR) {
            matchRecNINVO(currentNodePos, strPos, str, currentDistance,
                    corrected, substitutionFoundChar, substitutionExpectedChar);

        } else if (nodeType == RadixTreeWriter.NODE_INTERNAL_NOVALUE_RADIX) {
            matchRecNINVR(currentNodePos, strPos, str, currentDistance,
                    corrected, substitutionFoundChar, substitutionExpectedChar);

        } else if (nodeType == RadixTreeWriter.NODE_INTERNAL_VALUE_ONECHAR) {
            matchRecNIVO(currentNodePos, strPos, str, currentDistance,
                    corrected, substitutionFoundChar, substitutionExpectedChar);

        } else if (nodeType == RadixTreeWriter.NODE_INTERNAL_VALUE_RADIX) {
            matchRecNIVR(currentNodePos, strPos, str, currentDistance,
                    corrected, substitutionFoundChar, substitutionExpectedChar);

        } else if (nodeType == RadixTreeWriter.NODE_FINAL_ONECHAR) {
            char c = (char)BinaryUtils.decodeLE16(tree.buffer, (int)currentNodePos + 1);
//                        System.out.println(indent(strPos) + " FINAL_ONECHAR " + c);

            corrected.add(c);
            if (strPos + 1 == str.length() && c == str.charAt(strPos)) {
                registerMatch(currentNodePos + 3, currentDistance, corrected);
            } else if (strPos + 1 >= str.length()){
                // String is done but value is wrong -> substitution
                registerMatch(currentNodePos + 3, currentDistance + 1, corrected);
            }
            corrected.remove(corrected.size() - 1);

        } else if (nodeType == RadixTreeWriter.NODE_FINAL_RADIX) {
            //            System.out.println(indent(strPos ) + " FINAL_RADIX ");

            readVInt(currentNodePos + 1);
            int radixLength = (int)lastVInt.value;
            byte[] strBuf = new byte[radixLength];
            System.arraycopy(tree.buffer, (int)currentNodePos + 1 + lastVInt.codeSize, strBuf, 0, (int)lastVInt.value);

            char[] radixChars = new char[radixLength];
            int radixCharLength = BinaryUtils.decodeUTF8(strBuf, radixChars);
            //String radix = new String(strBuf); // TODO UTF8

            for (int i = 0; i < radixCharLength; i++) {
                corrected.add(radixChars[i]);
            }

            matchInRadix(Long.MAX_VALUE, strPos, str, currentDistance, 0, radixChars, radixCharLength, corrected,
                    currentNodePos + 1 + lastVInt.codeSize + strBuf.length, (char)0, (char)0);

            for (int i = 0; i < radixCharLength; i++) corrected.remove(corrected.size() - 1);


        } else if (nodeType == RadixTreeWriter.NODE_HEADER) {
            //            System.out.println(indent(strPos) + " HEADER NODE");
            // This node always matches, recurse on children
            LongList children = new LongList();
            getChildrenPositions(currentNodePos + 1, children);
            for (long childPos : children) {
                matchRec(childPos, strPos + 1, str, currentDistance, corrected, (char)0, (char)0);
            }
        } else {
            System.out.println(indent(strPos) + " UNKNONWN TYPE IS " + nodeType);
        }
    }

    private String curKey(List<Character> corrected) {
        String s = "";
        for (char c : corrected) {
            s += c;
        }
        return s;
    }
//    private String curKey(List<Character> corrected, char additional) {
//        return curKey(corrected) + additional;
//    }

    private void registerMatch(long valuePos, int distance, List<Character> corrected) {
        if (distance > maxDistance) {
            //            System.out.println("Cancel bad match distance");
            return;
        }
        ApproximateMatch am = new ApproximateMatch();
        am.distance = distance;
        if (tree.byteArrayMode) {
            readVInt(valuePos);
            int valueSize = (int)lastVInt.value;
            am.byteArrayValue = new byte[valueSize];
            System.arraycopy(tree.buffer, (int)valuePos + lastVInt.codeSize, am.byteArrayValue, 0, valueSize);
        } else {
            readVInt(valuePos);
            am.value = lastVInt.value;
        }
        am.key = curKey(corrected);
        //        System.out.println("************ MATCH " + am.key +" v=" + am.value + " at " + am.distance);

        matches.add(am);
    }
}
