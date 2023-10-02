(ns banks.main
  (:require
   [banks.utils :refer [get-value-from-request-body
                        get-request-body
                        get-and-update-account-balance]]
   [banks.db :refer [init-db]]
   [io.pedestal.http :as http]
   [io.pedestal.http.route :as route]
   [io.pedestal.test :as test]
   [cheshire.core :refer [generate-string]]
   [datomic.api :as d]))

(defonce database (atom nil))

(defn set-db-atom
  []
  (let [db-conn @database]
    (when-not db-conn
      (reset! db-conn (init-db)))))

(defn account-update [conn account-id {:keys {ammount description}}]
  (let [db (d/db conn)
        [account-id counter-val]
        (first (d/q '[:find ?e ?counter-val
                      :where [?e :db/id account-id
                              ?e :account/counter ?counter-val]]
                    db account-id))]
    @(d/transact conn [{:db/id account-id :account/ammount ammount :account/description description
                        :account/counter (inc counter-val)}])))

(defn create-account
  [conn {:keys [name ammount]}]
  @(d/transact conn {:account/name name
                     :account/ammount ammount
                     :account/counter 0}))

(def db-interceptor
  {:name :database-interceptor
   :enter (fn [context]
            (update context :request assoc :database @database))
   :leave (fn [context]
            (if-let [tx-data (:tx-data context)]
              (do
                (mapv
                 (fn [[account-id & args]]
                   (account-update @database account-id args))
                 tx-data)
                (assoc-in context [:request :database] @database))
              context))})

(defn response [status body & {:as headers}]
  {:status status :body body :headers headers})

(def ok (partial response 200))
(def created (partial response 201))
(def accepted (partial response 202))
(def bad-request (partial response 400))


(def account-create
  {:name :account-create
   :enter
   (fn [context]
     (let [name (get-value-from-request-body
                 context "name" "Unamed Account")
           db-id (str (gensym "acc"))
           url (route/url-for :account-view :params {:account-id db-id})]
       (assoc context
              :response (created (create-account @database name) "Location" url))))})

(def account-deposit
  {:name :deposit-to-account
   :enter (fn [context]
            (let [account-id (get-in context [:request :path-params :account-id])
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
                     :tx-data [[account-id account]]
                     :response (created account "Location" url))))})

(def account-withdraw
  {:name :withdraw-from-account
   :enter (fn [context]
            (let [ammount (get-value-from-request-body
                           context "ammount" 0)
                  account-id (get-in context [:request :path-params :account-id])
                  account (get-in context [:request :database account-id])]
              (when (< ammount (:ammount account))
                (assoc-in context [:request
                                   :query-params
                                   :ammount] (- 0 ammount)))))})

(def send-money
  {:name :send-money
   :enter (fn [context]
            (let [request-body (get-request-body
                                context)
                  ammount (get request-body
                               "ammount" 0)
                  account-number (get request-body
                                      "account-number" nil)
                  credit-account (get-in context [:request :database account-number])
                  account-id (get-in context [:request :path-params :account-id])
                  debit-account (get-in context [:request :database account-id])
                  updated-debit-account (update-in debit-account [:ammount] - ammount)]
              (when (< ammount (:ammount debit-account))
                (assoc context
                       :result updated-debit-account
                       :tx-data [[account-id updated-debit-account]
                                 [account-number
                                  (update-in credit-account [:ammount] + ammount)]]))))})

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
     ["/account/:account-id/withdraw" :post [entity-render db-interceptor account-withdraw account-deposit] :route-name :withdraw-from-account]
     ["/account/:account-id/send" :post [entity-render db-interceptor send-money]]}))

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
  (set-db-atom)
  (restart)
  ;; name set correctly
  (io.pedestal.test/response-for (::http/service-fn @server) :post "/account" :body (generate-string {:name "Frankline"}))
  ;; no name set to unamed account; or we ask for name with a 400
  (io.pedestal.test/response-for (::http/service-fn @server) :post "/account")
  ;; test get account
  (io.pedestal.test/response-for (::http/service-fn @server) :get "/account/acc21830")

  ;; deposit
  (io.pedestal.test/response-for (::http/service-fn @server) :post "/account/acc21830/deposit" :body (generate-string {:ammount 100}))
  (io.pedestal.test/response-for (::http/service-fn @server) :post "/account/acc21830/deposit" :body (generate-string {:ammount 100}))
  (io.pedestal.test/response-for (::http/service-fn @server) :post "/account/acc21830/deposit" :body (generate-string {:ammount 100}))

  ;; withdraw
  (io.pedestal.test/response-for (::http/service-fn @server) :post "/account/acc21830/withdraw" :body (generate-string {:ammount 100}))

  ;; send
  (io.pedestal.test/response-for (::http/service-fn @server) :post "/account/acc21830/send" :body (generate-string
                                                                                                   {:ammount 100
                                                                                                    :account-number "acc21833"}))
  (keys @database)
  routes
  @server
  (reset! server nil))
