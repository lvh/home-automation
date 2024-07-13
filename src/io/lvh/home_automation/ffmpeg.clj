(ns io.lvh.home-automation.ffmpeg
  (:require
   [babashka.process :as p]
   [jsonista.core :as json]
   [camel-snake-kebab.core :as csk]
   [babashka.fs :as fs]))

(def kebab-case-mapper
  (json/object-mapper
   {:decode-key-fn  csk/->kebab-case-keyword}))

(defn ffprobe
  "Uses ffprobe to figure out what a source supports.

  Warning: this takes a while."
  [url]
  (->
   (p/sh
    {:out :string}
    "ffprobe" "-v" "quiet" "-print_format" "json"
    "-show_format" "-show_streams" url)
   :out
   (json/read-value kebab-case-mapper)))

(defn take-screenshot
  "Take a screenshot of the given source. Returns a path to the screenshot."
  [url]
  (let [temp-dir (fs/create-temp-dir {:prefix "lvh-home-automation"})
        file-name (str (gensym "screenshot") ".jpeg")
        temp-path (fs/path temp-dir file-name)]
    (p/sh "ffmpeg" "-i" url "-update" "-frames:v" "1" (str temp-path))
    temp-path))
