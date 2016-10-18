(ns iplant_groups.util.config
  (:use [slingshot.slingshot :only [throw+]])
  (:require [clojure-commons.config :as cc]
            [clojure-commons.error-codes :as ce]))

(def default-config-file "/etc/iplant/de/iplant-groups.properties")

(def docs-uri "/docs")

(def svc-info
  {:desc     "RESTful facade for the Grouper API."
   :app-name "iplant-groups"
   :group-id "org.cyverse"
   :art-id   "iplant-groups"
   :service  "iplant-groups"})

(def ^:private props
  "A ref for storing the configuration properties."
  (ref nil))

(def ^:private config-valid
  "A ref for storing a configuration validity flag."
  (ref true))

(def ^:private configs
  "A ref for storing the symbols used to get configuration settings."
  (ref []))

(cc/defprop-optint listen-port
  "The port that iplant-groups listens on."
  [props config-valid configs]
  "iplant-groups.app.listen-port" 60000)

(cc/defprop-optstr grouper-base
  "The base URL to use when connecting to the Grouper API."
  [props config-valid configs]
  "iplant-groups.grouper.base-url" "http://grouper:60000/grouper-ws/")

(cc/defprop-optstr grouper-api-version
  "The Grouper REST API version used by this facade."
  [props config-valid configs]
  "iplant-groups.grouper.api-version" "v2_2_000")

(cc/defprop-optstr grouper-username
  "The username to use when authenticating to Grouper."
  [props config-valid configs]
  "iplant-groups.grouper.username" "GrouperSystem")

(cc/defprop-optstr grouper-password
  "The password to use when authenticating to Grouper."
  [props config-valid configs]
  "iplant-groups.grouper.password" "notprod")

(cc/defprop-optstr amqp-uri
  "The URI to use to establish AMQP connections."
  [props config-valid configs]
  "iplant-groups.amqp.uri" "amqp://guest:guest@rabbit:5672/")

(cc/defprop-optstr exchange-name
  "The name of AMQP exchange to connect to."
  [props config-valid configs]
  "iplant-groups.amqp.exchange.name" "de")

(cc/defprop-optboolean exchange-durable?
  "Whether or not the AMQP exchange is durable."
  [props config-valid configs]
  "iplant-groups.amqp.exchange.durable" true)

(cc/defprop-optboolean exchange-auto-delete?
  "Whether or not the AMQP exchange is auto-deleted."
  [props config-valid configs]
  "iplant-groups.amqp.exchange.auto-delete" false)

(cc/defprop-optstr queue-name
  "The name of the AMQP queue that is used for clockwork."
  [props config-valid configs]
  "iplant-groups.amqp.queue.name" "events.iplant-groups.queue")

(cc/defprop-optboolean queue-durable?
  "Whether or not the AMQP queue is durable."
  [props config-valid configs]
  "iplant-groups.amqp.queue.durable" true)

(cc/defprop-optboolean queue-auto-delete?
  "Whether or not to delete the AMQP queue."
  [props config-valid configs]
  "iplant-groups.amqp.queue.auto-delete" false)

(defn- validate-config
  "Validates the configuration settings after they've been loaded."
  []
  (when-not (cc/validate-config configs config-valid)
    (throw+ {:type :clojure-commons.exception/invalid-cfg})))

(defn load-config-from-file
  "Loads the configuration settings from a file."
  [cfg-path]
  (cc/load-config-from-file cfg-path props)
  (cc/log-config props)
  (validate-config))
