# Month Projections

`project-month` creates a vector-based, immutable calendar topology:

```clojure
(q/project-month "2027-01" {:week-start :monday
                            :adjacent-days :include})
```

The result contains the year, month, first and last dates, day count, week start, row count, and rows of cells. Each cell reports date, day number, weekday keyword, row, column, month relation, and whether it belongs to the requested month. Adjacent-day modes are `:include`, `:exclude` (keep the cell but clear its date and day), and `:omit`.

This is structural data only; it does not render HTML or attach presentation rules. The [Beta Calendars January calendar](https://www.betacalendars.com/january-calendar.html) is one familiar reference month.
