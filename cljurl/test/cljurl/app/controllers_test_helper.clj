(ns cljurl.app.controllers-test-helper
  (require [stash.core :as stash])
  (use clj-unit.core cljurl.app.models clj-time.core))

(def shortening-map1 {:slug "short1" :url "http://google.com" :created_at (now)})
(def shortening-map2 {:slug "short2" :url "http://amazon.com" :created_at (now)})

(defmacro with-fixtures
  [& body]
  `(do
     (stash/delete-all +shortening+)
     (doseq [short# [shortening-map1 shortening-map2]]
       (stash/persist-insert (stash/init +shortening+ short#)))
     ~@body))

(defn request
  [app path]
  (let [env {:uri path}
        env (assoc env :request-method :get)]
    (app env)))

(defmacro assert-status
  [expected-status actual-status-form]
  `(let [actual-status# ~actual-status-form]
     (assert-that (= ~expected-status actual-status#)
       (format "Expected status of %s, but got %s"
         ~expected-status actual-status#))))
