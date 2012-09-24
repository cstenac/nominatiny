package fr.openstreetmap.search.text;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Normalizes (ie, removes accents ...) strings
 */
public class StringNormalizer {
	static Pattern pattern;
	static {
		pattern =Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
	}

	public static String normalize(String in) {
		return pattern.matcher(Normalizer.normalize(in, Normalizer.Form.NFD)).replaceAll("");
	}
}
