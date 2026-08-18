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
  ;; Fail the build on a new dependency conflict rather than printing a
  ;; warning nobody reads.
  :pedantic? :abort
  ;; Records versions Leiningen already resolves, read off the resolved
  ;; classpath rather than copied from lein's "Consider using these
  ;; :managed-dependencies" hint -- that hint names the version that LOST the
  ;; conflict, so pasting it would be a silent upgrade.
  ;;
  ;; The jackson-* entries align a family the cheshire 5 -> 6 upgrade split
  ;; (core/cbor/smile moved to 2.21.1, databind/annotations stayed at 2.18.3).
  ;; On main it was coherent at 2.17.x. Jackson needs these to move together;
  ;; :pedantic? cannot see the split because each artifact is individually
  ;; unambiguous.
  :managed-dependencies [[com.fasterxml.jackson.core/jackson-annotations "2.21"]
                         [com.fasterxml.jackson.core/jackson-databind "2.21.1"]
                         [commons-codec "1.16.1"]
                         [prismatic/schema "1.1.12"]]
  :dependencies [[org.clojure/clojure "1.12.5"]
                 [cheshire "6.2.0"]
                 [clj-http "3.13.1"]
                 [clj-time "0.15.2"]
                 [com.cemerick/url "0.1.1" :exclusions [com.cemerick/clojurescript.test]]
                 [medley "1.4.0"]
                 [metosin/compojure-api "1.1.14"]
                 [me.raynes/fs "1.4.6"]
                 [org.cyverse/clojure-commons "3.0.13"]
                 [org.cyverse/common-cfg "2.8.4"]
                 [org.cyverse/common-cli "2.8.3"]
                 [org.cyverse/common-swagger-api "3.4.23"]
                 [org.cyverse/event-messages "0.0.1"]
                 [org.cyverse/service-logging "2.8.6"]
                 [com.novemberain/langohr "5.6.0"]
                 [ring/ring-core "1.12.2"]
                 [ring/ring-jetty-adapter "1.12.2"]]
  :eastwood {:exclude-linters [:unlimited-use]}
  :plugins [[jonase/eastwood "1.4.3"]
            [lein-ancient "1.0.0"]
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
