# Design

The public model stays close to Clojure data: maps, vectors, keywords, sets, sequences, and transducers. `LocalDate` supplies date-only arithmetic, while `YearMonth` represents month identity. Neither depends on the machine time zone.

Source generation, query validation, query execution, windowing, explanation, and month topology are separate concerns. The core has no background threads, global cache, or mutable shared state. Global operations are called out because they materialize results.
