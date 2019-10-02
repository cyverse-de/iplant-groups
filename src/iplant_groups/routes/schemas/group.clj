(ns iplant-groups.routes.schemas.group
  (:require [common-swagger-api.schema.groups :as group-schema]
            [schema.core :as s]))

(s/defschema BaseGroup (group-schema/base-group "group"))
(s/defschema Group (group-schema/group "group"))
(s/defschema GroupUpdate (group-schema/group-update "group"))
(s/defschema GroupStub (group-schema/group-stub "group"))
(s/defschema GroupWithDetail (group-schema/group-with-detail "group"))
(s/defschema GroupList (group-schema/group-list "group" "groups"))
(s/defschema GroupListWithDetail (group-schema/group-list-with-detail "group" "groups"))
(s/defschema GroupMembers (group-schema/group-members "group"))
(def GroupMembersUpdate group-schema/GroupMembersUpdate)
(def GroupMembersUpdateResponse group-schema/GroupMembersUpdateResponse)
