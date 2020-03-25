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
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [cheshire "5.10.0"]
                 [clj-http "2.0.0"]
                 [clj-time "0.15.2"]
                 [com.cemerick/url "0.1.1" :exclusions [com.cemerick/clojurescript.test]]
                 [medley "1.3.0"]
                 [metosin/compojure-api "1.1.13"]
                 [me.raynes/fs "1.4.6"]
                 [org.cyverse/clojure-commons "3.0.5"]
                 [org.cyverse/common-cfg "2.8.1"]
                 [org.cyverse/common-cli "2.8.1"]
                 [org.cyverse/common-swagger-api "3.0.0"]
                 [org.cyverse/event-messages "0.0.1"]
                 [org.cyverse/service-logging "2.8.2"]
                 [com.novemberain/langohr "3.5.1"]
                 [ring/ring-core "1.8.0"]
                 [ring/ring-jetty-adapter "1.8.0"]]
  :eastwood {:exclude-namespaces [:test-paths]
             :linters [:wrong-arity :wrong-ns-form :wrong-pre-post :wrong-tag :misplaced-docstrings]}
  :plugins [[lein-ring "0.12.5"]
            [jonase/eastwood "0.3.11"]
            [test2junit "1.1.3"]]
  :profiles {:dev {:resource-paths ["conf/test"]}
             :uberjar {:aot :all}}
  :main ^:skip-aot iplant-groups.core
  :ring {:handler iplant-groups.routes/app
         :init    iplant-groups.core/init-service
         :port    31310}
  :uberjar-exclusions [#"(?i)META-INF/[^/]*[.](SF|DSA|RSA)"]
  :jvm-opts ["-Dlogback.configurationFile=/etc/iplant/de/logging/iplant-groups-logging.xml"])
