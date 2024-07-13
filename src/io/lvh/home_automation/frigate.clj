(ns io.lvh.home-automation.frigate
  (:require
   [clj-yaml.core :as yaml]
   [clojure.string :as str]
   [clojure.data.edn :as edn]))

;; # Setup

;; EmpireTech cameras are described below and all configured the same way.
;; Primary stream (0) is high-resolution, H.265, and used for recording.
;; Substream is low-resolution, H.264, and used for detection. Some cameras
;; additionall support substream 2 with a higher resolution.

(def empiretech-camera-info (-> "private/cameras.edn" slurp edn/read-string))

(defn format-empiretech-rtsp-url
  [ip role]
  (str
   "rtsp://{FRIGATE_RTSP_USER}:{FRIGATE_RTSP_PASSWORD}@" ip ":554"
   "/cam/realmonitor?channel=1&subtype=" (case role :record "0" :detect "1")))

(defn stream-name
  [stream-id role]
  (str stream-id (->> role name str/capitalize)))

(def go2rtc
  [camera-info]
  {:go2rtc
   {:streams
    (->>
     (for [{:keys [id ip]} camera-info
           role [:record :detect]
           :let [stream-name (stream-name id role)]]
       [stream-name
        (->>
         (concat
          [(format-empiretech-rtsp-url ip role)]
          (when (= role :record) ;; WebRTC transcode
            [(->>
              [["audio" "copy"] ["audio" "opus"] ["video" "h264"] ["hardware"]]
              (map (partial str/join "="))
              (into [(str "ffmpeg:" stream-name)])
              (str/join "#"))]))
         (into [] (filter some?)))])
     (into {}))

    :webrtc
    {:candidates (-> "private/webrtc-candidates.edn" slurp edn/read-string)}}})

(def go2rtc-trace
  "Configure trace logging for go2rtc."
  {:go2rtc {:log {:exec "trace"}}})

(defn cameras
  [camera-info]
  {:cameras
   (->>
    (for [{:keys [id detect]} camera-info]
      [id
       {:ffmpeg
        {:inputs
         (for [role [:record :detect]]
           {:path (str "rtsp://127.0.0.1:8554/" (stream-name id role))
            :input_args "preset-rtsp-restream"
            :roles [role]})}
        :detect (zipmap [:width :height] detect)
        :live {:stream_name (stream-name id :record)}}])
    (into {}))})

(def mqtt
  {:mqtt
   {:host "{FRIGATE_MQTT_HOST}"
    :user "{FRIGATE_MQTT_USER}"
    :password "{FRIGATE_MQTT_PASSWORD}"}})

(def vaapi {:ffmpeg {:hwaccel_args "preset-vaapi"}})
(def coral {:detectors {:coral {:type "edgetpu" :device "usb"}}})

(def manual-db-path
  "We manually set the database path because since it moved in [0.13.0-beta7] and
  we want to remain compatible across versions. The new default does make more
  sense: the recordings directory is often a NAS, but you want the database to
  be snappy. That does require the config directory to be mounted rw in Docker
  Compose, which we previously didn't do.

  [0.13.0-beta7]: https://github.com/blakeblackshear/frigate/releases/tag/v0.13.0-beta7"
  {:database {:path "/config/frigate.db"}})

(def snapshots
  {:snapshots
   {:enabled true
    :retain {:default 10}
    :timestamp true}})

(def record
  {:record
   {:enabled true
    :events {:retain {:default 10}}}})

(def detect-people
  {:objects {:track [:person]}})

(def detect-cars
  {:objects {:track [:car]}})

(def detect-dogs
  {:objects {:track [:dog]}})

(defn deep-merge [& ms]
  (letfn [(rec-merge [v1 v2]
            (cond
              (map? v1) (merge-with rec-merge v1 v2)
              (or (vector? v1) (set? v1)) (into v1 v2)
              :else v2))]
    (reduce rec-merge ms)))

(def yaml-opts
  {:dumper-options {:flow-style :block}})

(let [cameras (cameras empiretech-camera-info)
      go2rtc (go2rtc empiretech-camera-info)]
  (->
    (deep-merge
      go2rtc go2rtc-trace
      cameras mqtt vaapi coral manual-db-path snapshots record
      detect-people detect-cars detect-dogs)
    (yaml/generate-string yaml-opts)))
