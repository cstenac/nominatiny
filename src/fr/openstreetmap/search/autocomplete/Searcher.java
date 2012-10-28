package fr.openstreetmap.search.autocomplete;
import java.io.IOException;
import java.util.List;

/**
 * Gets the inverted list of a single token
 */
public interface Searcher {
	public static class DebugInfo {
	    public String value;
		public long radixTreeMatchTime;
		public long radixTreeMatches;
		public long listsDecodingTime;
		public long decodedMatches;
	}

	public static class Entry implements Comparable<Entry>{
		Entry(long offset, int distance, long score) {
			this.offset = offset;
			this.distance = distance;
			this.score = score;
		}
		public String correctedPrefix;
		public int distance;
		public long offset;
		public long score;
		@Override
		public int compareTo(Entry o) {
			if (distance < o.distance) return -1;
			if (distance > o.distance) return 1;
			if (score > o.score) return -1;
			if (score < o.score) return 1;
			return 0;
		}
	}

	public List<Entry> getOffsets(String query, int maxDistance, DebugInfo di);
	
	/** Get the data associated to an autocomplete entry */
	public String getData(long offset) throws IOException;

	/** Get the data associated to an autocomplete entry */
    public byte[] getByteData(long offset) throws IOException;
}