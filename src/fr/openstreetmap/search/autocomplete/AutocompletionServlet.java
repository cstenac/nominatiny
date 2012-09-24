package fr.openstreetmap.search.autocomplete;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		long startup = System.nanoTime();
		String query = req.getParameter("q");
		if (query == null) {
			query = req.getParameter("term");
		}

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

			List<Autocompleter.AutocompleterEntry> results = null;
			if (tokensList.size() > 0) {
				results = mwa.autocomplete(tokensList, 1, di);
			} else {
				results = new ArrayList<Autocompleter.AutocompleterEntry>();
			}

			long afterQuery= System.nanoTime();

			Collections.sort(results);

			long afterSort= System.nanoTime();

			resp.setContentType("application/json");

			System.out.println("QUERY: " + query);

			BufferedWriter bwr = new BufferedWriter(resp.getWriter());
			JSONWriter wr = new JSONWriter(bwr);
			try {
				wr.object().key("matches").array();

				int nbRes = 0;
				for (Autocompleter.AutocompleterEntry ae : results) {
					String name = mwa.completer.getData(ae.offset);
					System.out.println("   RES  " + name + " d=" + ae.distance + " s=" + ae.score + " p=" + ae.correctedPrefix);
					//					System.out.println(" " + ae.offset + " - " + ae.score + " " + ae.distance + " correct prefix=" + ae.correctedPrefix);
					//        	System.out.println("   " + a.getData(ae.offset));
					wr.object().key("label").value(name).key("distance").value(ae.distance);
					wr.key("score").value(ae.score).key("prefix").value(ae.correctedPrefix).endObject();
					if (nbRes++ > 25) break;
				}
				wr.endArray();

				long afterWriteData = System.nanoTime();

				wr.key("debug").object();
				wr.key("stopWords").value(StringUtils.join(stopped, ", "));
				wr.key("tokens").array();
				for (int i = 0; i < tokensList.size(); i++) {
					wr.object().key("token").value(tokensList.get(i));
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
				wr.key("intersectionTime").value(di.intersectionTime);
				wr.key("sortTime").value((afterSort - afterQuery)/1000);
				wr.key("resultsWriteTime").value((afterWriteData- afterSort)/1000);
				wr.key("totalServerTime").value((atEnd - startup)/1000);
				wr.endObject();

				wr.endObject();

				System.out.print("QUERY: " + query + "DONE: ppTime= " + (beforeQuery - startup)/1000);
				System.out.print(" pTime= " + (afterQuery - beforeQuery)/1000);
				System.out.print(" sTime= " + (afterSort - afterQuery)/1000);
				System.out.print(" rWTime= " + (afterWriteData- afterSort)/1000);
				System.out.println("tSTime= " + (atEnd - startup)/1000);

				bwr.flush();

			} catch (JSONException e) {
				throw new IOException(e);
			}
		}
	}

}
