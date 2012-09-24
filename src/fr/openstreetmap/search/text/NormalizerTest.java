package fr.openstreetmap.search.text;

import java.text.Normalizer;
import java.util.regex.Pattern;

import org.junit.Test;

public class NormalizerTest {
	@Test
	public void normalize() {
		String a = "héhé, ohèh, ça suxêreait";
		
		System.out.println();
		
		 Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
		 String normalized =  pattern.matcher(Normalizer.normalize(a, Normalizer.Form.NFD)).replaceAll("");
		 
		 System.out.println(normalized);

	}
}
