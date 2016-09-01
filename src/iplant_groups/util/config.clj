(ns iplant_groups.util.config
  (:use [slingshot.slingshot :only [throw+]])
  (:require [clojure-commons.config :as cc]
            [clojure-commons.error-codes :as ce]))

(def default-config-file "/etc/iplant/de/iplant-groups.properties")

(def docs-uri "/docs")

(def svc-info
  {:desc     "RESTful facade for the Grouper API."
   :app-name "iplant-groups"
   :group-id "org.iplantc"
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
