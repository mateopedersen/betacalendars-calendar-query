# Security

Query maps are interpreted through a fixed operator and field vocabulary. Query data is never passed to `eval`, `load-string`, symbol resolution, generated source, or arbitrary function invocation.

`read-query` uses `clojure.edn/read-string`, enables no tagged readers, and rejects tagged literal values. EDN parsing does not execute code, but a very large input can still consume memory and time; put request-size and request-rate controls at network or application boundaries.
