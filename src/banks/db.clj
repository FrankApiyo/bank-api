(ns banks.db
  (:require [datomic.api :as d]))

(def account-schema [{:db/ident :account/name
                      :db/valueType :db.type/string
                      :db/cardinality :db.cardinality/one
                      :db/doc "The name of the account"}

                     {:db/ident :account/ammount
                      :db/valueType :db.type/long
                      :db/cardinality :db.cardinality/one
                      :db/doc "Ammount in the account"}

                     {:db/ident       :account/counter
                      :db/valueType   :db.type/long
                      :db/cardinality :db.cardinality/one
                      :db/doc         "Current value of the counter"}

                     {:db/ident      :account/description
                      :db/valueType   :db.type/string
                      :db/cardinality :db.cardinality/one
                      :db/doc         "Description for each transaction"}])

(defn init-db
  []
  (let [db-uri "datomic:mem://banks"]
    (d/create-database db-uri)
    (d/connect db-uri)))

(comment
  (def conn (init-db))

  @(d/transact conn account-schema)
  (def first-account [{:account/name "Frankline"
                       :account/ammount 100
                       :account/counter 0}])

  @(d/transact conn first-account)
  (def db (d/db conn))
  (def all-accounts-q '[:find ?account-name ?e
                        :where [?e :account/name ?account-name]])
  (d/q all-accounts-q db)
  (let [[account-id counter-val]
        (first (d/q '[:find ?e ?counter-val
                      :where [?e :account/name "Frankline"
                              ?e :account/counter ?counter-val]]
                    db))]
    @(d/transact conn [{:db/id account-id :account/ammount 100 :account/description "Add a small amount"
                        :account/counter (inc counter-val)}]))
  (let [[account-id counter-val]
        (first (d/q '[:find ?e ?counter-val
                      :where [?e :account/name "Frankline"
                              ?e :account/counter ?counter-val]]
                    db))]
    @(d/transact conn [{:db/id account-id :account/ammount 200 :account/description "Add more money"
                        :account/counter (inc counter-val)}]))
  (d/q
   '[:find ?account-balance ?e
     :where
     [?e :account/name "Frankline"]
     [?e :account/ammount ?account-balance]]
   db)
  (def db (d/db conn))
  (def hdb (d/history db))
  (d/q '[:find ?balance ?description
         :where [?e :account/name "Frankline"]
         [?e :account/ammount ?balance]
         [?e :account/description ?description]]
       hdb))
