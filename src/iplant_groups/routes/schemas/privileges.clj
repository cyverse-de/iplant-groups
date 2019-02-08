(ns iplant-groups.routes.schemas.privileges
  (:use [common-swagger-api.schema :only [describe StandardUserQueryParams NonBlankString]])
  (:require [common-swagger-api.schema.groups :as group-schema]
            [common-swagger-api.schema.subjects :as subjects]
            [iplant-groups.routes.schemas.group :as group]
            [iplant-groups.routes.schemas.folder :as folder]
            [schema.core :as s]))

(def ValidFolderPrivileges (s/enum "create" "stem" "stemAttrRead" "stemAttrUpdate"))
(def ValidGroupPrivileges group-schema/ValidGroupPrivileges)
(def PrivilegeInheritanceLevel (s/enum "immediate" "inherited"))
(def ValidEntityTypes (s/enum "group" "folder"))

(s/defschema PrivilegeSearchQueryParams
  (assoc StandardUserQueryParams
    (s/optional-key :inheritance-level)
    (describe PrivilegeInheritanceLevel "Allows the results to be filtered by inheritance level")))

(s/defschema GroupPrivilegeSearchQueryParams
  (assoc PrivilegeSearchQueryParams
    (s/optional-key :privilege)
    (describe ValidGroupPrivileges "The privilege name to search for")

    (s/optional-key :subject-id)
    (describe NonBlankString "The subject ID to search for")

    (s/optional-key :subject-source-id)
    (describe NonBlankString "The subject source ID to search for")))

(s/defschema SubjectPrivilegeSearchQueryParams
  (assoc PrivilegeSearchQueryParams
    (s/optional-key :entity-type)
    (describe ValidEntityTypes (str "If the entity type is provided, only privileges for the selected entity type "
                                    "will be listed"))

    (s/optional-key :folder)
    (describe NonBlankString (str "If the folder name is provided, only privileges for entities underneath the "
                                  "folder will be listed"))))

(def GroupPrivilegeUpdate group-schema/GroupPrivilegeUpdate)
(def GroupPrivilegeUpdates group-schema/GroupPrivilegeUpdates)
(def GroupPrivilegeRemoval group-schema/GroupPrivilegeRemoval)
(def GroupPrivilegeRemovals group-schema/GroupPrivilegeRemovals)

(s/defschema FolderPrivilegeUpdate
  {:subject_id (describe String "The subject ID")
   :privileges (describe [ValidFolderPrivileges] "The folder privileges to assign")})

(s/defschema FolderPrivilegeUpdates
  {:updates (describe [FolderPrivilegeUpdate] "The privilege updates to process")})

(def BasePrivilege group-schema/Privilege)

(s/defschema GroupPrivilege
  (assoc BasePrivilege
    :group (describe group/Group "The group the permission applies to")))

(s/defschema FolderPrivilege
  (assoc BasePrivilege
    :folder (describe folder/Folder "The folder the permission applies to")))

(s/defschema GroupPrivileges
  {:privileges (describe [GroupPrivilege] "A list of group-centric privileges")})

(s/defschema FolderPrivileges
  {:privileges (describe [FolderPrivilege] "A list of folder-centric privileges")})

(s/defschema Privilege
  (assoc BasePrivilege
    (s/optional-key :group)
    (describe group/Group "The group the permission applies to")

    (s/optional-key :folder)
    (describe folder/Folder "The folder the permission applies to")))

(s/defschema Privileges
  {:privileges (describe [Privilege] "The list of privileges")})
