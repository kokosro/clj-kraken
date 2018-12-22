(ns clj-kraken.core
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [ring.util.codec :as codec])
  (:import (javax.crypto Mac)
           (javax.crypto.spec SecretKeySpec)
           (org.apache.commons.codec.binary Base64)
           (java.security MessageDigest))
  (:gen-class))




(defn sign-req
  [k secret nonce-stamp uri raw] 
  (let [secret-spec (SecretKeySpec. (Base64/decodeBase64 secret)
                                    "HmacSHA512")
        mac (Mac/getInstance "HmacSHA512")
        _ (.init mac secret-spec)
        nonce (str nonce-stamp)
        data (assoc raw :nonce nonce)
        form-str (codec/form-encode data)
        digest (MessageDigest/getInstance "SHA-256")
        signature-data (str nonce form-str)
        _ (.update digest (.getBytes signature-data))
        _ (.update mac (.getBytes uri "UTF-8"))
        raw-signature (.doFinal mac (.digest digest))
        signature (String. (Base64/encodeBase64 raw-signature false))]
    signature))



(defmulti question 
  (fn [ask-type _ _ _]
    ask-type))


(defn create-nonce-generator
  [& [start-from]]
  (let [start-from (or start-from (System/currentTimeMillis))
        nonce-holder (atom start-from)]
    (fn []
      (locking nonce-holder 
        (reset! nonce-holder (inc @nonce-holder))))))

(def nonce-generator (create-nonce-generator))

(def log-api-call
  (atom (fn [& _ ] nil)))

(defn change-api-logger 
  [new-logger]
  (reset! log-api-call new-logger))

(defmethod question :private 
  [ask-type uri-x conf raw nonce]
  (let [
        data (assoc raw :nonce nonce)
        c conf 
        uri (str "/" (:version (:api c))
                 "/"
                 (name ask-type) "/" uri-x)
        
        sign (sign-req (:key (:api c))
                       (:secret (:api c)) 
                       nonce
                       uri data)
        headers {"API-Key" (-> c :api :key)
                 "API-Sign" sign}]
    {:timeout 1000
     :headers headers
     :form-params data
     :method :post
     :url (str (:scheme (:api c))
               (:host (:api c))
               uri)
     :insecure? true}))

(defmethod question :public 
  [ask-type uri-x conf raw nonce]
  (let [data raw
        c conf
        uri (str "/" (:version (:api c)) "/"
                 (name ask-type)
                 "/" uri-x)
        ]
    {:timeout 1000
     :form-params data
     :method :post
     :url (str (:scheme (:api c))
               (:host (:api c))
               uri)
     :insecure? true}))


"ask - ofertele puse la vanzare
bid - cererile puse la vanzare"

(defn ask 
  ([uri conf data]
   (ask :public uri conf data nil))
  ([uri conf data kp]
   (ask :public uri conf data kp))
  ([ask-type uri conf data kp]
   (let [nonce (nonce-generator)
         q (question ask-type uri conf data nonce)
         
         r {:nonce nonce
            :uri uri
            :data data 
            :rsp (json/read-str (:body (http/request q))
                                  :key-fn keyword)}
        ;; _ (println r)
         _ (if (and (contains? (:rsp r) :error)
                    (not (nil? (first (:error (:rsp r))))))
             (do 
               (future (try (apply @log-api-call [{:type ask-type 
                                                   :name uri 
                                                   :data data 
                                                   :nonce nonce 
                                                   :response (:rsp r)
                                                   :is_error 1}])
                            (catch Exception e (println e))))
               (println "ERROR ON KRAKEN" r))
             (future (try (apply @log-api-call [{:type ask-type 
                                                 :name uri 
                                                 :data data 
                                                 :nonce nonce 
                                                 :response (:rsp r)
                                                 :is_error 0}])
                          (catch Exception e (println e)))))]
     (if (nil? kp)
       r 
       (get-in r kp)))))


;;;methods - public

(def server-time (partial ask "Time"))
(def assets (partial ask "Assets"))
(def asset-pairs (partial ask "AssetPairs"))
(def ticker (partial ask "Ticker"))
(def ohlc (partial ask "OHLC"))
(def depth (partial ask "Depth"))
(def trades (partial ask "Trades"))
(def spread (partial ask "Spread"))

;;methods - private

(def balance (partial ask :private "Balance"))
(def trade-balance (partial ask :private "TradeBalance"))
(def open-orders (partial ask :private "OpenOrders"))
(def closed-orders (partial ask :private "ClosedOrders"))
(def query-orders (partial ask :private "QueryOrders"))
(def trades-history (partial ask :private "TradesHistory"))
(def balance (partial ask :private "Balance"))
(def query-trades (partial ask :private "QueryTrades"))
(def open-positions (partial ask :private "OpenPositions"))
(def ledgers (partial ask :private "Ledgers"))
(def query-ledgers (partial ask :private "QueryLedgers"))
(def trade-volume (partial ask :private "TradeVolume"))

;;trading - private

(def add-order (partial ask :private "AddOrder"))
(def cancel-order (partial ask :private "CancelOrder"))

;;funding - private

(def deposit-methods (partial ask :private "DepositMethods"))
(def deposit-addresses (partial ask :private "DepositAddresses"))
(def deposit-status (partial ask :private "DepositStatus"))
(def withdraw-info (partial ask :private "WithdrawInfo"))
(def withdraw (partial ask :private "Withdraw"))
(def withdraw-status (partial ask :private "WithdrawStatus"))
(def withdraw-cancel (partial ask :private "WithdrawCancel"))


