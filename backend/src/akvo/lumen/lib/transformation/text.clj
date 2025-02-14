(ns akvo.lumen.lib.transformation.text
  "Simple text transforms"
  (:require [akvo.lumen.lib.transformation.engine :as engine]
            [akvo.lumen.db.transformation.text :as db.tx.text]
            [akvo.lumen.util :as util]
            [clojure.tools.logging :as log]))

(defn- transform
  [tenant-conn table-name columns op-spec fn]
  (let [{column-name "columnName"} (engine/args op-spec)]
    (db.tx.text/text-transform tenant-conn {:table-name table-name
                                 :column-name column-name
                                 :fn fn})
    {:success? true
     :execution-log [(format "Text transform %s on %s" fn column-name)]
     :columns columns}))

(defn valid? [op-spec]
  (util/valid-column-name? (get (engine/args op-spec) "columnName")))

(defmethod engine/valid? "core/trim" [op-spec]
  (valid? op-spec))

(defmethod engine/apply-operation "core/trim"
  [{:keys [tenant-conn]} table-name columns op-spec]
  (transform tenant-conn table-name columns op-spec "trim"))

(defmethod engine/columns-used "core/trim"
  [applied-transformation columns]
  [(:columnName (:args applied-transformation))])

(defmethod engine/avoidable-if-missing? "core/trim"
  [applied-transformation]
  true)

(defmethod engine/valid? "core/to-lowercase" [op-spec]
  (valid? op-spec))

(defmethod engine/apply-operation "core/to-lowercase"
  [{:keys [tenant-conn]} table-name columns op-spec]
  (transform tenant-conn table-name columns op-spec "lower"))

(defmethod engine/columns-used "core/to-lowercase"
  [applied-transformation columns]
  [(:columnName (:args applied-transformation))])

(defmethod engine/avoidable-if-missing? "core/to-lowercase"
  [applied-transformation]
  true)

(defmethod engine/valid? "core/to-uppercase" [op-spec]
  (valid? op-spec))

(defmethod engine/apply-operation "core/to-uppercase"
  [{:keys [tenant-conn]} table-name columns op-spec]
  (transform tenant-conn table-name columns op-spec "upper"))

(defmethod engine/columns-used "core/to-uppercase"
  [applied-transformation columns]
  [(:columnName (:args applied-transformation))])

(defmethod engine/avoidable-if-missing? "core/to-uppercase"
  [applied-transformation]
  true)

(defmethod engine/valid? "core/to-titlecase" [op-spec]
  (valid? op-spec))

(defmethod engine/columns-used "core/to-titlecase"
  [applied-transformation columns]
  [(:columnName (:args applied-transformation))])

(defmethod engine/avoidable-if-missing? "core/to-titlecase"
  [applied-transformation]
  true)

(defmethod engine/apply-operation "core/to-titlecase"
  [{:keys [tenant-conn]} table-name columns op-spec]
  (transform tenant-conn table-name columns op-spec "initcap"))

(defmethod engine/valid? "core/trim-doublespace" [op-spec]
  (valid? op-spec))

(defmethod engine/apply-operation "core/trim-doublespace"
  [{:keys [tenant-conn]} table-name columns op-spec]
  (let [{column-name "columnName"} (engine/args op-spec)]
    (db.tx.text/trim-doublespace tenant-conn {:table-name table-name
                                   :column-name column-name})
    {:success? true
     :execution-log [(format "Text transform trim-doublespace on %s" column-name)]
     :columns columns})
  {:success? true
   :columns columns})

(defmethod engine/columns-used "core/trim-doublespace"
  [applied-transformation columns]
  [(:columnName (:args applied-transformation))])


(defmethod engine/avoidable-if-missing? "core/trim-doublespace"
  [applied-transformation]
  true)
