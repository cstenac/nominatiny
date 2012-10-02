package fr.openstreetmap.search.autocomplete;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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

    public enum ElementType {
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
        AERIALWAY;
       
        public static ElementType fromString(String type) {
            return ElementType.valueOf(type.toUpperCase());
        }
    }

    public static byte typeId(String type) {
        return (byte)ElementType.fromString(type).ordinal();
    }
    public static String typeName(byte type) {
        return ElementType.values()[type].toString().toLowerCase();
    }
    
    public static class MatchData {
        boolean isWay;
        String type;
        String name;
        String[] cityNames;
        double lon;
        double lat;
    }

    
    public OSMAutocompleteUtils() {
        bse = new BinaryStreamEncoder(baos);
    }
    
    public byte[] encodeData(boolean isWay, String type, String name, String[] cityNames, double lon, double lat) throws IOException {
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
        
        return baos.toByteArray();
    }
    
    public String jsonLegacyEncodedData(boolean isWay, String type, String name, String[] cityNames, double lon, double lat) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("name", name);
        obj.put("city", StringUtils.join(cityNames, ","));
        obj.put("type", type);
        obj.put("lat", lat);
        obj.put("lon", lon);
        return obj.toString();

    }
    
    public MatchData decodeData(byte[] encoded) throws IOException {
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

        return ret;
     }
}