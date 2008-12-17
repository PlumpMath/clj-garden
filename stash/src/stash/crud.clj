(ns stash.crud
  (:require [clj-jdbc.core :as jdbc])
  (:use stash.utils))

(defn insert-sql
  "Returns the insert sql for the instance."
  [instance]
  (let [model        (instance-model instance)
        quoter       (quoter-by-name model)
        column-names  (column-names model)]
    (str "INSERT INTO " (table-name-str model) " "
         "(" (str-join ", " (map #(name %) column-names)) ") "
         "VALUES "
         "(" (str-join ", "
               (map #(quoter % (% instance)) column-names)) ")")))

(defn update-sql
  "Returns the update sql for the instance."
  [instance]
  (let [model        (instance-model instance)
        column-names (column-names model)
        quoter       (quoter-by-name model)]
    (str "UPDATE " (table-name-str model) " SET "
         (str-join ","
           (map #(str (name %) " = " (quoter (% instance))) column-names)) " "
         "WHERE id = '" (:id instance) "'")))

(defn delete-sql
  "Returns the delete sql for the instance."
  [instance]
  (let [model (instance-model instance)]
    (str "DELETE FROM " (table-name-str model) " "
         "WHERE id = '" (:id instance) "'")))

(defn persist-insert
  "Persists the new instance to the database, returns an instance
  that is no longer marked as new."
  [instance]
  (let [sql (insert-sql instance)]
    (jdbc/with-connection [conn (instance-data-source instance)]
      (jdbc/modify conn sql))
    (with-assoc-meta instance :new false)))

(defn persist-update
  "Persists all of the instance to the database, returns the instance."
  [instance]
  (let [sql (update-sql instance)]
    (jdbc/with-connection [conn (instance-data-source instance)]
      (jdbc/modify conn sql))
    instance)))

(defn delete
  "Delete the instance from the database. Returns an instance indicating that
  it has been deleted."
  [instance]
  (let [sql (delete-sql instance)]
    (jdbc/with-connection [conn (instance-data-source instance)]
      (jdbc/modify conn sql)
      (with-assoc-meta instance :deleted true))))

(defn new
  "Returns an instance of the model with the given attrs having new status."
  [model attrs]
  (with-meta {:model model :new true} (assoc attrs :id (gen-uuid))))

(defn cast-attrs
  "Returns a version of the uncast-attrs cast according to the specifactions
  of the mdoel."
  [model uncast-attrs]
  (let [caster (caster-by-name model)]
    (reduce
      (fn [[name val] cast-attrs] (assoc cast-attrs name (caster val)))
      {}
      uncast-attrs)))

(defn instantiate
  "Returns an instance based on cast versions of the given quoted attrs having 
  non-new status. "
  [model uncast-attrs]
  (with-meta {:model model} (cast-attrs model uncast-atrs)))

(defn create
  "Creates an instance of the model with the attrs. Validations and validations
  and create callbacks."
  [model attrs]
  (save (new model attrs)))

(defn new?
  "Returns true if the instance has not been saved to the database."
  [instance]
  (:new (meta instance)))

(defn save
  "Save the instance to the database. Returns the instance, marked as not new 
  and perhaps trasformed by callbacks."
  [instance]
  (let [new        (new? instance)
        bv-name    (if new :before-validation-on-create
                           :before-validation-on-update)
        av-name    (if new :after-validation-on-create
                           :after-validation-on-update)
        bs-name    (if new :before-create :before-update)
        as-name    (if new :after-create  :after-update)
        persist-fn (if new persist-insert persist-update)]
    (let [[bv-instance bv-success] (run-callbacks bv-name instance)]
      (if-not bv-success
        bv-instance
        (let [v-instance (validated instance)]
          (if-not (valid? v-instance)
            v-instance
            (let [[av-instance av-success] (run-callbacks av-name instance)
                  [bs-instance bs-success] (run-callbacks bs-name instance)]
              (if-not bs-success
                bs-instance
                (let [s-instance (persist instance)
                      [as-instance as-success] (run-callbacks as-name instance)]
                  as-instance)))))))))

(defn destroy
  "Deletes the instance, running before- and after- destroy callbacks.
   Returns the instance, which is marked as deleted if appropriate."
  [instance]
  (let [[bd-instance bd-success] (run-callbacks :before-destroy instance)]
    (if-not bd-success
      bd-instance
      (do
        (let [d-instance (delete instance)
              [ad-instance ad-success] (run-callbacks :after-destroy instance)]
          ad-instance)))))