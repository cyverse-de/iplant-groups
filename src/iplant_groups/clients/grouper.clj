(ns iplant-groups.clients.grouper
  (:use [medley.core :only [distinct-by map-kv remove-vals]]
        [slingshot.slingshot :only [throw+ try+]])
  (:require [cemerick.url :as curl]
            [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.set :as set]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojure-commons.error-codes :as ce]
            [iplant-groups.util.config :as config]
            [iplant-groups.util.service :as service]))

(def ^:private content-type "text/x-json")

(def ^:private default-act-as-subject-id "GrouperSystem")

(def ^:private all-group-privileges
  #{"view" "read" "update" "admin" "optin" "optout" "groupAttrRead" "groupAttrUpdate"})

(def ^:private all-folder-privileges
  #{"create" "stem" "stemAttrRead" "stemAttrUpdate"})

(defn- auth-params
  []
  (vector (config/grouper-username) (config/grouper-password)))

(defn- build-error-object
  [error-code body]
  (let [result-metadata (:resultMetadata (val (first body)))]
    {:error_code             error-code
     :grouper_result_code    (:resultCode result-metadata)
     :grouper_result_message (:resultMessage result-metadata)}))

(defn- default-error-handler
  [error-code {:keys [body] :as response}]
  (log/warn "Grouper request failed:" response)
  (throw+ (build-error-object error-code (service/parse-json body))))

(defmacro ^:private with-trap
  [[handle-error] & body]
  `(try+
    (do ~@body)
    (catch [:status 400] bad-request#
      (~handle-error ce/ERR_BAD_REQUEST bad-request#))
    (catch [:status 404] not-found#
      (~handle-error ce/ERR_NOT_FOUND not-found#))
    (catch [:status 500] server-error#
      (~handle-error ce/ERR_REQUEST_FAILED server-error#))))

(defn- grouper-uri
  [& components]
  (str (apply curl/url (config/grouper-base) "servicesRest" (config/grouper-api-version)
              (mapv curl/url-encode components))))

(defn- grouper-post
  [body & uri-parts]
  (->> {:body         (json/encode body)
        :basic-auth   (auth-params)
        :content-type content-type
        :as           :json}
       (http/post (apply grouper-uri uri-parts))
       (:body)))

(defn- grouper-put-no-exceptions
  [body & uri-parts]
  (json/decode (->> {:body             (json/encode body)
                     :basic-auth       (auth-params)
                     :content-type     content-type
                     :throw-exceptions false}
                    (http/put (apply grouper-uri uri-parts))
                    (:body))
               true))

(defn- grouper-put
  [body & uri-parts]
  (->> {:body         (json/encode body)
        :basic-auth   (auth-params)
        :content-type content-type
        :as           :json}
       (http/put (apply grouper-uri uri-parts))
       (:body)))

(defn grouper-ok?
  []
  (try+
   (http/get (str (curl/url (config/grouper-base) "status"))
             {:query-params   {:diagnosticType "sources"}
              :socket-timeout 10000
              :conn-timeout   10000})
   true
   (catch Object err
     (log/warn "Grouper diagnostic check failed:" err)
     false)))

(defn- act-as-subject-lookup
  ([username]
     {:subjectId (or username default-act-as-subject-id)})
  ([]
     (act-as-subject-lookup default-act-as-subject-id)))

(defn- coerce-boolean
  [bool-str]
  (when bool-str (Boolean/parseBoolean bool-str)))

(defn- role-lookup
  [role-name]
  (when-not (nil? role-name)
    {:groupName role-name}))

(defn- role-lookups
  [role-names]
  (when-not (every? nil? role-names)
    (mapv role-lookup (remove nil? role-names))))

(defn- subject-lookup
  [subject-id]
  (when-not (nil? subject-id)
    {:subjectId subject-id}))

(defn- subject-lookups
  [subject-ids]
  (when-not (every? nil? subject-ids)
    (mapv subject-lookup (remove nil? subject-ids))))

(defn- name-lookup
  [name]
  (when-not (nil? name)
    {:name name}))

(defn- name-lookups
  [names]
  (when-not (every? nil? names)
    (mapv name-lookup (remove nil? names))))

(defn- uuid-lookup
  [uuid]
  (when-not (nil? uuid)
    {:uuid uuid}))

(defn- uuid-lookups
  [uuids]
  (when-not (every? nil? uuids)
    (mapv uuid-lookup (remove nil? uuids))))

;; Group search.

(defn- group-search-query-filter
  [stem name]
  (remove-vals nil? {:groupName       name
                     :queryFilterType "FIND_BY_GROUP_NAME_APPROXIMATE"
                     :stemName        stem}))

(defn- format-group-search-request
  [username stem name details]
  {:WsRestFindGroupsRequest
   {:actAsSubjectLookup (act-as-subject-lookup username)
    :wsQueryFilter      (group-search-query-filter stem name)
    :includeGroupDetail (if details "T" "F")}})

(defn group-search
  [username stem name details]
  (with-trap [default-error-handler]
    (-> (format-group-search-request username stem name details)
        (grouper-post "groups")
        :WsFindGroupsResults
        :groupResults)))

;; Group retrieval.

(defn- group-retrieval-query-filter
  [group-name]
  (remove-vals nil? {:groupName       group-name
                     :queryFilterType "FIND_BY_GROUP_NAME_EXACT"}))

(defn- format-group-retrieval-request
  [username group-name]
  {:WsRestFindGroupsRequest
   {:actAsSubjectLookup (act-as-subject-lookup username)
    :wsQueryFilter      (group-retrieval-query-filter group-name)
    :includeGroupDetail "T"}})

(defn get-group
  [username group-name]
  (with-trap [default-error-handler]
    (-> (format-group-retrieval-request username group-name)
        (grouper-post "groups")
        :WsFindGroupsResults
        :groupResults
        first)))

(defn- format-group-retrieval-by-id-request
  [username group-id]
  {:WsRestFindGroupsRequest
   {:actAsSubjectLookup   (act-as-subject-lookup username)
    :wsGroupLookups       [{:uuid group-id}]
    :includeGroupDetail   "T"}})

(defn get-group-by-id
  [username group-id]
  (with-trap [default-error-handler]
    (-> (format-group-retrieval-by-id-request username group-id)
        (grouper-post "groups")
        :WsFindGroupsResults
        :groupResults
        first)))

;; Group add/update

(defn- format-group-add-update-request
  [group-lookup update? username type name display-extension description]
  {:WsRestGroupSaveRequest
   {:actAsSubjectLookup (act-as-subject-lookup username)
    :wsGroupToSaves [
     {:wsGroup
      (remove-vals nil? {:name name
                         :description description
                         :displayExtension display-extension
                         :typeOfGroup type})
      :wsGroupLookup group-lookup
      :saveMode (if update? "UPDATE" "INSERT")}]
    :includeGroupDetail "T"}})

(defn- format-group-add-request
  [username type name display-extension description]
  (format-group-add-update-request
    {:groupName name}
    false username type name display-extension description))

(defn- format-group-update-request
  [username original-name new-name display-extension description]
  (format-group-add-update-request
    {:groupName original-name}
    true username nil new-name display-extension description)) ;; nil is for 'type' which we shouldn't change for now

(defn- add-update-group
  [request-body]
  (with-trap [default-error-handler]
    (-> (grouper-post request-body "groups")
        :WsGroupSaveResults
        :results
        first
        :wsGroup)))

(defn add-group
  [username type name display-extension description]
  (add-update-group
    (format-group-add-request username type name display-extension description)))

(defn update-group
  [username original-name new-name display-extension description]
  (add-update-group
    (format-group-update-request username original-name new-name display-extension description)))

;; Group delete

(defn- format-group-delete-request
  [username group-name]
  {:WsRestGroupDeleteRequest
   {:actAsSubjectLookup (act-as-subject-lookup username)
    :wsGroupLookups [
     {:groupName group-name}]}})

(defn delete-group
  [username group-name]
  (with-trap [default-error-handler]
    (-> (format-group-delete-request username group-name)
        (grouper-post "groups")
        :WsGroupDeleteResults
        :results
        first
        :wsGroup)))

;; Group membership listings.

(defn- group-membership-listing-error-handler
  [group error-code {:keys [body] :as response}]
  (log/warn "Grouper request failed:" response)
  (let [body    (service/parse-json body)
        get-grc (fn [m] (-> m :WsGetMembersResults :results first :resultMetadata :resultCode))]
    (if (and (= error-code ce/ERR_REQUEST_FAILED) (= (get-grc body) "GROUP_NOT_FOUND"))
      (service/not-found "group" group)
      (throw+ (build-error-object error-code body)))))

(defn- format-member-filter [member-filter]
  (string/capitalize (or member-filter "immediate")))

(defn- format-group-member-by-id-listing-request
  [username group-id member-filter]
  {:WsRestGetMembersRequest
   {:actAsSubjectLookup   (act-as-subject-lookup username)
    :memberFilter         (format-member-filter member-filter)
    :includeSubjectDetail "T"
    :includeGroupDetail   "T"
    :wsGroupLookups       [{:uuid group-id}]}})

(defn get-group-members-by-id
  [username group-id member-filter]
  (with-trap [(partial group-membership-listing-error-handler group-id)]
    (let [response (-> (format-group-member-by-id-listing-request username group-id member-filter)
                       (grouper-post "groups")
                       :WsGetMembersResults)]
      [(:wsSubjects (first (:results response))) (:subjectAttributeNames response)])))

(defn- format-group-member-listing-request
  [username group-name member-filter]
  {:WsRestGetMembersRequest
   {:actAsSubjectLookup   (act-as-subject-lookup username)
    :memberFilter         (format-member-filter member-filter)
    :includeSubjectDetail "T"
    :includeGroupDetail   "T"
    :wsGroupLookups       [{:groupName group-name}]}})

(defn get-group-members
  [username group-name member-filter]
  (with-trap [(partial group-membership-listing-error-handler group-name)]
    (let [response (-> (format-group-member-listing-request username group-name member-filter)
                       (grouper-post "groups")
                       :WsGetMembersResults)]
      [(:wsSubjects (first (:results response))) (:subjectAttributeNames response)])))

;; General functions for formatting group membership update requests.

(defn- format-group-member-update-request
  [replace-all? username subject-ids]
  {:WsRestAddMemberRequest
   {:actAsSubjectLookup (act-as-subject-lookup username)
    :replaceAllExisting (if replace-all? "T" "F")
    :subjectLookups     (subject-lookups subject-ids)}})

(defn- format-group-member-removal-request
  [username subject-ids]
  {:WsRestDeleteMemberRequest
   {:actAsSubjectLookup (act-as-subject-lookup username)
    :subjectLookups     (subject-lookups subject-ids)}})

;; Replace all group members.

(def ^:private format-member-replacement-request
  (partial format-group-member-update-request true))

(defn replace-group-members
  [username group-name subject-ids]
  (-> (format-member-replacement-request username subject-ids)
      (grouper-put-no-exceptions "groups" group-name "members")
      :WsAddMemberResults
      :results))

;; Add multiple group members.

(def ^:private format-multiple-member-addition-request
  (partial format-group-member-update-request false))

(defn add-group-members
  [username group-name subject-ids]
  (-> (format-multiple-member-addition-request username subject-ids)
      (grouper-put-no-exceptions "groups" group-name "members")
      :WsAddMemberResults
      :results))

;; Remove multiple group members.

(defn remove-group-members
  [username group-name subject-ids]
  (-> (format-group-member-removal-request username subject-ids)
      (grouper-put-no-exceptions "groups" group-name "members")
      :WsDeleteMemberResults
      :results))

;; Add group member.

(defn- format-member-addition-request
  [username subject-id]
  (format-group-member-update-request false username [subject-id]))

(defn add-group-member
  [username group-name subject-id]
  (with-trap [default-error-handler]
    (-> (format-member-addition-request username subject-id)
        (grouper-put "groups" group-name "members"))))

;; Remove group member.

(defn remove-group-member
  [username group-name subject-id]
  (with-trap [default-error-handler]
    (-> (format-group-member-removal-request username [subject-id])
        (grouper-post "groups" group-name "members"))))

;; Folder search.

(defn- folder-search-query-filter
  [name]
  (remove-vals nil? {:stemName            name
                     :stemQueryFilterType "FIND_BY_STEM_NAME_APPROXIMATE"}))

(defn- format-folder-search-request
  [username name]
  {:WsRestFindStemsRequest
   {:actAsSubjectLookup (act-as-subject-lookup username)
    :wsStemQueryFilter  (folder-search-query-filter name)}})

(defn folder-search
  [username name]
  (with-trap [default-error-handler]
    (-> (format-folder-search-request username name)
        (grouper-post "stems")
        :WsFindStemsResults
        :stemResults)))

;; Folder retrieval.

(defn- folder-retrieval-query-filter
  [folder-name]
  {:stemName            folder-name
   :stemQueryFilterType "FIND_BY_STEM_NAME"})

(defn- format-folder-retrieval-request
  [username folder-name]
  {:WsRestFindStemsRequest
   {:actAsSubjectLookup (act-as-subject-lookup username)
    :wsStemQueryFilter  (folder-retrieval-query-filter folder-name)}})

(defn get-folder
  [username folder-name]
  (with-trap [default-error-handler]
    (-> (format-folder-retrieval-request username folder-name)
        (grouper-post "stems")
        :WsFindStemsResults
        :stemResults
        first)))

;; Folder add.

(defn- folder-forbidden-error-handler
  [result-key folder-name error-code {:keys [body] :as response}]
  (log/warn "Grouper request failed:" response)
  (let [body    (service/parse-json body)
        get-grc (fn [m] (-> m result-key :results first :resultMetadata :resultCode))]
    (if (and (= error-code ce/ERR_REQUEST_FAILED) (= (get-grc body) "INSUFFICIENT_PRIVILEGES"))
      (service/forbidden "folder" folder-name)
      (throw+ (build-error-object error-code body)))))

(defn- format-folder-add-update-request
  [stem-lookup update? username name display-extension description]
  {:WsRestStemSaveRequest
   {:actAsSubjectLookup (act-as-subject-lookup username)
    :wsStemToSaves [
     {:wsStem
      (remove-vals nil? {:name name
                         :description description
                         :displayExtension display-extension})
      :wsStemLookup stem-lookup
      :saveMode (if update? "UPDATE" "INSERT")}
    ]}})

(defn- format-folder-add-request
  [username name display-extension description]
  (format-folder-add-update-request
    {:stemName name}
    false username name display-extension description))

(defn- format-folder-update-request
  [username original-name new-name display-extension description]
  (format-folder-add-update-request
    {:stemName original-name}
    true username new-name display-extension description))

(defn- add-update-folder
  [request-body name]
  (with-trap [(partial folder-forbidden-error-handler :WsStemSaveResults name)]
    (-> (grouper-post request-body "stems")
        :WsStemSaveResults
        :results
        first
        :wsStem)))

(defn add-folder
  [username name display-extension description]
  (add-update-folder
    (format-folder-add-request username name display-extension description) name))

(defn update-folder
  [username original-name new-name display-extension description]
  (add-update-folder
    (format-folder-update-request username original-name new-name display-extension description)
    original-name))

;; Folder delete

(defn- format-folder-delete-request
  [username folder-name]
  {:WsRestStemDeleteRequest
   {:actAsSubjectLookup (act-as-subject-lookup username)
    :wsStemLookups [
     {:stemName folder-name}]}})

(defn delete-folder
  [username folder-name]
  (with-trap [(partial folder-forbidden-error-handler :WsStemDeleteResults folder-name)]
    (-> (format-folder-delete-request username folder-name)
        (grouper-post "stems")
        :WsStemDeleteResults
        :results
        first
        :wsStem)))

;; General privilege filtering functions

(defn- filter-privileges-by-subject-source-id
  [privileges {:keys [subject-source-id]}]
  (if subject-source-id
    (filter (fn [priv] (= (:sourceId (:wsSubject priv)) subject-source-id)) privileges)
    privileges))

(defn- filter-privileges-by-inheritance-level
  [privileges {:keys [inheritance-level]}]
  (cond
    (= inheritance-level "immediate")
    (filter (fn [priv] (= (:ownerSubject priv) (:wsSubject priv))) privileges)

    (= inheritance-level "inherited")
    (filter (fn [priv] (not= (:ownerSubject priv) (:wsSubject priv))) privileges)

    :else
    privileges))

(defn- filter-privileges-by-entity-type
  [privileges {:keys [entity-type]}]
  (cond
    (= entity-type "group")
    (filter (fn [priv] (contains? priv :wsGroup)) privileges)

    (= entity-type "folder")
    (filter (fn [priv] (contains? priv :wsStem)) privileges)

    :else
    privileges))

(defn- filter-privileges-by-folder
  [privileges {:keys [folder]}]
  (if folder
    (let [get-name (fn [priv] (or (get-in priv [:wsStem :name]) (get-in priv [:wsGroup :name])))]
      (filter (fn [priv] (string/starts-with? (get-name priv) folder)) privileges))
    privileges))

(defn- filter-privileges [privileges params]
  (-> privileges
      (filter-privileges-by-subject-source-id params)
      (filter-privileges-by-inheritance-level params)
      (filter-privileges-by-entity-type params)
      (filter-privileges-by-folder params)))

;; Get group/folder privileges

;; This is only available as a Lite request; ActAsSubject works differently.
(defn- format-group-folder-privileges-lookup-request
  [entity-type username group-or-folder-name params]
  (if-let [name-key (get {:group  :groupName
                          :folder :stemName}
                         entity-type)]
    {:WsRestGetGrouperPrivilegesLiteRequest
     (remove-vals nil? {:actAsSubjectId       username
                        :includeSubjectDetail "T"
                        :includeGroupDetail   "T"
                        name-key              group-or-folder-name
                        :subjectId            (:subject-id params)
                        :subjectSourceId      (:subject-source-id params)
                        :privilegeName        (:privilege params)})}
    (throw+ {:type :clojure-commons.exception/bad-request :entity-type entity-type})))

(defn- get-group-folder-privileges
  [entity-type username name params]
  (with-trap [default-error-handler]
    (let [response (-> (format-group-folder-privileges-lookup-request entity-type username name params)
                       (grouper-post "grouperPrivileges")
                       :WsGetGrouperPrivilegesLiteResult)]
      [(filter-privileges (:privilegeResults response) params)
       (:subjectAttributeNames response)])))

(defn get-group-privileges
  [username group-name & [params]]
  (get-group-folder-privileges :group username group-name params))

(defn get-folder-privileges
  [username folder-name & [params]]
  (get-group-folder-privileges :folder username folder-name params))

;; Get subject privileges

(defn- format-subject-privileges-lookup-request
  [username subject-id params]
  {:WsRestGetGrouperPrivilegesLiteRequest
   (remove-vals nil? {:actAsSubjectId       username
                      :includeSubjectDetail "T"
                      :includeGroupDetail   "T"
                      :subjectId            subject-id
                      :privilegeName        (:privilege params)})})

(defn get-subject-privileges
  [username subject-id params]
  (with-trap [default-error-handler]
    (let [response (-> (format-subject-privileges-lookup-request username subject-id params)
                       (grouper-post "grouperPrivileges")
                       :WsGetGrouperPrivilegesLiteResult)]
      [(filter-privileges (:privilegeResults response) params)
       (:subjectAttributeNames response)])))

;; Add/remove group/folder privileges

(defn- format-group-folder-privileges-add-remove-request
  [entity-lookup allowed? username subject-ids privilege-names]
  {:WsRestAssignGrouperPrivilegesRequest
   (assoc entity-lookup
     :actAsSubjectLookup (act-as-subject-lookup username)
     :includeSubjectDetail "T"
     :includeGroupDetail   "T"
     :clientVersion "v2_2_000"
     :privilegeNames privilege-names
     :allowed (if allowed? "T" "F")
     :wsSubjectLookups (vec (for [subject-id subject-ids] {:subjectId subject-id})))})

(defn- format-group-privileges-add-remove-request
  [allowed? username group-name subject-ids privilege-names]
  (format-group-folder-privileges-add-remove-request
    {:wsGroupLookup {:groupName group-name}}
    allowed? username subject-ids privilege-names))

(defn- format-folder-privileges-add-remove-request
  [allowed? username folder-name subject-ids privilege-names]
  (format-group-folder-privileges-add-remove-request
    {:wsStemLookup {:stemName folder-name}}
    allowed? username subject-ids privilege-names))

(defn- add-remove-group-folder-privileges
  [request-body]
  (with-trap [default-error-handler]
    (let [response (-> (grouper-post request-body "grouperPrivileges")
                       :WsAssignGrouperPrivilegesResults)]
      [(first (:results response)) (:subjectAttributeNames response)])))

(defn- add-remove-group-privileges
  [allowed? username group-name subject-ids privilege-names]
  (add-remove-group-folder-privileges
    (format-group-privileges-add-remove-request allowed? username group-name subject-ids privilege-names)))

(defn- add-remove-folder-privileges
  [allowed? username folder-name subject-ids privilege-names]
  (add-remove-group-folder-privileges
    (format-folder-privileges-add-remove-request allowed? username folder-name subject-ids privilege-names)))

(defn add-group-privileges
  [username group-name subject-ids privilege-names]
  (add-remove-group-privileges true username group-name subject-ids privilege-names))

(defn remove-group-privileges
  [username group-name subject-ids privilege-names]
  (add-remove-group-privileges false username group-name subject-ids privilege-names))

(defn update-group-privileges
  [username replace? group-name subject-ids privilege-names]
  (let [privs-to-add    (set privilege-names)
        privs-to-remove (set/difference all-group-privileges privs-to-add)]
    (when (seq privs-to-add)
      (add-group-privileges username group-name subject-ids privs-to-add))
    (when (and replace? (seq privs-to-remove))
      (remove-group-privileges username group-name subject-ids privs-to-remove))))

(defn add-folder-privileges
  [username folder-name subject-ids privilege-names]
  (add-remove-folder-privileges true username folder-name subject-ids privilege-names))

(defn remove-folder-privileges
  [username folder-name subject-ids privilege-names]
  (add-remove-folder-privileges false username folder-name subject-ids privilege-names))

(defn update-folder-privileges
  [username folder-name subject-ids privilege-names]
  (remove-folder-privileges username folder-name subject-ids all-folder-privileges)
  (when (seq privilege-names)
    (add-folder-privileges username folder-name subject-ids privilege-names)))

;; General subject functions.

(defn- subject-id-lookup
  [subject-id]
  (remove-vals nil? {:subjectId subject-id}))

;; Subject lookup.

(defn- format-multi-subject-lookup-request
  [username subject-ids]
  {:WsRestGetSubjectsRequest
   {:actAsSubjectLookup   (act-as-subject-lookup username)
    :includeGroupDetail   "T"
    :includeSubjectDetail "T"
    :wsSubjectLookups     (map subject-id-lookup subject-ids)}})

(defn look-up-subjects
  [username subject-ids]
  (with-trap [default-error-handler]
    (let [response (-> (format-multi-subject-lookup-request username (set subject-ids))
                       (grouper-post "subjects")
                       :WsGetSubjectsResults)]
      [(:wsSubjects response) (:subjectAttributeNames response)])))

;; Subject search.

(defn- format-subject-search-request
  [username search-string]
  {:WsRestGetSubjectsRequest
   {:actAsSubjectLookup   (act-as-subject-lookup username)
    :includeGroupDetail   "T"
    :includeSubjectDetail "T"
    :searchString         search-string}})

(defn subject-search
  [username search-string]
  (with-trap [default-error-handler]
    (let [response (-> (format-subject-search-request username search-string)
                       (grouper-post "subjects")
                       :WsGetSubjectsResults)]
      [(:wsSubjects response) (:subjectAttributeNames response)])))

;; Subject retrieval.

(defn- format-subject-id-lookup-request
  [username subject-id]
  {:WsRestGetSubjectsRequest
   {:actAsSubjectLookup   (act-as-subject-lookup username)
    :includeGroupDetail   "T"
    :includeSubjectDetail "T"
    :wsSubjectLookups     [(subject-id-lookup subject-id)]}})

(defn get-subject
  [username subject-id]
  (with-trap [default-error-handler]
    (let [response (-> (format-subject-id-lookup-request username subject-id)
                       (grouper-post "subjects")
                       :WsGetSubjectsResults)]
      [(first (:wsSubjects response)) (:subjectAttributeNames response)])))

;; Groups for a subject.

(defn- format-groups-for-subject-request
  ([username subject-id details]
   {:WsRestGetGroupsRequest
    {:actAsSubjectLookup   (act-as-subject-lookup username)
     :subjectLookups       [(subject-id-lookup subject-id)]
     :includeGroupDetail   (if details "T" "F")}})
  ([username subject-id details folder-name]
   {:WsRestGetGroupsRequest
    {:actAsSubjectLookup   (act-as-subject-lookup username)
     :subjectLookups       [(subject-id-lookup subject-id)]
     :wsStemLookup {:stemName folder-name}
     :stemScope "ALL_IN_SUBTREE"
     :includeGroupDetail   (if details "T" "F")}}))

(defn- groups-for-subject*
  [request-body]
  (with-trap [default-error-handler]
    (-> (grouper-post request-body "subjects")
        :WsGetGroupsResults
        :results
        first
        :wsGroups)))

(defn groups-for-subject
  [username subject-id details]
  (groups-for-subject* (format-groups-for-subject-request username subject-id details)))

(defn groups-for-subject-folder
  [username subject-id details folder-name]
  (groups-for-subject* (format-groups-for-subject-request username subject-id details folder-name)))

;; Attribute Definition Name search
(defn- format-attribute-name-search-request
  [username search exact?]
  (let [query (if exact?
                  {:wsAttributeDefNameLookups [{:name search}]}
                  {:scope search})]
    {:WsRestFindAttributeDefNamesRequest
      (assoc query
             :actAsSubjectLookup (act-as-subject-lookup username))}))

(defn attribute-name-search
  [username search exact?]
  (with-trap [default-error-handler]
    (-> (format-attribute-name-search-request username search exact?)
        (grouper-post "attributeDefNames")
        :WsFindAttributeDefNamesResults
        :attributeDefNameResults)))

;; Attribute Definition Name add/update

(defn- format-attribute-name-add-update-request
  [update? username attribute-def name display-extension description]
  {:WsRestAttributeDefNameSaveRequest
   {:actAsSubjectLookup (act-as-subject-lookup username)
    :wsAttributeDefNameToSaves [
     {:wsAttributeDefName
      (remove-vals nil? {:attributeDefId (:id attribute-def)
                         :attributeDefName (:name attribute-def)
                         :name name
                         :description description
                         :displayExtension display-extension})
      :saveMode (if update? "UPDATE" "INSERT")}]}})

(defn- format-attribute-name-add-request
  [username attribute-def name display-extension description]
  (format-attribute-name-add-update-request false username attribute-def name display-extension description))

(defn- format-attribute-name-delete-request
  [username attribute-def-name]
  {:WsRestAttributeDefNameDeleteRequest
   {:actAsSubjectLookup (act-as-subject-lookup username)
    :wsAttributeDefNameLookups [{:name attribute-def-name}]}})

(defn- add-update-attribute-name
  [request-body]
  (with-trap [default-error-handler]
    (-> (grouper-post request-body "attributeDefNames")
        :WsAttributeDefNameSaveResults
        :results
        first
        :wsAttributeDefName)))

(defn add-attribute-name
  [username attribute-def name display-extension description]
  (add-update-attribute-name
   (format-attribute-name-add-request username attribute-def name display-extension description)))

(defn delete-attribute-name
  [username attribute-name]
  (add-update-attribute-name
   (format-attribute-name-delete-request username attribute-name)))

;; Permission assignment
;; search/lookup

(defn- format-attribute-def-lookup
  [{:keys [attribute_def_id attribute_def]}]
  (cond attribute_def_id (uuid-lookups [attribute_def_id])
        attribute_def    (name-lookups [attribute_def])))

(defn- format-attribute-def-name-lookup
  [{:keys [attribute_def_name_ids attribute_def_names]}]
  (concat (uuid-lookups attribute_def_name_ids)
          (name-lookups attribute_def_names)))

(defn- format-role-lookup
  [{:keys [role_id role]}]
  (cond role_id (uuid-lookups [role_id])
        role    (role-lookups [role])))

(defn- format-action-names-lookup
  [{:keys [action_names]}]
  (when (seq action_names) action_names))

(defn- format-permission-search-request
  [username {:keys [subject_id immediate_only] :as params}]
  {:WsRestGetPermissionAssignmentsRequest
   (remove-vals nil? {:actAsSubjectLookup (act-as-subject-lookup username)
                      :includePermissionAssignDetail "T"
                      :wsAttributeDefLookups (format-attribute-def-lookup params)
                      :wsAttributeDefNameLookups (format-attribute-def-name-lookup params)
                      :roleLookups (format-role-lookup params)
                      :actions (format-action-names-lookup params)
                      :wsSubjectLookups (subject-lookups [subject_id])
                      :immediateOnly (coerce-boolean immediate_only)})})

(defn permission-assignment-search*
  [request-body]
  (with-trap [default-error-handler]
    (-> (grouper-post request-body "permissionAssignments")
        :WsGetPermissionAssignmentsResults
        :wsPermissionAssigns)))

(defn permission-assignment-search
  [username params]
  (permission-assignment-search* (format-permission-search-request username params)))

;; assign/remove
(defn- format-permission-assign-remove-request
  "Format request. lookups-and-type should have the permissionType key as well as any lookups necessary for that
   type (e.g. type role + roleLookups)"
  [assignment? lookups-and-type username attribute-def-name allowed? action-names]
  {:WsRestAssignPermissionsRequest
    (assoc lookups-and-type
           :permissionAssignOperation (if assignment? "assign_permission" "remove_permission")
           :actAsSubjectLookup (act-as-subject-lookup username)
           :permissionDefNameLookups [{:name attribute-def-name}]
           :disallowed (if allowed? "F" "T")
           :actions action-names)})

(defn- format-permission-assign-request
  [& args]
  (apply format-permission-assign-remove-request true args))

(defn- format-permission-remove-request
  [& args]
  (apply format-permission-assign-remove-request false args))

(defn- role-permissions
  [role-names]
  {:permissionType "role"
   :roleLookups (role-lookups role-names)})

(defn- role-permission
  [role-name]
  (role-permissions [role-name]))

(defn- subject-role-lookup
  [[role-name subject-id]]
  {:wsGroupLookup (role-lookup role-name)
   :wsSubjectLookup (subject-lookup subject-id)})

(defn- membership-permissions
  [roles-and-subjects]
  {:permissionType "role_subject"
   :subjectRoleLookups (mapv subject-role-lookup roles-and-subjects)})

(defn- membership-permission
  [role-name subject-id]
  (membership-permissions [[role-name subject-id]]))

(defn- format-role-permission-assign-request
  [username attribute-def-name role-name allowed? action-names]
  (format-permission-assign-request
    (role-permission role-name)
    username attribute-def-name allowed? action-names))

(defn- format-membership-permission-assign-request
  [username attribute-def-name role-name subject-id allowed? action-names]
  (format-permission-assign-request
    (membership-permission role-name subject-id)
    username attribute-def-name allowed? action-names))

; For remove requests, 'allowed' is ignored. Pass true as a default.
(defn- format-role-permission-remove-request
  [username attribute-def-name role-name action-names]
  (format-permission-remove-request
    (role-permission role-name)
    username attribute-def-name true action-names))

(defn- format-membership-permission-remove-request
  [username attribute-def-name role-name subject-id action-names]
  (format-permission-remove-request
    (membership-permission role-name subject-id)
    username attribute-def-name true action-names))

(defn- assign-remove-permission
  [request-body]
  (with-trap [default-error-handler]
    (-> (grouper-post request-body "permissionAssignments")
        :WsAssignPermissionsResults
        :wsAssignPermissionResults
        first
        :wsAttributeAssigns
        first)))

(defn- format-permission-id-search-request
  [username attribute-def-name role subject-id permission-type]
  {:WsRestGetPermissionAssignmentsRequest
   (remove-vals nil? {:actAsSubjectLookup (act-as-subject-lookup username)
                      :wsAttributeDefNameLookups (name-lookups [attribute-def-name])
                      :roleLookups (role-lookups [role])
                      :wsSubjectLookups (subject-lookups [subject-id])
                      :permissionType permission-type})})

(defn- get-permission-assign-ids
  [username attribute-def-name & [{:keys [role subject-id permission-type]}]]
  (->> (format-permission-id-search-request username attribute-def-name role subject-id permission-type)
       (permission-assignment-search*)
       (distinct-by :attributeAssignId)
       (group-by :permissionType)
       (map-kv (fn [k v] [k (mapv :attributeAssignId v)]))))

(defn- format-permission-assign-id-removal-request
  [username permission-type ids]
  {:WsRestAssignPermissionsRequest
   {:permissionAssignOperation "remove_permission"
    :actAsSubjectLookup (act-as-subject-lookup username)
    :permissionType permission-type
    :wsAttributeAssignLookups (uuid-lookups ids)}})

(defn- remove-permission-assign-ids
  [username permission-type ids]
  (->> (format-permission-assign-id-removal-request username permission-type ids)
       (assign-remove-permission)))

(defn- remove-existing-permissions
  [username attribute-def-name & [params]]
  (->> (get-permission-assign-ids username attribute-def-name params)
       (mapv (fn [[permission-type ids]] (remove-permission-assign-ids username permission-type ids)))))

(defn- remove-existing-role-permissions
  [username attribute-def-name role-name]
  (remove-existing-permissions username attribute-def-name
                               {:role role-name
                                :permission-type "role"}))

(defn remove-existing-membership-permissions
  [username attribute-def-name role-name subject-id]
  (remove-existing-permissions username attribute-def-name
                               {:role role-name
                                :subject-id subject-id
                                :permission-type "role_subject"}))

(defn- assign-role-permissions
  [username attribute-def-name new-role-permissions]
  (let [fmt (fn [[k v]] (format-permission-assign-request v username attribute-def-name true [k]))]
    (->> (group-by :action_name new-role-permissions)
         (map-kv (fn [k v] [k (role-permissions (mapv :role_name v))]))
         (map (comp assign-remove-permission fmt))
         dorun)))

(defn- assign-membership-permissions
  [username attribute-def-name new-membership-permissions]
  (let [fmt (fn [[k v]] (format-permission-assign-request v username attribute-def-name true [k]))]
    (->> (group-by :action_name new-membership-permissions)
         (map-kv (fn [k v] [k (membership-permissions (map (juxt :role_name :subject_id) v))]))
         (map (comp assign-remove-permission fmt))
         dorun)))

(defn replace-permissions
  [username attribute-def-name new-role-permissions new-membership-permissions]
  (remove-existing-permissions username attribute-def-name)
  (assign-role-permissions username attribute-def-name new-role-permissions)
  (assign-membership-permissions username attribute-def-name new-membership-permissions))

(defn assign-role-permission
  [username attribute-def-name role-name allowed? action-names]
  (remove-existing-role-permissions username attribute-def-name role-name)
  (assign-remove-permission
   (format-role-permission-assign-request
    username attribute-def-name role-name allowed? action-names)))

(defn remove-role-permission
  [username attribute-def-name role-name action-names]
  (assign-remove-permission
   (format-role-permission-remove-request
    username attribute-def-name role-name action-names)))

(defn assign-membership-permission
  [username attribute-def-name role-name subject-id allowed? action-names]
  (remove-existing-membership-permissions username attribute-def-name role-name subject-id)
  (assign-remove-permission
   (format-membership-permission-assign-request
    username attribute-def-name role-name subject-id allowed? action-names)))

(defn remove-membership-permission
  [username attribute-def-name role-name subject-id action-names]
  (assign-remove-permission
   (format-membership-permission-remove-request
    username attribute-def-name role-name subject-id action-names)))
