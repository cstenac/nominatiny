package fr.openstreetmap.search;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import fr.openstreetmap.search.autocomplete.AutocompleteMultiWordSearcher;
import fr.openstreetmap.search.autocomplete.MultiWordSearcher;
import fr.openstreetmap.search.polygons.PolygonsServlet;
import fr.openstreetmap.search.simple.AdminDesc;

public class MainPolygons {
	public static void main(String[] args) throws Exception {
		BasicConfigurator.configure();
		File outDir = new File(args[0]);
		File adminList = new File(args[1]);
		File adminRelationsFile = new File(args[2]);

		Map<Long, AdminDesc> adminRelations = AdminDesc.parseCityList(adminList);
		AdminDesc.parseCityParents(adminRelationsFile, adminRelations);

		logger.info("Loading radix tree");
		byte[] radixBuffer = FileUtils.readFileToByteArray(new File(outDir, "radix")); 

		logger.info("Loading data");
		FileChannel dataChannel = new RandomAccessFile(new File(outDir, "data"), "r").getChannel();
		MappedByteBuffer dataBuffer = dataChannel.map(MapMode.READ_ONLY, 0, dataChannel.size());
		dataBuffer.load();

		AutocompleteMultiWordSearcher shard = new AutocompleteMultiWordSearcher();
		shard.radixBuffer = radixBuffer;
		shard.dataBuffer = dataBuffer;

		/* Preload a bit */
		logger.info("Preloading shard");
		shard.autocomplete(new String[]{"jol", "cur"}, 1, null);

		logger.info("Loading done, starting server");

		Server server = new Server(8081);

		ServletContextHandler sch = new ServletContextHandler();

		sch.setContextPath("/");
		server.setHandler(sch);

		PolygonsServlet servlet = new PolygonsServlet();
		servlet.shard = shard;

		sch.addServlet(new ServletHolder(servlet), "/complete");

		server.start();
		server.join();
	}

	private static Logger logger  = Logger.getLogger("search");
}
