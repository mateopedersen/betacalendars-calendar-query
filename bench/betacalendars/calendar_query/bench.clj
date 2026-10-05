(ns betacalendars.calendar-query.bench
  (:require [criterium.core :as criterium]
            [betacalendars.calendar-query :as q]))

(def start-date "2000-01-01")
(def end-date "2099-12-31")

(defn -main [& _]
  (println "Lazy sequence source; consume all dates")
  (criterium/quick-bench (into [] (q/date-range start-date end-date)))
  (println "Reducible source; consume all dates")
  (criterium/quick-bench (into [] (q/reducible-date-range start-date end-date)))
  (println "Lazy sequence with weekday filtering")
  (criterium/quick-bench
   (transduce (q/compile-query {:where [:weekday-in #{:monday :wednesday :friday}]
                                :select [:date]})
              conj [] (q/date-range start-date end-date)))
  (println "Reducible source with the same weekday filtering")
  (criterium/quick-bench
   (transduce (q/compile-query {:where [:weekday-in #{:monday :wednesday :friday}]
                                :select [:date]})
              conj [] (q/reducible-date-range start-date end-date))))
