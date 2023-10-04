(ns bank.main-test
  (:require [io.pedestal.test :refer [response-for]]
            [banks.main :refer [set-db-atom start-dev server stop-dev]]
            [cheshire.core :refer [generate-string]]
            [io.pedestal.http :as http]
            [clojure.test :refer [is testing]]))

(defn select-body-and-status
  [response]
  (select-keys
   response
   [:body :status]))

(testing "Ensure that:"
  (set-db-atom)
  (start-dev)
  (testing "name set correctly"
    (is
     (=
      {:status 201,
       :body "{:name \"Frankline\", :id 17592186045418, :ammount 0}"}
      (select-body-and-status
       (response-for
        (::http/service-fn @server)
        :post "/account"
        :body (generate-string {:name "Frankline"}))))))
  (testing "no name set to unamed account"
    (is
     (=
      {:body "{:name \"Unamed Account\", :id 17592186045420, :ammount 0}",
       :status 201}
      (select-body-and-status
       (response-for
        (::http/service-fn @server)
        :post "/account")))))
  (testing "Test get account"
    (is
     (=
      {:body "{:ammount 0, :name \"Frankline\"}", :status 200}
      (select-body-and-status
       (response-for
        (::http/service-fn @server)
        :get "/account/17592186045418")))))
  (testing "Test get account"
    (is
     (=
      {:body "{:ammount 0, :name \"Unamed Account\"}",
       :status 200}
      (select-body-and-status
       (response-for
        (::http/service-fn @server)
        :get "/account/17592186045420")))))
  (testing "Deposit"
    (is (=
         {:body "{:ammount 100, :name \"Frankline\", :id 17592186045418}"
          :status 201}
         (select-body-and-status
          (response-for
           (::http/service-fn @server)
           :post "/account/17592186045418/deposit"
           :body (generate-string {:ammount 100}))))))
  (testing "Deposit"
    (is (=
         {:status 201
          :body "{:ammount 200, :name \"Frankline\", :id 17592186045418}"}
         (select-body-and-status
          (response-for
           (::http/service-fn @server)
           :post "/account/17592186045418/deposit"
           :body (generate-string {:ammount 100}))))))
  (testing "Deposit"
    (is (=
         {:status 201
          :body "{:ammount 300, :name \"Frankline\", :id 17592186045418}"}
         (select-body-and-status
          (response-for
           (::http/service-fn @server)
           :post "/account/17592186045418/deposit"
           :body (generate-string {:ammount 100}))))))
  (testing "Deposit"
    (is (=
         {:body "{:ammount 400, :name \"Frankline\", :id 17592186045418}"
          :status 201}
         (select-body-and-status
          (response-for
           (::http/service-fn @server)
           :post "/account/17592186045418/deposit"
           :body (generate-string {:ammount 100}))))))
  (testing "Withdraw"
    (is (=
         {:body "{:ammount 300, :name \"Frankline\", :id 17592186045418}"
          :status 201}
         (select-body-and-status
          (response-for
           (::http/service-fn @server)
           :post "/account/17592186045418/withdraw"
           :body (generate-string {:ammount 100}))))))
  (testing "Send"
    (is (=
         {:status 200,
          :body "{:name \"Frankline\", :ammount 200, :id 17592186045418, :description \"sent to 17592186045420\"}"}
         (select-body-and-status
          (response-for
           (::http/service-fn @server)
           :post "/account/17592186045418/send"
           :body (generate-string
                  {:ammount 100 :account-number "17592186045420"}))))))
  (testing "Audit"
    (is (=
         {:body "[{:sequence 1, :description \"\", :debit -100} {:sequence 1, :description \"\", :credit 100} {:sequence 3, :description \"\", :debit -100} {:sequence 5, :description \"Account deposit\", :debit 0} {:sequence 3, :description \"\", :credit 100} {:sequence 5, :description \"\", :debit -100} {:sequence 5, :description \"\", :credit 100} {:sequence 3, :description \"Account deposit\", :debit 0} {:sequence 1, :description \"sent to 17592186045420\", :debit 0} {:sequence 1, :description \"Account deposit\", :debit 0} {:sequence 5, :description \"sent to 17592186045420\", :debit 0} {:sequence 3, :description \"sent to 17592186045420\", :debit 0} {:sequence 5, :description \"sent to 17592186045420\", :debit -100} {:sequence 5, :description \"sent to 17592186045420\", :credit 100} {:sequence 3, :description \"sent to 17592186045420\", :debit -100} {:sequence 3, :description \"sent to 17592186045420\", :credit 100} {:sequence 1, :description \"\", :debit 0} {:sequence 5, :description \"Account deposit\", :credit 100} {:sequence 3, :description \"\", :debit 0} {:sequence 5, :description \"Account deposit\", :debit -100} {:sequence 3, :description \"Account deposit\", :debit -100} {:sequence 5, :description \"\", :debit 0} {:sequence 3, :description \"Account deposit\", :credit 100} {:sequence 1, :description \"Account deposit\", :debit -100} {:sequence 1, :description \"Account deposit\", :credit 100} {:sequence 0, :description \"sent to 17592186045420\", :credit 100} {:sequence 2, :description \"sent to 17592186045420\", :debit -100} {:sequence 0, :description \"sent to 17592186045420\", :debit -100} {:sequence 0, :description \"\", :debit -100} {:sequence 0, :description \"\", :credit 100} {:sequence 2, :description \"\", :debit -100} {:sequence 2, :description \"\", :credit 100} {:sequence 6, :description \"Account deposit\", :debit 0} {:sequence 4, :description \"\", :debit -100} {:sequence 4, :description \"Account deposit\", :debit 0} {:sequence 4, :description \"\", :credit 100} {:sequence 6, :description \"\", :debit -100} {:sequence 2, :description \"Account deposit\", :debit 0} {:sequence 0, :description \"sent to 17592186045420\", :debit 0} {:sequence 6, :description \"\", :credit 100} {:sequence 0, :description \"Account deposit\", :debit 0} {:sequence 4, :description \"sent to 17592186045420\", :debit 0} {:sequence 2, :description \"sent to 17592186045420\", :debit 0} {:sequence 4, :description \"sent to 17592186045420\", :credit 100} {:sequence 6, :description \"sent to 17592186045420\", :debit -100} {:sequence 2, :description \"sent to 17592186045420\", :credit 100} {:sequence 4, :description \"sent to 17592186045420\", :debit -100} {:sequence 6, :description \"sent to 17592186045420\", :credit 100} {:sequence 0, :description \"\", :debit 0} {:sequence 2, :description \"\", :debit 0} {:sequence 6, :description \"Account deposit\", :credit 100} {:sequence 6, :description \"Account deposit\", :debit -100} {:sequence 4, :description \"Account deposit\", :credit 100} {:sequence 4, :description \"\", :debit 0} {:sequence 2, :description \"Account deposit\", :credit 100} {:sequence 6, :description \"\", :debit 0} {:sequence 4, :description \"Account deposit\", :debit -100} {:sequence 0, :description \"Account deposit\", :credit 100} {:sequence 2, :description \"Account deposit\", :debit -100} {:sequence 0, :description \"Account deposit\", :debit -100} {:sequence 1, :description \"sent to 17592186045420\", :debit -100} {:sequence 1, :description \"sent to 17592186045420\", :credit 100} {:sequence 6, :description \"sent to 17592186045420\", :debit 0}]", :status 200}
         (select-body-and-status
          (response-for
           (::http/service-fn @server)
           :get "/account/17592186045418/audit")))))
  (testing "Test get account"
    (is
     (=
      {:body "{:ammount 200, :name \"Frankline\"}", :status 200}
      (select-body-and-status
       (response-for
        (::http/service-fn @server)
        :get "/account/17592186045418")))))
  (testing "Test get account"
    (is
     (=
      {:body "{:ammount 100, :name \"Unamed Account\"}",
       :status 200}
      (select-body-and-status
       (response-for
        (::http/service-fn @server)
        :get "/account/17592186045420")))))
  (stop-dev))

(defn -main
  []
  (clojure.test/run-tests 'bank.main-test))
