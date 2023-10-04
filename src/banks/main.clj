(ns banks.main
  (:require
   [banks.utils :refer [get-value-from-request-body
                        get-request-body]]
   [banks.db :refer [init-db account-schema]]
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
      (reset! database (init-db))
      @(d/transact @database account-schema))))

(defn account-update [conn account-id {:keys [ammount description _name credit]}]
  (let [db (d/db conn)
        [account-id counter-val]
        (first (d/q
                '[:find ?e  ?counter-val
                  :in $ ?e
                  :where [?e :account/counter ?counter-val]]
                db (if (string? account-id)
                     (read-string account-id)
                     account-id)))]
    @(d/transact conn [{:db/id account-id :account/ammount ammount :account/description (or description "")
                        :account/counter (inc counter-val)
                        :account/credit credit}])))

(defn create-account
  [conn name]
  @(d/transact conn [{:account/name name
                      :account/ammount 0
                      :account/counter 0
                      :account/credit 0
                      :account/description ""}]))

(def db-interceptor
  {:name :database-interceptor
   :enter (fn [context]
            (update context :request assoc :database @database))
   :leave (fn [context]
            (if-let [tx-data (:tx-data context)]
              (do
                (mapv
                 (fn [[account-id args]]
                   (account-update @database account-id args))
                 tx-data)
                context)
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
           url (route/url-for :account-view :params {:account-id db-id})
           id (-> (create-account @database name)
                  :tempids
                  vals
                  first)]
       (assoc context
              :response (created
                         {:name name
                          :id id
                          :ammount 0}
                         "Location" url))))})

(defn get-and-update-account-balance
  [account-id diff description]
  (let [db (d/db @database)
        [account-balance account-id account-name counter-val]
        (first
         (d/q
          '[:find ?account-balance ?e ?name ?counter-val
            :in $ ?e
            :where [?e :account/ammount ?account-balance]
            [?e :account/counter ?counter-val]
            [?e :account/name ?name]]
          db (if (string? account-id)
               (read-string account-id)
               account-id)))
        new-ammount (+ diff account-balance)]
    @(d/transact @database [{:db/id account-id :account/ammount new-ammount :account/description description
                             :account/counter (inc counter-val)
                             :account/credit diff}])
    {:ammount new-ammount
     :name account-name
     :id account-id}))

(def account-deposit
  {:name :deposit-to-account
   :enter (fn [context]
            (let [account-id (get-in context [:request :path-params :account-id])
                  withdraw-ammount (get-in context
                                           [:request
                                            :query-params
                                            :ammount])
                  deposit-ammount (get-value-from-request-body
                                   context "ammount" 0)
                  ammount (or withdraw-ammount
                              deposit-ammount)
                  account (get-and-update-account-balance
                           account-id
                           ammount
                           (if deposit-ammount
                             "Account deposit"
                             "Account withdraw"))
                  url (route/url-for :account-view :params {:account-id account-id})]
              (assoc context
                     :response (created account "Location" url))))})

(def account-withdraw
  {:name :withdraw-from-account
   :enter (fn [context]
            (let [ammount (get-value-from-request-body
                           context "ammount" 0)
                  account-id (get-in context [:request :path-params :account-id])
                  db (d/db @database)
                  [balance id account-name] (first
                                             (d/q
                                              '[:find ?account-balance ?e ?name
                                                :in $ ?e
                                                :where [?e :account/ammount ?account-balance]
                                                [?e :account/name ?name]]
                                              db (read-string account-id)))
                  account {:name account-name
                           :ammount balance
                           :id id}]
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
                  ammount (if (string? ammount)
                            (read-string ammount)
                            ammount)
                  account-number (get request-body
                                      "account-number" nil)
                  db (d/db @database)
                  [balance id account-name] (first
                                             (d/q
                                              '[:find ?account-balance ?e ?name
                                                :in $ ?e
                                                :where [?e :account/ammount ?account-balance]
                                                [?e :account/name ?name]]
                                              db (if (string? account-number)
                                                   (read-string account-number)
                                                   account-number)))
                  account-id (get-in context [:request :path-params :account-id])
                  credit-account {:name account-name
                                  :ammount balance
                                  :id id
                                  :description (str "Received from " account-id)}
                  [debit-balance debit-id debit-account-name]
                  (first
                   (d/q
                    '[:find ?account-balance ?e ?name
                      :in $ ?e
                      :where [?e :account/ammount ?account-balance]
                      [?e :account/name ?name]]
                    db (if (string? account-id)
                         (read-string account-id)
                         account-id)))
                  debit-account {:name debit-account-name
                                 :ammount debit-balance
                                 :id debit-id
                                 :description (str "sent to " id)}
                  updated-debit-account
                  (update-in debit-account [:ammount] - ammount)]
              (when (< ammount (:ammount debit-account))
                (assoc context
                       :result updated-debit-account
                       :tx-data [[account-id (assoc updated-debit-account
                                                    :credit (- 0 ammount))]
                                 [account-number
                                  (->
                                   credit-account
                                   (update-in [:ammount] + ammount)
                                   (assoc :credit ammount))]]))))})

(def account-view
  {:name :account-view
   :leave
   (fn [context]
     (let [account-id (get-in context [:request :path-params :account-id])
           db (d/db @database)
           [account-balance _account-id account-name]
           (first
            (d/q
             '[:find ?account-balance ?e ?name
               :in $ ?e
               :where [?e :account/ammount ?account-balance]
               [?e :account/name ?name]]
             db (read-string account-id)))]
       (assoc context :result {:ammount account-balance
                               :name account-name})))})

(def entity-render
  {:name :entity-render
   :leave
   (fn [context]
     (if-let [item (:result context)]
       (assoc context :response (ok item))
       context))})

(def account-audit
  {:name :account-audit
   :leave
   (fn [context]
     (let [account-id (get-in context [:request :path-params :account-id])
           hdb (d/history (d/db @database))
           history (d/q '[:find ?counter-val ?description ?credit
                          :in $ ?e
                          :where [?e :account/ammount ?balance]
                          [?e :account/counter ?counter-val]
                          [?e :account/credit ?credit]
                          [?e :account/description ?description]]
                        hdb (if (string? account-id)
                              (read-string account-id)
                              account-id))]
       (assoc context :result
              (mapv
               (fn [[counter-val description credit]]
                 (merge
                  {:sequence counter-val
                   :description description}
                  (if (pos? credit)
                    {:credit credit}
                    {:debit credit})))
               history))))})

(def routes
  (route/expand-routes
   #{["/account" :post [entity-render db-interceptor account-create]]
     ["/account/:account-id" :get [entity-render account-view db-interceptor] :route-name :account-view]
     ["/account/:account-id/deposit" :post [entity-render db-interceptor account-deposit]]
     ["/account/:account-id/withdraw" :post [entity-render db-interceptor account-withdraw account-deposit] :route-name :withdraw-from-account]
     ["/account/:account-id/send" :post [entity-render db-interceptor send-money]]
     ["/account/:account-id/audit" :get [entity-render account-audit]]}))

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
  (reset! server nil)
  (start-dev))

(comment
  (set-db-atom)
  (start-dev)

  (restart)
  ;; name set correctly
  (io.pedestal.test/response-for (::http/service-fn @server) :post "/account" :body (generate-string {:name "Frankline"}))
  ;; no name set to unamed account; or we ask for name with a 400
  (io.pedestal.test/response-for (::http/service-fn @server) :post "/account")
  ;; test get account
  (io.pedestal.test/response-for (::http/service-fn @server) :get "/account/17592186045418")
  (io.pedestal.test/response-for (::http/service-fn @server) :get "/account/17592186045420")


  ;; deposit x 5
  (io.pedestal.test/response-for (::http/service-fn @server) :post "/account/17592186045418/deposit" :body (generate-string {:ammount 100}))

  ;; withdraw
  (io.pedestal.test/response-for (::http/service-fn @server) :post "/account/17592186045418/withdraw" :body (generate-string {:ammount 100}))

  ;; send
  (io.pedestal.test/response-for (::http/service-fn @server) :post "/account/17592186045418/send" :body (generate-string
                                                                                                         {:ammount 100 :account-number "17592186045420"}))

  ;; test get account
  (io.pedestal.test/response-for (::http/service-fn @server) :get "/account/17592186045418")
  (io.pedestal.test/response-for (::http/service-fn @server) :get "/account/17592186045420")

  (io.pedestal.test/response-for (::http/service-fn @server) :get "/account/17592186045420/audit"))
