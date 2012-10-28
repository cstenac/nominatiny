package fr.openstreetmap.search.polygons;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONWriter;

import fr.openstreetmap.search.autocomplete.MultiWordSearcher;
import fr.openstreetmap.search.autocomplete.MultiWordSearcher.DebugInfo;
import fr.openstreetmap.search.autocomplete.MultiWordSearcher.MultiWordAutocompleterEntry;
import fr.openstreetmap.search.simple.OSMAutocompleteUtils.MatchData;

public class PolygonsServlet extends HttpServlet{
	private static final long serialVersionUID = 1L;
	public MultiWordSearcher shard;

	static class FinalResult {
		public FinalResult(MultiWordAutocompleterEntry ae) {
			this.ae = ae;
		}
		MultiWordAutocompleterEntry ae;
		byte[] encodedData;
		MatchData decodedData;
	}

	int getIntParam(HttpServletRequest req, String name, int defaultValue) {
		String v = req.getParameter(name);
		if (v == null) return defaultValue;
		return Integer.parseInt(v);
	}

	static class ShardLookup {
		MultiWordSearcher shard;
		List<MultiWordAutocompleterEntry> entries;
		MultiWordSearcher.DebugInfo di;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		int returnLimit = getIntParam(req, "limit", 25);
		int boostLimit = getIntParam(req, "boostLimit", 200);
		int debug = getIntParam(req, "debug", 1);

		long startup = System.nanoTime();
		String query = req.getParameter("q");

		System.out.println("QUERY: " + query);

		List<String> stopped = new ArrayList<String>();
		final List<String> tokensList = new ArrayList<String>();
		Builder.tokenize(query, tokensList);

		ListIterator<String> it = tokensList.listIterator();
		while (it.hasNext()) {
			String token = it.next();
//			if (stopWords.contains(token)) {
//				stopped.add(token);
//				it.remove();
//			}
			if (token.length() < 3) {
				stopped.add(token);
				it.remove();
			}
//			if (Builder.definitelyStopWords.contains(token)) {
//				stopped.add(token);
//				it.remove();
//			}
		}
		try {

			long beforeQuery= System.nanoTime();
			DebugInfo di = new DebugInfo();
			List<MultiWordAutocompleterEntry> results = shard.autocompleteLong(tokensList, 1, di);
			long afterQuery= System.nanoTime();

			/* First, sort by distance then score */
			Collections.sort(results, new Comparator<MultiWordAutocompleterEntry>() {
				@Override
				public int compare(MultiWordAutocompleterEntry o1, MultiWordAutocompleterEntry o2) {
					if (o1.distance < o2.distance) return -1;
					if (o1.distance > o2.distance) return 1;
					if (o1.score > o2.score) return -1;
					if (o1.score < o2.score) return 1;
					return 0;
				}
			});

			long afterSort1 = System.nanoTime();

			/* If there are some stopped words, but we can find them in the summary, then strongly boost the score.
			 * This way, stuff that did not really match will get downvoted.
			 */
			//            List<MultiWordAutocompleterEntry> fresults = new ArrayList<MultiWordAutocompleterEntry>();
			//
			//            int nbRes = 0;
			//            for (MultiWordAutocompleterEntry ae : results) {
			//                for (String stop : stopped) {
			//                    String decodedName = fr.decodedData.name.toLowerCase();
			//                    StringTokenizer st = Builder.getTokenizer(decodedName);
			//                    while (st.hasMoreTokens()) {
			//                        String nt = st.nextToken();
			//                        if (nt.equals(stop)) {
			//                            // The stop word exists as a separate token, best boost
			//                            fr.ae.score += 10000;
			//                        } else if (nt.startsWith(stop)){
			//                            // The stop word is the beginning of a token, also boost
			//                            fr.ae.score += 1000;
			//                        }
			//
			//                    }
			//                }
			//                fresults.add(fr);
			//
			//                // Don't decode too much
			//                if (++nbRes >= boostLimit) break;
			//            }

			long afterBoost = System.nanoTime();

			//            Collections.sort(fresults, new Comparator<FinalResult>() {
			//                @Override
			//                public int compare(FinalResult o1, FinalResult o2) {
			//                    int cmp = o1.ae.compareTo(o2.ae);
			//                    if (cmp != 0) return cmp;
			//                    return o1.decodedData.name.length() - o2.decodedData.name.length();
			//                }
			//            });

			long afterSort= System.nanoTime();

			resp.setCharacterEncoding("utf8");
			resp.setContentType("application/json");
			resp.addHeader("Access-Control-Allow-Origin", "*");

			BufferedWriter bwr = new BufferedWriter(resp.getWriter());
			JSONWriter wr = new JSONWriter(bwr);

			wr.object().key("matches").array();

			int nbRes = 0;
			for (MultiWordAutocompleterEntry ae : results) {
				wr.object();

				String display = new String(shard.getByteData(ae.offset));
				System.out.println(display);

				String[] chunks = StringUtils.splitPreserveAllTokens(display, "|");

				wr.key("name").value(chunks[0]);
				wr.key("wkt").value(chunks[1]);
				wr.key("osmId").value(Long.parseLong(chunks[2]));

				wr.key("distance").value(ae.distance);
				wr.key("score").value(ae.score);
				wr.key("prefix").value(StringUtils.join(ae.correctedTokens, " "));
				wr.endObject();
				if (++nbRes >= returnLimit) break;
			}
			wr.endArray();

			long atEnd = System.nanoTime();

			long totalTokensMatchTime = 0, totalFilterTime = 0, totalIntersectionTime = 0;

			if (debug > 0) {
				wr.key("debug").object();
				wr.key("stopWords").value(StringUtils.join(stopped, ", "));
				wr.key("tokens").array();
				for (int i = 0; i < di.tokensDebugInfo.size(); i++) {
					wr.object().key("token").value(di.tokensDebugInfo.get(i).value);
					wr.key("rtMatches").value(di.tokensDebugInfo.get(i).radixTreeMatches);
					wr.key("decodedMatches").value(di.tokensDebugInfo.get(i).decodedMatches);
					wr.key("rtMatchTime").value(di.tokensDebugInfo.get(i).radixTreeMatchTime);
					wr.key("decodingTime").value(di.tokensDebugInfo.get(i).listsDecodingTime);
					wr.endObject();
				}
				wr.endArray(); // tokens
				wr.key("finalMatches").value(results.size());

				wr.key("preprocessTime").value((beforeQuery - startup)/1000);
				wr.key("processTime").value((afterQuery - beforeQuery)/1000);
				wr.key("totalMatchTime").value(totalTokensMatchTime);
				wr.key("filterTime").value(totalFilterTime);
				wr.key("intersectionTime").value(totalIntersectionTime);
				wr.key("sortTime").value((afterSort - afterQuery)/1000);
				wr.key("resultsWriteTime").value((atEnd- afterSort)/1000);
				wr.key("totalServerTime").value((atEnd - startup)/1000);
				wr.endObject();
			}
			wr.endObject();

			bwr.flush();


			System.out.print("QUERY: " + query + " DONE: totalMatches=" + results.size());
			System.out.print(" totalTime=" + (atEnd-startup)/1000);
			System.out.print(" preprocTime=" + (beforeQuery - startup)/1000);
			System.out.print(" processTime=" + (afterQuery - beforeQuery)/1000);
			System.out.print(" (match=" + totalTokensMatchTime + " filter=" + totalFilterTime + " intersect=" + totalIntersectionTime + ")");
			System.out.print(" sortTime=" + (afterSort - afterQuery)/1000 );
			System.out.print(" (s1=" + (afterSort1 - afterQuery)/1000 + " b=" + (afterBoost-afterSort1)/1000 + " s2=" + (afterSort-afterBoost)/1000 + ")");

			System.out.println(" writeTime=" + (atEnd- afterSort)/1000);

		} catch (Exception e) {
			logger.error("Query failed", e);
			throw new IOException(e);
		}

	}

	private static Logger logger = Logger.getLogger("search.servlet");
}
