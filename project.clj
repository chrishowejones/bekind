(defproject bekind "0.1.0-SNAPSHOT"
  :description "Utility for retries"
  :url "http://github.com/chrishowejones"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [com.taoensso/timbre "4.10.0"]]
  :profiles {:dev {:dependencies [[org.clojure/core.async "0.3.443"]]}})
