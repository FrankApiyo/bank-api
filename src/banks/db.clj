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
                      :db/doc         "Description for each transaction"}

                     {:db/ident       :account/credit
                      :db/valueType   :db.type/long
                      :db/cardinality :db.cardinality/one
                      :db/doc         "how much was removed or added"}])

(defn init-db
  []
  (let [db-uri "datomic:mem://banks"]
    (d/create-database db-uri)
    (d/connect db-uri)))
