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
