package fr.openstreetmap.search.tree;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import fr.openstreetmap.search.binary.BinaryStreamEncoder;


public class RadixTreeWriter {
    long written;
    public boolean byteBuf;
    StringBuilder debug = new StringBuilder();

    // An internal node on the stack
    static class RadixTreeNode {
        public RadixTreeNode(int prefixSizeBefore, String data) {
            this.data = data;
            this.prefixSizeBefore = prefixSizeBefore;
        }
        public RadixTreeNode(int prefixSizeBefore, String data, long value, byte[] byteValue) {
            this.data = data;
            this.prefixSizeBefore = prefixSizeBefore;
            setValue(value, byteValue);
        }

        List<Long> flushedChildrenPositions = new ArrayList<Long>();
        int prefixSizeBefore;
        String data;

        public boolean hasValue() {
            return hasValue;
        }

        public long getValue() {
            return value;
        }
        public byte[] getByteValue() {
            return byteValue;
        }

        public void setValue(long value, byte[] byteValue) {
            hasValue = true;
            this.value = value;
            this.byteValue = byteValue;
        }

        public void clearValue() {
            hasValue = false;
        }

        private boolean hasValue;
        private long value;
        private byte[] byteValue;

    }


    public RadixTreeWriter(OutputStream os) {
        encoder = new BinaryStreamEncoder(os);
    }

    private int commonPrefixLength(String newEntry) {
        int smallestSize = Math.min(newEntry.length(), previousEntry.length());
        for (int i = 0; i <  smallestSize; i++) {
            if (newEntry.charAt(i) != previousEntry.charAt(i)) {
                return i;
            }
        }
        return smallestSize;
    }

    private void flushStackNode(RadixTreeNode node, RadixTreeNode parent) throws IOException {
//        System.out.println("  ****Flushing node " + node.data + " at " + node.prefixSizeBefore + " into " + parent.data + "/" + parent.prefixSizeBefore + "***");

        written++;

        long writtenAt = writeNode(node);

        parent.flushedChildrenPositions.add(writtenAt);
    }

    public static final byte NODE_FINAL_ONECHAR = 1;
    public static final byte NODE_FINAL_RADIX = 2;
    public static final byte NODE_INTERNAL_VALUE_ONECHAR = 3;
    public static final byte NODE_INTERNAL_VALUE_RADIX = 4;
    public static final byte NODE_INTERNAL_NOVALUE_ONECHAR = 5;
    public static final byte NODE_INTERNAL_NOVALUE_RADIX = 6;
    public static final byte NODE_HEADER = 7;

    private void writeChildrenPositions(RadixTreeNode node) throws IOException{
        // TODO: Delta-encode !
        encoder.writeVInt(node.flushedChildrenPositions.size());
        for (int i = 0; i < node.flushedChildrenPositions.size(); i++) {
//            System.out.println("WRITING CHILD OF NODE, it's at " + node.flushedChildrenPositions.get(i));
            encoder.writeVInt(node.flushedChildrenPositions.get(i));
        }
    }

    private long writeNode(RadixTreeNode node) throws IOException {
        long writtenPosition = encoder.getWritten();
//        System.out.println("WRITE NODE " + node.data + " AT " + writtenPosition +" with " + node.flushedChildrenPositions.size() + " children");
        if (node.flushedChildrenPositions.size() == 0) {
//            System.out.println(" IT4S FINAL");
            // Final nodes must have  values !
            assert(node.hasValue());
            // And must have text
            assert(node.data.length() > 0);

            if (node.data.length() == 1) {
                encoder.writeByte(NODE_FINAL_ONECHAR);
                encoder.writeUCS2Char(node.data.charAt(0));
                if (byteBuf) {
                    encoder.writeVInt(node.getByteValue().length);
                    encoder.writeBytes(node.getByteValue());
                } else {
                    encoder.writeVInt(node.getValue());
                }
            } else if (node.data.length() > 1) {
                encoder.writeByte(NODE_FINAL_RADIX);
                encoder.writeUTF8LenAndString(node.data);
                if (byteBuf) {
                    encoder.writeVInt(node.getByteValue().length);
                    encoder.writeBytes(node.getByteValue());
                } else {
                    encoder.writeVInt(node.getValue());
                }
            }
        } else {
            if (node.data.length() == 0) {
                System.out.println("WRITE HEADER AAT " + encoder.getWritten());
                long beforeHeaderNodeChildren = encoder.getWritten(); 
                encoder.writeByte(NODE_HEADER);
                writeChildrenPositions(node);
                long afterHeaderNodeChildren = encoder.getWritten();
                System.out.println("WROTE HEADER AT " + encoder.getWritten());

                encoder.writeLE32((int)(afterHeaderNodeChildren - beforeHeaderNodeChildren));
                return writtenPosition;
            }
            
            // Internal node
            if (node.hasValue()) {
                if (node.data.length() == 1) {
                    encoder.writeByte(NODE_INTERNAL_VALUE_ONECHAR);
                    encoder.writeUCS2Char(node.data.charAt(0));
                    if (byteBuf) {
                        encoder.writeVInt(node.getByteValue().length);
                        encoder.writeBytes(node.getByteValue());
                    } else {
                        encoder.writeVInt(node.getValue());
                    }

                    writeChildrenPositions(node);
                } else if (node.data.length() > 1) {
                    encoder.writeByte(NODE_INTERNAL_VALUE_RADIX);
                    encoder.writeUTF8LenAndString(node.data);
                    if (byteBuf) {
                        encoder.writeVInt(node.getByteValue().length);
                        encoder.writeBytes(node.getByteValue());
                    } else {
                        encoder.writeVInt(node.getValue());
                    }
                    writeChildrenPositions(node);
                }
            } else {
                if (node.data.length() == 1) {
                    encoder.writeByte(NODE_INTERNAL_NOVALUE_ONECHAR);
                    encoder.writeUCS2Char(node.data.charAt(0));
                    writeChildrenPositions(node);
                } else if (node.data.length() > 1) {
                    encoder.writeByte(NODE_INTERNAL_NOVALUE_RADIX);
                    encoder.writeUTF8LenAndString(node.data);
                    writeChildrenPositions(node);
                }
            }
        }

        debug.append("[NODE " + written + "] TEXT=" + node.data + " CHILDREN=");
        for (int i = 0; i < node.flushedChildrenPositions.size(); i++) {
            debug.append(" " + node.flushedChildrenPositions.get(i) + " ");
        }
        if (node.hasValue()) {
            debug.append("  VALUE=" + node.getValue());
        }
        debug.append("\n");
        
        return writtenPosition;
    }

    public void flush()  throws IOException {
        if (previousEntry == null) return; // Empty radix tree !
        for (int j = stack.size() - 1; j >= 1; j--) {
            flushStackNode(stack.get(j), stack.get(j-1));
        }
        writeNode(stack.get(0));
    }
    
    public void addEntry(String entry, long value) throws IOException {
        addEntry(entry, value, null);
    }

    public void addEntry(String entry, long value, byte[] byteValue) throws IOException {
//        System.out.println("******** Add Entry " + entry + " stackSize= " + stack.size());
        if (previousEntry == null) {
            previousEntry = entry;
            stack.add(new RadixTreeNode(0, ""));
            stack.add(new RadixTreeNode(0, previousEntry, value, byteValue));
            return;
        }
        
        if (entry.compareTo(previousEntry) < 0) {
        	throw new IOException("NEw key is too small: " + previousEntry + " -> " + entry);
        }

        int commonPrefixLength = commonPrefixLength(entry);
//        System.out.println("Add " + entry + " prev="+ previousEntry + " comLen=" + commonPrefixLength(entry));

        for (int i = 0; i < stack.size(); i++) {
            RadixTreeNode rtn = stack.get(i);
//            System.out.println("  Check vs " + rtn.data + " (v=" + rtn.getValue() + ")");
            if (commonPrefixLength == rtn.prefixSizeBefore + rtn.data.length()) {
//                System.out.println("Exact match");
                // The common prefix stops right at the boundary of this stack element:
                // We must flush the nodes for all elements that are further in the stack,
                // starting by the last one.
                for (int j = stack.size() - 1; j >= i + 1; j--) {
                    flushStackNode(stack.get(j), stack.get(j-1));
                }
                while (stack.size() > i + 1) {
                    stack.remove(stack.size() - 1);
                }
                break;
            } else if (commonPrefixLength < rtn.prefixSizeBefore + rtn.data.length()) {
                // The common prefix is smaller than this stack element, so we must split this element
                // to create a new one, and flush the further nodes, starting by the last one.
                int leftSize =  commonPrefixLength - rtn.prefixSizeBefore ;
//                System.out.println("Partial match splitAt=" + leftSize);
                String leftData = rtn.data.substring(0, leftSize);
                String rightData = rtn.data.substring(leftSize);
                // Create a node with the right data
                RadixTreeNode rtnRight = new RadixTreeNode(rtn.prefixSizeBefore + leftSize, rightData);
                for (int k = 0 ; k < rtn.flushedChildrenPositions.size(); k++) {
                    rtnRight.flushedChildrenPositions.add(rtn.flushedChildrenPositions.get(k));
                }
                if (rtn.hasValue()) {
                    rtnRight.setValue(rtn.getValue(), rtn.getByteValue());
                }
                rtn.clearValue();
                rtn.flushedChildrenPositions.clear();
//                System.out.println(" Created a new RIGHT node: " + rtnRight.data + "/" + rtnRight.prefixSizeBefore + " v=" + rtnRight.getValue());

                // Replace the current node by the right data one
                stack.set(i, rtnRight);

                // Flush until the right part
//                System.out.println("Flushing after the right node into the right node");
                for (int j = stack.size() - 1; j >= i + 1; j--) {
                    flushStackNode(stack.get(j), stack.get(j-1));
                }
                // Stop at i, not i  + 1, we'll repush the left part
                while (stack.size() > i) {
                    stack.remove(stack.size() - 1);
                }
//                System.out.println("Flushing the right node into the left node,stack size= "+ stack.size());

                // And flush the right part, adding it to the left part
                rtn.data = leftData;
                flushStackNode(rtnRight, rtn);
                // And don't loose it :)
                stack.add(rtn);
            }
        }
        /* Now, add the non common part as a new stack element */
        RadixTreeNode rtn = new RadixTreeNode(commonPrefixLength, entry.substring(commonPrefixLength));
        rtn.setValue(value, byteValue);
//        System.out.println("Will push on stack " + rtn.data + "/" + rtn.prefixSizeBefore + " ssize= " + stack.size());
        stack.add(rtn);

        previousEntry = entry;
    }

    private OutputStream os;
    private BinaryStreamEncoder encoder;
    private List<RadixTreeNode> stack = new ArrayList<RadixTreeNode>(); 
    private String previousEntry;
}