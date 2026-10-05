(ns betacalendars.calendar-query-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [betacalendars.calendar-query :as q])
  (:import (java.time LocalDate YearMonth)
           (java.util Locale TimeZone)))

(deftest year-month-helpers
  (is (= (YearMonth/of 2027 2) (q/parse-year-month "2027-02")))
  (is (= "2027-02" (q/format-year-month (YearMonth/of 2027 2))))
  (is (= (LocalDate/of 2027 2 1) (q/month-start "2027-02")))
  (is (= (LocalDate/of 2027 2 28) (q/month-end "2027-02")))
  (is (= "2027-03" (str (q/shift-month "2027-02" 1)))))

(deftest inclusive-date-sources
  (let [expected (mapv #(LocalDate/of 2026 11 %) (range 1 6))]
    (is (= expected (vec (q/date-range "2026-11-01" "2026-11-05"))))
    (is (= expected (into [] (q/reducible-date-range "2026-11-01" "2026-11-05"))))
    (is (= [1 2] (transduce (comp (take 2) (map #(.getDayOfMonth ^LocalDate %))) conj []
                             (q/reducible-date-range "2026-11-01" "2026-11-05")))))
  (is (= [(YearMonth/of 2026 11) (YearMonth/of 2026 12) (YearMonth/of 2027 1)]
         (vec (q/month-range "2026-11" "2027-01"))))
  (is (thrown? clojure.lang.ExceptionInfo (q/date-range "2027-01-02" "2027-01-01"))))

(deftest requested-boundary-months
  (doseq [[ym expected-count] [["2026-11" 30] ["2026-12" 31]
                               ["2027-01" 31] ["2027-02" 28]]]
    (let [month (q/year-month ym)
          days (q/date-range (q/month-start month) (q/month-end month))]
      (is (= expected-count (count days)) ym)
      (is (every? #(= month (YearMonth/from %)) days) ym)))
  (is (= 29 (count (q/date-range "2028-02-01" "2028-02-29")))))

(deftest timezone-and-locale-invariance
  (let [original-zone (TimeZone/getDefault)
        original-locale (Locale/getDefault)
        expected (mapv :weekday
                       (q/execute {:from "2027-01-01" :to "2027-01-10"
                                   :where [:weekday-in #{:monday :friday}]
                                   :select [:weekday]}))]
    (try
      (doseq [[zone locale] [["UTC" Locale/US]
                             ["Europe/Istanbul" (Locale/forLanguageTag "tr-TR")]
                             ["America/New_York" Locale/US]
                             ["Asia/Tokyo" (Locale/forLanguageTag "ja-JP")]]]
        (TimeZone/setDefault (TimeZone/getTimeZone zone))
        (Locale/setDefault locale)
        (is (= expected
               (mapv :weekday
                     (q/execute {:from "2027-01-01" :to "2027-01-10"
                                 :where [:weekday-in #{:monday :friday}]
                                 :select [:weekday]})))
            (str zone " / " locale)))
      (finally
        (TimeZone/setDefault original-zone)
        (Locale/setDefault original-locale)))))

(deftest query-filter-derive-project
  (let [result (q/execute {:from "2026-11-01" :to "2026-11-08"
                           :where [:and [:weekday-in #{:monday :wednesday :friday}]
                                   [:not [:weekend?]]]
                           :derive {:quarter :quarter :iso-week :iso-week}
                           :select [:date :weekday :quarter :iso-week]
                           :serializable? true})]
    (is (= ["2026-11-02" "2026-11-04" "2026-11-06"] (mapv :date result)))
    (is (every? #(= 4 (:quarter %)) result))))

(deftest date-fields-come-from-date-value
  (is (= [{:label :marker :year 2027}]
         (vec (q/execute {:select [:year :label]}
                         [{:date "2027-01-01" :year 1900 :label :marker}])))))

(deftest validation-and-safe-edn
  (is (= {:source {:type :date-range :from "2026-11-01" :to "2026-11-30"}
          :plan [{:op :filter :predicate [:weekend?]}]
          :streaming? true :materialization-required? false
          :estimated-materialization :none :optimizations [] :options {}}
         (q/explain {:from "2026-11-01" :to "2026-11-30" :where [:weekend?]})))
  (is (= {:where [:month-in #{1 2 12}]} (q/read-query "{:where [:month-in #{1 2 12}]}")))
  (is (thrown? clojure.lang.ExceptionInfo (q/read-query "#=(System/exit 0)")))
  (is (= ::q/invalid-operator-arity
         (:type (ex-data (try (q/validate-query {:where [:weekday? :funday]})
                              (catch clojure.lang.ExceptionInfo e e))))))
  (is (= ::q/invalid-query
         (:type (ex-data (try (q/validate-query {:mystery true})
                              (catch clojure.lang.ExceptionInfo e e))))))
  (is (= ::q/unknown-operator
         (:type (ex-data (try (q/validate-query {:where [:arbitrary?]})
                              (catch clojure.lang.ExceptionInfo e e))))))
  (is (= [:remove-duplicate-selections]
         (:optimizations (q/explain {:select [:date :weekday :date]})))))

(deftest materialization-is-explicit
  (let [plan (q/explain {:from "2026-11-01" :to "2026-11-03"
                         :order-by [[:date :desc]]})]
    (is (false? (:streaming? plan)))
    (is (:materialization-required? plan)))
  (is (= [3 2 1]
         (mapv #(.getDayOfMonth ^LocalDate (:date %))
               (q/execute {:order-by [[:date :desc]]}
                          (q/date-range "2026-11-01" "2026-11-03"))))))

(deftest grouping-and-calendar-windows
  (let [days (q/date-range "2026-11-29" "2026-12-02")
        windows (into [] (q/calendar-windows :month) days)
        grouped (q/execute {:group-by :year-month}
                           (q/date-range "2026-11-29" "2026-12-02"))]
    (is (= [2 2] (mapv count windows)))
    (is (= [(YearMonth/of 2026 11) (YearMonth/of 2026 12)]
           (mapv #(YearMonth/from (first %)) windows)))
    (is (= 2 (count grouped)))
    (is (= [2 2] (mapv count (vals grouped)))))
  (let [grouped (q/execute {:group-by :year-month
                            :aggregate [:count :first-date :last-date :min-day :max-day]
                            :serializable? true}
                           (q/date-range "2026-11-29" "2026-12-02"))]
    (is (= {:count 2 :first-date "2026-11-29" :last-date "2026-11-30"
            :min-day 29 :max-day 30}
           (get grouped "2026-11")))
    (is (= 2 (:count (get grouped "2026-12")))))
  (let [records (into [] (q/annotate-boundaries)
                      (q/date-range "2028-02-28" "2028-03-01"))]
    (is (= [false true false] (mapv :leap-day? records)))
    (is (:month-end? (second records))))
  (is (:quarter-start?
       (first (into [] (q/annotate-boundaries)
                    (q/date-range "2028-04-01" "2028-04-01")))))

(deftest calendar-projections
  (let [month (q/project-month "2027-01" {:week-start :monday})
        cells (vec (mapcat identity (:weeks month)))
        january (filter :current-month? cells)]
    (is (= 31 (:day-count month)))
    (is (= 5 (:row-count month)))
    (is (= (range 1 32) (map :day january)))
    (is (= :monday (:weekday (first cells)))))
  (let [grid (q/blank-grid {:rows 6 :columns 7 :week-start :sunday})]
    (is (= 6 (count (:weeks grid))))
    (is (every? #(= 7 (count %)) (:weeks grid)))
    (is (every? #(= #{:row :column} (set (keys %))) (mapcat identity (:weeks grid))))))

(deftest generated-range-properties
  (let [property
        (prop/for-all [year (gen/choose 1900 2100)
                       month (gen/choose 1 12)
                       day (gen/choose 1 28)
                       length (gen/choose 0 40)]
          (let [start (LocalDate/of year month day)
                end (.plusDays start length)
                lazy-values (vec (q/date-range start end))
                reduced-values (into [] (q/reducible-date-range start end))]
            (and (= reduced-values lazy-values)
                 (= (inc length) (count lazy-values))
                 (every? true? (map #(.equals (.plusDays ^LocalDate %1 1) %2)
                                    (butlast lazy-values) (rest lazy-values))))))
        result (tc/quick-check 250 property)]
    (is (:pass? result) (pr-str result))))
