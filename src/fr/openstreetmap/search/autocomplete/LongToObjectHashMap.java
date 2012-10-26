package fr.openstreetmap.search.autocomplete;

import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class LongToObjectHashMap<V> {
    private static final long EMPTY_KEY = Long.MIN_VALUE;

    /* First dimension: "size" of the hash map, ie current number of buckets.
     * Second dimension: chained list of matching keys and values for this bucket
     */
    private long[][] keys;
    private V[][] values;
    private int size;

    private int nbBuckets;

    public LongToObjectHashMap() {
        /* Initial number of buckets */
        keys = new long[2048][];
        values = (V[][]) new Object[2048][];
        nbBuckets = 2048;
    }

    public int size() {
        return size;
    }

    public boolean containsKey(long key) {
        return get(key) != null;
    }

    public V get(long key) {
        int bucket = (int) (bucket(key) & (nbBuckets - 1));

        long[] chain = keys[bucket];
        if (chain == null) return null;

        /* Try to find it in the chain at this bucket */
        for (int i = 0; i < chain.length; i++) {
            long innerKey = chain[i];
            if (innerKey == key) {
                return values[bucket][i];
            }
        }

        return null;
    }

    public V put(long key, V value) {
        int bucket = (int) (bucket(key) & (nbBuckets - 1));

        long[] keyChain = keys[bucket];
        V[] valueChain = values[bucket];

        if (keyChain == null) {
            /* Create it */
            keys[bucket] = new long[16];
            keyChain = keys[bucket];
            Arrays.fill(keyChain, EMPTY_KEY);

            values[bucket] = (V[])new Object[16];
            valueChain = values[bucket];

            keyChain[0] = key;
            valueChain[0] = value;
            ++size;
            return null;
        } else {
            /* Try to see if it already exists in the chain, to replace it. Or to find an empty
             * slot
             */
            int found;
            for (found = 0; found < keyChain.length; found++) {
                if (keyChain[found] == EMPTY_KEY) {
                    /* Empty slot, put it here ! */
                    keyChain[found] = key;
                    valueChain[found] = value;
                    size++;
                    return null;
                }
                if (keyChain[found] == key) {
                    /* Already exists, replace it here ! */
                    V oldValue = valueChain[found];
                    keyChain[found] = key;
                    valueChain[found] = value;
                    return oldValue;
                }
            }
            /* No more free slots, resize chain */
            keys[bucket] = Arrays.copyOf(keyChain, keyChain.length * 2);
            keyChain = keys[bucket];
            Arrays.fill(keyChain, found, keyChain.length, EMPTY_KEY);
            values[bucket] = Arrays.copyOf(valueChain, valueChain.length * 2);
            valueChain = values[bucket];

            keyChain[found] = key;
            valueChain[found] = value;
            size++;
            return null;
        }
    }
    
    public V remove(long key) {
        int bucket = (int) (bucket(key) & (nbBuckets - 1));


        long[] keyChain = keys[bucket];
        
        if (keyChain == null) return null;
        
        /* Try to find it in the chain at this bucket */
        for (int i = 0; i < keyChain.length; i++) {
            long chainKey = keyChain[i];
            if (chainKey == key) {
                V ret = values[bucket][i];
                values[bucket][i] = null;
                keys[bucket][i] = EMPTY_KEY;
                return ret;
            }
        }
        return null;
    }

    public void clear() {
        size = 0;
        Arrays.fill(keys, null);
        Arrays.fill(values, null);
    }

    public void fillValues(List<V> out) {
        for (int i = 0; i < nbBuckets; i++) {
            if (keys[i] == null) continue;

            long[] keyChain = keys[i];
            V[] valueChain = values[i];

            for (int j = 0; j < keyChain.length; j++) {
                if (keyChain[j] != EMPTY_KEY) {
                    out.add(valueChain[j]);
                }
            }
        }
    }
    
    public KeyIterator keyIterator() {
        return new KeyIterator();
    }


    public class KeyIterator {
        int curBucket = 0;
        int curInChain = -1;

        /* Returns Long.MIN when done */
        public long next() {
//            System.out.println("next, cB=" + curBucket + " cic=" + curInChain);
            begin: while (true) {
                if (curBucket >= keys.length) return Long.MIN_VALUE;
                if (keys[curBucket] == null) {
//                    System.out.println("CB " + curBucket + " is empty goto next");
                    curBucket++;
                    if (curBucket >= keys.length) return Long.MIN_VALUE;
                    curInChain = -1;
                    if (keys[curBucket] == null) continue begin;
                }
                
//                System.out.println("Chain: " + curBucket +" cis= " + curInChain);
                next_in_chain: while (true) {
                    curInChain++;
                    if (curInChain >= keys[curBucket].length) {
                        curInChain = -1;
                        curBucket++;
                        continue begin;
                    }
//                    System.out.println("CB = " + curBucket + " CIC = " + curInChain);
                    if (keys[curBucket][curInChain] == EMPTY_KEY) continue next_in_chain;
                    return keys[curBucket][curInChain];
                }
            }
        }
    }



    /* From Google's murmur3 */
    private long bucket(long key) {
        key ^= key >>> 33;
        key *= 0xff51afd7ed558ccdL;
        key ^= key >>> 33;
        key *= 0xc4ceb9fe1a85ec53L;
        key ^= key >>> 33;
        return key;
    }
}
