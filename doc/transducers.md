# Transducers

`compile-query` returns a Clojure transducer for the query's filtering, derivation, `:drop`, `:take`, and projection stages. It can be used with reducible inputs without first building a date vector:

```clojure
(transduce (q/compile-query query) conj []
           (q/reducible-date-range "2026-11-01" "2027-02-28"))
```

The same transducer can be passed to `into`, `sequence`, or `eduction`. It has no shared mutable state. `calendar-windows` is also a transducer; it emits consecutive vectors grouped by `:date`, `:month`, `:quarter`, `:year`, or `:iso-week`.

`reducible-date-range` is an inclusive `IReduce`/`IReduceInit` source. `date-range` is a lazy sequence alternative. This library does not claim that either source is faster; measure with the workload and JDK that matter to your application.
