(ns akvo.lumen.lib.transformation.derive
  (:require [akvo.lumen.lib.dataset.utils :as dataset.utils]
            [akvo.lumen.lib.transformation.derive.js-engine :as js-engine]
            [akvo.lumen.lib.transformation.engine :as engine]
            [akvo.lumen.util :as util]
            [akvo.lumen.db.transformation.engine :as db.tx.engine]
            [akvo.lumen.db.transformation.derive :as db.tx.derive]
            [clj-time.coerce :as tc]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.walk :as walk]))


(defn row-template-format [s]
  (let [double-quote-template "row[\"%s\"]"
        single-quote-template "row['%s']"]
    (if (re-find #"row\[" s)
      (if (re-find #"row\[\'" s)
        single-quote-template
        (if (re-find #"row\[\"" s)
          double-quote-template
          (throw (ex-info (str "Problem with js row template with code: " s) {:s s}))))
      single-quote-template)))

(defn columnName>columnTitle
  "Replace code column references and fall back to use code pattern if there is no
  references."
  [computed columns]
  (let [columns (walk/keywordize-keys columns)]
    (reduce (fn [code {:strs [column-name id pattern]}]
              (if column-name
                (str/replace code id (format (row-template-format pattern)
                                             (-> (try (dataset.utils/find-column columns column-name)
                                                      (catch Exception e nil))
                                                 :title)))
                (str/replace code id pattern)))
            (get computed "template")
            (get computed "references"))))

(defn parse-row-object-references
  "Parse js code and return a sequence of row-references e.g. row.foo row['foo']
  or row[\"foo\"]. For every reference return a tuple with matched pattern and
  the row column as in [\"row.foo\" \"foo\"]."
  [code]
  (let [re #"(?U)row.([\w\d]+)|row(?:\[')([^']*)(?:'\])|row(?:\[\")([^\"]*)(?:\"\])"
        refs (map #(remove nil? %) (re-seq re code))]
    (if (empty? refs)
      `([~code ~code])
      refs)))

(defn compute-transformation-code
  "analyses code to find columns relations between column-title and column-name when using js code"
  [code columns]
  (let [columns (walk/keywordize-keys columns)]
    (reduce (fn [m [pattern column-title]]
              (let [id (str (util/squuid))]
                (-> m
                    (update-in ["template"] #(str/replace % pattern id))
                    (update-in ["references"]
                               #(conj % {"id" id
                                         "pattern" pattern
                                         "column-name" (try
                                                         (:columnName
                                                          (dataset.utils/find-column columns column-title :title))
                                                         (catch Exception e nil))})))))
            {"template" code
             "references" []}
            (parse-row-object-references code))))

(defmethod engine/adapt-transformation "core/derive"
  [op-spec older-columns new-columns]
  (update-in op-spec ["args" "code"]
             #(columnName>columnTitle (compute-transformation-code % older-columns) new-columns)))

(defn lumen->pg-type [type]
  (condp = type
    "text"   "text"
    "number" "double precision"
    "date"   "timestamptz"))

(defn args [op-spec]
  (let [{code         "code"
         column-title "newColumnTitle"
         column-type  "newColumnType"} (engine/args op-spec)]
    {::code code ::column-title column-title ::column-type column-type}))

(defmethod engine/valid? "core/derive"
  [op-spec]
  (let [{:keys [::code
                ::column-title
                ::column-type]} (args op-spec)]
    (and (string? column-title) 
         (util/valid-type? column-type)
         (#{"fail" "leave-empty" "delete-row"} (engine/error-strategy op-spec))
         (js-engine/evaluable? code))))

(defn js-execution>sql-params [js-seq result-kw]
  (->> js-seq
       (filter (fn [[j r i]]
                 (= r result-kw)))
       (map (fn [[i _ v]] [i v]))))

(defn set-cells-values! [conn opts data]
  (->> data
       (map (fn [[i v]] (db.tx.derive/set-cell-value conn (merge {:value v :rnum i} opts))))
       doall))

(defn delete-rows! [conn opts data]
  (->> data
       (map (fn [[i]] (db.tx.derive/delete-row conn (merge {:rnum i} opts))))
       doall))

(defmethod engine/apply-operation "core/derive"
  [{:keys [tenant-conn]} table-name columns op-spec]
  (engine/columns-used (walk/keywordize-keys op-spec) columns)
  (jdbc/with-db-transaction [conn tenant-conn]
    (let [{:keys [::code
                  ::column-title
                  ::column-type]} (args op-spec)
          new-column-name         (engine/next-column-name columns)
          row-fn                  (js-engine/row-transform-fn {:columns     columns
                                                               :code        code
                                                               :column-type column-type})
          js-execution-seq        (->> (db.tx.derive/all-data conn {:table-name table-name})
                                       (map (fn [i]
                                              (try
                                                [(:rnum i) :set-value! (row-fn i)]
                                                (catch Exception e
                                                  (condp = (engine/error-strategy op-spec)
                                                    "leave-empty" [(:rnum i) :set-value! nil]
                                                    "delete-row"  [(:rnum i) :delete-row!]
                                                    "fail"        (throw e) ;; interrupt js execution
                                                    ))))))
          base-opts               {:table-name  table-name
                                   :column-name new-column-name}]
      (db.tx.engine/add-column conn {:table-name      table-name
                        :column-type     (lumen->pg-type column-type)
                        :new-column-name new-column-name})
      (set-cells-values! conn base-opts (js-execution>sql-params js-execution-seq :set-value!))
      (delete-rows! conn base-opts (js-execution>sql-params js-execution-seq :delete-row!))      
      {:success?      true
       :execution-log [(format "Derived columns using '%s'" code)]
       :columns       (conj columns {"title"      column-title
                                     "type"       column-type
                                     "sort"       nil
                                     "hidden"     false
                                     "direction"  nil
                                     "columnName" new-column-name})})))

(defmethod engine/columns-used "core/derive"
  [applied-transformation columns]
  (let [code (-> applied-transformation :args :code)
        computed (compute-transformation-code code columns)]
    (reduce (fn [c r]
              (conj c (if-let [column-name (:column-name r)]
                        column-name
                        (throw (ex-info (format "Column '%s' doesn't exist." (last (last (parse-row-object-references(:pattern r)))))
                                        {:transformation applied-transformation
                                         :columns columns
                                         :computed computed
                                         :reference r}))))
              ) [] (walk/keywordize-keys (get computed "references")))))
