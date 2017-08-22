(ns bekind.core-test
  (:require [bekind.core :refer :all]
            [clojure.core.async :as async]
            [clojure.test :refer :all]
            [taoensso.timbre :as log]))

(declare)


(defn try-stuff [tries]
    (let [count (atom tries)]
      (fn []
        (if (= @count 0)
          :winner!
          (do
            (swap! count dec)
            (throw (ex-info "Dummy exception" {})))))))

(deftest check-retries-that-pass
  (testing "passes on first try"
    (let [output (retry (try-stuff 0) {:retries 0})]
      (is (= :success (:status output)))
      (is (= :winner! (:result output)))
      (is (= 1 (:attempts output)))))
  (testing "passes on second try"
    (log/with-merged-config {:ns-blacklist ["shopkeeper.comms"]}
      (let [output (retry (try-stuff 1) {:retries 1})]
        (is (= :winner! (:result output)))
        (is (= :success (:status output)))
        (is (= 2 (:attempts output))))))
  (testing "passes on second try with retries higher than 1"
    (log/with-merged-config {:ns-blacklist ["shopkeeper.comms"]}
      (let [output (retry (try-stuff 1) {:retries 2})]
        (is (= :winner!  (:result output)))
        (is (= :success (:status output)))
        (is (= 2 (:attempts output)))))))

(deftest check-retries-that-fail
  (testing "fails on first try"
    (log/with-merged-config {:ns-blacklist ["shopkeeper.comms"]}
      (let [<out (async/chan 1)
            output (retry (try-stuff 1) {:retries 0})]
        (is (= :failed (:status output)))
        (is (= 1 (:attempts output)))
        (is (= 1 (count (:failures output))))
        (is (instance? Exception (first (:failures output)))))))
  (testing "fails with one new-retry"
    (log/with-merged-config {:ns-blacklist ["shopkeeper.comms"]}
      (let [<out (async/chan 1)
            output (retry (try-stuff 2) {:retries 1})]
        (is (nil? (:result output)))
        (is (= :failed (:status output)))
        (is (= 2 (:attempts output)))
        (is (= 2 (count (:failures output))))
        (is (and
             (instance? Exception (first (:failures output)))
             (instance? Exception (second (:failures output))))))))
  (testing "fails with retries > 1"
    (log/with-merged-config {:ns-blacklist ["shopkeeper.comms"]}
     (let [<out (async/chan 1)
            output (retry (try-stuff 3) {:retries 2})]
        (is (nil?  (:result output)))
        (is (= :failed (:status output)))
        (is (= 3 (:attempts output)))
        (is (= 3 (count (:failures output))))
        (is (and
             (instance? Exception (first (:failures output)))
             (instance? Exception (second (:failures output)))
             (instance? Exception (last (:failures output)))))))))

(deftest check-retries-timing
  (testing "passes on second try with new-retry of half second"
    (log/with-merged-config {:ns-blacklist ["shopkeeper.comms"]}
      (let [start (System/currentTimeMillis)
            output (retry (try-stuff 1) {:retries 1 :delays [500]})
            end (System/currentTimeMillis)]
        (is (= :winner! (:result output)))
        (is (= :success (:status output)))
        (is (= 2 (:attempts output)))
        (is (<= 500 (- end start) 600)))))
  (testing "passes on fourth try with retries of 200ms"
    (log/with-merged-config {:ns-blacklist ["shopkeeper.comms"]}
      (let [start (System/currentTimeMillis)
            output (retry (try-stuff 3) {:retries 3 :delays [200]})
            end (System/currentTimeMillis)]
        (is (= :winner!  (:result output)))
        (is (= :success (:status output)))
        (is (= 4 (:attempts output)))
        (is (<= 600 (- end start) 700)))))
  (testing "passes on fourth try with retries of 100 200 500"
    (log/with-merged-config {:ns-blacklist ["shopkeeper.comms"]}
      (let [start (System/currentTimeMillis)
            output (retry (try-stuff 3) {:retries 3 :delays [100 200 500]})
            end (System/currentTimeMillis)]
        (is (= :winner!  (:result output)))
        (is (= :success (:status output)))
        (is (= 4 (:attempts output)))
        (is (<= 800 (- end start) 900))))))

(defn try-stuff-fails-other-than-exception [tries]
    (let [count (atom tries)]
      (fn []
        (if (= @count 0)
          {:status 200 :body :winner!}
          (do
            (swap! count dec)
            {:status 500 :body :failed})))))

(deftest check-using-non-exception-failure
  (testing "Use status check for success predicate - successful call"
    (log/with-merged-config {:ns-blacklist ["shopkeeper.comms"]}
     (let [output (retry (try-stuff-fails-other-than-exception 3)
                          {:delays [1] :retries 5 :success? (fn [res] (= 200 (:status res)))})]
        (is (= :winner! (-> output
                            :result
                            :body)))
        (is (= 200 (-> output
                       :result
                       :status)))
        (is (= :success (:status output)))
        (is (= 4 (:attempts output))))))
  (testing "Use status check for success predicate - failure call"
    (log/with-merged-config {:ns-blacklist ["shopkeeper.comms"]}
      (let [output (retry (try-stuff-fails-other-than-exception 5)
                          {:delays [1] :retries 4 :success? (fn [res] (= 200 (:status res)))})]
        (is (nil? (-> output
                      :result
                      :body)))
        (is (nil? (:result output)))
        (is (= :failed (:status output)))
        (is (= 500 (:status (last (:failures output)))))
        (is (= 5 (:attempts output)))))))
