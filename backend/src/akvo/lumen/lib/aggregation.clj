(ns akvo.lumen.lib.aggregation
  (:require [akvo.lumen.db.dataset :as db.dataset]
            [akvo.lumen.lib :as lib]
            [akvo.lumen.lib.aggregation.pie :as pie]
            [akvo.lumen.lib.aggregation.line :as line]
            [akvo.lumen.lib.aggregation.bar :as bar]
            [akvo.lumen.lib.aggregation.pivot :as pivot]
            [akvo.lumen.lib.aggregation.scatter :as scatter]
            [akvo.lumen.lib.aggregation.bubble :as bubble]
            [clojure.tools.logging :as log]
            [clojure.java.jdbc :as jdbc]
            [clojure.walk :as walk]))

(defmulti query*
  (fn [tenant-conn dataset visualisation-type query]
    visualisation-type))

(defmethod query* :default
  [tenant-conn dataset visualisation-type query]
  (lib/bad-request {"message" "Unsupported visualisation type"
                    "visualisationType" visualisation-type
                    "query" query}))

(defn query [tenant-conn dataset-id visualisation-type query]
  (jdbc/with-db-transaction [tenant-tx-conn tenant-conn {:read-only? true}]
    (if-let [dataset (db.dataset/table-name-and-columns-by-dataset-id tenant-tx-conn {:id dataset-id})]
      (try
        (query* tenant-tx-conn (update dataset :columns (comp walk/keywordize-keys vec)) visualisation-type query)
        (catch clojure.lang.ExceptionInfo e
          (log/warn e :query query :visualisation-type visualisation-type)
          (lib/bad-request (merge {:message (.getMessage e)}
                                  (ex-data e)))))
      (lib/not-found {"datasetId" dataset-id}))))

(defmethod query* "pivot"
  [tenant-conn dataset _ query]
  (pivot/query tenant-conn dataset query))

(defmethod query* "pie"
  [tenant-conn dataset _ query]
  (pie/query tenant-conn dataset query))

(defmethod query* "donut"
  [tenant-conn dataset _ query]
  (pie/query tenant-conn dataset query))

(defmethod query* "line"
  [tenant-conn dataset _ query]
  (line/query tenant-conn dataset query))

(defmethod query* "bar"
  [tenant-conn dataset _ query]
  (bar/query tenant-conn dataset query))

(defmethod query* "scatter"
  [tenant-conn dataset _ query]
  (scatter/query tenant-conn dataset query))

(defmethod query* "bubble"
  [tenant-conn dataset _ query]
  (bubble/query tenant-conn dataset query))
