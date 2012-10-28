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
import fr.openstreetmap.search.autocomplete.CompleteWordMultiWordSearcher;
import fr.openstreetmap.search.autocomplete.MultiWordSearcher;
import fr.openstreetmap.search.polygons.PolygonsServlet;
import fr.openstreetmap.search.simple.AdminDesc;
import fr.openstreetmap.search.simple.AutocompletionServlet;

public class MainCombined {
	static String[] frenchStopWords = new String[]{
		"le", "la", "les", "et", "ou", "du", "de", "des"
	};


	static AutocompleteMultiWordSearcher loadPrefixShard(File shardDir, ExecutorService es) throws Exception {
		logger.info("Loading shard " + shardDir.getName());

		logger.info("Loading radix tree");
		byte[] radixBuffer = FileUtils.readFileToByteArray(new File(shardDir, "radix")); 

		logger.info("Loading data");
		FileChannel dataChannel = new RandomAccessFile(new File(shardDir, "data"), "r").getChannel();
		MappedByteBuffer dataBuffer = dataChannel.map(MapMode.READ_ONLY, 0, dataChannel.size());
		dataBuffer.load();

		AutocompleteMultiWordSearcher shard = new AutocompleteMultiWordSearcher(es);
		shard.radixBuffer = radixBuffer;
		shard.dataBuffer = dataBuffer;
		return shard;
	}
	static CompleteWordMultiWordSearcher loadNonPrefixShard(File shardDir, ExecutorService es) throws Exception {
		logger.info("Loading shard " + shardDir.getName());

		logger.info("Loading radix tree");
		byte[] radixBuffer = FileUtils.readFileToByteArray(new File(shardDir, "radix")); 

		logger.info("Loading data");
		FileChannel dataChannel = new RandomAccessFile(new File(shardDir, "data"), "r").getChannel();
		MappedByteBuffer dataBuffer = dataChannel.map(MapMode.READ_ONLY, 0, dataChannel.size());
		dataBuffer.load();

		CompleteWordMultiWordSearcher shard = new CompleteWordMultiWordSearcher(es);
		shard.radixBuffer = radixBuffer;
		shard.dataBuffer = dataBuffer;
		return shard;
	}

	public static void main(String[] args) throws Exception {
		BasicConfigurator.configure();
		File prefixShardsDir = new File(args[0]);
		File nonPrefixShardsDir = new File(args[1]);
		File polygonsDir = new File(args[2]);
		File adminList = new File(args[3]);
		File adminRelationsFile = new File(args[4]);

		List<MultiWordSearcher> prefixShards = new ArrayList<MultiWordSearcher>();
		List<MultiWordSearcher> nonPrefixShards = new ArrayList<MultiWordSearcher>();

		ExecutorService topExecutor = Executors.newFixedThreadPool(8);
		ExecutorService bottomExecutor = Executors.newFixedThreadPool(8);

		Map<Long, AdminDesc> adminRelations = AdminDesc.parseCityList(adminList);
		AdminDesc.parseCityParents(adminRelationsFile, adminRelations);

		for (File shardDir : prefixShardsDir.listFiles()) {
			if (!shardDir.isDirectory()) continue;

			AutocompleteMultiWordSearcher shard = loadPrefixShard(shardDir, bottomExecutor);

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
			prefixShards.add(shard);
		}

		for (File shardDir : nonPrefixShardsDir.listFiles()) {
			if (!shardDir.isDirectory()) continue;

			CompleteWordMultiWordSearcher shard = loadNonPrefixShard(shardDir, bottomExecutor);

			logger.info("Preloading shard");
			shard.autocomplete(new String[]{"jol", "cur"}, 1, null);

			logger.info("Computing filters");
			shard.computeAndAddFilter("rue");
			shard.computeAndAddFilter("des");
			shard.computeAndAddFilter("aux");
			shard.computeAndAddFilter("france");
			shard.computeAndAddFilter("deutschland");
			shard.dumpFiltersInfo();

			shard.shardName = shardDir.getName();
			nonPrefixShards.add(shard);
		}

		logger.info("Loading polygons shard");
		MultiWordSearcher polygonsShard = loadPrefixShard(polygonsDir, bottomExecutor);
		logger.info("Preloading shard");
		polygonsShard.autocomplete(new String[]{"jol", "cur"}, 1, null);


		logger.info("Loading done, starting server");

		Server server = new Server(8080);

		ServletContextHandler sch = new ServletContextHandler();

		sch.setContextPath("/");
		server.setHandler(sch);

		{
			AutocompletionServlet servlet = new AutocompletionServlet(topExecutor);
			servlet.adminRelations = adminRelations;
			servlet.shards = prefixShards;
			servlet.initSW(("/dev/null"));

			sch.addServlet(new ServletHolder(servlet), "/complete");
		}

		{
			AutocompletionServlet servlet = new AutocompletionServlet(topExecutor);
			servlet.adminRelations = adminRelations;
			servlet.minTokenLength = 0;
			servlet.shards = nonPrefixShards;
			servlet.initSW(("/dev/null"));
			for (String s : frenchStopWords) {
				servlet.stopWords.add(s);
			}

			sch.addServlet(new ServletHolder(servlet), "/exact");
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
