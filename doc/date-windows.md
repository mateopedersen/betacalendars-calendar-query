# Date Windows

`calendar-windows` is a transducer over consecutive input records. Supported keys are `:date`, `:day`, `:month`, `:quarter`, `:year`, and `:iso-week`. The input order is preserved, so unsorted input can produce multiple windows for the same calendar key.

```clojure
(into [] (q/calendar-windows :month) date-records)
```

Each output is a vector of source records. This operation buffers only the current consecutive window.
