package fr.openstreetmap.search.autocomplete;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** This class is not thread safe */
public class MultipleWordsAutocompleter {
	public Autocompleter completer;
	
	public static class DebugInfo {
		List<Autocompleter.DebugInfo> tokensDebugInfo = new ArrayList<Autocompleter.DebugInfo>();
		long intersectionTime;
	}
	public List<Autocompleter.AutocompleterEntry> autocomplete(String[] tokens, int maxDistance, DebugInfo di) {
		List<String> list = new ArrayList<String>();
		for (String t : tokens) list.add(t);
		return autocomplete(list, maxDistance, di);
	}
	
	public List<Autocompleter.AutocompleterEntry> autocomplete(List<String> tokens, int maxDistance, DebugInfo di) {
		long t0 = 0;
		
		Autocompleter.DebugInfo token0DI = null;
		if (di != null) {
			token0DI = new Autocompleter.DebugInfo();
		}

		/* Very poor's man inverted list intersection !!! */
		Map<Long, Autocompleter.AutocompleterEntry> map = new HashMap<Long, Autocompleter.AutocompleterEntry>();
		for (Autocompleter.AutocompleterEntry e :  completer.getOffsets(tokens.get(0), maxDistance, token0DI)) {
			map.put(e.offset, e);
		}
		System.out.println("After token 0, have " + map.size() + " matches");
		if (di != null) {
			di.tokensDebugInfo.add(token0DI);
		}

		for (int i = 1; i < tokens.size() ; i++) {
			Map<Long, Autocompleter.AutocompleterEntry> prevMap = map;
			map = new HashMap<Long, Autocompleter.AutocompleterEntry>();
			
			Autocompleter.DebugInfo tokenDI = null;
			if (di != null) {
				tokenDI = new Autocompleter.DebugInfo();
			}
			List<Autocompleter.AutocompleterEntry> thisList = completer.getOffsets(tokens.get(i), maxDistance, tokenDI);
			if (di != null) {
				di.tokensDebugInfo.add(tokenDI);
				t0 = System.nanoTime();
			}
			for (Autocompleter.AutocompleterEntry e : thisList) {
			    Autocompleter.AutocompleterEntry prevE = prevMap.get(e.offset);
			    if (prevE == null) continue;
			    e.distance = Math.max(prevE.distance, e.distance);
			    e.score = Math.min(prevE.score, e.score);
			    map.put(e.offset, e);
			}
			if (di != null) {
				di.intersectionTime += (System.nanoTime() - t0)/1000;
			}
			System.out.println("After token " + i + ", have " + map.size() + " matches");

		}

		List<Autocompleter.AutocompleterEntry> ret = new ArrayList<Autocompleter.AutocompleterEntry>();
		for (Autocompleter.AutocompleterEntry e : map.values()) {
			ret.add(e);
		}
		return ret;
	}

}
