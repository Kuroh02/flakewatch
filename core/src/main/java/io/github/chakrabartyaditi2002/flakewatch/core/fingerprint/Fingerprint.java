package io.github.chakrabartyaditi2002.flakewatch.core.fingerprint;

import java.util.HexFormat;
import java.util.List;

/** The identity of a failure group, plus the parts it was built from (shown in the UI). */
public record Fingerprint(byte[] hash, String failureType, String normalizedMessage, List<String> topFrames) {

    public Fingerprint {
        hash = hash.clone();
        topFrames = List.copyOf(topFrames);
    }

    @Override
    public byte[] hash() {
        return hash.clone();
    }

    public String hex() {
        return HexFormat.of().formatHex(hash);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Fingerprint f && f.hex().equals(hex());
    }

    @Override
    public int hashCode() {
        return hex().hashCode();
    }

    @Override
    public String toString() {
        return "Fingerprint[" + hex() + ", " + failureType + ", " + normalizedMessage + ", " + topFrames + "]";
    }
}
