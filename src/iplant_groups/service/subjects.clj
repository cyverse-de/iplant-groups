(ns iplant-groups.service.subjects
  (:require [iplant-groups.clients.grouper :as grouper]
            [iplant-groups.service.format :as fmt]))

(defn lookup
  [{:keys [user]} {subject-ids :subject_ids}]
  (let [[subjects attribute-names] (grouper/look-up-subjects user subject-ids)]
    {:subjects (fmt/format-subjects-ignore-missing attribute-names subjects)}))

(defn subject-search
  [{:keys [user search]}]
  (let [[subjects attribute-names] (grouper/subject-search user search)]
    {:subjects (mapv #(fmt/format-subject attribute-names %) subjects)}))

(defn get-subject
  [subject-id {:keys [user]}]
  (let [[subject attribute-names] (grouper/get-subject user subject-id)]
    (fmt/format-subject attribute-names subject)))

(defn groups-for-subject
  [subject-id {:keys [user folder]}]
  (let [result (if folder
                 (grouper/groups-for-subject-folder user subject-id folder)
                 (grouper/groups-for-subject user subject-id))]
    {:groups (mapv fmt/format-group result)}))

(defn privileges-for-subject
  [subject-id {:keys [user] :as params}]
  (let [[privileges attribute-names] (grouper/get-subject-privileges user subject-id params)]
    {:privileges (mapv #(fmt/format-privilege attribute-names %) privileges)}))
