(ns iplant-groups.service.groups
  (:require [iplant-groups.clients.grouper :as grouper]
            [iplant-groups.service.format :as fmt]
            [iplant-groups.service.subjects :as subjects]
            [iplant-groups.service.util :as util]
            [iplant-groups.util.service :as service]
            [iplant-groups.amqp :as amqp]))

(defn- enqueue-group-propagation
  [{:keys [id] :as group}]
  (amqp/publish-msg (str "index.group." id) "")
  group)

(defn- enqueue-group-propagation-by-name
  [group-name user]
  (when-let [group (grouper/get-group user group-name)]
    (enqueue-group-propagation (fmt/format-group group))))

(defn group-search
  [{:keys [user search folder details]}]
  (let [results  (grouper/group-search user folder search details)
        groups   (if details
                   (->> (mapv fmt/format-group-with-detail results)
                        (subjects/add-creator-details-to-groups user))
                   (mapv fmt/format-group results))]
    {:groups groups}))

(defn get-group
  [group-name {:keys [user]}]
  (if-let [group (grouper/get-group user group-name)]
    (fmt/format-group-with-detail group)
    (service/not-found "group" group-name)))

(defn get-group-by-id
  [group-id {:keys [user]}]
  (if-let [group (grouper/get-group-by-id user group-id)]
    (fmt/format-group-with-detail group)
    (service/not-found "group" group-id)))

(defn get-group-members-by-id
  [group-id {:keys [user member-filter]}]
  (let [[subjects attribute-names] (grouper/get-group-members-by-id user group-id member-filter)]
    {:members (mapv #(fmt/format-subject attribute-names %) subjects)}))

(defn get-group-members
  [group-name {:keys [user member-filter]}]
  (let [[subjects attribute-names] (grouper/get-group-members user group-name member-filter)]
    {:members (mapv #(fmt/format-subject attribute-names %) subjects)}))

(defn get-group-privileges
  [group-name {:keys [user] :as params}]
  (let [[privileges attribute-names] (grouper/get-group-privileges user group-name params)]
    {:privileges (mapv #(fmt/format-privilege attribute-names %) privileges)}))

(defn add-group
  [{:keys [type name description display_extension]} {:keys [user]}]
  (let [group (grouper/add-group user type name display_extension description)
        formatted (fmt/format-group-with-detail group)]
    (enqueue-group-propagation formatted)))

(defn update-group-privileges
  [group-name {:keys [updates]} {:keys [user replace] :or {replace true} :as params}]
  (when replace (util/verify-not-removing-own-privileges user (map :subject_id updates)))
  (doseq [[privileges vs] (group-by (comp set :privileges) updates)]
    (grouper/update-group-privileges user replace group-name (mapv :subject_id vs) privileges))
  (get-group-privileges group-name params))

(defn remove-group-privileges
  [group-name {:keys [updates]} {:keys [user] :as params}]
  (util/verify-not-removing-own-privileges user (map :subject_id updates))
  (doseq [[privileges vs] (group-by (comp set :privileges) updates)]
    (grouper/remove-group-privileges user group-name (mapv :subject_id vs) privileges))
  (get-group-privileges group-name params))

(defn add-group-privilege
  [group-name subject-id privilege-name {:keys [user]}]
  (let [[privilege attribute-names] (grouper/add-group-privileges user group-name [subject-id] [privilege-name])]
    (fmt/format-privilege attribute-names privilege)))

(defn remove-group-privilege
  [group-name subject-id privilege-name {:keys [user]}]
  (util/verify-not-removing-own-privileges user [subject-id])
  (let [[privilege attribute-names] (grouper/remove-group-privileges user group-name [subject-id] [privilege-name])]
    (fmt/format-privilege attribute-names privilege)))

(defn update-group
  [group-name {:keys [name description display_extension]} {:keys [user]}]
  (let [group (grouper/update-group user group-name name display_extension description)
        formatted (fmt/format-group-with-detail group)]
    (enqueue-group-propagation formatted)))

(defn delete-group
  [group-name {:keys [user]}]
  (enqueue-group-propagation (fmt/format-group (grouper/delete-group user group-name))))

(defn replace-members
  [group-name {:keys [members]} {:keys [user]}]
  (let [ret (grouper/replace-group-members user group-name members)]
    (future (enqueue-group-propagation-by-name group-name user))
    {:results (mapv fmt/format-member-subject-update-response ret)}))

(defn add-members
  [group-name {:keys [members]} {:keys [user]}]
  (let [ret (grouper/add-group-members user group-name members)]
    (future (enqueue-group-propagation-by-name group-name user))
    {:results (mapv fmt/format-member-subject-update-response ret)}))

(defn remove-members
  [group-name {:keys [members]} {:keys [user]}]
  (let [ret (grouper/remove-group-members user group-name members)]
    (future (enqueue-group-propagation-by-name group-name user))
    {:results (mapv fmt/format-member-subject-update-response ret)}))

(defn add-member
  [group-name subject-id {:keys [user]}]
  (let [ret (grouper/add-group-member user group-name subject-id)]
    (future (enqueue-group-propagation-by-name group-name user))
    ret))


(defn remove-member
  [group-name subject-id {:keys [user]}]
  (let [ret (grouper/remove-group-member user group-name subject-id)]
    (future (enqueue-group-propagation-by-name group-name user))
    ret))
