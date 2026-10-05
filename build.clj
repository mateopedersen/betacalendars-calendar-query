(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'net.clojars.mateopedersen/calendar-query)
(def version "0.1.0")
(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name lib) version))
(def basis (delay (b/create-basis {:project "deps.edn"})))

(def pom-options
  {:pom-data
   [[:name "BetaCalendars Calendar Query"]
    [:description "Declarative, composable calendar data queries for Clojure"]
    [:url "https://www.betacalendars.com/"]
    [:licenses
     [:license
      [:name "MIT License"]
      [:url "https://opensource.org/license/mit/"]
      [:distribution "repo"]]]
    [:scm
     [:url "https://github.com/mateopedersen/betacalendars-calendar-query"]
     [:connection "scm:git:git://github.com/mateopedersen/betacalendars-calendar-query.git"]
     [:developerConnection "scm:git:ssh://git@github.com/mateopedersen/betacalendars-calendar-query.git"]
     [:tag "v0.1.0"]]]})

(defn clean [_]
  (b/delete {:path "target"})
  (b/delete {:path "pom.xml"}))

(defn jar [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis @basis
                :src-dirs ["src"]
                :pom-data (:pom-data pom-options)})
  (b/copy-file {:src (b/pom-path {:lib lib :class-dir class-dir})
                :target "pom.xml"})
  (b/copy-dir {:src-dirs ["src"] :target-dir class-dir})
  (b/jar {:class-dir class-dir :jar-file jar-file})
  {:jar jar-file :coordinate (str lib) :version version})

(defn ci [_]
  (clean nil)
  (doseq [args [["clojure" "-M:test"]
                ["clojure" "-M:lint"]
                ["bash" "-lc" "test -f doc/cljdoc.edn && grep -q 'doc/cljdoc.edn' .github/workflows/ci.yml"]]]
    (let [{:keys [exit]} (b/process {:command-args args})]
      (when-not (zero? exit)
        (throw (ex-info "Quality gate failed" {:command args :exit exit})))))
  (jar nil))
