(ns banks.utils
  (:require [cheshire.core :refer [parse-string]]))

(defn get-value-from-request-body
  "Parse request body to edn"
  [context value-key default-value]
  (-> context
      (get-in [:request :body])
      slurp
      parse-string
      (get value-key default-value)))

(defn get-and-update-account-balance
  [context account-id diff]
  (let [account (get-in context [:request :database account-id])]
    (update-in account [:ammount] + diff)))

(comment
  (=
   {:ammount 300
    :name "Frankline"}
   (get-and-update-account-balance
    {:request
     {:database
      {1 {:ammount 200
          :name "Frankline"}}}}
    1 100)))
