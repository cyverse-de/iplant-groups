(use '[clojure.java.shell :only (sh)])
(require '[clojure.string :as string])

(defn git-ref
  []
  (or (System/getenv "GIT_COMMIT")
      (string/trim (:out (sh "git" "rev-parse" "HEAD")))
      ""))

(defproject org.cyverse/iplant-groups "3.0.1-SNAPSHOT"
  :description "A REST front-end for Grouper."
  :url "https://github.com/cyverse-de/iplant-groups"
  :license {:name "BSD"
            :url "https://cyverse.org/license"}
  :manifest {"Git-Ref" ~(git-ref)}
  :uberjar-name "iplant-groups-standalone.jar"
  :dependencies [[org.clojure/clojure "1.11.3"]
                 [cheshire "5.13.0"]
                 [clj-http "3.13.0"]
                 [clj-time "0.15.2"]
                 [com.cemerick/url "0.1.1" :exclusions [com.cemerick/clojurescript.test]]
                 [medley "1.4.0"]
                 [metosin/compojure-api "1.1.14"]
                 [me.raynes/fs "1.4.6"]
                 [org.cyverse/clojure-commons "3.0.9"]
                 [org.cyverse/common-cfg "2.8.3"]
                 [org.cyverse/common-cli "2.8.2"]
                 [org.cyverse/common-swagger-api "3.4.5"]
                 [org.cyverse/event-messages "0.0.1"]
                 [org.cyverse/service-logging "2.8.4"]
                 [com.novemberain/langohr "5.4.0"]
                 [ring/ring-core "1.12.2"]
                 [ring/ring-jetty-adapter "1.12.2"]]
  :eastwood {:exclude-linters [:unlimited-use]}
  :plugins [[jonase/eastwood "1.4.3"]
            [lein-ancient "0.7.0"]
            [lein-ring "0.12.6"]
            [test2junit "1.4.4"]]
  :profiles {:dev {:resource-paths ["conf/test"]}
             :uberjar {:aot :all}}
  :main ^:skip-aot iplant-groups.core
  :ring {:handler iplant-groups.routes/app
         :init    iplant-groups.core/init-service
         :port    31310}
  :uberjar-exclusions [#"(?i)META-INF/[^/]*[.](SF|DSA|RSA)"]
  :jvm-opts ["-Dlogback.configurationFile=/etc/iplant/de/logging/iplant-groups-logging.xml"])
