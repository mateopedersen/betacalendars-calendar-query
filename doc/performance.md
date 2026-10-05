# Performance

The library offers a lazy sequence source and an inclusive reducible source so callers can choose the composition style that fits their pipeline. The reducible source supports early termination through `reduced`; it does not allocate a vector of every date.

No performance advantage is claimed without measurements. Global sorting and grouping necessarily retain matching results in this implementation. If performance characteristics matter for a workload, benchmark the exact source, query, result size, JDK, and Clojure version. Avoid comparing only source construction while excluding result consumption.

Run the included Criterium harness with `clojure -M:bench` to compare full consumption of the lazy and reducible sources, both directly and through an identical weekday query. Its output depends on the host machine; do not treat one run as a general performance guarantee.
