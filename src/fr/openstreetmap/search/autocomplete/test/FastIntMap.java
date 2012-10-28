package fr.openstreetmap.search.autocomplete.test;

/**
 * The goal of this map is to speed up the put operation. 
 * Notably, it does not create intermediate "Entry" elements.
 * 
 * The map is a flat array of (key, value) slots. 
 * 
 * Each key as a theoretical 
 */
public class FastIntMap {
    
    long nPut;
    long nPutLoops;
    long nGet;
    long nGetLoops;
    
    public void stats() {
        System.out.println("put nb=" + nPut + " loops=" + nPutLoops);
        System.out.println("get nb=" + nGet + " loops=" + nGetLoops);

    }
    
    public FastIntMap(int initialCapacity) {
        growTo(initialCapacity);
    }
    
    int size; // Current size of the map (number of valid entries)
    int entriesCapacity; // Total number that we want to store before growing
    int memoryCapacity; // Number of entries storable in the array
    
    long[] data;
    
    long keyAt(int index) {
        return data[index*2];
    }
    long valueAt(int index) {
        return data[index*2 + 1];
    }

    public void put(long key, long value) {
        if (size == entriesCapacity) {
            growTo(entriesCapacity * 2);
        }
        
        ++nPut;
        
        /* Scan for an empty slot */
        int index = (int) (key % memoryCapacity);
        while (keyAt(index) != Long.MAX_VALUE && keyAt(index) != key) {
            ++index;
            ++nPutLoops;
            if (index == memoryCapacity) index = 0; 
        }
        
        if (keyAt(index) != key) ++size;
        
        data[index*2] = key;
        data[index*2 + 1] = value;
    }
    
    /** Returns Long.MAX_VALUE if not found */
    public long get(long key) {
        /* This will stop, because there is always at least one empty element in the map */
        int index = (int)(key % memoryCapacity);
        
        ++nGet;
        
        while (keyAt(index) != Long.MAX_VALUE && keyAt(index) != key) {
            ++index;
            ++nGetLoops;
            if (index == memoryCapacity) index = 0; 
        }
        if (keyAt(index) == key) return valueAt(index);
        return Long.MAX_VALUE;
    }
    
    private void growTo(int newEntriesCapacity) {
        entriesCapacity = newEntriesCapacity;
        memoryCapacity = 3 * entriesCapacity / 2;
        
        long[] oldData = data;
        
        data = new long[2 * memoryCapacity];
        for (int i = 0; i < data.length; i++) data[i] = Long.MAX_VALUE;
        
        size = 0;
        
        // Reinsert all
        if (oldData != null) {
            for (int i = 0; i < oldData.length / 2; i++){
                if (oldData[i*2] != Long.MAX_VALUE) {
                    put(oldData[i*2], oldData[i*2 + 1]);
                }
            }
        }
    }
}
