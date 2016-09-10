(use '[clojure.java.shell :only (sh)])
(require '[clojure.string :as string])

(defn git-ref
  []
  (or (System/getenv "GIT_COMMIT")
      (string/trim (:out (sh "git" "rev-parse" "HEAD")))
      ""))

(defproject org.cyverse/iplant-groups "2.8.1-SNAPSHOT"
  :description "A REST front-end for Grouper."
  :url "https://github.com/cyverse-de/iplant-groups"
  :license {:name "BSD"
            :url "http://iplantcollaborative.org/sites/default/files/iPLANT-LICENSE.txt"}
  :manifest {"Git-Ref" ~(git-ref)}
  :uberjar-name "iplant-groups-standalone.jar"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [cheshire "5.5.0"]
                 [clj-http "2.0.0"]
                 [clj-time "0.10.0"]
                 [com.cemerick/url "0.1.1"]
                 [medley "0.7.0"]
                 [metosin/compojure-api "0.24.5"]
                 [me.raynes/fs "1.4.6"]
                 [org.cyverse/clojure-commons "2.8.1-SNAPSHOT"]
                 [org.cyverse/common-cfg "2.8.0"]
                 [org.cyverse/common-cli "2.8.0"]
                 [org.cyverse/common-swagger-api "2.8.1-SNAPSHOT"]
                 [org.cyverse/service-logging "2.8.0"]
                 [ring/ring-core "1.4.0"]
                 [ring/ring-jetty-adapter "1.4.0"]]
  :eastwood {:exclude-namespaces [:test-paths]
             :linters [:wrong-arity :wrong-ns-form :wrong-pre-post :wrong-tag :misplaced-docstrings]}
  :plugins [[lein-ring "0.9.6"]
            [jonase/eastwood "0.2.3"]
            [test2junit "1.1.3"]]
  :profiles {:dev {:resource-paths ["conf/test"]}}
  ;; compojure-api route macros should not be AOT compiled:
  ;; https://github.com/metosin/compojure-api/issues/135#issuecomment-121388539
  ;; https://github.com/metosin/compojure-api/issues/102
  :aot [#"iplant_groups.(?!routes).*"]
  :main iplant_groups.core
  :ring {:handler iplant_groups.routes/app
         :init    iplant_groups.core/init-service
         :port    31310}
  :uberjar-exclusions [#"(?i)META-INF/[^/]*[.](SF|DSA|RSA)"]
  :jvm-opts ["-Dlogback.configurationFile=/etc/iplant/de/logging/iplant-groups-logging.xml"])
