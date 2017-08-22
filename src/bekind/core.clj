(ns bekind.core
  (:require [taoensso.timbre :as log]))

(defn retry
  "Takes a function - f - and a map of options. The options include:
     :retries - the number of times to retry if a failure occurs.
     :delays - a vector of delays in ms between each retry. If the number of retries exceeds the number of delays the last delay value will be reused.
     :success? - a function that takes the result of the function f and will return truthy if the result is deemed successful.
  The result of this call is a map containing:
     :status - either :success or :failed
     :attempts - the number of attempts made (i.e. calls made to function f
     :result - the result of the call if successful or nil if failed
     :failures - a vector of the results of any failed calls in the order they occurred"
  [f {:keys [retries delays success?]}]
  {:pre [(integer? retries)
         (or (zero? retries) (pos? retries))
         (every? integer? delays)
         (or (nil? success?) (fn? success?))]}
  (loop [n 0 remaining-delays (if (empty? delays) [100] delays) failures []]
    (if (<= n retries)
      (let [result (try
                     (f)
                     (catch Exception ex
                       (log/warn {:error ex
                                  :will-retry? (<= (inc n) retries)
                                  :delays (rest remaining-delays)})
                       {::fail ex}))]
        (if (or (::fail result)
                (when success? (not (success? result))))
          (do ;; on failure - wait for required delay then loop back recording failure, incrementing attempts (n) and removing used delay if required
            (log/warn {:failed result
                       :will-retry? (<= (inc n) retries)
                       :delays (rest remaining-delays)})
            (Thread/sleep (first remaining-delays))
            (let [new-failures (if (::fail result) (conj failures (::fail result)) (conj failures result))]
              (recur (inc n)
                     (if (< 1 (count remaining-delays)) (rest remaining-delays) remaining-delays)
                     new-failures)))
          {:status :success :attempts (inc n) :result result :failures failures}))
      {:status :failed :attempts n :result nil :failures failures})))
