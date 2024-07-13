(ns io.lvh.home-automation
  (:require
   [io.lvh.home-automation.recon :as recon])
  (:gen-class))

(defn -main
  [& args]
  (println "default gw ip: " (recon/get-default-gateway-ip))
  (println "arp for router: " (recon/ip->mac "192.168.1.1"))
  ;; mDNS resolution creates daemon threads, so we need to explicitly exit
  (System/exit 0))
