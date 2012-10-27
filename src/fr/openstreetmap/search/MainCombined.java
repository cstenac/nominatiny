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

import fr.openstreetmap.search.autocomplete.MultipleWordsAutocompleter;
import fr.openstreetmap.search.polygons.PolygonsServlet;
import fr.openstreetmap.search.simple.AdminDesc;
import fr.openstreetmap.search.simple.AutocompletionServlet;

public class MainCombined {
	public static void main(String[] args) throws Exception {
		BasicConfigurator.configure();
		File shardsDir = new File(args[0]);
		File polygonsDir = new File(args[1]);
		File adminList = new File(args[2]);
		File adminRelationsFile = new File(args[3]);

		List<MultipleWordsAutocompleter> shards = new ArrayList<MultipleWordsAutocompleter>();

		ExecutorService topExecutor = Executors.newFixedThreadPool(8);
		ExecutorService bottomExecutor = Executors.newFixedThreadPool(8);

		Map<Long, AdminDesc> adminRelations = AdminDesc.parseCityList(adminList);
		AdminDesc.parseCityParents(adminRelationsFile, adminRelations);

		for (File shardDir : shardsDir.listFiles()) {
			if (!shardDir.isDirectory()) continue;

			logger.info("Loading shard " + shardDir.getName());

			logger.info("Loading radix tree");
			byte[] radixBuffer = FileUtils.readFileToByteArray(new File(shardDir, "radix")); 

			logger.info("Loading data");
			FileChannel dataChannel = new RandomAccessFile(new File(shardDir, "data"), "r").getChannel();
			MappedByteBuffer dataBuffer = dataChannel.map(MapMode.READ_ONLY, 0, dataChannel.size());
			dataBuffer.load();

			MultipleWordsAutocompleter shard = new MultipleWordsAutocompleter(bottomExecutor);
			shard.radixBuffer = radixBuffer;
			shard.dataBuffer = dataBuffer;

			/* Preload a bit */
			logger.info("Preloading shard");
			shard.autocomplete(new String[]{"jol", "cur"}, 1, null);

			logger.info("Computing filters");
			shard.computeAndAddFilter("rue");
			shard.computeAndAddFilter("des");
			shard.computeAndAddFilter("aux");

			shard.computeAndAddFilterRecursive("france");
			shard.computeAndAddFilterRecursive("deutschland");
			shard.computeAndAddFilterRecursive("bayern");

			shard.dumpFiltersInfo();

			shard.shardName = shardDir.getName();
			shards.add(shard);
		}

		MultipleWordsAutocompleter polygonsShard = null;
		{
			logger.info("Loading polygons");
			logger.info("Loading radix tree");
			byte[] radixBuffer = FileUtils.readFileToByteArray(new File(polygonsDir, "radix")); 

			logger.info("Loading data");
			FileChannel dataChannel = new RandomAccessFile(new File(polygonsDir, "data"), "r").getChannel();
			MappedByteBuffer dataBuffer = dataChannel.map(MapMode.READ_ONLY, 0, dataChannel.size());
			dataBuffer.load();

			polygonsShard= new MultipleWordsAutocompleter();
			polygonsShard.radixBuffer = radixBuffer;
			polygonsShard.dataBuffer = dataBuffer;

			/* Preload a bit */
			logger.info("Preloading shard");
			polygonsShard.autocomplete(new String[]{"jol", "cur"}, 1, null);
		}

		logger.info("Loading done, starting server");

		Server server = new Server(8080);

		ServletContextHandler sch = new ServletContextHandler();

		sch.setContextPath("/");
		server.setHandler(sch);

		{
			AutocompletionServlet servlet = new AutocompletionServlet(topExecutor);
			servlet.adminRelations = adminRelations;
			servlet.shards = shards;
			servlet.initSW(("/dev/null"));

			sch.addServlet(new ServletHolder(servlet), "/complete");
		}
		{
			PolygonsServlet servlet = new PolygonsServlet();
			servlet.shard = polygonsShard;
			sch.addServlet(new ServletHolder(servlet), "/polygons");
		}

		server.start();
		server.join();
	}

	private static Logger logger  = Logger.getLogger("search");
}
