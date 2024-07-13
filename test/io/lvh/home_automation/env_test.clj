(ns io.lvh.home-automation.env-test
  (:require
   [io.lvh.home-automation.env :as env]
   [clojure.test :as t]
   [clojure.java.io :as io]))

(t/deftest read-env-file-tests
  (t/is (= {"FOO" "bar" "BAZ" "quux"} (env/read-env-file (io/resource "env/test-env")))))

(t/deftest interpolate-tests
  (t/is
   (=
    "http://127.0.0.1:11434/api/generate"
    (env/interpolate-env
     "http://{host}:{port}/api/generate"
     {"host" "127.0.0.1" "port" "11434"}))))
