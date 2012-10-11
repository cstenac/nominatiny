package fr.openstreetmap.search.autocomplete;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import fr.openstreetmap.search.binary.BinaryUtils;
import fr.openstreetmap.search.binary.LongList;
import fr.openstreetmap.search.tree.RadixTree;
import fr.openstreetmap.search.tree.RadixTreeFuzzyLookup;


/**
 * Performs auto-completion of a single token
 * This class is not thread safe.
 */
public class Autocompleter {
	ByteBuffer dataBuffer;

	RadixTree rt = new RadixTree();

	public Autocompleter(ByteBuffer radixBuffer, ByteBuffer dataBuffer) {
		this.dataBuffer = dataBuffer;
		rt.buffer = radixBuffer.array();
		rt.totalSize = radixBuffer.limit();
		rt.byteArrayMode = true;
	}
	public Autocompleter(byte[] radixBuffer, ByteBuffer dataBuffer) {
        this.dataBuffer = dataBuffer;
        rt.buffer = radixBuffer;
        rt.totalSize = radixBuffer.length;
        rt.byteArrayMode = true;
    }
	
	public static class DebugInfo {
	    public String value;
		public long radixTreeMatchTime;
		public long radixTreeMatches;
		public long listsDecodingTime;
		public long decodedMatches;
	}

	public static class AutocompleterEntry implements Comparable<AutocompleterEntry>{
		AutocompleterEntry(long offset, int distance, long score) {
			this.offset = offset;
			this.distance = distance;
			this.score = score;
		}
		public String correctedPrefix;
		public int distance;
		public long offset;
		public long score;
		@Override
		public int compareTo(AutocompleterEntry o) {
			if (distance < o.distance) return -1;
			if (distance > o.distance) return 1;
			if (score > o.score) return -1;
			if (score < o.score) return 1;
			return 0;
		}
	}
	
	public void getOffsets(String query, int maxDistance, DebugInfo di, LongList offsets, LongList scores, LongList distances,
	                       List<String> correctedPrefixes) {
	    long t0 = 0, t1 = 0;
        RadixTreeFuzzyLookup rtfl = new RadixTreeFuzzyLookup(rt);
        
        if (di != null) t0 = System.nanoTime();
        rtfl.match(query, maxDistance);
        if (di != null) {
            t1 = System.nanoTime();
            di.radixTreeMatchTime = (t1 - t0)/1000;
            di.radixTreeMatches = rtfl.getMatches().size();
        }
        
//      System.out.println("QUERY "  + query + " has " + rtfl.matches.size() +  "approximate matches");
        
        BinaryUtils.VInt vint = new BinaryUtils.VInt();
        
        for (int i = 0; i < rtfl.getMatches().size(); i++) {
            RadixTreeFuzzyLookup.ApproximateMatch match = rtfl.getMatches().get(i);
//          System.out.println("Value " + match.byteArrayValue.length);
            int pos = 0;
            BinaryUtils.readVInt(match.byteArrayValue, 0, vint);
//          System.out.println("Found " + vint.value + " offsets");
            pos = vint.codeSize;
            int nbVals = (int)vint.value;
//          System.out.println("*** Approx match:" + rtfl.matches.get(i).key + " d=" + rtfl.matches.get(i).distance + " nvals=" + nbVals);

            for (int j = 0; j < nbVals; j++) {
                BinaryUtils.readVInt(match.byteArrayValue, pos, vint);
                pos += vint.codeSize;
                long v = vint.value;
                BinaryUtils.readVInt(match.byteArrayValue, pos, vint);
                pos += vint.codeSize;
                long s = vint.value;

                offsets.add(v);
                distances.add(match.distance);
                scores.add(s);
                correctedPrefixes.add(match.key);
            }
        }
        if (di != null) {
            di.listsDecodingTime = (System.nanoTime() - t1)/1000;
            di.decodedMatches = offsets.size();
        }
	}

	public List<AutocompleterEntry> getOffsets(String query, int maxDistance, DebugInfo di) {
		long t0 = 0, t1 = 0;
		
		List<AutocompleterEntry> ret = new ArrayList<Autocompleter.AutocompleterEntry>(2000);
		RadixTreeFuzzyLookup rtfl = new RadixTreeFuzzyLookup(rt);
		
		if (di != null) t0 = System.nanoTime();
		rtfl.match(query, maxDistance);
		if (di != null) {
			t1 = System.nanoTime();
			di.value = query;
			di.radixTreeMatchTime = (t1 - t0)/1000;
			di.radixTreeMatches = rtfl.getMatches().size();
		}
		
//		System.out.println("QUERY "  + query + " has " + rtfl.matches.size() +  "approximate matches");
		
		BinaryUtils.VInt vint = new BinaryUtils.VInt();
		
		for (int i = 0; i < rtfl.getMatches().size(); i++) {
			RadixTreeFuzzyLookup.ApproximateMatch match = rtfl.getMatches().get(i);
			
			// In autocompletion mode, we don't care about *longer* terms than us, because they'll always have a worse distance,
			// while carrying the same values than us (and actually, carrying less)
			// BUT only if we are a prefix of them (ie, no autocorrection on our body)
			if (match.key.length() > query.length() && rtfl.getMatches().size() > 1
			    && match.key.startsWith(query)) continue;
			
//			System.out.println("Value " + match.byteArrayValue.length);
			int pos = 0;
			BinaryUtils.readVInt(match.byteArrayValue, 0, vint);
//			System.out.println("Found " + vint.value + " offsets");
			pos = vint.codeSize;
			int nbVals = (int)vint.value;
//			System.out.println("*** Approx match:" + rtfl.getMatches().get(i).key + " d=" + rtfl.getMatches().get(i).distance + " nvals=" + nbVals);

			for (int j = 0; j < nbVals; j++) {
				BinaryUtils.readVInt(match.byteArrayValue, pos, vint);
				pos += vint.codeSize;
				long v = vint.value;
//				System.out.println("v=" + vint.value + " cs=" + vint.codeSize);
				BinaryUtils.readVInt(match.byteArrayValue, pos, vint);
				pos += vint.codeSize;
				long s = vint.value;
//				System.out.println("s=" + vint.value + " cs=" + vint.codeSize);

				AutocompleterEntry ae = new AutocompleterEntry(v, match.distance, s);
				ae.correctedPrefix = match.key;
//				System.out.println("**" + v + " " + s + " " + match.key);
				ret.add(ae);
			}
		}
		if (di != null) {
			di.listsDecodingTime = (System.nanoTime() - t1)/1000;
			di.decodedMatches = ret.size();
		}
		return ret;

		//        }
	}

	
	/** Get the data associated to an autocomplete entry */
	public String getData(long offset) throws IOException {
		BinaryUtils.VInt ret = new BinaryUtils.VInt();
		BinaryUtils.readVInt(dataBuffer, offset, ret);
		byte[] dataBuf = new byte[(int)ret.value];
		dataBuffer.position((int)(offset + ret.codeSize));
		dataBuffer.get(dataBuf, 0, (int)ret.value);
		return new String(dataBuf, "utf8");
	}
	
	   /** Get the data associated to an autocomplete entry */
    public byte[] getByteData(long offset) throws IOException {
        BinaryUtils.VInt ret = new BinaryUtils.VInt();
        BinaryUtils.readVInt(dataBuffer, offset, ret);
        byte[] dataBuf = new byte[(int)ret.value];
        dataBuffer.position((int)(offset + ret.codeSize));
        dataBuffer.get(dataBuf, 0, (int)ret.value);
        return dataBuf;
    }
}