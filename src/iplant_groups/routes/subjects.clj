(ns iplant-groups.routes.subjects
  (:use [common-swagger-api.schema]
        [common-swagger-api.schema.subjects]
        [iplant-groups.routes.schemas.group]
        [iplant-groups.routes.schemas.params]
        [iplant-groups.routes.schemas.privileges]
        [ring.util.http-response :only [ok]])
  (:require [iplant-groups.service.subjects :as subjects]))

(defroutes subjects
  (GET "/" []
    :query       [params SearchParams]
    :return      SubjectList
    :summary     "Subject Search"
    :description "This endpoint allows callers to search for subjects by name."
    (ok (subjects/subject-search params)))

  (POST "/lookup" []
    :return      SubjectList
    :query       [params StandardUserQueryParams]
    :body        [body SubjectIdList]
    :summary     "Look Up Multiple Subject IDs"
    :description "This endpoint allows callers to look up multiple subjects by ID in one API call.
    Note: the response body will only contain the users that are found in the subject store."
    (ok (subjects/lookup params body)))

  (context "/:subject-id" []
    :path-params [subject-id :- SubjectIdPathParam]

    (GET "/" []
      :query       [params StandardUserQueryParams]
      :return      Subject
      :summary     "Get Subject Information"
      :description "This endpoint allows callers to get information about a single subject."
      (ok (subjects/get-subject subject-id params)))

    (GET "/groups" []
      :query       [params GroupsForSubjectParams]
      :return      GroupListWithDetail
      :summary     "List Groups for a Subject"
      :description "This endpoint allows callers to list all groups that a subject belongs to."
      (ok (subjects/groups-for-subject subject-id params)))

    (GET "/privileges" []
      :query       [params SubjectPrivilegeSearchQueryParams]
      :return      Privileges
      :summary     "List Subject Privileges"
      :description "This endpoint allows callers to list all of the privileges granted to a subject."
      (ok (subjects/privileges-for-subject subject-id params)))))
