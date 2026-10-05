# Blank Projections

`blank-grid` creates a fixed rows-by-columns structure with no invented dates:

```clojure
(q/blank-grid {:rows 6 :columns 7 :week-start :monday})
```

Its cells contain only `:row` and `:column`. Use this when downstream code needs a layout topology before any month or date is chosen. For a printable-calendar starting point, see the [blank calendar reference](https://www.betacalendars.com/blank-calendar).
