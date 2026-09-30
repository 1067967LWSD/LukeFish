package chess.engine;

import chess.model.Move;

/**
 * A bounded cache, not an ever-growing HashMap. Different keys can share a
 * slot, so lookup checks the full key before trusting the entry.
 */
final class TranspositionTable {
    private final Entry[] entries = new Entry[1 << 18];

    Entry find(long key) {
        Entry entry = entries[index(key)];
        return entry != null && entry.key() == key ? entry : null;
    }

    void store(Entry entry) {
        int index = index(entry.key());
        Entry old = entries[index];
        if (old == null || old.key() != entry.key() || entry.depth() >= old.depth()) {
            entries[index] = entry;
        }
    }

    private int index(long key) {
        return (int) (key ^ (key >>> 32)) & (entries.length - 1);
    }

    enum Bound {
        EXACT, LOWER, UPPER
    }

    /**
     * Identical boards may have different draw futures. Score reuse requires
     * the same reversible clock AND history multiset fingerprint. A cached
     * move can still help ordering when the history differs.
     */
    record Entry(long key, long historySignature, int halfmoveClock,
            int depth, int score, Bound bound, Move bestMove) {
    }
}
