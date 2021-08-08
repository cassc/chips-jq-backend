(ns chips.utils-test
  (:require
   [clj-time.coerce :as cc]
   [sparrows.cypher :refer [md5]]
   [clj-time.core :refer [date-time time-zone-for-offset]]
   [chips.utils :refer :all]
   [clojure.test :refer :all])
  (:import
   [chips AlgoProfileBuilder SexEnum]
   [pbkdf2 PasswordHash]))



(deftest utils-test
  (let [pwd "12134124"
        md5-pwd (md5 pwd)]
    (testing "new encryption"'
     (is (check-password pwd (encrypt pwd)))
     (is (check-password pwd (encrypt md5-pwd)))
     (is (check-password md5-pwd (encrypt pwd)))
     (is (check-password md5-pwd (encrypt md5-pwd)))))
  #_(let [hashes (repeatedly 10 #(PasswordHash/createHash "mypassword"))]
    (clojure.pprint/pprint hashes)
    (is (every? #(PasswordHash/validatePassword "mypassword" %) hashes)))

  #_(let [builder (doto (AlgoProfileBuilder.)
                 (.withSex SexEnum/M)
                 (.withAge 33)
                 (.withHeight 168)
                 (.withWeight 61)
                 (.withResistance 12)
                 (.withExerciselevel 1))
        profile (.build builder)]
    (prn (.fatScore profile)
         (.visceraScore profile)))
  )
