(ns akvo.lumen.lib.transformation.reverse-geocode
  (:require [akvo.lumen.lib.transformation.engine :as engine]
            [akvo.lumen.db.transformation :as db.transformation]
            [akvo.lumen.db.transformation.engine :as db.tx.engine]
            [akvo.lumen.db.transformation.reverse-geocode :as db.tx.reverse-geocode]
            [akvo.lumen.util :as util]
            [clojure.java.jdbc :as jdbc]))

(defmethod engine/valid? "core/reverse-geocode"
  [op-spec]
  (let [{:strs [target source]} (get op-spec "args")]
    (and (string? (get target "title"))
         (util/valid-column-name? (get target "geopointColumn"))
         (util/valid-dataset-id? (get source "datasetId"))
         (util/valid-column-name? (get source "geoshapeColumn"))
         (util/valid-column-name? (get source "mergeColumn")))))

(defn table-qualify [table-name column-name]
  (str table-name "." column-name))

(defn source-table-name [conn {:strs [datasetId]}]
  (-> (db.transformation/latest-dataset-version-by-dataset-id conn {:dataset-id datasetId})
      :table-name))

(defmethod engine/apply-operation "core/reverse-geocode"
  [{:keys [tenant-conn]} table-name columns {:strs [args] :as op-spec}]
  (let [column-name (engine/next-column-name columns)
        {:strs [target source]} args
        geopointColumn (get target "geopointColumn")
        {:strs [mergeColumn geoshapeColumn]} source
        source-table-name (source-table-name tenant-conn source)]
    (db.tx.engine/add-column tenant-conn {:column-type "text"
                      :new-column-name column-name
                      :table-name table-name})
    (db.tx.reverse-geocode/reverse-geocode tenant-conn {:point-column (table-qualify table-name geopointColumn)
                           :shape-column (table-qualify source-table-name geoshapeColumn)
                           :source-column-name (table-qualify source-table-name mergeColumn)
                           :source-table-name source-table-name
                           :target-column-name column-name
                           :target-table-name table-name})
    {:success? true
     :execution-log ["Geocoded"]
     :columns (conj columns
                    {"title" (get target "title")
                     "type" "text"
                     "sort" nil
                     "hidden" false
                     "direction" nil
                     "columnName" column-name})}))

(defmethod engine/columns-used "core/reverse-geocode"
  [applied-transformation columns]
  [(-> applied-transformation :args :target :geopointColumn)])
