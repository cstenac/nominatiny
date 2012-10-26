package fr.openstreetmap.search.autocomplete;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.mutable.MutableInt;
import org.json.JSONException;
import org.json.JSONObject;

import fr.openstreetmap.search.binary.BinaryStreamEncoder;
import fr.openstreetmap.search.binary.BinaryUtils;
import fr.openstreetmap.search.binary.BinaryUtils.VInt;

public class OSMAutocompleteUtils {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    BinaryStreamEncoder bse;
    
    public static final byte TYPE_WAY = 1;
    public static final byte TYPE_NODE = 2; 

    public static enum ElementType {
        UNKNOWN ,
        HIGHWAY,
        RESIDENTIAL, 
        WATERWAY , 
        CRAFT , 
        AEROWAY , 
        SPORT , 
        SHELTER , 
        LEISURE , 
        POW , 
        SCHOOL , 
        HISTORIC , 
        TOURISM , 
        RESTAURANT , 
        SHOP ,
        PLACE, 
        PUBLIC_TRANSPORT , 
        RAILWAY , 
        BUILDING, 
        AERIALWAY,
        PLACE_OF_WORSHIP,
        OFFICE;
       
        public static ElementType fromString(String type) {
            return ElementType.valueOf(type.toUpperCase());
        }
    }

    public static byte typeId(String type) {
        if (type.length() == 0) return (byte)ElementType.UNKNOWN.ordinal();
        try {
            return (byte)ElementType.fromString(type).ordinal();
        } catch (Throwable t) {
            throw new Error("Failed to resolve type --" + type + "--");
        }
    }
    public static String typeName(byte type) {
        return ElementType.values()[type].toString().toLowerCase();
    }
    
    public static class MatchData {
        public boolean isWay;
        public String type;
        public String name;
        public String[] cityNames;
        public double lon;
        public double lat;
        public long osmId;
    }

    
    public OSMAutocompleteUtils() {
        bse = new BinaryStreamEncoder(baos);
    }
    
    public byte[] encodeData(boolean isWay, String type, String name, String[] cityNames, double lon, double lat, long osmId) throws IOException {
        baos.reset();
        bse.writeByte(isWay ? TYPE_WAY : TYPE_NODE);
        bse.writeByte(typeId(type));
        
        bse.writeUTF8LenAndString(name);
        bse.writeVInt(cityNames.length);
        for (int i = 0; i < cityNames.length; i++) {
            bse.writeUTF8LenAndString(cityNames[i]);
        }
        bse.writeLE64(Double.doubleToLongBits(lon));
        bse.writeLE64(Double.doubleToLongBits(lat));
        
        bse.writeLE64(osmId);
        
        return baos.toByteArray();
    }
    
    public static String jsonLegacyEncodedData(boolean isWay, String type, String name, String[] cityNames, double lon, double lat) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("name", name);
        obj.put("city", StringUtils.join(cityNames, ","));
        obj.put("type", type);
        obj.put("lat", lat);
        obj.put("lon", lon);
        return obj.toString();

    }
    
    public static MatchData decodeData(byte[] encoded) throws IOException {
        MatchData ret = new MatchData();
        MutableInt mi = new MutableInt();
        VInt vi = new VInt();
        
        ret.isWay = encoded[0] == TYPE_WAY;
        ret.type = typeName(encoded[1]);
        
        mi.setValue(2);
        ret.name = BinaryUtils.readUTF8LenAndString(encoded, mi.intValue(), mi);
        
        BinaryUtils.readVInt(encoded, mi.intValue(), vi);
        int nbCities = (int)vi.value;
        mi.setValue(mi.intValue() + vi.codeSize);
        
        ret.cityNames = new String[nbCities];
        for (int i = 0; i < nbCities; i++) {
            ret.cityNames[i] = BinaryUtils.readUTF8LenAndString(encoded, mi.intValue(), mi);
        }
        ret.lon = Double.longBitsToDouble(BinaryUtils.decodeLE64(encoded, mi.intValue()));
        ret.lat = Double.longBitsToDouble(BinaryUtils.decodeLE64(encoded, mi.intValue() + 8));
        
        ret.osmId = BinaryUtils.decodeLE64(encoded, mi.intValue() + 16);
        return ret;
     }
}
