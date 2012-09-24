package fr.openstreetmap.search.autocomplete;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

public class OSMInputParser {


	class OSMContentHandler implements ContentHandler {
		String curId;
		List<String> tagKeys = new ArrayList<String>();
		List<String> tagValues = new ArrayList<String>();
		List<String> wayNodes = new ArrayList<String>();
		boolean node;
		boolean way;
		boolean relation;
		String curLat;
		String curLong;

		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
		}

		@Override
		public void endDocument() throws SAXException {
			//	            System.out.println("END node=" + node + " w=" + way);
			if (node) {
				try {
					onNode(curId, curLat, curLong, tagKeys, tagValues);
				} catch (Exception e) {
					throw new SAXException("Map error", e);
				}
			} else if (way) {
				try {
					onWay(curId, wayNodes, tagKeys, tagValues);
				} catch (Exception e) {
					throw new SAXException("Map error", e);
				}
			} else if (relation) {
				try {
					onRelation(curId, tagKeys, tagValues);
				} catch (Exception e) {
					throw new SAXException("Map error", e);
				}
			}
		}

		@Override
		public void startDocument() throws SAXException {
			node = false; way = false; relation = false;
			curId = null; curLat = null; curLong = null;
			tagKeys.clear(); tagValues.clear(); wayNodes.clear();
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
			if (qName.equals("node")) {
				node = true;
				curLong = atts.getValue("lon");
				curLat = atts.getValue("lat");
				curId = atts.getValue("id");
			} else if (qName.equals("way")) {
				way = true;
				curId = atts.getValue("id");
			} else if (qName.equals("relation")) {
				relation = true;
				curId = atts.getValue("id");
			} else if (qName.equals("nd")) {
				wayNodes.add(atts.getValue("ref"));
			} else if (qName.equals("tag")) {
				tagKeys.add(atts.getValue("k"));
				tagValues.add(atts.getValue("v"));
			}
		}

		@Override
		public void startPrefixMapping(String prefix, String uri) throws SAXException {
		}
		@Override
		public void skippedEntity(String name) throws SAXException {
		}
		@Override
		public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
		}

		@Override
		public void processingInstruction(String target, String data) throws SAXException {
		}

		@Override
		public void setDocumentLocator(Locator locator) {
		}

		@Override
		public void endElement(String uri, String localName, String qName) throws SAXException {
		}

		@Override
		public void endPrefixMapping(String prefix) throws SAXException {
		}
	}


	XMLReader saxReader;

	public void setup() throws Exception {
		//	            saxReader = SAXParserFactory.newInstance("com.bluecast.xml.JAXPSAXParserFactory", Thread.currentThread().getContextClassLoader()).newSAXParser().getXMLReader();
		saxReader = XMLReaderFactory.createXMLReader();
		//saxReader = createXMLReader("org.apache.xerces.parsers.SAXParser");
		saxReader.setContentHandler(new OSMContentHandler());

	}


	public void process(InputStream is) throws Exception {
		InputSource source = new InputSource(is);
		saxReader.parse(source);
	}
	protected void onNode(String id, String lat, String nodeLong, List<String> tagKeys, List<String> tagValues) throws Exception {}
	protected void onWay(String id,  List<String> nodeIds, List<String> tagKeys, List<String> tagValues) throws Exception {}
	protected void onRelation(String id, List<String> tagKeys, List<String> tagValues) throws Exception {}


}
