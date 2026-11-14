package io.github.chakrabartyaditi2002.flakewatch.test.props;

public class PropertyAssertionError extends AssertionError {

    private final long seed;
    private final int caseNumber;
    private final Object originalInput;
    private final Object shrunkInput;

    public PropertyAssertionError(
            String message,
            long seed,
            int caseNumber,
            Object originalInput,
            Object shrunkInput,
            Throwable cause) {
        super(message, cause);
        this.seed = seed;
        this.caseNumber = caseNumber;
        this.originalInput = originalInput;
        this.shrunkInput = shrunkInput;
    }

    public long getSeed() {
        return seed;
    }

    public int getCaseNumber() {
        return caseNumber;
    }

    public Object getOriginalInput() {
        return originalInput;
    }

    public Object getShrunkInput() {
        return shrunkInput;
    }
}
