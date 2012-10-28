package fr.openstreetmap.search.autocomplete;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** This class is thread safe */
public  class AutocompleteMultiWordSearcher extends MultiWordSearcher {
    public ByteBuffer dataBuffer;
    public byte[] radixBuffer;
    public String shardName;
    
    public AutocompleteMultiWordSearcher() {
        executor = Executors.newFixedThreadPool(8);
    }
    
    public AutocompleteMultiWordSearcher(ExecutorService executor) {
        this.executor = executor;
    }
    
    @Override
    protected Searcher newSearcher() {
        return new Autocompleter(radixBuffer, dataBuffer);
    }
}