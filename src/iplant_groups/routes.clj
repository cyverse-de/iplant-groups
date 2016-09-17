(ns iplant_groups.routes
  (:use [service-logging.middleware :only [wrap-logging add-user-to-context clean-context]]
        [clojure-commons.query-params :only [wrap-query-params]]
        [compojure.core :only [wrap-routes]]
        [common-swagger-api.schema]
        [ring.middleware.keyword-params :only [wrap-keyword-params]])
  (:require [compojure.route :as route]
            [cheshire.core :as json]
            [iplant_groups.routes.folders :as folder-routes]
            [iplant_groups.routes.groups :as group-routes]
            [iplant_groups.routes.status :as status-routes]
            [iplant_groups.routes.subjects :as subject-routes]
            [iplant_groups.routes.attributes :as attribute-routes]
            [iplant_groups.util.config :as config]
            [clojure-commons.exception :as cx]))

(defapi app
  {:exceptions cx/exception-handlers}
  (swagger-routes
    {:ui      config/docs-uri
     :options {:ui {:validatorUrl nil}}
     :data    {:info {:title       "RESTful Service Facade for Grouper"
                      :description "Documentation for the iplant-groups API"
                      :version     "2.8.0"}
               :tags [{:name "folders", :description "Folder Information"}
                      {:name "groups", :description "Group Information"}
                      {:name "service-info", :description "Service Status Information"}
                      {:name "subjects", :description "Subject Information"}
                      {:name "attributes", :description "Attribute/Permission Information"}]}})
  (middleware
   [clean-context
    wrap-keyword-params
    wrap-query-params
    [wrap-routes wrap-logging]]
   (context "/" []
    :tags ["service-info"]
    status-routes/status))
  (middleware
   [clean-context
    wrap-keyword-params
    wrap-query-params
    add-user-to-context
    wrap-logging]
   (context "/folders" []
    :tags ["folders"]
    folder-routes/folders)
   (context "/groups" []
    :tags ["groups"]
    group-routes/groups)
   (context "/subjects" []
    :tags ["subjects"]
    subject-routes/subjects)
   (context "/attributes" []
    :tags ["attributes"]
    attribute-routes/attributes)
   (undocumented (route/not-found (json/encode {:success false :msg "unrecognized service path"})))))
