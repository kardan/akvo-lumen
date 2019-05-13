(ns akvo.lumen.lib.transformation.derive-category
  (:require [akvo.lumen.util :as util]
            [akvo.lumen.lib.transformation.engine :as engine]
            [akvo.lumen.lib.dataset.utils :as dataset.utils]
            [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [clojure.set :as set]
            [clojure.set :refer (rename-keys) :as set]
            [clojure.string :as string]
            [clojure.walk :as walk]
            [hugsql.core :as hugsql]
            [clojure.spec.alpha :as s]
            [akvo.lumen.specs.transformation :as transformation.s]))

(hugsql/def-db-fns "akvo/lumen/lib/transformation/derive.sql")
(hugsql/def-db-fns "akvo/lumen/lib/transformation/engine.sql")

(defmethod engine/valid? "core/derive-category"
  [op-spec]
  (s/valid? (transformation.s/op-spec {:op "core/derive-category"}) (walk/keywordize-keys op-spec)))

(defmethod engine/apply-operation "core/derive-category"
  [{:keys [tenant-conn]} table-name columns op-spec]
  (let [op-spec (walk/keywordize-keys op-spec)
        source-column-name (get-in op-spec [:args :source :column :columnName])
        column-title (get-in op-spec [:args :target :column :title])
        uncategorized-value (get-in op-spec [:args :derivation :uncategorizedValue])
        mappings (->> (get-in op-spec [:args :derivation :mappings])
                      (into {}))
        new-column-name (engine/next-column-name columns)
        all-data (all-data tenant-conn {:table-name table-name})]
    (jdbc/with-db-transaction [tenant-conn tenant-conn]
      (add-column tenant-conn {:table-name      table-name
                               :column-type     "text"
                               :new-column-name new-column-name})
      (->> all-data
           (map (fn [i]
                  (set-cell-value tenant-conn
                                  {:value (get mappings (get i (keyword source-column-name)) uncategorized-value)
                                   :rnum (:rnum i)
                                   :column-name new-column-name
                                   :table-name table-name} )))
           doall)
      {:success? true
       :execution-log [(format "Derived category '%s' using column: '%s' and mappings: '%s'"
                               column-title
                               (:title (dataset.utils/find-column (walk/keywordize-keys columns) source-column-name))
                               mappings)]
       :columns (conj columns {"title"      column-title
                               "type"       "text"
                               "sort"       nil
                               "hidden"     false
                               "direction"  nil
                               "columnName" new-column-name})})))
