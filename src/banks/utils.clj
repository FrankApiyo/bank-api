(ns banks.utils
  (:require [cheshire.core :refer [parse-string]]))

(defn get-request-body
  [context]
  (-> context
      (get-in [:request :body])
      slurp
      parse-string))

(defn get-value-from-request-body
  "Parse request body to edn"
  [context value-key default-value]
  (-> context
      (get-in [:request :body])
      slurp
      parse-string
      (get value-key default-value)))

;; (comment
;;   (=
;;    {:ammount 300
;;     :name "Frankline"}
;;    (get-and-update-account-balance
;;     {:request
;;      {:database
;;       {1 {:ammount 200
;;           :name "Frankline"}}}}
;;     1 100)))
