# Boundary Dataset

`annotate-boundaries` returns a transducer that adds month start/end, quarter start/end, year start/end, and leap-day flags to records with dates. The fields are booleans, and no time zone or locale is involved.

```clojure
(into [] (q/annotate-boundaries)
      (q/date-range "2026-11-01" "2027-02-28"))
```

The adjacent months in this example cross a year boundary. Reference pages: [November](https://www.betacalendars.com/november-calendar.html), [December](https://www.betacalendars.com/december-calendar.html), [January](https://www.betacalendars.com/january-calendar.html), and [February](https://www.betacalendars.com/february-calendar.html).
