(ns io.lvh.home-automation.mdns
  (:import
   (javax.jmdns JmDNS ServiceEvent ServiceListener)))

(defn ^:private service-listener
  [state]
  (proxy [ServiceListener] []
    (serviceAdded [event])
    (serviceRemoved [event])
    (serviceResolved [^ServiceEvent event]
      (let [info (.getInfo event)
            details #::{:name (.getName info)
                        :type (.getType info)
                        :subtype (.getSubtype info)
                        :application  (.getApplication info)
                        :addresses (-> info .getHostAddresses seq)
                        :port (.getPort info)}]
        (swap! state conj details)))))

(defn discover!
  ([]
   (discover! {}))
  ([{::keys [service-type wait-time]
     :or {service-type  "_http._tcp.local."
          wait-time 500}}]
   (let [state (atom [])]
     (with-open [jmdns (JmDNS/create)]
       (.addServiceListener jmdns service-type (service-listener state))
       (Thread/sleep wait-time))
     state)))

(comment
  (discover!))
