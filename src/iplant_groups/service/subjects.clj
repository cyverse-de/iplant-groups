(ns iplant-groups.service.subjects
  (:require [iplant-groups.clients.grouper :as grouper]
            [iplant-groups.service.format :as fmt]))

(defn- get-creator-id
  [group]
  (get-in group [:detail :created_by]))

(defn- get-creator-ids
  [groups]
  (-> (mapv #(get-creator-id %) groups)
      (distinct)))

(defn- match-subject-for-group
  "Returns group with the :created_by_detail key updated with the corresponding subject from subjects"
  [group subjects]
  (let [group-creator-id (get-creator-id group)
        creator-details  (->> (:subjects subjects)
                              (filter #(= (:id %) group-creator-id))
                              first)]
    (if creator-details
      (assoc-in group [:detail :created_by_detail] creator-details)
      group)))

(defn lookup
  [{:keys [user]} {subject-ids :subject_ids}]
  (let [[subjects attribute-names] (grouper/look-up-subjects user subject-ids)]
    {:subjects (fmt/format-subjects-ignore-missing attribute-names subjects)}))

(defn add-creator-details-to-groups
  [user groups]
  (let [creator-ids     (get-creator-ids groups)
        creator-details (lookup user {:subject_ids creator-ids})]
    (mapv #(match-subject-for-group % creator-details) groups)))

(defn subject-search
  [{:keys [user search]}]
  (let [[subjects attribute-names] (grouper/subject-search user search)]
    {:subjects (mapv #(fmt/format-subject attribute-names %) subjects)}))

(defn get-subject
  [subject-id {:keys [user]}]
  (let [[subject attribute-names] (grouper/get-subject user subject-id)]
    (fmt/format-subject attribute-names subject)))

(defn groups-for-subject
  [subject-id {:keys [user folder details]}]
  (let [result (if folder
                 (grouper/groups-for-subject-folder user subject-id details folder)
                 (grouper/groups-for-subject user subject-id details))
        groups (if details
                 (->> (mapv fmt/format-group-with-detail result)
                      (add-creator-details-to-groups user))
                 (mapv fmt/format-group result))]
    {:groups groups}))

(defn privileges-for-subject
  [subject-id {:keys [user] :as params}]
  (let [[privileges attribute-names] (grouper/get-subject-privileges user subject-id params)]
    {:privileges (mapv #(fmt/format-privilege attribute-names %) privileges)}))
