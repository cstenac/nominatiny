package fr.openstreetmap.search.binary;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.apache.commons.lang.mutable.MutableInt;


public class BinaryUtils {
    
    /* Returns the length */
    public static int decodeUTF8(byte[] data, char[] chars) {
        int len = 0;
        int offset = 0;
        while (offset < data.length) {
            if ((data[offset] & 0x80) == 0) {
                // 0xxxxxxx - it is an ASCII char, so copy it exactly as it is
                chars[len] = (char) data[offset];
                len++;
                offset++;
            } else {
                int uc = 0;
                if ((data[offset] & 0xE0) == 0xC0) {
                    uc = (int) (data[offset] & 0x1F);
                    offset++;
                    uc <<= 6;
                    uc |= (int) (data[offset] & 0x3F);
                    offset++;
                } else if ((data[offset] & 0xF0) == 0xE0) {
                    uc = (int) (data[offset] & 0x0F);
                    offset++;
                    uc <<= 6;
                    uc |= (int) (data[offset] & 0x3F);
                    offset++;
                    uc <<= 6;
                    uc |= (int) (data[offset] & 0x3F);
                    offset++;

                } else if ((data[offset] & 0xF8) == 0xF0) {
                    uc = (int) (data[offset] & 0x07);
                    offset++;
                    uc <<= 6;
                    uc |= (int) (data[offset] & 0x3F);
                    offset++;
                    uc <<= 6;
                    uc |= (int) (data[offset] & 0x3F);
                    offset++;
                    uc <<= 6;
                    uc |= (int) (data[offset] & 0x3F);
                    offset++;

                } else if ((data[offset] & 0xFC) == 0xF8) {
                    uc = (int) (data[offset] & 0x03);
                    offset++;
                    uc <<= 6;
                    uc |= (int) (data[offset] & 0x3F);
                    offset++;
                    uc <<= 6;
                    uc |= (int) (data[offset] & 0x3F);
                    offset++;
                    uc <<= 6;
                    uc |= (int) (data[offset] & 0x3F);
                    offset++;
                    uc <<= 6;
                    uc |= (int) (data[offset] & 0x3F);
                    offset++;

                } else if ((data[offset] & 0xFE) == 0xFC) {
                    uc = (int) (data[offset] & 0x01);
                    offset++;
                    uc <<= 6;
                    uc |= (int) (data[offset] & 0x3F);
                    offset++;
                    uc <<= 6;
                    uc |= (int) (data[offset] & 0x3F);
                    offset++;
                    uc <<= 6;
                    uc |= (int) (data[offset] & 0x3F);
                    offset++;
                    uc <<= 6;
                    uc |= (int) (data[offset] & 0x3F);
                    offset++;
                    uc <<= 6;
                    uc |= (int) (data[offset] & 0x3F);
                    offset++;
                }

                len = toChars(uc, chars, len);
            }
        }
        return len;
    }
    public static int toChars(int codePoint, char[] dst, int index) {
        if (codePoint < 0 || codePoint > Character.MAX_CODE_POINT) {
            throw new IllegalArgumentException();
        }
        if (codePoint < Character.MIN_SUPPLEMENTARY_CODE_POINT) {
            dst[index] = (char) codePoint;
            return ++index;
        }
        int offset = codePoint - Character.MIN_SUPPLEMENTARY_CODE_POINT;
        dst[index + 1] = (char) ((offset & 0x3ff) + Character.MIN_LOW_SURROGATE);
        dst[index] = (char) ((offset >>> 10) + Character.MIN_HIGH_SURROGATE);
        return index + 2;
    }
    
    final public static void encodeLE64(long value, byte[] buffer, int pos) {
        buffer[pos] = (byte) ((int) (value) & 0xFF);
        buffer[pos + 1] = (byte) ((int) (value >> 8) & 0xFF);
        buffer[pos + 2] = (byte) ((int) (value >> 16) & 0xFF);
        buffer[pos + 3] = (byte) ((int) (value >> 24) & 0xFF);
        buffer[pos + 4] = (byte) ((int) (value >> 32) & 0xFF);
        buffer[pos + 5] = (byte) ((int) (value >> 40) & 0xFF);
        buffer[pos + 6] = (byte) ((int) (value >> 48) & 0xFF);
        buffer[pos + 7] = (byte) ((int) (value >> 56) & 0xFF);
    }

    /** Write a little-endian 32-bit integer. */
    final public static void encodeLE32(int value, byte[] buffer, int pos) {
        buffer[pos] = (byte) ((value) & 0xFF);
        buffer[pos + 1] = (byte) ((value >> 8) & 0xFF);
        buffer[pos + 2] = (byte) ((value >> 16) & 0xFF);
        buffer[pos + 3] = (byte) ((value >> 24) & 0xFF);
    }
    
    /** Write a little-endian 32-bit integer. */
    final public static void encodeLE16(short value, byte[] buffer, int pos) {
        buffer[pos] = (byte) ((value) & 0xFF);
        buffer[pos + 1] = (byte) ((value >> 8) & 0xFF);
    }

    final public static int decodeLE32(byte[] buffer, int offset){
        return (((int)buffer[offset + 0] & 0xff)      ) |
                (((int)buffer[offset + 1] & 0xff) <<  8) |
                (((int)buffer[offset + 2] & 0xff) << 16) |
                (((int)buffer[offset + 3] & 0xff) << 24);
    }

    final public static long decodeLE64(byte[] buffer, int offset) {
        return (((long)buffer[offset] & 0xff)      ) |
                (((long)buffer[offset + 1] & 0xff) <<  8) |
                (((long)buffer[offset + 2] & 0xff) << 16) |
                (((long)buffer[offset + 3] & 0xff) << 24) |
                (((long)buffer[offset + 4] & 0xff) << 32) |
                (((long)buffer[offset + 5] & 0xff) << 40) |
                (((long)buffer[offset + 6] & 0xff) << 48) |
                (((long)buffer[offset + 7] & 0xff) << 56);

    }

    /** Write a vint into a buffer. Returns the number of written bytes */
    public static int writeVInt(long value, byte[] buffer, int pos) {
        int written = 0;
        while (true) {
            byte b = (byte)(value & 0x7F);
            value >>= 7;
                if (value == 0) {
                    buffer[pos + (written++)] = (byte) (b << 1);
                    break;
                }
                else {
                    buffer[pos + (written++)] = (byte) ((b << 1) | 0x1);
                }

        }
        return written;
    }

    
    public static class VInt {
        public long value;
        public int codeSize;
    }
    
    /** Read a vint from the buffer */
    public static void readVInt(byte[] buffer, int pos, VInt ret) {
        ret.codeSize = 0;
        int shift = 0;
        long result = 0;
        while (shift < 64) {
            ret.codeSize++;
            byte b = buffer[pos++];
            long lb = b >= 0 ? b : 256 +(long)b;
            result |= (lb >> 1) << shift;
            shift += 7;
            if ((lb & 0x01) == 0) {
                ret.value = result;
                return;
            }
        }
        throw new Error("Malformed Vint");
    }
    
    /** Read a vint from a byte buffer at a position */
    public static void readVInt(ByteBuffer buffer, long pos, VInt ret) {
    	byte[] tmpBuf = new byte[10];
    	buffer.position((int)pos);
    	buffer.get(tmpBuf, 0, Math.min(10, buffer.remaining()));
//    	System.out.println("readVInt at" + pos);
    	readVInt(tmpBuf, 0, ret);
    }

    /** Read a vint from the buffer */
    public static long readVInt(byte[] buffer, int pos) {
        int shift = 0;
        long result = 0;
        while (shift < 64) {
            byte b = buffer[pos++];
            long lb = b >= 0 ? b : 256 +(long)b;
            result |= (lb >> 1) << shift;
            shift += 7;
            if ((lb & 0x01) == 0) {
                return result;
            }
        }
        throw new Error("Malformed Vint");
    }
    
    public static String readUTF8LenAndString(byte[] buffer, int pos, MutableInt newPos) throws IOException {
        VInt v = new VInt();
        readVInt(buffer, pos, v);
        byte[] data = Arrays.copyOfRange(buffer, pos + v.codeSize, (int)(pos + v.codeSize + v.value));
        newPos.setValue(pos + v.codeSize + v.value);
        return new String(data, "utf8");
    }
}
