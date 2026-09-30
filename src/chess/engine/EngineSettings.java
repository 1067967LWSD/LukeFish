package chess.engine;

/**
 * An immutable settings snapshot: changing a spinner cannot change a search
 * halfway through a recursive call. Presets are starting points, not Elo claims.
 */
public record EngineSettings(int maxDepth, int timeLimitMillis, int quiescenceDepth,
        int randomnessCp, boolean transpositionTable, boolean moveOrdering,
        boolean verboseThinking, EvaluationWeights weights) {

    public EngineSettings {
        requireRange("Search depth", maxDepth, 1, 12);
        requireRange("Thinking time", timeLimitMillis, 100, 30_000);
        requireRange("Quiescence depth", quiescenceDepth, 0, 12);
        requireRange("Randomness", randomnessCp, 0, 300);
        if (weights == null) {
            throw new IllegalArgumentException("Evaluation weights are required.");
        }
    }

    private static void requireRange(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max + ".");
        }
    }

    public static EngineSettings defaults() {
        return Preset.CHALLENGE.settings();
    }

    /** Percentages let students disable one idea without editing search. */
    public record EvaluationWeights(int materialPercent, int placementPercent,
            int mobilityCp, int pawnStructurePercent, int kingSafetyPercent) {
        public EvaluationWeights {
            requireRange("Material weight", materialPercent, 0, 200);
            requireRange("Placement weight", placementPercent, 0, 200);
            requireRange("Mobility weight", mobilityCp, 0, 10);
            requireRange("Pawn structure weight", pawnStructurePercent, 0, 200);
            requireRange("King safety weight", kingSafetyPercent, 0, 200);
        }

        public static EvaluationWeights defaults() {
            return new EvaluationWeights(100, 100, 3, 100, 100);
        }
    }

    public enum Preset {
        FIRST_STEPS("First steps", 2, 250, 0, 180),
        CASUAL("Casual", 4, 1_000, 3, 50),
        CHALLENGE("Challenge", 7, 2_500, 6, 0),
        STRONGEST("Strongest", 12, 5_000, 8, 0);

        private final String label;
        private final int depth;
        private final int milliseconds;
        private final int quiescence;
        private final int randomness;

        Preset(String label, int depth, int milliseconds, int quiescence, int randomness) {
            this.label = label;
            this.depth = depth;
            this.milliseconds = milliseconds;
            this.quiescence = quiescence;
            this.randomness = randomness;
        }

        public EngineSettings settings() {
            return new EngineSettings(depth, milliseconds, quiescence, randomness,
                    true, true, false, EvaluationWeights.defaults());
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
