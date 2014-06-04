(ns taoensso.timbre.appenders.irc-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [irclj.core :as irc]
            [taoensso.timbre.appenders.irc :refer :all]))

(defonce test-conn (atom {}))

(def test-config
  {:irc {:host "irc.example.org"
         :nick "lazylog"
         :name "Lazare Logerus"
         :chan "#logs"
         :user "lazare"
         :pass "s3kret"}})

(deftest test-doc
  (is (< 0 (count (:doc irc-appender)))))

(deftest test-irc
  (with-redefs [irc/connect
                (fn [host port nick & attrs]
                  (reset! test-conn
                          {:rooms []
                           :messages []
                           :config (apply hash-map
                                          :host host
                                          :port port
                                          :nick nick attrs)})
                  test-conn)

                irc/join
                (fn [conn chan] (swap! conn update-in [:rooms] conj chan))

                irc/message
                (fn [conn chan & strs]
                  (swap! conn
                         update-in [:messages]
                         conj {:target  chan
                               :message (str/join " " strs)}))]

    (testing "default constructor"
      (reset! conn nil)
      ((:fn irc-appender) {:ap-config test-config
                           :prefix    "[prefix]"
                           :message   "the message"})
      (= @test-conn
         {:rooms ["#logs"],
          :messages [{:target "#logs", :message "[prefix] the message"}],
          :config
          {:nick "lazylog",
           :username "lazare",
           :port 6667,
           :callbacks {},
           :host "irc.example.org",
           :pass "s3kret",
           :real-name "Lazare Logerus"}}))))
