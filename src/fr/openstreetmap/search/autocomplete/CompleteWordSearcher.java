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
public class CompleteWordSearcher implements Searcher {
	ByteBuffer dataBuffer;
	RadixTree rt = new RadixTree();

	public CompleteWordSearcher(byte[] radixBuffer, ByteBuffer dataBuffer) {
		this.dataBuffer = dataBuffer;
		rt.buffer = radixBuffer;
		rt.totalSize = radixBuffer.length;
		rt.byteArrayMode = true;
	}

	public List<Entry> getOffsets(String query, int maxDistance, DebugInfo di) {
		long t0 = 0, t1 = 0;
		List<Entry> ret = new ArrayList<Autocompleter.Entry>(2000);

		if (di != null) t0 = System.nanoTime();

		if (false) {
			//		if (maxDistance == 0) {
			//			rt.getEntry(query);

		} else {

			RadixTreeFuzzyLookup rtfl = new RadixTreeFuzzyLookup(rt);
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

				long prevVal = 0;
				for (int j = 0; j < nbVals; j++) {
					BinaryUtils.readVInt(match.byteArrayValue, pos, vint);
					pos += vint.codeSize;
					long v = vint.value + prevVal;
					prevVal = v;
					//				System.out.println("v=" + vint.value + " cs=" + vint.codeSize);
					BinaryUtils.readVInt(match.byteArrayValue, pos, vint);
					pos += vint.codeSize;
					long s = vint.value;
					//				System.out.println("s=" + vint.value + " cs=" + vint.codeSize);

					Entry ae = new Entry(v, match.distance, s);
					ae.correctedPrefix = match.key;
					//				System.out.println("**" + v + " " + s + " " + match.key);
					ret.add(ae);
				}
			}
			if (di != null) {
				di.listsDecodingTime = (System.nanoTime() - t1)/1000;
				di.decodedMatches = ret.size();
			}
		}
		return ret;
	}

	/** Get the data associated to an autocomplete entry */
	public String getData(long offset) throws IOException {
		return new String(getByteData(offset), "utf8");
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