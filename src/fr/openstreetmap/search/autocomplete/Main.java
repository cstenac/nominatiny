package fr.openstreetmap.search.autocomplete;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public class Main {
	public static void main(String[] args) throws Exception {
		String dataFile = args[0];
		String radixFile = args[1];
		String swFile = args[2];

		FileChannel radixChannel = new RandomAccessFile(new File(radixFile), "r").getChannel();
		MappedByteBuffer radixDirectBuffer = radixChannel.map(MapMode.READ_ONLY, 0, radixChannel.size());
		radixDirectBuffer.load();
		byte[] radixData = new byte[(int)radixChannel.size()];
		radixDirectBuffer.get(radixData, 0, (int)radixChannel.size());
		ByteBuffer radixBuffer = ByteBuffer.wrap(radixData);

		//	        ByteBuffer radixBuffer = radixDirectBuffer;

		FileChannel dataChannel = new RandomAccessFile(new File(dataFile), "r").getChannel();
		MappedByteBuffer dataBuffer = dataChannel.map(MapMode.READ_ONLY, 0, dataChannel.size());

		Autocompleter a = new Autocompleter(radixBuffer, dataBuffer);

		MultipleWordsAutocompleter mwa = new MultipleWordsAutocompleter();
		mwa.completer = a;
		
		/* Preload a bit */
		mwa.autocomplete(new String[]{"jol", "cur"}, 1, null);

		Server server = new Server(8080);

		ServletContextHandler sch = new ServletContextHandler();

		sch.setContextPath("/");
		server.setHandler(sch);
		
		AutocompletionServlet servlet = new AutocompletionServlet();
		servlet.mwa = mwa;
		servlet.initSW(swFile);
		
		sch.addServlet(new ServletHolder(servlet), "/complete");
		
		server.start();
		server.join();
	}
}
