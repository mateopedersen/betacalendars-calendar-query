(ns betacalendars.calendar-query
  "A declarative query and projection toolkit for civil-date data. Queries are
  ordinary EDN values; this namespace never evaluates query forms."
  (:require [clojure.edn :as edn]
            [betacalendars.calendar-query.date :as dates])
  (:import (java.time LocalDate)
           (java.time.temporal IsoFields)))

(def ^:private weekday-set
  #{:monday :tuesday :wednesday :thursday :friday :saturday :sunday})

(def ^:private base-fields
  #{:date :year :month :day :weekday :day-of-year :year-month :iso-week
    :iso-week-year :quarter :weekend? :leap-year? :month-start? :month-end?
    :year-start? :year-end? :month-length})

(defn- invalid! [message data]
  (throw (ex-info message data)))

(defn- date-value [x]
  (try (dates/local-date x)
       (catch clojure.lang.ExceptionInfo e
         (throw (ex-info "Invalid query date" (assoc (ex-data e) :type ::invalid-date) e)))))

(defn- field-value [^LocalDate d field]
  (case field
    :date d
    :year (.getYear d)
    :month (.getMonthValue d)
    :day (.getDayOfMonth d)
    :weekday (dates/weekday-keyword d)
    :day-of-year (.getDayOfYear d)
    :year-month (java.time.YearMonth/from d)
    :iso-week (.get d IsoFields/WEEK_OF_WEEK_BASED_YEAR)
    :iso-week-year (.get d IsoFields/WEEK_BASED_YEAR)
    :quarter (inc (quot (dec (.getMonthValue d)) 3))
    :weekend? (boolean (#{:saturday :sunday} (dates/weekday-keyword d)))
    :leap-year? (.isLeapYear (.getYear d))
    :month-start? (= 1 (.getDayOfMonth d))
    :month-end? (= (.lengthOfMonth d) (.getDayOfMonth d))
    :year-start? (and (= 1 (.getMonthValue d)) (= 1 (.getDayOfMonth d)))
    :year-end? (and (= 12 (.getMonthValue d)) (= 31 (.getDayOfMonth d)))
    :month-length (.lengthOfMonth d)
    ::unknown))

(defn- predicate?* [expr path]
  (when-not (and (vector? expr) (keyword? (first expr)))
    (invalid! "Predicate must be a vector beginning with an operator keyword"
              {:type ::invalid-query :path path :value expr}))
  (let [[op & args] expr]
    (case op
      :and (do (when (empty? args)
                 (invalid! "and requires at least one predicate"
                           {:type ::invalid-operator-arity :operator op :path path}))
               (doseq [[i x] (map-indexed vector args)] (predicate?* x (conj path (inc i)))))
      :or (do (when (empty? args)
                (invalid! "or requires at least one predicate"
                          {:type ::invalid-operator-arity :operator op :path path}))
              (doseq [[i x] (map-indexed vector args)] (predicate?* x (conj path (inc i)))))
      :not (do (when-not (= 1 (count args))
                 (invalid! "not requires exactly one predicate"
                           {:type ::invalid-operator-arity :operator op :path path}))
               (predicate?* (first args) (conj path 1)))
      :weekday? (when-not (and (= 1 (count args)) (contains? weekday-set (first args)))
                  (invalid! "weekday? requires a weekday keyword"
                            {:type ::invalid-operator-arity :operator op :path path :value args}))
      :month? (when-not (and (= 1 (count args)) (integer? (first args))
                             (<= 1 (first args) 12))
                (invalid! "month? requires an integer from 1 through 12"
                          {:type ::invalid-operator-arity :operator op :path path :value args}))
      :year? (when-not (and (= 1 (count args)) (integer? (first args)))
               (invalid! "year? requires an integer"
                         {:type ::invalid-operator-arity :operator op :path path :value args}))
      :day-of-month? (when-not (and (= 1 (count args)) (integer? (first args))
                                    (<= 1 (first args) 31))
                       (invalid! "day-of-month? requires an integer from 1 through 31"
                                 {:type ::invalid-operator-arity :operator op :path path :value args}))
      :iso-week? (when-not (and (= 1 (count args)) (integer? (first args))
                                (<= 1 (first args) 53))
                   (invalid! "iso-week? requires an integer from 1 through 53"
                             {:type ::invalid-operator-arity :operator op :path path :value args}))
      :quarter? (when-not (and (= 1 (count args)) (integer? (first args))
                               (<= 1 (first args) 4))
                  (invalid! "quarter? requires an integer from 1 through 4"
                            {:type ::invalid-operator-arity :operator op :path path :value args}))
      :weekday-in (when-not (and (= 1 (count args)) (coll? (first args))
                                 (every? weekday-set (first args)))
                    (invalid! "weekday-in requires a collection of weekday keywords"
                              {:type ::invalid-operator-arity :operator op :path path :value args}))
      :month-in (when-not (and (= 1 (count args)) (coll? (first args))
                               (every? #(and (integer? %) (<= 1 % 12)) (first args)))
                  (invalid! "month-in requires a collection of month numbers"
                            {:type ::invalid-operator-arity :operator op :path path :value args}))
      :day-between
      (when-not (and (= 2 (count args)) (every? integer? args)
                     (<= 1 (first args) (second args) 31))
        (invalid! "day-between requires two integer bounds"
                  {:type ::invalid-operator-arity :operator op :path path}))
      (:weekend? :leap-year? :month-start? :month-end? :year-start? :year-end?)
      (when (seq args)
        (invalid! "Zero-argument predicate received arguments"
                  {:type ::invalid-operator-arity :operator op :path path}))
      (invalid! "Unknown calendar query operator"
                {:type ::unknown-operator :operator op :path path}))))

(defn- compile-predicate [expr]
  (let [[op & args] expr]
    (case op
      :and (let [fs (mapv compile-predicate args)]
             (fn [d] (every? #(% d) fs)))
      :or (let [fs (mapv compile-predicate args)]
            (fn [d] (boolean (some #(% d) fs))))
      :not (let [f (compile-predicate (first args))] (fn [d] (not (f d))))
      :weekday? (fn [d] (= (first args) (field-value d :weekday)))
      :weekday-in (let [wanted (set (first args))]
                    (fn [d] (contains? wanted (field-value d :weekday))))
      :weekend? (fn [d] (field-value d :weekend?))
      :month? (fn [d] (= (first args) (field-value d :month)))
      :month-in (let [wanted (set (first args))]
                  (fn [d] (contains? wanted (field-value d :month))))
      :year? (fn [d] (= (first args) (field-value d :year)))
      :day-of-month? (fn [d] (= (first args) (field-value d :day)))
      :day-between (let [[lo hi] args]
                     (fn [d] (<= lo (field-value d :day) hi)))
      :iso-week? (fn [d] (= (first args) (field-value d :iso-week)))
      :quarter? (fn [d] (= (first args) (field-value d :quarter)))
      :leap-year? (fn [d] (field-value d :leap-year?))
      :month-start? (fn [d] (field-value d :month-start?))
      :month-end? (fn [d] (field-value d :month-end?))
      :year-start? (fn [d] (field-value d :year-start?))
      :year-end? (fn [d] (field-value d :year-end?)))))

(defn- normalize-derive [derive]
  (when-not (or (nil? derive) (map? derive))
    (invalid! ":derive must be a map" {:type ::invalid-query :path [:derive]}))
  (let [unsupported (seq (remove #{:quarter :month-length :year-month :iso-week
                                   :iso-week-year :days-from-start :month-index}
                                 (keys derive)))]
    (when unsupported
      (invalid! "Unknown derived field" {:type ::unknown-field :field (first unsupported)
                                          :path [:derive (first unsupported)]})))
  (doseq [[field expression] derive]
    (let [op (if (and (vector? expression) (= 1 (count expression)))
               (first expression) expression)]
      (when-not (= field op)
        (invalid! "Derived fields must name a built-in expression"
                  {:type ::invalid-query :field field :expression expression
                   :path [:derive field]}))))
  derive)

(defn- derived-value [^LocalDate d field start]
  (case field
    :quarter (field-value d :quarter)
    :month-length (.lengthOfMonth d)
    :year-month (java.time.YearMonth/from d)
    :iso-week (field-value d :iso-week)
    :iso-week-year (field-value d :iso-week-year)
    :days-from-start (.between java.time.temporal.ChronoUnit/DAYS start d)
    :month-index (+ (* 12 (- (.getYear d) (.getYear start)))
                    (- (.getMonthValue d) (.getMonthValue start)))
    ::unknown))

(defn- query-date [x]
  (cond
    (instance? LocalDate x) x
    (and (map? x) (contains? x :date) (:date x)) (date-value (:date x))
    :else (invalid! "Input records must be LocalDate or maps containing :date"
                    {:type ::invalid-input :value x})))

(defn- record-for [x]
  (let [d (query-date x)]
    (if (map? x) (assoc x :date d) {:date d})))

(defn- project-record [record select serializable?]
  (let [d (:date record)
        computed (into {} (map (fn [k] [k (field-value d k)]) base-fields))
        custom (apply dissoc record (conj base-fields :date))
        all (merge computed custom)
        value (fn [k]
                (let [v (get all k ::unknown)]
                  (if (and serializable? v)
                    (cond (instance? LocalDate v) (str v)
                          (instance? java.time.YearMonth v) (str v)
                          :else v)
                    v)))]
    (if select
      (reduce (fn [m k] (assoc m k (value k))) (sorted-map) select)
      (reduce (fn [m k] (assoc m k (value k))) (sorted-map) (sort-by str (keys all))))))

(defn validate-query
  "Validate query structure and return normalized, date-coerced query data."
  [query]
  (when-not (map? query)
    (invalid! "Query must be a map" {:type ::invalid-query :path [] :value query}))
  (when-let [key (first (remove #{:from :to :where :derive :select :order-by :group-by
                                 :aggregate :take :drop :serializable?}
                               (keys query)))]
    (invalid! "Unknown query option" {:type ::invalid-query :path [key] :key key}))
  (let [from (some-> (:from query) date-value)
        to (some-> (:to query) date-value)
        derived (normalize-derive (:derive query))
        _valid-range (when (and from to (.isAfter ^LocalDate from ^LocalDate to))
            (invalid! "Query start must not follow query end"
                      {:type ::invalid-range :from from :to to :path [:from]}))
        _valid-where (when-let [where (:where query)] (predicate?* where [:where]))
        select (:select query)
        _valid-select (when (and select (not (and (vector? select) (every? keyword? select))))
            (invalid! ":select must be a vector of field keywords"
                      {:type ::invalid-query :path [:select] :value select}))
        order-by (:order-by query)
        _valid-order (when (and order-by
                     (not (and (vector? order-by)
                               (every? (fn [[field direction]]
                                         (and (keyword? field) (#{:asc :desc} direction)))
                                       order-by))))
            (invalid! "Invalid :order-by; expected [[field :asc|:desc] ...]"
                      {:type ::invalid-query :path [:order-by] :value order-by}))
        _selected-order-fields (when-let [field (and select
                                                      (some (fn [[f _]]
                                                              (when-not (some #{f} select) f))
                                                            order-by))]
                                 (invalid! "Ordered fields must be included in :select"
                                           {:type ::invalid-query :field field :path [:select]}))
        group-field (:group-by query)
        _valid-group (when (and group-field (not (keyword? group-field)))
                       (invalid! ":group-by must be a field keyword"
                                 {:type ::invalid-query :path [:group-by] :value group-field}))
        _selected-group (when (and group-field select (not (some #{group-field} select)))
                          (invalid! "Grouped field must be included in :select"
                                    {:type ::invalid-query :field group-field :path [:select]}))
        aggregate (:aggregate query)
        allowed-aggregates #{:count :first-date :last-date :min-day :max-day}
        _valid-aggregate (when (and aggregate
                                    (not (and (vector? aggregate)
                                              (every? allowed-aggregates aggregate))))
                           (invalid! ":aggregate must be a vector of supported aggregate keywords"
                                     {:type ::invalid-query :path [:aggregate] :value aggregate}))
        _aggregate-requires-group (when (and aggregate (nil? group-field))
                                    (invalid! ":aggregate requires :group-by"
                                              {:type ::invalid-query :path [:group-by]}))
        _group-sort-ambiguous (when (and group-field order-by)
                                (invalid! "Use group ordering or row ordering in separate steps"
                                          {:type ::invalid-query :path [:order-by :group-by]}))
        _valid-window (doseq [k [:take :drop]]
            (when (and (contains? query k)
                       (not (and (integer? (get query k)) (not (neg? (get query k))))))
              (invalid! "Window counts must be non-negative integers"
                        {:type ::invalid-window :path [k] :value (get query k)})))
        requires-start? (some #{:days-from-start :month-index} (keys derived))
        _requires-start (when (and requires-start? (nil? from))
                          (invalid! "This derived field requires :from"
                                    {:type ::invalid-query :path [:from]}))
        normalized-select (when select (vec (distinct select)))
        _valid-serializable (when (and (contains? query :serializable?)
                                       (not (boolean? (:serializable? query))))
                              (invalid! ":serializable? must be boolean"
                                        {:type ::invalid-query :path [:serializable?]}))]
    (assoc query :from from :to to :derive derived :select normalized-select)))

(defn- record-with-derived [record derived start]
  (reduce (fn [m field] (assoc m field (derived-value (:date record) field start)))
          record (keys derived)))

(defn compile-query
  "Compile the streaming-compatible query portion to a transducer. Global
  ordering and grouping are intentionally handled by execute, which reports
  their materialization cost in explain."
  [query]
  (let [{:keys [where derive select from to order-by group-by]} (validate-query query)]
    (when (or order-by group-by)
      (invalid! "Global ordering or grouping requires execute, not compile-query"
                {:type ::materialization-required
                 :operations (cond-> [] order-by (conj :order-by) group-by (conj :group-by))}))
    (let [start (or from LocalDate/MIN)
          pred (when where (compile-predicate where))
          serializable? (boolean (:serializable? query))]
      (comp
       (map record-for)
       (filter (fn [{:keys [date]}]
                 (and (or (nil? from) (not (.isBefore ^LocalDate date from)))
                      (or (nil? to) (not (.isAfter ^LocalDate date to)))
                      (or (nil? pred) (pred date)))))
       (map #(record-with-derived % derive start))
       (cond-> identity (:drop query) (comp (drop (:drop query))))
       (cond-> identity (:take query) (comp (take (:take query))))
       (map #(project-record % select serializable?))))))

(defn- compare-values [a b]
  (cond
    (and (instance? LocalDate a) (instance? LocalDate b)) (.compareTo ^LocalDate a ^LocalDate b)
    (and (instance? java.time.YearMonth a) (instance? java.time.YearMonth b))
    (.compareTo ^java.time.YearMonth a ^java.time.YearMonth b)
    :else (compare a b)))

(defn execute
  "Execute a query over date or date-record input. Global order-by materializes;
  use explain to inspect that requirement before choosing an input size."
  ([query] (let [{:keys [from to]} (validate-query query)]
             (when-not (and from to)
               (invalid! "One-argument execute requires :from and :to"
                         {:type ::invalid-query :path [:from :to]}))
             (execute query (dates/reducible-date-range from to))))
  ([query input]
   (let [{:keys [order-by group-by aggregate select]} (validate-query query)
         serializable? (boolean (:serializable? query))
         streaming-query (assoc (dissoc query :order-by :group-by :aggregate :select)
                               :serializable? false)
         xf (compile-query streaming-query)]
     (cond
       group-by (let [rows (into [] xf input)
                      groups (clojure.core/group-by #(get % group-by) rows)
                      aggregate-group (fn [records]
                                        (if-not (seq aggregate)
                                          (mapv #(project-record % select serializable?) records)
                                          (reduce
                                           (fn [result op]
                                             (assoc result op
                                                    (case op
                                                      :count (count records)
                                                      :first-date (let [d (:date (first records))]
                                                                    (if serializable? (str d) d))
                                                      :last-date (let [d (:date (last records))]
                                                                   (if serializable? (str d) d))
                                                      :min-day (reduce min (map #(.getDayOfMonth ^LocalDate (:date %)) records))
                                                      :max-day (reduce max (map #(.getDayOfMonth ^LocalDate (:date %)) records)))))
                                           (sorted-map) aggregate)))]
                  (into (sorted-map-by compare-values)
                        (map (fn [[k records]]
                               [(if (and serializable?
                                         (or (instance? LocalDate k)
                                             (instance? java.time.YearMonth k)))
                                  (str k) k)
                                (aggregate-group records)])
                             groups)))
       order-by (let [rows (into [] xf input)
                      terms (vec order-by)]
                  (vec (sort (fn [a b]
                               (loop [[[field direction] & more] terms]
                                 (if-not field 0
                                   (let [c (compare-values (get a field) (get b field))
                                         c (if (= direction :desc) (- c) c)]
                                     (if (zero? c) (recur more) c)))))
                             rows)))
       :else (eduction xf input)))))

(defn explain
  "Return a deterministic EDN map describing the normalized execution plan."
  [query]
  (let [{:keys [from to where derive select order-by group-by take drop] :as q}
        (validate-query query)
        duplicate-select? (and (:select query)
                               (not= (count (:select query))
                                     (count (distinct (:select query)))))
        steps (cond-> [] where (conj {:op :filter :predicate where})
                (seq derive) (conj {:op :derive :fields (vec (sort-by str (keys derive)))})
                (or take drop) (conj {:op :window :take take :drop drop})
                select (conj {:op :select :fields select})
                order-by (conj {:op :sort :fields order-by})
                group-by (conj {:op :group :field group-by
                                :aggregates (vec (sort-by str (:aggregate q)))}))
        materialize? (boolean (or order-by group-by))]
    {:source (cond-> {:type :date-range}
               from (assoc :from (str from))
               to (assoc :to (str to)))
     :plan steps
     :streaming? (not materialize?)
     :materialization-required? materialize?
     :estimated-materialization (if materialize? :all-matching-records :none)
     :optimizations (if duplicate-select? [:remove-duplicate-selections] [])
     :options (select-keys q [:serializable?])}))

(defn- period-key [period record]
  (let [^LocalDate d (query-date record)]
    (case period
      :date d
      :day d
      :month (java.time.YearMonth/from d)
      :quarter [(.getYear d) (inc (quot (dec (.getMonthValue d)) 3))]
      :year (.getYear d)
      :iso-week [(.get d IsoFields/WEEK_BASED_YEAR)
                 (.get d IsoFields/WEEK_OF_WEEK_BASED_YEAR)]
      (invalid! "Unknown calendar window period"
                {:type ::invalid-window :period period}))))

(defn calendar-windows
  "Return a transducer that emits vectors of consecutive records grouped by
  :date, :month, :quarter, :year, or :iso-week. Input order is preserved."
  [period]
  (when-not (#{:date :day :month :quarter :year :iso-week} period)
    (invalid! "Unknown calendar window period"
              {:type ::invalid-window :period period}))
  (fn [rf]
    (let [window-key (volatile! ::none)
          items (volatile! [])]
      (fn
        ([] (rf))
        ([result]
         (let [result (if (seq @items) (rf result @items) result)
               result (if (reduced? result) @result result)]
           (rf result)))
        ([result input]
         (let [k (period-key period input)]
           (if (or (= ::none @window-key) (= k @window-key))
             (do (when (= ::none @window-key) (vreset! window-key k))
                 (vswap! items conj input)
                 result)
             (let [next-result (rf result @items)]
               (if (reduced? next-result)
                 next-result
                 (do (vreset! window-key k)
                     (vreset! items [input])
                     next-result))))))))))

(defn annotate-boundaries
  "Return a transducer that adds calendar boundary flags to date records."
  []
  (map (fn [record]
         (let [^LocalDate d (query-date record)
               m (.getMonthValue d)
               day (.getDayOfMonth d)
               quarter-start? (and (#{1 4 7 10} m) (= 1 day))
               quarter-end? (and (#{3 6 9 12} m) (= (.lengthOfMonth d) day))]
           (assoc (if (map? record) record {:date d})
                  :date d
                  :month-start? (= 1 day)
                  :month-end? (= day (.lengthOfMonth d))
                  :quarter-start? quarter-start?
                  :quarter-end? quarter-end?
                  :year-start? (and (= 1 m) (= 1 day))
                  :year-end? (and (= 12 m) (= 31 day))
                  :leap-day? (and (= 2 m) (= 29 day)))))))

(defn read-query
  "Read query data from EDN text using clojure.edn/read-string. No tagged
  readers are enabled. Callers should impose input-size limits at boundaries."
  [s]
  (when-not (string? s)
    (invalid! "EDN query must be text" {:type ::invalid-query :value s}))
  (try
    (edn/read-string {:readers {} :default (fn [tag value]
                                             (invalid! "Tagged literals are not allowed"
                                                       {:type ::invalid-edn-tag :tag tag :value value}))}
                     s)
    (catch clojure.lang.ExceptionInfo e (throw e))
    (catch Exception e
      (throw (ex-info "Could not read EDN query" {:type ::invalid-edn} e)))))

(def date-range dates/date-range)
(def reducible-date-range dates/reducible-date-range)
(def month-range dates/month-range)
(def year-month dates/year-month)
(def year-month? dates/year-month?)
(def parse-year-month dates/parse-year-month)
(def format-year-month dates/format-year-month)
(def month-start dates/month-start)
(def month-end dates/month-end)
(def shift-month dates/shift-month)
(def project-month dates/project-month)
(def blank-grid dates/blank-grid)
