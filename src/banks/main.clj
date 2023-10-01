(ns banks.main
  (:require [banks.utils :refer [get-value-from-request-body
                                 get-and-update-account-balance]]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.test :as test]
            [utils :refer get-value-from-request-body]
            [cheshire.core :refer [generate-string]]))

(defonce database (atom {}))

(def db-interceptor
  {:name :database-interceptor
   :enter (fn [context]
            (update context :request assoc :database @database))
   :leave (fn [context]
            (if-let [[op & args] (:tx-data context)]
              (do
                (apply swap! database op args)
                (assoc-in context [:request :database] @database))
              context))})

(defn response [status body & {:as headers}]
  {:status status :body body :headers headers})

(def ok (partial response 200))
(def created (partial response 201))
(def accepted (partial response 202))

(defn make-account [name]
  {:name name})

(def account-create
  {:name :account-create
   :enter
   (fn [context]
     (let [name (get-value-from-request-body
                 context "name" "Unamed Account")
           db-id (str (gensym "acc"))
           url (route/url-for :account-view :params {:account-id db-id})
           new-account (make-account name)]
       (assoc context
              :tx-data [assoc db-id new-account]
              :response (created new-account "Location" url))))})

(def account-deposit
  {:name :deposit-to-account
   :enter (fn [context]
            (let [account-id (get-in context [:request :query-params :account-id])
                  ammount (or
                           (get-in context
                                   [:request
                                    :query-params
                                    :ammount])
                           (get-value-from-request-body
                            context "ammount" 0))
                  account (get-and-update-account-balance
                           context
                           account-id
                           ammount)
                  url (route/url-for :account-view :params {:account-id account-id})]
              (assoc context
                     :tx-data [assoc account-id account]
                     :response (created account "Location" url))))})

(def account-withdraw
  {:name :withdraw-from-account
   :enter (fn [context]
            (let [ammount (get-value-from-request-body
                           context "ammount" 0)]
              ;; TODO: check if there's money to withdraw
              (assoc-in context [:request
                                 :query-params
                                 :ammount] (- ammount))))})

(def account-view
  {:name :account-view
   :leave
   (fn [context]
     (let [account-id (get-in context [:request :path-params :account-id])
           account (get-in context [:request :database account-id])]
       (assoc context :result account)))})

(def entity-render
  {:name :entity-render
   :leave
   (fn [context]
     (if-let [item (:result context)]
       (assoc context :response (ok item))
       context))})

(def routes
  (route/expand-routes
   #{["/account" :post [entity-render db-interceptor account-create]]
     ["/account/:account-id" :get [entity-render account-view db-interceptor] :route-name :account-view]
     ["/account/:account-id/deposit" :post [entity-render db-interceptor account-deposit]]
     ["/account/:account-id/deposit" :post [entity-render db-interceptor account-withdraw account-deposit] :route-name :withdraw-from-account]}))

(def service-map
  {::http/routes routes
   ::http/type :jetty
   ::http/port 8890})

(defn start []
  (http/start (http/create-server service-map)))

(defonce server (atom nil))

(defn start-dev []
  (reset! server
          (http/start (http/create-server
                       (assoc service-map
                              ::http/join? false)))))

(defn stop-dev
  []
  (http/stop @server))

(defn restart []
  (stop-dev)
  (start-dev))

(comment
  (restart)
  ;; name set correctly
  (io.pedestal.test/response-for (::http/service-fn @server) :post "/account" :body (generate-string {:name "Frankline"}))
  ;; no name set to unamed account; or we ask for name with a 400
  (io.pedestal.test/response-for (::http/service-fn @server) :post "/account")
  ;; test get account
  (io.pedestal.test/response-for (::http/service-fn @server) :get "/account/acc21826")
  (keys @database)
  routes
  @server
  (reset! server nil))
