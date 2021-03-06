package fr.openstreetmap.search.autocomplete.test;

import org.apache.commons.lang.StringUtils;
import org.junit.Test;

import fr.openstreetmap.search.simple.OSMAutocompleteUtils;
import fr.openstreetmap.search.simple.OSMAutocompleteUtils.MatchData;

public class OSMAutocompleteUtilsTest {
    @Test
       public void encodeDecode() throws Exception {
           OSMAutocompleteUtils u = new OSMAutocompleteUtils();
           
           System.out.println(
                   OSMAutocompleteUtils.jsonLegacyEncodedData(true, "highway", "rue des pouets", new String[]{"Boulogne", "Paris"}, 42.1, -44.7).getBytes("utf8").length);
           
           byte[] data = u.encodeData(true, "highway", "rue des pouets", new long[]{12, 42, 12303}, 42.1, -44.7, 42);
           
           System.out.println(data.length);
           MatchData md = OSMAutocompleteUtils.decodeData(data);
           
           System.out.println(md.isWay);
           System.out.println(md.type);
           System.out.println(md.name);
           System.out.println(md.cityIds[1]);
           System.out.println(md.lon);
           System.out.println(md.lat);

       }
}
