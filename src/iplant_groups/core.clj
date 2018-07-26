(ns iplant-groups.core
  (:gen-class)
  (:require [iplant-groups.routes :as routes]
            [iplant-groups.util.config :as config]
            [iplant-groups.events :as events]
            [iplant-groups.amqp :as amqp]
            [me.raynes.fs :as fs]
            [clj-http.client :as http]
            [clojure.tools.logging :as log]
            [common-cli.core :as ccli]
            [service-logging.thread-context :as tc]))

(defn init-service
  ([]
     (init-service config/default-config-file))
  ([config-path]
     (config/load-config-from-file config-path)))

(defn cli-options
  []
  [["-c" "--config PATH" "Path to the config file"
    :default config/default-config-file
    :validate [#(fs/exists? %) "The config file does not exist."
               #(fs/readable? %) "The config file is not readable."]]
   ["-v" "--version" "Print out the version number."]
   ["-h" "--help"]])

(defn listen-for-events
  []
  (let [exchange-cfg (events/exchange-config)
        queue-cfg    (events/queue-config)]
    (amqp/connect exchange-cfg queue-cfg {"events.iplant-groups.ping" events/ping-handler})))

(defn run-jetty
  []
  (require 'ring.adapter.jetty)
  (log/warn "Started listening on" (config/listen-port))
  ((eval 'ring.adapter.jetty/run-jetty) routes/app {:port (config/listen-port)}))

(defn -main
  [& args]
  (tc/with-logging-context config/svc-info
    (let [{:keys [options arguments errors summary]} (ccli/handle-args config/svc-info args cli-options)]
      (init-service (:config options))
      (.start (Thread. listen-for-events))
      (http/with-connection-pool {:timeout 5 :threads 10 :insecure? false :default-per-route 10}
        (run-jetty)))))
