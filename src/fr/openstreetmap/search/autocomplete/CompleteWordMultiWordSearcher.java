package fr.openstreetmap.search.autocomplete;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** This class is thread safe */
public  class CompleteWordMultiWordSearcher extends MultiWordSearcher {
    public ByteBuffer dataBuffer;
    public byte[] radixBuffer;
    public String shardName;
    
    public CompleteWordMultiWordSearcher() {
        executor = Executors.newFixedThreadPool(8);
    }
    
    public CompleteWordMultiWordSearcher(ExecutorService executor) {
        this.executor = executor;
    }
    
    @Override
    protected Searcher newSearcher() {
        return new CompleteWordSearcher(radixBuffer, dataBuffer);
    }
}