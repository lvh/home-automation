(ns io.lvh.home-automation.camera-autoconf
  (:require
   [io.lvh.home-automation.ollama :as ollama]
   [io.lvh.home-automation.ffmpeg :as ffmpeg]
   [clojure.java.io :as io]
   [clojure.string :as str]))

;; Strategies for discovering things that might be cameras:
;;
;; * mDNS (note: cameras so far don't seem to advertise RTSP 🙁)
;; * Ping sweeps

;; Strategies for figuring out if something is a camera:
;;
;; * Check OUI in MAC and compare against list of camera vendors
;; * Try to connect on RTSP main/alt ports

;; Strategies for identifying a specific camera:
;;
;; * mDNS self-advertised host name
;; * Local DNS server reverse resolution

;; Strategies for identifying what a camera is looking at:
;;
;; * Take a screenshot and chuck it into a multimodal LLM

(defn identify-camera-location!
  [screencap]
  (ollama/ensure!)
  (ollama/enerate!
   {:model "bakllava"
    :prompt ""
    :images [screencap]}))

(comment
  (identify-camera-location! (io/resource "camera-screencaps/1.jpeg")))
