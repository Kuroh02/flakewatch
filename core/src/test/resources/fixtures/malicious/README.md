# Malicious fixtures (hand-written)

These files are **deliberately hostile** and hand-written. They prove the parser refuses them safely (NFR-SEC-002).
They are not examples of any real tool's output.

| File | Attack |
|---|---|
| `xxe-external-entity.xml` | XXE: an external entity that tries to read `/etc/passwd` |
| `billion-laughs.xml` | Entity expansion ("billion laughs") |
| `external-dtd.xml` | A DOCTYPE that points at an external DTD |
