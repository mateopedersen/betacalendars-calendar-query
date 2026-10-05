# BetaCalendars Calendar Query

Calendar Query is a small Clojure library for querying civil-date data with ordinary maps and EDN. It provides inclusive date sources, a checked predicate language, transducer-based filtering and projection, calendar windows, a data-valued plan explanation, and pure month-grid projections.

Source repository: [mateopedersen/betacalendars-calendar-query](https://github.com/mateopedersen/betacalendars-calendar-query).

Dates use `java.time.LocalDate`; month values use `java.time.YearMonth`. There are no runtime dependencies beyond Clojure and the JDK.

## Install

```clojure
com.betacalendars/calendar-query {:mvn/version "0.1.0"}
```

## Query dates

```clojure
(require '[betacalendars.calendar-query :as q])

(def query
  {:from "2026-11-01"
   :to "2026-11-30"
   :where [:and
           [:weekday-in #{:monday :wednesday :friday}]
           [:not [:weekend?]]]
   :derive {:quarter :quarter :iso-week :iso-week}
   :select [:date :weekday :quarter :iso-week]
   :serializable? true})

(q/execute query)
;; => an eduction of selected maps, with ISO date strings
```

The same query can run over caller-owned records:

```clojure
(transduce (q/compile-query query) conj []
           (q/reducible-date-range "2026-11-01" "2026-11-30"))
```

`compile-query` supports streaming predicates, built-in derivation, projection, `:take`, and `:drop`. Global `:order-by` and `:group-by` operations use `execute` and materialize matching rows. Grouping can return matching records or a small set of built-in aggregates. `explain` reports that materialization:

```clojure
(q/explain query)
;; => EDN map with source bounds, plan steps, and materialization flags
```

## Calendar shapes

```clojure
(q/project-month "2027-01" {:week-start :monday :adjacent-days :include})
(q/blank-grid {:rows 6 :columns 7 :week-start :monday})
(into [] (q/calendar-windows :iso-week) dates)
```

Month projections are pure data; blank grids contain row and column positions without invented dates.

## Query safety

Queries are data, never Clojure forms. `read-query` uses `clojure.edn/read-string` with no tagged readers. It does not evaluate symbols or resolve functions. Applications accepting remote query text should also set a request-size limit before parsing.

## Development

Run the local quality gate with `clojure -T:build ci`. It runs unit and generated-range property tests, clj-kondo, checks the cljdoc article manifest is present, and builds a source JAR and POM. See [Getting Started](doc/getting-started.md), [Query Language](doc/query-language.md), and the other guides under `doc/`.

## References

The library is independent of any calendar website. For background examples, see the [Beta Calendars homepage](https://www.betacalendars.com/) and its [blank calendar reference](https://www.betacalendars.com/blank-calendar).

## License

MIT. See [LICENSE](LICENSE).
