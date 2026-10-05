# Getting Started

## Dependency

Add `net.clojars.mateopedersen/calendar-query` version `0.1.0` to your Clojure CLI dependencies, then require `betacalendars.calendar-query` as `q`.

## Produce a weekday projection

```clojure
(q/execute {:from "2027-01-01"
            :to "2027-01-31"
            :where [:weekday? :monday]
            :select [:date :weekday]
            :serializable? true})
```

`execute` returns an `eduction` when a query is streaming. Use `seq`, `into`, `transduce`, or `reduce` to consume it. If you provide `:order-by` or `:group-by`, the result is a materialized vector or sorted map.

## Use your own data

Input items can be `LocalDate` values or maps with a `:date` value. A string date is parsed as ISO-8601. Other map fields pass through unless `:select` limits the output.
