(ns betacalendars.calendar-query.date
  "Civil-date sources and small YearMonth helpers. All ranges are inclusive."
  (:import (java.time DayOfWeek LocalDate YearMonth)
           (java.time.format DateTimeFormatter DateTimeParseException)))

(def ^:private date-format DateTimeFormatter/ISO_LOCAL_DATE)
(def ^:private month-format (DateTimeFormatter/ofPattern "uuuu-MM"))

(defn local-date
  "Coerce an ISO-8601 date string or LocalDate to LocalDate."
  ^LocalDate [x]
  (cond
    (instance? LocalDate x) x
    (string? x) (try
                  (LocalDate/parse x date-format)
                  (catch DateTimeParseException e
                    (throw (ex-info "Invalid ISO date" {:type ::invalid-date :value x} e))))
    :else (throw (ex-info "Expected LocalDate or ISO date string"
                          {:type ::invalid-date :value x}))))

(defn year-month
  "Coerce a YearMonth or YYYY-MM string to YearMonth."
  ^YearMonth [x]
  (cond
    (instance? YearMonth x) x
    (string? x) (try
                  (YearMonth/parse x month-format)
                  (catch DateTimeParseException e
                    (throw (ex-info "Invalid year-month" {:type ::invalid-year-month :value x} e))))
    :else (throw (ex-info "Expected YearMonth or YYYY-MM string"
                          {:type ::invalid-year-month :value x}))))

(defn year-month? [x] (instance? YearMonth x))
(defn parse-year-month ^YearMonth [s] (year-month s))
(defn format-year-month [x] (str (year-month x)))
(defn month-start ^LocalDate [x] (.atDay (year-month x) 1))
(defn month-end ^LocalDate [x] (.atEndOfMonth (year-month x)))
(defn shift-month ^YearMonth [x n] (.plusMonths (year-month x) (long n)))

(defn- range-step [^LocalDate start ^LocalDate end rf init]
  (loop [^LocalDate d start acc init]
    (if (.isAfter d end)
      acc
      (let [next-acc (rf acc d)]
        (if (reduced? next-acc)
          @next-acc
          (if (= d end)
            next-acc
            (recur (.plusDays d 1) next-acc)))))))

(defn reducible-date-range
  "Inclusive reducible LocalDate range. Supports reduce, transduce and into."
  [start end]
  (let [^LocalDate start (local-date start)
        ^LocalDate end (local-date end)]
    (when (.isAfter start end)
      (throw (ex-info "Range start must not follow range end"
                      {:type ::invalid-range :from start :to end})))
    (reify
      clojure.lang.IReduceInit
      (reduce [_ rf init] (range-step start end rf init))
      clojure.lang.IReduce
      (reduce [_ rf] (if (= start end)
                       start
                       (range-step (.plusDays start 1) end rf start))))))

(defn date-range
  "Lazy inclusive sequence of LocalDate values from start through end."
  [start end]
  (let [^LocalDate start (local-date start)
        ^LocalDate end (local-date end)]
    (when (.isAfter start end)
      (throw (ex-info "Range start must not follow range end"
                      {:type ::invalid-range :from start :to end})))
    (letfn [(step [^LocalDate d]
              (lazy-seq
               (when-not (.isAfter d end)
                 (if (= d end)
                   (list d)
                   (cons d (step (.plusDays d 1)))))))]
      (step start))))

(defn month-range
  "Lazy inclusive sequence of YearMonth values from start through end."
  [start end]
  (let [^YearMonth start (year-month start)
        ^YearMonth end (year-month end)]
    (when (.isAfter start end)
      (throw (ex-info "Range start must not follow range end"
                      {:type ::invalid-range :from start :to end})))
    (letfn [(step [^YearMonth m]
              (lazy-seq
               (when-not (.isAfter m end)
                 (if (= m end)
                   (list m)
                   (cons m (step (.plusMonths m 1)))))))]
      (step start))))

(def ^:private weekday->day
  {:monday DayOfWeek/MONDAY, :tuesday DayOfWeek/TUESDAY,
   :wednesday DayOfWeek/WEDNESDAY, :thursday DayOfWeek/THURSDAY,
   :friday DayOfWeek/FRIDAY, :saturday DayOfWeek/SATURDAY,
   :sunday DayOfWeek/SUNDAY})

(defn weekday-keyword [^LocalDate date]
  (some (fn [[k ^DayOfWeek v]] (when (= (.getDayOfWeek date) v) k)) weekday->day))

(defn- first-grid-date ^LocalDate [^YearMonth ym week-start]
  (let [^LocalDate first (.atDay ym 1)
        first-day (.getValue (.getDayOfWeek first))
        start-day (.getValue (weekday->day week-start))
        offset (mod (- first-day start-day) 7)]
    (.minusDays first offset)))

(defn project-month
  "Return an immutable grid projection for a month. Adjacent cells can be
  included (default), excluded (their :date is nil), or omitted."
  ([month] (project-month month {}))
  ([month {:keys [week-start adjacent-days]
           :or {week-start :monday adjacent-days :include}}]
   (let [^YearMonth ym (year-month month)
         _valid-week-start (when-not (contains? weekday->day week-start)
             (throw (ex-info "Unknown week start" {:type ::invalid-week-start :value week-start})))
         _valid-adjacent-mode (when-not (#{:include :exclude :omit} adjacent-days)
             (throw (ex-info "Unknown adjacent-days mode"
                             {:type ::invalid-adjacent-days :value adjacent-days})))
         first-date (.atDay ym 1)
         last-date (.atEndOfMonth ym)
         grid-start (first-grid-date ym week-start)
         count-days (+ (.lengthOfMonth ym)
                       (mod (- (.getValue (.getDayOfWeek first-date))
                               (.getValue (weekday->day week-start))) 7))
         row-count (long (Math/ceil (/ (double count-days) 7.0)))
         weeks (mapv
                (fn [row]
                  (into []
                        (keep (fn [column]
                                (let [^LocalDate d (.plusDays grid-start (+ (* row 7) column))
                                      current? (= ym (YearMonth/from d))]
                                  (cond
                                    (and (not current?) (= adjacent-days :omit)) nil
                                    :else {:date (when (or current? (= adjacent-days :include)) d)
                                           :day (when (or current? (= adjacent-days :include)) (.getDayOfMonth d))
                                           :weekday (weekday-keyword d)
                                           :row row :column column
                                           :month-relation (cond current? :current
                                                                 (.isBefore d first-date) :previous
                                                                 :else :next)
                                           :current-month? current?})))
                              (range 7))))
                (range row-count))]
     {:year (.getYear ym) :month (.getMonthValue ym)
      :first-date first-date :last-date last-date
      :day-count (.lengthOfMonth ym) :week-start week-start
      :row-count row-count :weeks weeks})))

(defn blank-grid
  "Create date-free structural cells for a fixed rows-by-columns grid."
  [{:keys [rows columns week-start]
    :or {week-start :monday}}]
  (when-not (and (integer? rows) (pos? rows) (integer? columns) (pos? columns))
    (throw (ex-info "Grid dimensions must be positive integers"
                    {:type ::invalid-grid-size :rows rows :columns columns})))
  (when-not (contains? weekday->day week-start)
    (throw (ex-info "Unknown week start" {:type ::invalid-week-start :value week-start})))
  {:rows rows :columns columns :week-start week-start
   :weeks (mapv (fn [row]
                  (mapv (fn [column] {:row row :column column}) (range columns)))
                (range rows))})
