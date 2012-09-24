package fr.openstreetmap.search.binary;
import java.io.IOException;
import java.io.OutputStream;


public class BinaryStreamEncoder {
    private byte[] buffer = new byte[10];
    private OutputStream os;
    private long written;
    
    public BinaryStreamEncoder(OutputStream os) {
        this.os = os;
    }
    
    public long getWritten() {
        return written;
    }
    
    public void writeVInt(long value) throws IOException {
        int codeSize = BinaryUtils.writeVInt(value, buffer, 0);
        os.write(buffer, 0, codeSize);
        written += codeSize;
    }

    public void writeLE64(long value) throws IOException {
        BinaryUtils.encodeLE64(value, buffer, 0);
        os.write(buffer, 0, 8);
        written += 8;
    }
    
    public void writeLE32(int value) throws IOException {
        BinaryUtils.encodeLE32(value, buffer, 0);
        os.write(buffer, 0, 4);
        written += 4;
    }

    public void writeUCS2Char(char c) throws IOException {
        BinaryUtils.encodeLE16((short)c, buffer, 0);
        os.write(buffer, 0, 2);
        written += 2;
    }

    public void writeUTF8LenAndString(String s) throws IOException {
        byte[] data = s.getBytes("utf8");
        writeVInt(data.length);
        os.write(data);
        written += data.length;
    }
    
    public void writeByte(byte b) throws IOException {
        os.write(b);
        written++;
    }
    public void writeBytes(byte[] b) throws IOException {
        os.write(b);
        written+=b.length ;
    }
}
