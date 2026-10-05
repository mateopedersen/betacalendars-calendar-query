# Query Language

A query is a Clojure map and can be written as plain EDN. Its main keys are `:from`, `:to`, `:where`, `:derive`, `:select`, `:take`, `:drop`, `:order-by`, and `:group-by`.

## Predicate operators

Boolean expressions compose recursively: `[:and expr ...]`, `[:or expr ...]`, and `[:not expr]`. Date predicates include `[:weekday? :monday]`, `[:weekday-in #{:monday :friday}]`, `[:month? 2]`, `[:month-in #{1 2 12}]`, `[:year? 2027]`, `[:day-of-month? 1]`, `[:day-between 1 15]`, `[:iso-week? 1]`, `[:quarter? 1]`, `[:weekend?]`, `[:leap-year?]`, `[:month-start?]`, `[:month-end?]`, `[:year-start?]`, and `[:year-end?]`.

Unknown operators, fields, malformed forms, and reversed bounds raise `ex-info` with a namespaced `:type` and useful path data. `validate-query` returns the date-coerced normalized query.

Use `:group-by` with an optional `:aggregate` vector. Supported aggregate operations are `:count`, `:first-date`, `:last-date`, `:min-day`, and `:max-day`. Without `:aggregate`, each key maps to a vector of matching projected records. `:first-date` and `:last-date` follow input order, so provide chronologically ordered input when that is the desired meaning.

## Derived and selected fields

Built-in derived fields are `:quarter`, `:month-length`, `:year-month`, `:iso-week`, `:iso-week-year`, `:days-from-start`, and `:month-index`. A derive map explicitly names built-ins, for example `{:quarter :quarter}`. The last two require a query `:from` bound. Base fields include `:date`, `:year`, `:month`, `:day`, `:weekday`, `:day-of-year`, `:year-month`, `:iso-week`, `:iso-week-year`, `:quarter`, and boundary flags.

Selected fields are returned in a map with deterministic keyword ordering. Set `:serializable? true` to encode `LocalDate` and `YearMonth` values as ISO strings.

## EDN input

`read-query` rejects tagged literals and uses `clojure.edn/read-string`. It never enables arbitrary reader functions. The library leaves transport-level size limits to the application so callers can apply a limit appropriate to their boundary.
