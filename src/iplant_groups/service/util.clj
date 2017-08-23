(ns iplant_groups.service.util
  (:require [clojure-commons.exception-util :as cxu]))

(defn verify-not-removing-own-privileges [user subjects]
  (when ((set subjects) user)
    (cxu/bad-request "Updating your own privileges using this service is not permitted.")))
