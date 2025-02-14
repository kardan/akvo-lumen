(ns akvo.lumen.lib.transformation.multiple-column.caddisfly
  (:require [akvo.lumen.postgres :as postgres]
            [akvo.lumen.lib.transformation.engine :as engine]
            [akvo.lumen.lib.dataset.utils :refer (find-column)]
            [akvo.lumen.component.caddisfly :refer (get-schema)]
            [akvo.lumen.db.transformation :as db.transformation]
            [akvo.lumen.db.transformation.engine :as db.tx.engine]
            [akvo.lumen.lib.multiple-column :as multiple-column]
            [clojure.walk :as walk]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as string]
            [clojure.tools.logging :as log]))

(defn columns-to-extract [columns selected-column caddisfly-schema extractImage]
  (let [columns   (filter :extract columns)
        base-column (dissoc selected-column :multipleId :type :multipleType :columnName :title)]
    (cond->>
        (map #(assoc base-column :title (:name %) :type (:type %) :caddisfly-test-id (:id %)) columns)
      (and (:hasImage caddisfly-schema) extractImage)
      (cons (with-meta (assoc base-column :type "text" :title (str (:title selected-column) "| Image"))
              {:image true})))))

(defn- test-results
  "`test`-results in caddisfly terminology means the values extracted of caddisfly samples.
  This function assoc values from json parsed data to selected columns to extract."
  [cell-value columns-to-extract]
  (map (fn [c]
         (if (:image (meta c))
           (:image cell-value)
           (:value (some #(when (= (:caddisfly-test-id c) (:id %)) %) (:result cell-value)))))
   columns-to-extract))

(defn apply-operation 
  [{:keys [tenant-conn caddisfly] :as deps} table-name current-columns op-spec]
  (jdbc/with-db-transaction [conn tenant-conn]
    (let [{:keys [onError op args]} op-spec
          selected-column (find-column (walk/keywordize-keys current-columns) (-> args :selectedColumn :columnName))

          caddisfly-schema (if-let [multipleId (:multipleId selected-column)]
                             (get-schema caddisfly multipleId)
                             (throw
                              (ex-info "this column doesn't have a caddisflyResourceUuid currently associated!"
                                       {:message
                                        {:possible-reason "maybe you don't update the flow dataset!? (via client dashboard ...)"}})))

          new-columns (->> (columns-to-extract (:columns args) selected-column caddisfly-schema (:extractImage args))
                           (multiple-column/add-name-to-new-columns current-columns))
          
          _ (log/debug ::apply-operation table-name (:columnName selected-column) onError)
          _ (log/debug :new-columns new-columns :selected-column selected-column :extractImage (:extractImage args))

          add-db-columns (doseq [c new-columns]
                           (db.tx.engine/add-column conn {:table-name      table-name
                                             :column-type     (:type c)
                                             :new-column-name (:id c)}))
          update-db-columns (->> (db.transformation/select-rnum-and-column conn {:table-name table-name :column-name (:columnName selected-column)})
                                 (map
                                  (fn [m]
                                    (let [cell-value (multiple-column/multiple-cell-value m (:columnName selected-column))
                                          cad-results (or (test-results cell-value new-columns)
                                                          (repeat nil))
                                          update-vals (->> (map
                                                            (fn [new-column-name new-column-val]
                                                              [(keyword new-column-name) new-column-val])
                                                            (map :id new-columns)
                                                            cad-results)
                                                           (reduce #(apply assoc % %2) {}))]
                                      (log/debug :update-vals update-vals)
                                      (multiple-column/update-row conn table-name (:rnum m) update-vals "NULL"))))
                                 doall)]
      (log/debug :db-txs selected-column add-db-columns update-db-columns)
      {:success?      true
       :execution-log [(format "Extract caddisfly column %s" (:columnName selected-column))]
       :columns       (into current-columns (vec new-columns))})))
