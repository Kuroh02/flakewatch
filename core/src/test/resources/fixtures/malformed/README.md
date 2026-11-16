# Malformed fixtures (hand-written)

Hand-written files that are broken on purpose, to prove the parser fails or warns with a useful message (FR-ING-008, FR-ING-009).

| File | Problem |
|---|---|
| `not-well-formed.xml` | An unclosed element on line 4 |
| `missing-names.xml` | One `<testcase>` with no `name`, one with no `classname`, one with an invalid `time` |
