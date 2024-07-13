(ns io.lvh.home-automation.oui
  (:require
   [clojure.data.csv :as csv]
   [clojure.java.io :as io]))

;; (def ouis
;;   (let [ouis (->> "oui.csv" io/resource io/reader csv/read-csv rest)]
;;     (mapv (fn [[_ oui vendor]] (array-map :oui oui :vendor vendor)) ouis)))

;; (def ouis-by-vendor
;;   (-> (group-by :vendor ouis) (update-vals :oui)))
