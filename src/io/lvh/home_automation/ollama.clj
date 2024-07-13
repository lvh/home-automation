(ns io.lvh.home-automation.ollama
  (:require
   [babashka.process :as p]
   [babashka.fs :as fs]
   [hato.client :as hc])
  (:import
   (java.util Base64)))

(defn ensure!
  "Attempt to run a local ollama server in the background.

  If it's already running, this will just fail quietly. If ollama is not
  installed, attempt to install it."
  []
  (when-not (fs/which "ollama")
    (p/sh "brew" "install" "ollama"))
  (p/process "ollama" "serve"))

(defn ^:private image->base64
  [image-spec]
  (let [contents (fs/read-all-bytes image-spec)]
    (-> (Base64/getEncoder) (.encodeToString contents))))

(defn generate!
  [opts]
  (hc/post
   "http://127.0.0.1:11434/api/generate"
   {:form-params (-> opts (update :images (partial map image->base64)))
    :content-type :json}))
