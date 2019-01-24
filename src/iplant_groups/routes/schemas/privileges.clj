(ns iplant-groups.routes.schemas.privileges
  (:use [common-swagger-api.schema :only [describe StandardUserQueryParams NonBlankString]])
  (:require [common-swagger-api.schema.subjects :as subjects]
            [iplant-groups.routes.schemas.group :as group]
            [iplant-groups.routes.schemas.folder :as folder]
            [schema.core :as s]))

(def ValidFolderPrivileges (s/enum "create" "stem" "stemAttrRead" "stemAttrUpdate"))
(def ValidGroupPrivileges (s/enum "view" "read" "update" "admin" "optin" "optout" "groupAttrRead" "groupAttrUpdate"))
(def PrivilegeInheritanceLevel (s/enum "immediate" "inherited"))

(s/defschema GroupPrivilegeSearchQueryParams
  (assoc StandardUserQueryParams
    (s/optional-key :privilege)
    (describe ValidGroupPrivileges "The privilege name to search for.")

    (s/optional-key :subject-id)
    (describe NonBlankString "The subject ID to search for.")

    (s/optional-key :subject-source-id)
    (describe NonBlankString "The subject source ID to search for.")

    (s/optional-key :inheritance-level)
    (describe PrivilegeInheritanceLevel "Allows the results to be filtered by inheritance level.")))

(s/defschema GroupPrivilegeUpdate
  {:subject_id (describe String "The subject ID.")
   :privileges (describe [ValidGroupPrivileges] "The group privileges to assign.")})

(s/defschema GroupPrivilegeUpdates
  {:updates (describe [GroupPrivilegeUpdate] "The privilege updates to process.")})

(s/defschema GroupPrivilegeRemoval
  {:subject_id (describe String "The subject ID.")
   :privileges (describe [ValidGroupPrivileges] "The group privileges to remove.")})

(s/defschema GroupPrivilegeRemovals
  {:updates (describe [GroupPrivilegeRemoval] "The privilege updates to process.")})

(s/defschema FolderPrivilegeUpdate
  {:subject_id (describe String "The subject ID.")
   :privileges (describe [ValidFolderPrivileges] "The folder privileges to assign.")})

(s/defschema FolderPrivilegeUpdates
  {:updates (describe [FolderPrivilegeUpdate] "The privilege updates to process.")})

(s/defschema Privilege
  {:type
   (describe String "The general type of privilege.")

   :name
   (describe String "The privilege name, under the type")

   (s/optional-key :allowed)
   (describe Boolean "Whether the privilege is marked allowed.")

   (s/optional-key :revokable)
   (describe Boolean "Whether the privilege is marked revokable.")

   :subject
   (describe subjects/Subject "The subject/user with the privilege.")})

(s/defschema GroupPrivilege
  (assoc Privilege
         :group (describe group/Group "The group the permission applies to.")))

(s/defschema FolderPrivilege
  (assoc Privilege
         :folder (describe folder/Folder "The folder the permission applies to.")))

(s/defschema GroupPrivileges
  {:privileges (describe [GroupPrivilege] "A list of group-centric privileges")})

(s/defschema FolderPrivileges
  {:privileges (describe [FolderPrivilege] "A list of folder-centric privileges")})
