package fr.openstreetmap.search.tree;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import fr.openstreetmap.search.binary.BinaryUtils;
import fr.openstreetmap.search.binary.BinaryUtils.VInt;


public class RadixTree {
    public ByteBuffer buffer;
    public long totalSize;
    
    public boolean byteArrayMode;

    private static class Match {
        boolean found;
        boolean tooFar;
        long foundValue;
    }
    
    long getHeaderNodePos() {
        long headerNodeSizePos = totalSize - 4  ;
        byte[] tmpBuf =new byte[4];
        buffer.position((int)headerNodeSizePos);
        buffer.get(tmpBuf, 0, 4);

        int headerNodeSize = BinaryUtils.decodeLE32(tmpBuf, 0);
        return totalSize - 4 - headerNodeSize ;
    }
    
    long getPosAfterValue(long valuePos) {
        byte[] tmpBuf = new byte[10];
        BinaryUtils.VInt vi = new BinaryUtils.VInt();
            buffer.position((int)valuePos);
        
        buffer.get(tmpBuf, 0, Math.min(10, buffer.remaining()));
        BinaryUtils.readVInt(tmpBuf, 0, vi);
        if (byteArrayMode) {
//        	System.out.println("Read valuelen=" + vi.value + " codeSIze="  + vi.codeSize);
            return valuePos + vi.codeSize + vi.value;
        } else {
            return valuePos + vi.codeSize;
        }
    }

    public long getEntry(String entry) {
        long headerNodePos = getHeaderNodePos();
        Match parentMatch = new Match();
        getEntryRec((int)headerNodePos, -1, entry, parentMatch);
        return parentMatch.foundValue;
    }

    private void searchChildren(int currentPos, int nextStrPos, String str, Match match) {
        byte[] tmpBuf =new byte[10];
        BinaryUtils.VInt vint = new BinaryUtils.VInt();
        //        buffer.position(currentPos + 3);
        buffer.position(currentPos);
        buffer.get(tmpBuf, 0, Math.min(10, buffer.remaining()));
        BinaryUtils.readVInt(tmpBuf, 0, vint);
        int nbChildren = (int) vint.value;
        currentPos += vint.codeSize;

        System.out.println(indent(nextStrPos) + " children: " + nbChildren);

        for (int i = 0; i < nbChildren; i++) {
            // TODO better share
            buffer.position(currentPos);
            buffer.get(tmpBuf, 0, Math.min(10, buffer.remaining()));
            System.out.println(indent(nextStrPos)  + " readvint at " + currentPos);
            BinaryUtils.readVInt(tmpBuf, 0, vint);
            long childPos = vint.value;
            currentPos += vint.codeSize;
            System.out.println(indent(nextStrPos) + " recurse in child " + i +  "/" + nbChildren + " at " + childPos +" (prevvintsize=" + vint.codeSize);
            getEntryRec((int)childPos, nextStrPos, str, match);
            System.out.println(indent(nextStrPos) + " recurse in child " + i +  "/" + nbChildren + "  DONE at " + childPos +" f=" + match.found + " tf=" + match.tooFar);
            
            if (match.found || match.tooFar) break;
        }
    }

    private String indent(int pos) {
        String s = "";
        for (int i = 0; i <  pos; i++) {
            s += "  ";
        }
        return s;
    }

    byte[] lastVIntTmpBuf = new byte[10];
    BinaryUtils.VInt lastVInt = new BinaryUtils.VInt();

    private void readVInt(long pos) {
        buffer.position((int)pos);
        buffer.get(lastVIntTmpBuf, 0, Math.min(10, buffer.remaining()));
        BinaryUtils.readVInt(lastVIntTmpBuf, 0, lastVInt);
    }

    private void getEntryRec(int currentNodePos, int strPos, String str, Match parentMatch) {
        System.out.println(indent(strPos) + " GER " + currentNodePos + " at " + strPos);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        byte nodeType = buffer.get(currentNodePos);

        if (nodeType == RadixTreeWriter.NODE_INTERNAL_NOVALUE_ONECHAR) {
            short s = buffer.getShort(currentNodePos + 1);
            char c = (char)s;

            System.out.println(indent(strPos + 1) + " NOVALUE_ONECHAR " + c);

            if (c == str.charAt(strPos)) {
                System.out.println(indent(strPos + 1) + " match");
                // The current node is valid, so recurse on children
                Match childMatch = new Match();
                searchChildren(currentNodePos + 3, strPos + 1, str, childMatch);
                if (childMatch.found) {
                    parentMatch.found = true; 
                    parentMatch.foundValue = childMatch.foundValue;
                    return;
                }
            } else if (c > str.charAt(strPos)) {
                System.out.println(indent(strPos + 1) + " too far");
                parentMatch.tooFar = true;
                return;
            } else {
                System.out.println(indent(strPos + 1) + " too early");
                return;
            }
        } else if (nodeType == RadixTreeWriter.NODE_INTERNAL_NOVALUE_RADIX) {
            System.out.println(indent(strPos + 1) + " NOVALUE_RADIX ");

            readVInt(currentNodePos + 1);
            int radixLength = (int)lastVInt.value;
            byte[] strBuf = new byte[radixLength];
            buffer.position(currentNodePos + 1  + lastVInt.codeSize);
            buffer.get(strBuf, 0, (int) lastVInt.value);

            String radix = new String(strBuf); // TODO UTF8
            String currentLookup = str.substring(strPos);

            System.out.println(indent(strPos + 1) + " radix is " + radix);

            if (radix.length() > currentLookup.length()) {
                // Too long
                System.out.println(indent(strPos + 1) + " radix is too long tf=" + parentMatch.tooFar);
                parentMatch.tooFar = false; // TODO 
                return;
            } else if (currentLookup.startsWith(radix)){
                System.out.println(indent(strPos + 1) + " radix is OK");

                // This node is valid, recurse
                Match childMatch = new Match();
                searchChildren(currentNodePos + 1 + lastVInt.codeSize + strBuf.length, strPos + radix.length(), str, childMatch);
                if (childMatch.found) {
                    parentMatch.found = true; 
                    parentMatch.foundValue = childMatch.foundValue;
                    return;
                }
            } else {
                System.out.println(indent(strPos + 1) + " radix is NOK");

            }


        } else if (nodeType == RadixTreeWriter.NODE_INTERNAL_VALUE_ONECHAR) {
            short s = buffer.getShort(currentNodePos + 1);
            char c = (char)s;

            System.out.println(indent(strPos + 1) + " VALUE_ONECHAR " + c);

            if (c == str.charAt(strPos)) {
                System.out.println(indent(strPos + 1) + " match");
                if (strPos + 1 == str.length()) {
                    System.out.println(indent(strPos + 1 ) + " found");
                    parentMatch.found = true;
                    if (byteArrayMode) {
                    	readVInt(currentNodePos + 3);
                    } else {
                    	readVInt(currentNodePos + 3);
                    	parentMatch.foundValue = lastVInt.value;
                    }
                } else {
                    // First, skip value
                	long afterValuePos = getPosAfterValue(currentNodePos + 3);
                    // The current node is valid, so recurse on children
                    Match childMatch = new Match();
                    searchChildren((int)afterValuePos, strPos + 1, str, childMatch);
                    if (childMatch.found) {
                        parentMatch.found = true; 
                        parentMatch.foundValue = childMatch.foundValue;
                        return;
                    }
                }
            } else if (c > str.charAt(strPos)) {
                System.out.println(indent(strPos + 1) + " too far");
                parentMatch.tooFar = true;
                return;
            } else {
                System.out.println(indent(strPos + 1) + " too early");
                return;
            }
            
            
        } else if (nodeType == RadixTreeWriter.NODE_INTERNAL_VALUE_RADIX) {
            System.out.println(indent(strPos + 1) + " VALUE_RADIX ");

            readVInt(currentNodePos + 1);
            int radixLength = (int)lastVInt.value;
            byte[] strBuf = new byte[radixLength];
            buffer.position(currentNodePos + 1  + lastVInt.codeSize);
            buffer.get(strBuf, 0, (int) lastVInt.value);

            String radix = new String(strBuf); // TODO UTF8
            String currentLookup = str.substring(strPos);
            
            int posAfterRadix = currentNodePos + 1 + lastVInt.codeSize + radixLength;

            System.out.println(indent(strPos + 1) + " radix is " + radix);

            if (radix.length() > currentLookup.length()) {
                // Too long
                System.out.println(indent(strPos + 1) + " radix is too long");
                parentMatch.tooFar = false;
                return;
            } else if (currentLookup.equals(radix)) {
                System.out.println(indent(strPos + 1) + " found match");
                parentMatch.found = true;
                if (byteArrayMode) {
                	// TODO
                } else {
                	readVInt(posAfterRadix);
                	parentMatch.foundValue = lastVInt.value;
                }
            } else if (currentLookup.startsWith(radix)){
                System.out.println(indent(strPos + 1) + " radix is OK but no match");
                
                // Skip value
                long afterValuePos = getPosAfterValue(posAfterRadix);
                
                // This node is valid, recurse
                Match childMatch = new Match();
                searchChildren((int)afterValuePos, strPos + radix.length(), str, childMatch);
                if (childMatch.found) {
                    parentMatch.found = true; 
                    parentMatch.foundValue = childMatch.foundValue;
                    return;
                }
            } else {
                System.out.println(indent(strPos + 1) + " radix is NOK");

            }












        } else if (nodeType == RadixTreeWriter.NODE_FINAL_ONECHAR) {
            short s = buffer.getShort(currentNodePos + 1);
            char c = (char)s;
            System.out.println(indent(strPos + 1) + " FINAL_ONECHAR " + c);
            if (str.length() == strPos + 1 && c == str.charAt(strPos) ) {
                System.out.println(indent(strPos + 1) + " match");
                parentMatch.found = true;
                if (byteArrayMode) {
                	// TODO
                } else {
                	readVInt(currentNodePos + 3);
                	parentMatch.foundValue = lastVInt.value;
                }
            } else if (str.length() > strPos + 1 ) {
                System.out.println(indent(strPos +1  ) + " too LONG for ");
//                parentMatch.tooFar = true;
            } else if (c > str.charAt(strPos)) {
                System.out.println(indent(strPos + 1) + " too far for " + str.charAt(strPos));
                parentMatch.tooFar = true;
            }
        } else if (nodeType == RadixTreeWriter.NODE_FINAL_RADIX) {

            System.out.println(indent(strPos + 1) + " FINAL_RADIX ");

            readVInt(currentNodePos + 1);
            int radixLength = (int)lastVInt.value;
            byte[] strBuf = new byte[radixLength];
            buffer.position(currentNodePos + 1  + lastVInt.codeSize);
            buffer.get(strBuf, 0, (int) lastVInt.value);

            String radix = new String(strBuf); // TODO UTF8
            String currentLookup = str.substring(strPos);

            System.out.println(indent(strPos + 1) + " radix is " + radix);

            if (radix.equals(currentLookup)) {
                parentMatch.found = true;
                if (byteArrayMode) {
                	// TODO
                } else {
                	readVInt(currentNodePos + 1 + lastVInt.codeSize +  strBuf.length);
                	parentMatch.foundValue = lastVInt.value;
                }
            }



        } else if (nodeType == RadixTreeWriter.NODE_HEADER) {
            System.out.println(indent(strPos + 1) + " HEADER NODE");
            // This node always matches, recurse on children
            Match childMatch = new Match();
            searchChildren(currentNodePos + 1, strPos + 1, str, childMatch);
            if (childMatch.found) {
                parentMatch.found = true; 
                parentMatch.foundValue = childMatch.foundValue;
                return;
            }
        } else {
            System.out.println(indent(strPos + 1) + " UNKNONWN TYPE IS " + nodeType);
        }


    }
}
