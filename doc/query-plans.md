# Query Plans

`explain` returns data, not formatted prose. It reports the normalized source bounds, operation steps, streaming status, materialization estimate, and optimizer notes.

Filtering, derivation, `:take`, `:drop`, and selection can stream through `compile-query`. A global `:order-by` or `:group-by` requires `execute` to hold matching results before producing its output. The plan labels this as `:estimated-materialization :all-matching-records`. Grouping supports `:count`, `:first-date`, `:last-date`, `:min-day`, and `:max-day` aggregations.

The current optimizer performs only a semantics-preserving projection cleanup: repeated fields in `:select` are removed while keeping the first occurrence. It does not rewrite predicate meaning or guess at data statistics.
