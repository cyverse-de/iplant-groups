(ns iplant-groups.amqp
  (:require [clojure.tools.logging :as log]
            [iplant-groups.util.config :as config]
            [langohr.core :as rmq]
            [langohr.channel :as lch]
            [langohr.basic :as lb]
            [service-logging.thread-context :as tc]
            [slingshot.slingshot :refer [try+]]
            [cheshire.core :as cheshire]))

(defn publish-msg
  [routing-key msg]
  (try+
    (let [timeNow (new java.util.Date)
          connection (rmq/connect {:uri (config/amqp-uri)})
          channel (lch/open connection)]
      (tc/with-logging-context
        {:amqp-routing-key routing-key
         :amqp-message msg}
        (log/info (format "Publishing AMQP message. routing-key=%s" routing-key)))
      (lb/publish channel
                  (config/exchange-name)
                  routing-key
                  (cheshire/encode {:message      msg
                                    :timestamp_ms (.getTime timeNow)})
                  {:content-type "application/json"
                   :timestamp    timeNow})

      (lch/close channel)
      (rmq/close connection))
    (catch Object _
      (log/error (:throwable &throw-context) "Failed to publish message" (cheshire/encode msg)))))
