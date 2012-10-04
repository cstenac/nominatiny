package fr.openstreetmap.search.autocomplete;

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
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.json.JSONException;
import org.json.JSONWriter;

import fr.openstreetmap.search.autocomplete.Autocompleter.AutocompleterEntry;
import fr.openstreetmap.search.autocomplete.MultipleWordsAutocompleter.MultiWordAutocompleterEntry;
import fr.openstreetmap.search.autocomplete.OSMAutocompleteUtils.MatchData;

public class AutocompletionServlet extends HttpServlet{
	private static final long serialVersionUID = 1L;
	MultipleWordsAutocompleter mwa;
	Set<String> stopWords = new HashSet<String>();

	public void initSW(String swFile) throws Exception {
		BufferedReader br = new BufferedReader(new FileReader(swFile));
		while (true) {
			String line = br.readLine();
			if (line == null) break;
			stopWords.add(line.replace("\n", ""));
		}
	}
	
	static class FinalResult {
	    public FinalResult(MultiWordAutocompleterEntry ae) {
	        this.ae = ae;
	    }
	    MultiWordAutocompleterEntry ae;
	    byte[] encodedData;
	    MatchData decodedData;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		long startup = System.nanoTime();
		String query = req.getParameter("q");
		if (query == null) {
			query = req.getParameter("term");
		}
		System.out.println("QUERY: " + query);

		List<String> stopped = new ArrayList<String>();
		List<String> tokensList = new ArrayList<String>();
		CreateFromDBOutput.tokenize(query, tokensList);

		ListIterator<String> it = tokensList.listIterator();
		while (it.hasNext()) {
			String token = it.next();
			if (stopWords.contains(token)) {
				stopped.add(token);
				it.remove();
			}
			if (token.length() < 3) {
				stopped.add(token);
				it.remove();
			}
			if (CreateFromDBOutput.definitelyStopWords.contains(token)) {
				stopped.add(token);
				it.remove();
			}
		}

		synchronized (this) {
			MultipleWordsAutocompleter.DebugInfo di = new MultipleWordsAutocompleter.DebugInfo();

			long beforeQuery= System.nanoTime();
			List<MultiWordAutocompleterEntry> results = mwa.autocomplete(tokensList, 1, di);
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
			
			/* If there are some stopped words, but we can find them in the summary, then strongly boost the score.
			 * This way, stuff that did not really match will get downvoted.
			 */
			List<FinalResult> fresults = new ArrayList<AutocompletionServlet.FinalResult>();
            OSMAutocompleteUtils utils = new OSMAutocompleteUtils();

			int nbRes = 0;
			for (MultiWordAutocompleterEntry ae : results) {
			    FinalResult fr = new FinalResult(ae);
			    fr.encodedData = mwa.completer.getByteData(ae.offset);
			    
			    fr.decodedData = utils.decodeData(fr.encodedData);
			    
			    for (String stop : stopped) {
			        if (fr.decodedData.name.toLowerCase().contains(stop)) {
			            System.out.println("  Boosting " + fr.decodedData);
			            fr.ae.score += 1000;
			        }
			    }
			    fresults.add(fr);
			    
			    // Don't decode too much
			    if (nbRes++ > 100) break;
			}
			Collections.sort(fresults, new Comparator<FinalResult>() {

                @Override
                public int compare(FinalResult o1, FinalResult o2) {
                    return o1.ae.compareTo(o2.ae);
                }
            });
			
			long afterSort= System.nanoTime();

			resp.setContentType("application/json");

			BufferedWriter bwr = new BufferedWriter(resp.getWriter());
			JSONWriter wr = new JSONWriter(bwr);
			try {
				wr.object().key("matches").array();

				nbRes = 0;
				for (FinalResult fr : fresults) {
//					System.out.println("   RES  " + fr.decodedData + " d=" + fr.ae.distance + " s=" + fr.ae.score + " p=" + fr.ae.correctedTokens);
					//					System.out.println(" " + ae.offset + " - " + ae.score + " " + ae.distance + " correct prefix=" + ae.correctedPrefix);
					//        	System.out.println("   " + a.getData(ae.offset));
					wr.object();
					wr.key("name").value(fr.decodedData.name);
					wr.key("lat").value(fr.decodedData.lat);
					wr.key("lon").value(fr.decodedData.lon);
					wr.key("type").value(fr.decodedData.type);
					wr.key("cities").value(StringUtils.join(fr.decodedData.cityNames, ", "));
					wr.key("distance").value(fr.ae.distance);
					wr.key("score").value(fr.ae.score);
					wr.key("prefix").value(StringUtils.join(fr.ae.correctedTokens, " "));
					wr.endObject();
					if (nbRes++ > 25) break;
				}
				wr.endArray();

				long afterWriteData = System.nanoTime();

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

				long atEnd = System.nanoTime();

				wr.key("preprocessTime").value((beforeQuery - startup)/1000);
				wr.key("processTime").value((afterQuery - beforeQuery)/1000);
                wr.key("totalMatchTime").value(di.totalTokensMatchTime);
                wr.key("filterTime").value(di.filterTime);
				wr.key("intersectionTime").value(di.intersectionTime);
				wr.key("sortTime").value((afterSort - afterQuery)/1000);
				wr.key("resultsWriteTime").value((afterWriteData- afterSort)/1000);
				wr.key("totalServerTime").value((atEnd - startup)/1000);
				wr.endObject();
				wr.endObject();

                bwr.flush();
                
				System.out.print("QUERY: " + query + " DONE: totalMatches=" + results.size());
				System.out.print(" totalTime=" + (atEnd-startup)/1000);
				System.out.print(" preprocTime=" + (beforeQuery - startup)/1000);
				System.out.print(" processTime=" + (afterQuery - beforeQuery)/1000);
                System.out.print(" (match=" + di.totalTokensMatchTime + " filter=" + di.filterTime + " intersect=" + di.intersectionTime + ")");
				System.out.print(" sortTime=" + (afterSort - afterQuery)/1000);
				System.out.print(" writeTime=" + (afterWriteData- afterSort)/1000);

			} catch (JSONException e) {
				throw new IOException(e);
			}
		}
	}

}
