(ns io.lvh.home-automation.env
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]))

(defn read-env-file
  "Read a docker-style env file into a map."
  [path]
  (->>  path io/reader line-seq (map #(str/split % #"=" 2)) (into {})))

(defn interpolate-env
  "Interpolate the curly-bracketed placeholder values in the given template
  using the given environment map.

  This is just string interpolation: it does no escaping."
  [template env]
  (str/replace template #"\{(.*?)\}" (comp env second)))
