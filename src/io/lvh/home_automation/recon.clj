(ns io.lvh.home-automation.recon
  (:require
   [babashka.process :as p]
   [babashka.fs :as fs]
   [clojure.string :as str])
  (:import
   (java.net NetworkInterface InetAddress)
   (org.xbill.DNS Lookup Type ReverseMap PTRRecord SimpleResolver)))

(defn get-ifaces
  "Returns a seq of all local network interfaces."
  []
  (enumeration-seq (NetworkInterface/getNetworkInterfaces)))

(defn get-addrs
  "Returns a seq of all addresses for a given network interface."
  [^NetworkInterface iface]
  (-> iface .getInetAddresses enumeration-seq))

(defn is-local-addr?
  "Returns true if the given address is a local address."
  [^InetAddress addr]
  (.isSiteLocalAddress addr))

(defn get-local-addrs
  "Returns the local addresses for an interface."
  [^NetworkInterface iface]
  (->> iface get-addrs (filter is-local-addr?)))

(defn get-local-ifaces
  "Returns a seq of all local network interfaces with their addresses."
  []
  (->>
   (get-ifaces)
   (map (juxt identity get-local-addrs))
   (filter (comp seq second))))

(comment
  (get-local-ifaces))

(def os-names
  {"Mac OS X" :macos
   "Linux" :linux})

(defn get-os
  []
  (comp os-names (System/getProperty "os.name")))

(defn ip->mac
  "Gets the MAC address and hostname for a given IP address using ARP.

  We could ask the arp tool to also do reverse DNS for us. However, we commonly
  want to use the local LAN DNS server for that, which may not be the
  default (e.g. if the local machine overrides Quad9).

  Note that this shells out to `arp`, which uses the system's cache. It does not
  actually make fresh queries. We're assuming that the other things the system
  is doing will ensure the ARP cache is populated.

  This function intentionally does not attempt reverse DNS resolution. This is
  because we're just trying to find friendly human names, which are likely to
  come from the local gateway's DNS server. That said, there's no guarantee this
  host is using it (e.g. it may be overridden by Quad9)."
  [ip-addr]
  (let [os (get-os)
        arp (str (some fs/which ["arp" "/usr/sbin/arp"]))
        bsd-ly-args (when (= os :linux) ["-a"])
        ip ^String (if (instance? InetAddress ip-addr)
                     (.getHostAddress ^InetAddress ip-addr)
                     ip-addr)
        cmd (concat [arp "-n"] bsd-ly-args [ip])
        out (-> cmd (p/shell {:out :string}) :out)
        [_host _wrapped-ip _at mac] (str/split out #"\s+")]
    mac))

;; Linux:
;; lvh@whiting:~/frigate$ /usr/sbin/arp -a 192.168.1.213
;; 4704GarageWest.localdomain (192.168.1.213) at b4:4c:3b:2a:cc:22 [ether] on br0

;; macOS:
;; ❯ arp 192.168.1.213
;; 4704garagewest.localdomain (192.168.1.213) at b4:4c:3b:2a:cc:22 on en0 ifscope [ethernet]

;; Note: these samples don't use `arp -n` in which case host is replaced by `?`

(comment
  (ip->mac "192.168.1.1"))

(defn pingable?
  [host]
  (-> host InetAddress/getByName (.isReachable 50)))

(defn ping-scan!
  [ip-prefix]
  ;; HACK: I didn't know how to reliably get the netmask for an interface so we
  ;; just assume everything local is a /24 which is close enough for now.
  (->> (range 1 255) (map (fn [host] (str ip-prefix "." host))) (filter pingable?)))

(defn is-tailscale?
  "Returns true if the given address is a Tailscale address. Technically it checks
  for Carrier-grade NAT addresses. But who the heck uses those?!"
  [^InetAddress addr]
  (-> addr .getHostAddress (str/starts-with? "100.100.")))

(defn get-this-tailscale-ipv4-addr
  "Gets the current host's Tailscale IP(v4) address."
  []
  (let [ts (->>
            ["tailscale"
             "/Applications/Tailscale.app/Contents/MacOS/Tailscale"]
            (some fs/which)
            (str))]
    (-> (p/shell {:out :string} ts "ip" "--4") :out str/trim)))

(def ipv4-octet-re
  #"(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)")

(def ipv4-re
  (->> ipv4-octet-re (repeat 4) (str/join "\\.") re-pattern))

(defn get-default-gateway-ip!
  "Consult the OS routing table to find the IP of the default gateway."
  []
  (let [[dst gw] [first second]
        is-ipv4? (partial re-matches ipv4-re)]
    (->>
     (p/shell {:out :string} "netstat" "-nr") :out
     str/split-lines
     rest ;; discard header
     (map (fn [line] (str/split line #"\s+")))
     ;; Destinations will say "0.0.0.0" or "default" depending on OS. Gateway
     ;; entries will sometimes have entries like link#NN because they really
     ;; point to a tun or bridge interface.
     (filter (comp #{"0.0.0.0" "default"} dst))
     (filter (comp is-ipv4? gw))
     first gw)))

(comment
  (get-default-gateway-ip!))

;; Default gateway of 192.0.0.1 usually means you're tethered.

(defn local-reverse-lookup!
  "Do a reverse lookup against a specific resolver. This is useful if you're
  trying to determine local host names."
  [ip dns-server]
  (let [lookup (doto (-> ip ReverseMap/fromAddress (Lookup. Type/PTR))
                 (.setResolver (SimpleResolver. dns-server)))
        records (.run lookup)]
    (when (-> lookup .getResult (= Lookup/SUCCESSFUL))
      (str (.getTarget ^PTRRecord (first records))))))

(defmacro assert*
  "Like `assert` but on success returns the expr.

  Unlike `assert`, this can't be turned off via `*assert*`."
  ([expr]
   (assert* expr nil))
  ([expr msg]
   `(let [result# ~expr]
      (->>
       (str "Assert failed: " ~msg "\n" (pr-str '~expr))
       (new AssertionError) (throw) (when-not result#))
      result#)))
